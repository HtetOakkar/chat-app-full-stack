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
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.mapper.WorkspaceMemberMapper;
import com.example.chatapp.workspace.model.dto.DirectMessageDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMessageServiceImpl implements DirectMessageService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final MessageBroker messageBroker;
    private final WorkspaceMemberMapper workspaceMemberMapper;

    @Override
    @Transactional
    public DirectMessageDto getOrCreateDirectMessage(Long workspaceId, Long userId, Long targetUserId) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found with ID: " + workspaceId));

        WorkspaceMember member1 = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        WorkspaceMember member2 = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user is not a member of this workspace"));

        Optional<Conversation> convOpt = conversationRepository.findDirectMessageConversation(workspaceId, member1.getId(), member2.getId());
        Conversation conversation;

        if (convOpt.isPresent()) {
            conversation = convOpt.get();
        } else {
            conversation = Conversation.builder()
                    .workspace(ws)
                    .type(ConversationType.DIRECT_MESSAGE)
                    .build();
            conversation = conversationRepository.save(conversation);

            ConversationParticipant cp1 = ConversationParticipant.builder()
                    .conversation(conversation)
                    .workspaceMember(member1)
                    .build();
            ConversationParticipant cp2 = ConversationParticipant.builder()
                    .conversation(conversation)
                    .workspaceMember(member2)
                    .build();

            conversationParticipantRepository.save(cp1);
            conversationParticipantRepository.save(cp2);

            log.info("Created 1:1 DM conversation id={} between memberId={} and memberId={} in workspaceId={}",
                    conversation.getId(), member1.getId(), member2.getId(), workspaceId);
        }

        return DirectMessageDto.builder()
                .conversationId(conversation.getId())
                .workspaceId(workspaceId)
                .targetMember(workspaceMemberMapper.toDto(userId, member2))
                .unreadCount(0)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectMessageDto> getUserDirectMessages(Long workspaceId, Long userId) {
        WorkspaceMember currentMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        List<ConversationParticipant> myParticipations = conversationParticipantRepository.findByWorkspaceMemberId(currentMember.getId());
        List<Conversation> conversations = new ArrayList<>();
        List<WorkspaceMember> otherMembers = new ArrayList<>();

        for (ConversationParticipant cp : myParticipations) {
            Conversation conv = cp.getConversation();
            if (conv.getWorkspace() == null || !conv.getWorkspace().getId().equals(workspaceId)) {
                continue;
            }
            if (conv.getType() != ConversationType.DIRECT_MESSAGE) {
                continue;
            }

            List<ConversationParticipant> allParticipants = conversationParticipantRepository.findByConversationId(conv.getId());
            WorkspaceMember otherMember = allParticipants.stream()
                    .map(ConversationParticipant::getWorkspaceMember)
                    .filter(m -> !m.getId().equals(currentMember.getId()))
                    .findFirst()
                    .orElse(currentMember); // Self-DM fallback

            conversations.add(conv);
            otherMembers.add(otherMember);
        }

        List<WorkspaceMemberDto> targetMembers = workspaceMemberMapper.toDtos(userId, otherMembers);
        List<DirectMessageDto> result = new ArrayList<>();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = conversations.get(i);
            result.add(DirectMessageDto.builder()
                    .conversationId(conv.getId())
                    .workspaceId(workspaceId)
                    .targetMember(targetMembers.get(i))
                    .createdAt(conv.getCreatedAt())
                    .updatedAt(conv.getUpdatedAt())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePage getDirectMessageMessages(Long workspaceId, Long userId, Long targetUserId, String cursorStr, int limit) {
        WorkspaceMember member1 = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        WorkspaceMember member2 = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user is not a member of this workspace"));

        Optional<Conversation> convOpt = conversationRepository.findDirectMessageConversation(workspaceId, member1.getId(), member2.getId());
        if (convOpt.isEmpty()) {
            return new MessagePage(List.of(), null);
        }

        Conversation conv = convOpt.get();
        CursorCodec.Cursor cursor = CursorCodec.decodeCursor(cursorStr);
        Instant sentAt = cursor != null ? cursor.sentAt() : null;
        Long lastId = cursor != null ? cursor.id() : null;

        List<Message> dbMessages = messageRepository.findConversationMessages(conv.getId(), null, sentAt, lastId, PageRequest.of(0, limit));
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
    public MessageDto sendDirectMessage(Long workspaceId, Long senderId, Long targetUserId, MessageDto messageDto) {
        WorkspaceMember senderMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, senderId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));

        WorkspaceMember targetMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user is not a member of this workspace"));

        DirectMessageDto dmDto = getOrCreateDirectMessage(workspaceId, senderId, targetUserId);
        Conversation conv = conversationRepository.findById(dmDto.getConversationId()).orElseThrow();

        User senderUser = senderMember.getUser();
        Instant now = Instant.now();

        messageDto.setWorkspaceId(workspaceId);
        messageDto.setConversationId(conv.getId());
        messageDto.setSenderId(senderId);
        messageDto.setSenderMemberId(senderMember.getId());
        messageDto.setSenderUsername(senderUser.getUsername());
        messageDto.setSenderFullName(senderUser.getFullName());
        messageDto.setRecipientId(targetUserId);
        messageDto.setTimestamp(now);
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setIsRead(false);
        messageDto.setIsDeleted(false);
        messageDto.setIsDelivered(true);

        Message message = Message.builder()
                .conversation(conv)
                .senderMember(senderMember)
                .content(messageDto.getContent())
                .messageType(messageDto.getMessageType())
                .isRead(false)
                .isDeleted(false)
                .sentAt(now)
                .callOutcome(messageDto.getCallOutcome())
                .callDuration(messageDto.getCallDuration())
                .videoUsed(messageDto.getVideoUsed())
                .build();

        message = messageRepository.save(message);
        messageDto.setId(message.getId());

        // Route to recipient and sender user queues
        String dmQueue = String.format("/queue/workspace.%d.messages", workspaceId);
        messageBroker.publishToUser(targetUserId.toString(), dmQueue, messageDto);
        messageBroker.publishToUser(senderId.toString(), dmQueue, messageDto);

        return messageDto;
    }
}
