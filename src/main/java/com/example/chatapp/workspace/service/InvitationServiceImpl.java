package com.example.chatapp.workspace.service;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.*;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.model.request.BatchInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;
import com.example.chatapp.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MessageBroker messageBroker;

    @Override
    @Transactional
    public InvitationDto createEmailInvitation(Long workspaceId, Long userId, CreateEmailInvitationRequest request) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        validateInvitationRole(requestingMember, request.getRole());

        Workspace ws = requestingMember.getWorkspace();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(request.getExpiresInHours() != null ? request.getExpiresInHours() : 720, ChronoUnit.HOURS);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(ws)
                .inviter(requestingMember.getUser())
                .token(token)
                .type(InvitationType.EMAIL)
                .email(request.getEmail().trim().toLowerCase())
                .role(request.getRole() != null ? request.getRole() : WorkspaceRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        invitation = workspaceInvitationRepository.save(invitation);

        String inviterName = getInviterDisplayName(requestingMember.getUser());
        emailService.sendWorkspaceInvitation(request.getEmail(), ws.getName(), inviterName, token, null);

        log.info("Created email invitation id={} for email={} to workspace={}", invitation.getId(), request.getEmail(), ws.getName());

        return toInvitationDto(invitation);
    }

    @Override
    @Transactional
    public BatchInvitationResponse createBatchInvitations(Long workspaceId, Long userId, BatchInvitationRequest request) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        validateInvitationRole(requestingMember, request.getRole());

        Workspace ws = requestingMember.getWorkspace();

        // Validate initial channels if specified
        String initialChannelCsv = null;
        if (request.getInitialChannelIds() != null && !request.getInitialChannelIds().isEmpty()) {
            for (Long channelId : request.getInitialChannelIds()) {
                Channel channel = channelRepository.findById(channelId)
                        .orElseThrow(() -> new BadRequestException("Channel not found with ID: " + channelId));
                if (!channel.getWorkspace().getId().equals(workspaceId)) {
                    throw new BadRequestException("Channel does not belong to this workspace: " + channelId);
                }
                if (channel.getType() == ChannelType.PRIVATE) {
                    boolean isMember = channelMemberRepository.isUserChannelMember(channelId, userId);
                    if (!isMember) {
                        throw new BadRequestException("Cannot invite users into private channel you do not belong to: #" + channel.getName());
                    }
                }
            }
            initialChannelCsv = request.getInitialChannelIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }

        User inviterUser = requestingMember.getUser();
        String inviterName = getInviterDisplayName(inviterUser);

        List<BatchInvitationItemResult> results = new ArrayList<>();
        int sentCount = 0;
        int failedCount = 0;

        int expiresInHours = request.getExpiresInHours() != null ? request.getExpiresInHours() : 720; // 30 days
        Instant expiresAt = Instant.now().plus(expiresInHours, ChronoUnit.HOURS);
        WorkspaceRole inviteRole = request.getRole() != null ? request.getRole() : WorkspaceRole.MEMBER;
        String customMsg = request.getCustomMessage() != null && !request.getCustomMessage().isBlank()
                ? request.getCustomMessage().trim()
                : null;

        for (String rawEmail : request.getEmails()) {
            if (rawEmail == null || rawEmail.trim().isEmpty()) {
                continue;
            }
            String email = rawEmail.trim().toLowerCase();

            // 1. Format validation
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                results.add(BatchInvitationItemResult.builder()
                        .email(rawEmail.trim())
                        .status("invalid_email")
                        .message("Invalid email address format")
                        .build());
                failedCount++;
                continue;
            }

            // 2. Check if already a workspace member
            Optional<User> targetUserOpt = userRepository.findByEmail(email);
            if (targetUserOpt.isPresent() && workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserOpt.get().getId())) {
                results.add(BatchInvitationItemResult.builder()
                        .email(email)
                        .status("already_member")
                        .message("User is already a member of this workspace")
                        .build());
                failedCount++;
                continue;
            }

            // 3. Check if an active invitation already exists for this email
            Optional<WorkspaceInvitation> pendingOpt = workspaceInvitationRepository.findByWorkspaceIdAndEmailAndStatus(workspaceId, email, InvitationStatus.PENDING);
            if (pendingOpt.isPresent() && (pendingOpt.get().getExpiresAt() == null || pendingOpt.get().getExpiresAt().isAfter(Instant.now()))) {
                results.add(BatchInvitationItemResult.builder()
                        .email(email)
                        .status("already_invited")
                        .invitationId(pendingOpt.get().getId())
                        .message("An active invitation has already been sent to this email")
                        .build());
                failedCount++;
                continue;
            }

            // 4. Create and dispatch new invitation
            String token = UUID.randomUUID().toString().replace("-", "");
            WorkspaceInvitation inv = WorkspaceInvitation.builder()
                    .workspace(ws)
                    .inviter(inviterUser)
                    .token(token)
                    .type(InvitationType.EMAIL)
                    .email(email)
                    .role(inviteRole)
                    .customMessage(customMsg)
                    .initialChannelIds(initialChannelCsv)
                    .status(InvitationStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();
            inv = workspaceInvitationRepository.save(inv);

            emailService.sendWorkspaceInvitation(email, ws.getName(), inviterName, token, customMsg);

            results.add(BatchInvitationItemResult.builder()
                    .email(email)
                    .status("sent")
                    .invitationId(inv.getId())
                    .message("Invitation sent successfully")
                    .build());
            sentCount++;
        }

        return BatchInvitationResponse.builder()
                .results(results)
                .sentCount(sentCount)
                .failedCount(failedCount)
                .build();
    }

    @Override
    @Transactional
    public InvitationDto createJoinLink(Long workspaceId, Long userId, CreateJoinLinkRequest request) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can generate join links");
        }

        Workspace ws = requestingMember.getWorkspace();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(request.getExpiresInHours() != null ? request.getExpiresInHours() : 168, ChronoUnit.HOURS);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspace(ws)
                .inviter(requestingMember.getUser())
                .token(token)
                .type(InvitationType.SHAREABLE_LINK)
                .role(request.getRole() != null ? request.getRole() : WorkspaceRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .maxUses(request.getMaxUses())
                .expiresAt(expiresAt)
                .build();
        invitation = workspaceInvitationRepository.save(invitation);

        log.info("Created join link token={} for workspace={}", token, ws.getName());

        return toInvitationDto(invitation);
    }

    @Override
    @Transactional
    public InvitationDto getOrCreateShareableJoinLink(Long workspaceId, Long userId) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        Instant now = Instant.now();
        List<WorkspaceInvitation> existingLinks = workspaceInvitationRepository.findByWorkspaceIdAndTypeOrderByCreatedAtDesc(workspaceId, InvitationType.SHAREABLE_LINK);
        for (WorkspaceInvitation link : existingLinks) {
            if ((link.getStatus() == InvitationStatus.PENDING || link.getStatus() == InvitationStatus.INACTIVE)
                    && (link.getExpiresAt() == null || link.getExpiresAt().isAfter(now))) {
                return toInvitationDto(link);
            }
        }

        // None exists or previous expired: create canonical persistent shareable link
        Workspace ws = requestingMember.getWorkspace();
        WorkspaceInvitation newLink = createFreshShareableLink(ws, requestingMember.getUser());
        newLink = workspaceInvitationRepository.save(newLink);

        return toInvitationDto(newLink);
    }

    @Override
    @Transactional
    public InvitationDto toggleShareableJoinLink(Long workspaceId, Long userId) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can toggle shareable join links");
        }

        Instant now = Instant.now();
        List<WorkspaceInvitation> existingLinks = workspaceInvitationRepository.findByWorkspaceIdAndTypeOrderByCreatedAtDesc(workspaceId, InvitationType.SHAREABLE_LINK);
        WorkspaceInvitation targetLink = null;
        for (WorkspaceInvitation link : existingLinks) {
            if ((link.getStatus() == InvitationStatus.PENDING || link.getStatus() == InvitationStatus.INACTIVE)
                    && (link.getExpiresAt() == null || link.getExpiresAt().isAfter(now))) {
                targetLink = link;
                break;
            }
        }

        if (targetLink == null) {
            Workspace ws = requestingMember.getWorkspace();
            targetLink = createFreshShareableLink(ws, requestingMember.getUser());
        } else {
            if (targetLink.getStatus() == InvitationStatus.PENDING) {
                targetLink.setStatus(InvitationStatus.INACTIVE);
            } else {
                targetLink.setStatus(InvitationStatus.PENDING);
            }
        }

        targetLink = workspaceInvitationRepository.save(targetLink);
        return toInvitationDto(targetLink);
    }

    @Override
    @Transactional
    public InvitationDto resetShareableJoinLink(Long workspaceId, Long userId) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can reset shareable join links");
        }

        // Revoke all previous shareable links
        List<WorkspaceInvitation> existingLinks = workspaceInvitationRepository.findByWorkspaceIdAndTypeOrderByCreatedAtDesc(workspaceId, InvitationType.SHAREABLE_LINK);
        for (WorkspaceInvitation link : existingLinks) {
            if (link.getStatus() != InvitationStatus.REVOKED) {
                link.setStatus(InvitationStatus.REVOKED);
                workspaceInvitationRepository.save(link);
            }
        }

        // Create fresh persistent link
        Workspace ws = requestingMember.getWorkspace();
        WorkspaceInvitation freshLink = createFreshShareableLink(ws, requestingMember.getUser());
        freshLink = workspaceInvitationRepository.save(freshLink);

        return toInvitationDto(freshLink);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationDto> getPendingInvitations(Long workspaceId, Long userId) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can view pending invitations");
        }

        List<WorkspaceInvitation> pendingList = workspaceInvitationRepository.findByWorkspaceIdAndTypeAndStatusOrderByCreatedAtDesc(workspaceId, InvitationType.EMAIL, InvitationStatus.PENDING);
        Instant now = Instant.now();

        return pendingList.stream()
                .filter(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(now))
                .map(this::toInvitationDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InvitationDto resendInvitation(Long invitationId, Long userId) {
        WorkspaceInvitation invitation = workspaceInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        Long workspaceId = invitation.getWorkspace().getId();
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can resend invitations");
        }

        if (invitation.getType() != InvitationType.EMAIL || invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Only pending email invitations can be resent");
        }

        // Refresh expiration by 30 days
        invitation.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        invitation = workspaceInvitationRepository.save(invitation);

        String inviterName = getInviterDisplayName(requestingMember.getUser());
        emailService.sendWorkspaceInvitation(invitation.getEmail(), invitation.getWorkspace().getName(), inviterName, invitation.getToken(), invitation.getCustomMessage());

        return toInvitationDto(invitation);
    }

    @Override
    @Transactional
    public void revokeInvitation(Long invitationId, Long userId) {
        WorkspaceInvitation invitation = workspaceInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found with ID: " + invitationId));

        Long workspaceId = invitation.getWorkspace().getId();
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can revoke invitations");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        workspaceInvitationRepository.save(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationDto getInvitationByToken(String token) {
        WorkspaceInvitation invitation = workspaceInvitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found with token: " + token));

        validateInvitationActive(invitation);

        return toInvitationDto(invitation);
    }

    @Override
    @Transactional
    public WorkspaceDto acceptInvitation(String token, Long userId) {
        WorkspaceInvitation invitation = workspaceInvitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found with token: " + token));

        validateInvitationActive(invitation);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        if (invitation.getType() == InvitationType.EMAIL) {
            if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
                throw new BadRequestException("This invitation was sent to a different email address");
            }
        }

        Workspace ws = invitation.getWorkspace();
        boolean alreadyMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(ws.getId(), userId);

        if (!alreadyMember) {
            WorkspaceMember newMember = WorkspaceMember.builder()
                    .workspace(ws)
                    .user(user)
                    .role(invitation.getRole())
                    .build();
            newMember = workspaceMemberRepository.save(newMember);

            // Auto-join designated initial channels
            if (invitation.getInitialChannelIds() != null && !invitation.getInitialChannelIds().isBlank()) {
                String[] channelIdStrs = invitation.getInitialChannelIds().split(",");
                for (String cIdStr : channelIdStrs) {
                    try {
                        Long cId = Long.parseLong(cIdStr.trim());
                        Optional<Channel> chOpt = channelRepository.findById(cId);
                        if (chOpt.isPresent() && chOpt.get().getWorkspace().getId().equals(ws.getId())) {
                            Channel ch = chOpt.get();
                            if (ch.getType() == ChannelType.PRIVATE) {
                                if (!channelMemberRepository.existsByChannelIdAndWorkspaceMemberId(cId, newMember.getId())) {
                                    ChannelMember cm = ChannelMember.builder()
                                            .channel(ch)
                                            .workspaceMember(newMember)
                                            .build();
                                    channelMemberRepository.save(cm);

                                    // Broadcast member added to private channel
                                    String eventsTopic = String.format("/topic/workspace.%d.events", ws.getId());
                                    messageBroker.publishToTopic(eventsTopic, Map.of(
                                            "type", "CHANNEL_MEMBER_ADDED",
                                            "workspaceId", ws.getId(),
                                            "channelId", cId,
                                            "userId", user.getId()
                                    ));
                                }
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            invitation.setUsesCount(invitation.getUsesCount() + 1);
            if (invitation.getType() == InvitationType.EMAIL || (invitation.getMaxUses() != null && invitation.getUsesCount() >= invitation.getMaxUses())) {
                invitation.setStatus(InvitationStatus.ACCEPTED);
            }
            workspaceInvitationRepository.save(invitation);

            // Broadcast member joined event
            String eventsTopic = String.format("/topic/workspace.%d.events", ws.getId());
            messageBroker.publishToTopic(eventsTopic, Map.of(
                    "type", "MEMBER_JOINED",
                    "workspaceId", ws.getId(),
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "fullName", user.getFullName() != null ? user.getFullName() : user.getUsername(),
                    "role", newMember.getRole()
            ));

            log.info("User id={} username={} joined workspace id={} via invitation token={}",
                    user.getId(), user.getUsername(), ws.getId(), token);
        }

        int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
        int channelCount = channelRepository.countByWorkspaceId(ws.getId());

        return WorkspaceDto.builder()
                .id(ws.getId())
                .name(ws.getName())
                .slug(ws.getSlug())
                .iconUrl(ws.getIconUrl())
                .ownerId(ws.getOwner() != null ? ws.getOwner().getId() : null)
                .role(invitation.getRole())
                .memberCount(memberCount)
                .channelCount(channelCount)
                .createdAt(ws.getCreatedAt())
                .updatedAt(ws.getUpdatedAt())
                .build();
    }

    private void validateInvitationRole(WorkspaceMember requestingMember, WorkspaceRole targetRole) {
        if (targetRole == WorkspaceRole.OWNER) {
            throw new BadRequestException("Cannot invite users with the OWNER role");
        }
        if (requestingMember.getRole() == WorkspaceRole.MEMBER && targetRole == WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can send invitations with the ADMIN role");
        }
    }

    private String getInviterDisplayName(User inviter) {
        if (inviter == null) return null;
        return inviter.getFullName() != null && !inviter.getFullName().isBlank()
                ? inviter.getFullName()
                : inviter.getUsername();
    }

    private WorkspaceInvitation createFreshShareableLink(Workspace ws, User inviter) {
        String token = UUID.randomUUID().toString().replace("-", "");
        return WorkspaceInvitation.builder()
                .workspace(ws)
                .inviter(inviter)
                .token(token)
                .type(InvitationType.SHAREABLE_LINK)
                .role(WorkspaceRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(36500, ChronoUnit.DAYS))
                .build();
    }

    private void validateInvitationActive(WorkspaceInvitation invitation) {
        if (invitation.getStatus() == InvitationStatus.INACTIVE) {
            throw new BadRequestException("This shareable join link has been deactivated");
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new BadRequestException("This invitation has been revoked");
        }
        if (invitation.getStatus() == InvitationStatus.EXPIRED || (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now()))) {
            throw new BadRequestException("This invitation has expired");
        }
        if (invitation.getMaxUses() != null && invitation.getUsesCount() >= invitation.getMaxUses()) {
            throw new BadRequestException("This invitation has reached its maximum uses");
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED && invitation.getType() == InvitationType.EMAIL) {
            throw new BadRequestException("This email invitation has already been accepted");
        }
    }

    private InvitationDto toInvitationDto(WorkspaceInvitation inv) {
        Workspace ws = inv.getWorkspace();
        String inviterName = getInviterDisplayName(inv.getInviter());

        List<Long> initialChannelIds = null;
        List<ChannelDto> initialChannels = null;

        if (inv.getInitialChannelIds() != null && !inv.getInitialChannelIds().isBlank()) {
            initialChannelIds = new ArrayList<>();
            for (String part : inv.getInitialChannelIds().split(",")) {
                try {
                    initialChannelIds.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {}
            }

            if (!initialChannelIds.isEmpty()) {
                List<Channel> channels = channelRepository.findAllById(initialChannelIds);
                initialChannels = channels.stream()
                        .filter(ch -> ch.getWorkspace().getId().equals(ws.getId()))
                        .map(ch -> {
                            var convOpt = conversationRepository.findByChannelId(ch.getId());
                            Long convId = convOpt.map(Conversation::getId).orElse(null);
                            return ChannelDto.builder()
                                    .id(ch.getId())
                                    .workspaceId(ws.getId())
                                    .conversationId(convId)
                                    .name(ch.getName())
                                    .slug(ch.getSlug())
                                    .description(ch.getDescription())
                                    .type(ch.getType())
                                    .isArchived(ch.isArchived())
                                    .isMember(true)
                                    .memberCount(1)
                                    .createdAt(ch.getCreatedAt())
                                    .build();
                        })
                        .collect(Collectors.toList());
            }
        }

        return InvitationDto.builder()
                .id(inv.getId())
                .workspaceId(ws.getId())
                .workspaceName(ws.getName())
                .workspaceSlug(ws.getSlug())
                .token(inv.getToken())
                .type(inv.getType())
                .email(inv.getEmail())
                .role(inv.getRole())
                .status(inv.getStatus())
                .expiresAt(inv.getExpiresAt())
                .maxUses(inv.getMaxUses())
                .usesCount(inv.getUsesCount())
                .inviterName(inviterName)
                .customMessage(inv.getCustomMessage())
                .initialChannelIds(initialChannelIds)
                .initialChannels(initialChannels)
                .isActive(inv.getStatus() == InvitationStatus.PENDING)
                .createdAt(inv.getCreatedAt())
                .build();
    }
}
