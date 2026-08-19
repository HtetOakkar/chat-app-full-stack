package com.example.chatapp.workspace.service;

import com.example.chatapp.chat.util.CursorCodec;
import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.ChannelDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.model.request.CreateChannelRequest;
import com.example.chatapp.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final RedisMessageRepository redisMessageRepository;
    private final MessageBroker messageBroker;

    @Override
    @Transactional
    public ChannelDto createChannel(Long workspaceId, Long userId, CreateChannelRequest request) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found with ID: " + workspaceId));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        String slug = resolveUniqueSlug(workspaceId, request.getName(), request.getSlug());

        Channel channel = Channel.builder()
                .workspace(ws)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .type(request.getType() != null ? request.getType() : ChannelType.PUBLIC)
                .createdByMember(member)
                .build();
        channel = channelRepository.save(channel);

        if (channel.getType() == ChannelType.PRIVATE) {
            ChannelMember cm = ChannelMember.builder()
                    .channel(channel)
                    .workspaceMember(member)
                    .build();
            channelMemberRepository.save(cm);
        }

        Conversation conv = Conversation.builder()
                .workspace(ws)
                .type(ConversationType.CHANNEL)
                .channel(channel)
                .build();
        conv = conversationRepository.save(conv);

        log.info("Created channel id={} slug='{}' type={} in workspace id={}", channel.getId(), slug, channel.getType(), workspaceId);

        return toChannelDto(channel, conv.getId(), true, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelDto> getWorkspaceChannels(Long workspaceId, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedException("You are not a member of this workspace");
        }

        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceId);
        List<ChannelDto> result = new ArrayList<>();

        for (Channel channel : channels) {
            var convOpt = conversationRepository.findByChannelId(channel.getId());
            Long convId = convOpt.map(Conversation::getId).orElse(null);

            if (channel.getType() == ChannelType.PUBLIC) {
                int memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
                result.add(toChannelDto(channel, convId, true, memberCount));
            } else {
                boolean isMember = channelMemberRepository.isUserChannelMember(channel.getId(), userId);
                if (isMember) {
                    int memberCount = channelMemberRepository.countByChannelId(channel.getId());
                    result.add(toChannelDto(channel, convId, true, memberCount));
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelDto getChannelById(Long channelId, Long userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedException("You are not a member of this workspace");
        }

        boolean isMember = true;
        int memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
        if (channel.getType() == ChannelType.PRIVATE) {
            isMember = channelMemberRepository.isUserChannelMember(channelId, userId);
            if (!isMember) {
                throw new UnauthorizedException("You do not have access to this private channel");
            }
            memberCount = channelMemberRepository.countByChannelId(channelId);
        }

        var convOpt = conversationRepository.findByChannelId(channel.getId());
        Long convId = convOpt.map(Conversation::getId).orElse(null);

        return toChannelDto(channel, convId, isMember, memberCount);
    }

    @Override
    @Transactional
    public void addChannelMember(Long channelId, Long requestingUserId, Long targetUserId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        if (channel.getType() == ChannelType.PUBLIC) {
            throw new BadRequestException("Cannot explicitly add members to a public channel");
        }

        boolean requesterHasAccess = channelMemberRepository.isUserChannelMember(channelId, requestingUserId);
        if (!requesterHasAccess) {
            throw new UnauthorizedException("Only private channel members can invite colleagues");
        }

        WorkspaceMember targetMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user is not a member of this workspace"));

        if (!channelMemberRepository.existsByChannelIdAndWorkspaceMemberId(channelId, targetMember.getId())) {
            ChannelMember cm = ChannelMember.builder()
                    .channel(channel)
                    .workspaceMember(targetMember)
                    .build();
            channelMemberRepository.save(cm);
        }
    }

    @Override
    @Transactional
    public void removeChannelMember(Long channelId, Long requestingUserId, Long targetUserId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        WorkspaceMember targetMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Target member not found in workspace"));

        if (!requestingUserId.equals(targetUserId)) {
            WorkspaceMember requestingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, requestingUserId)
                    .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));
            if (requestingMember.getRole() != WorkspaceRole.OWNER && requestingMember.getRole() != WorkspaceRole.ADMIN) {
                throw new UnauthorizedException("Only workspace owners/admins or the member themselves can remove members");
            }
        }

        channelMemberRepository.deleteByChannelIdAndWorkspaceMemberId(channelId, targetMember.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberDto> getChannelMembers(Long channelId, Long userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        if (channel.getType() == ChannelType.PRIVATE && !channelMemberRepository.isUserChannelMember(channelId, userId)) {
            throw new UnauthorizedException("You do not have access to this private channel");
        }

        if (channel.getType() == ChannelType.PUBLIC) {
            return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                    .map(this::toWorkspaceMemberDto)
                    .collect(Collectors.toList());
        } else {
            return channelMemberRepository.findByChannelId(channelId).stream()
                    .map(cm -> toWorkspaceMemberDto(cm.getWorkspaceMember()))
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePage getChannelMessages(Long channelId, Long userId, String cursorStr, int limit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedException("You are not a member of this workspace");
        }

        if (channel.getType() == ChannelType.PRIVATE && !channelMemberRepository.isUserChannelMember(channelId, userId)) {
            throw new UnauthorizedException("You do not have access to this private channel");
        }

        CursorCodec.Cursor cursor = CursorCodec.decodeCursor(cursorStr);
        Instant sentAt = cursor != null ? cursor.sentAt() : null;
        Long lastId = cursor != null ? cursor.id() : null;

        List<Message> dbMessages = messageRepository.findChannelMessages(channelId, sentAt, lastId, PageRequest.of(0, limit));
        List<MessageDto> dtoList = dbMessages.stream().map(messageMapper::toMessageDto).collect(Collectors.toList());

        String nextCursor = null;
        if (!dtoList.isEmpty()) {
            MessageDto lastMsg = dtoList.get(dtoList.size() - 1);
            nextCursor = CursorCodec.encodeCursor(lastMsg.getTimestamp(), lastMsg.getId());
        }

        return new MessagePage(dtoList, nextCursor);
    }

    @Override
    @Transactional
    public MessageDto sendChannelMessage(Long channelId, Long userId, MessageDto messageDto) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found with ID: " + channelId));

        Long workspaceId = channel.getWorkspace().getId();
        WorkspaceMember senderMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        if (channel.getType() == ChannelType.PRIVATE && !channelMemberRepository.isUserChannelMember(channelId, userId)) {
            throw new UnauthorizedException("You do not have permission to send messages to this private channel");
        }

        Conversation conv = conversationRepository.findByChannelId(channelId)
                .orElseThrow(() -> new NotFoundException("Conversation not found for channel: " + channelId));

        User user = senderMember.getUser();
        Instant now = Instant.now();

        messageDto.setWorkspaceId(workspaceId);
        messageDto.setConversationId(conv.getId());
        messageDto.setChannelId(channelId);
        messageDto.setSenderId(userId);
        messageDto.setSenderMemberId(senderMember.getId());
        messageDto.setSenderUsername(user.getUsername());
        messageDto.setSenderFullName(user.getFullName());
        messageDto.setTimestamp(now);
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setIsRead(false);
        messageDto.setIsDeleted(false);
        messageDto.setIsDelivered(true);

        // Save directly to DB and Redis
        Message message = Message.builder()
                .conversation(conv)
                .senderMember(senderMember)
                .content(messageDto.getContent())
                .messageType(messageDto.getMessageType())
                .isRead(false)
                .isDeleted(false)
                .sentAt(now)
                .build();
        message = messageRepository.save(message);
        messageDto.setId(message.getId());

        // Broadcast to STOMP topic
        String topic = String.format("/topic/workspace.%d.channel.%d", workspaceId, channelId);
        messageBroker.publishToTopic(topic, messageDto);

        return messageDto;
    }

    private String resolveUniqueSlug(Long workspaceId, String name, String customSlug) {
        String baseSlug = (customSlug != null && !customSlug.isBlank()) ? slugify(customSlug) : slugify(name);
        if (baseSlug.isBlank()) baseSlug = "channel";

        String candidate = baseSlug;
        int counter = 1;
        while (channelRepository.existsByWorkspaceIdAndSlug(workspaceId, candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }

    private String slugify(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private ChannelDto toChannelDto(Channel channel, Long conversationId, boolean isMember, int memberCount) {
        return ChannelDto.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .conversationId(conversationId)
                .name(channel.getName())
                .slug(channel.getSlug())
                .description(channel.getDescription())
                .type(channel.getType())
                .isArchived(channel.isArchived())
                .isMember(isMember)
                .memberCount(memberCount)
                .createdAt(channel.getCreatedAt())
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
