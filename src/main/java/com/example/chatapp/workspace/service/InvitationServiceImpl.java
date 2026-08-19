package com.example.chatapp.workspace.service;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.InvitationDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;
import com.example.chatapp.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MessageBroker messageBroker;

    @Override
    @Transactional
    public InvitationDto createEmailInvitation(Long workspaceId, Long userId, CreateEmailInvitationRequest request) {
        WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners and admins can send invitations");
        }

        Workspace ws = requestingMember.getWorkspace();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(request.getExpiresInHours() != null ? request.getExpiresInHours() : 72, ChronoUnit.HOURS);

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

        User inviterUser = requestingMember.getUser();
        String inviterName = inviterUser != null && inviterUser.getFullName() != null && !inviterUser.getFullName().isBlank()
                ? inviterUser.getFullName()
                : (inviterUser != null ? inviterUser.getUsername() : null);

        emailService.sendWorkspaceInvitation(request.getEmail(), ws.getName(), inviterName, token);

        log.info("Created email invitation id={} for email={} to workspace={}", invitation.getId(), request.getEmail(), ws.getName());

        return toInvitationDto(invitation);
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
            workspaceMemberRepository.save(newMember);

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

    private void validateInvitationActive(WorkspaceInvitation invitation) {
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
                .createdAt(inv.getCreatedAt())
                .build();
    }
}
