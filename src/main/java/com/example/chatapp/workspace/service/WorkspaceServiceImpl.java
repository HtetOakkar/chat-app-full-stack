package com.example.chatapp.workspace.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public WorkspaceDto createWorkspace(Long userId, CreateWorkspaceRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        String slug = resolveUniqueSlug(request.getName(), request.getSlug());

        Workspace workspace = Workspace.builder()
                .name(request.getName().trim())
                .slug(slug)
                .iconUrl(request.getIconUrl())
                .owner(owner)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .build();
        ownerMember = workspaceMemberRepository.save(ownerMember);

        Channel generalChannel = Channel.builder()
                .workspace(workspace)
                .name("general")
                .slug("general")
                .description("Company-wide announcements and work-based matters")
                .type(ChannelType.PUBLIC)
                .createdByMember(ownerMember)
                .build();
        generalChannel = channelRepository.save(generalChannel);

        Conversation generalConversation = Conversation.builder()
                .workspace(workspace)
                .type(ConversationType.CHANNEL)
                .channel(generalChannel)
                .build();
        conversationRepository.save(generalConversation);

        log.info("Created workspace id={} slug='{}' for owner userId={}", workspace.getId(), slug, userId);

        return toWorkspaceDto(workspace, WorkspaceRole.OWNER, 1, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceDto> getUserWorkspaces(Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    Workspace ws = m.getWorkspace();
                    int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
                    int channelCount = channelRepository.countByWorkspaceId(ws.getId());
                    return toWorkspaceDto(ws, m.getRole(), memberCount, channelCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspaceById(Long workspaceId, Long userId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        Workspace ws = member.getWorkspace();
        int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
        int channelCount = channelRepository.countByWorkspaceId(ws.getId());
        return toWorkspaceDto(ws, member.getRole(), memberCount, channelCount);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspaceBySlug(String slug, Long userId) {
        Workspace ws = workspaceRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace not found with slug: " + slug));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(ws.getId(), userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
        int channelCount = channelRepository.countByWorkspaceId(ws.getId());
        return toWorkspaceDto(ws, member.getRole(), memberCount, channelCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberDto> getWorkspaceMembers(Long workspaceId, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedException("You are not a member of this workspace");
        }

        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toWorkspaceMemberDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceMemberDto getWorkspaceMember(Long workspaceId, Long userId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));
        return toWorkspaceMemberDto(member);
    }

    private String resolveUniqueSlug(String name, String customSlug) {
        String baseSlug;
        if (customSlug != null && !customSlug.isBlank()) {
            baseSlug = slugify(customSlug);
        } else {
            baseSlug = slugify(name);
        }

        if (baseSlug.isBlank()) {
            baseSlug = "workspace";
        }

        String candidate = baseSlug;
        int counter = 1;
        while (workspaceRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug;
    }

    private WorkspaceDto toWorkspaceDto(Workspace ws, WorkspaceRole role, int memberCount, int channelCount) {
        return WorkspaceDto.builder()
                .id(ws.getId())
                .name(ws.getName())
                .slug(ws.getSlug())
                .iconUrl(ws.getIconUrl())
                .ownerId(ws.getOwner() != null ? ws.getOwner().getId() : null)
                .role(role)
                .memberCount(memberCount)
                .channelCount(channelCount)
                .createdAt(ws.getCreatedAt())
                .updatedAt(ws.getUpdatedAt())
                .build();
    }

    private WorkspaceMemberDto toWorkspaceMemberDto(WorkspaceMember member) {
        User user = member.getUser();
        return WorkspaceMemberDto.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspace().getId())
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
