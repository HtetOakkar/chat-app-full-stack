package com.example.chatapp.message.service;

import com.example.chatapp.chat.util.CursorCodec;
import com.example.chatapp.message.model.dto.MessagePage;

import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import com.example.chatapp.workspace.repository.ConversationParticipantRepository;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final RedisMessageRepository redisMessageRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;

    @Override
    public void saveMessage(MessageDto messageDto) {
        if (messageDto.getId() == null) {
            messageDto.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 9007199254740991L));
        }
        if (messageDto.getTimestamp() == null) {
            messageDto.setTimestamp(Instant.now());
        }
        redisMessageRepository.saveMessage(messageDto);
    }

    @Override
    public List<MessageDto> getMessages() {
        return messageRepository.findAll().stream().map(messageMapper::toMessageDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePage getPublicMessages(String cursorStr, int limit) {
        CursorCodec.Cursor cursor = CursorCodec.decodeCursor(cursorStr);
        Instant sentAt = cursor != null ? cursor.sentAt() : null;
        Long lastId = cursor != null ? cursor.id() : null;

        List<MessageDto> redisMessages = redisMessageRepository.findPublicMessagesBefore(sentAt, lastId, limit);

        List<MessageDto> combined = new java.util.ArrayList<>(redisMessages);
        combined.sort((a, b) -> {
            int timeCompare = b.getTimestamp().compareTo(a.getTimestamp());
            if (timeCompare != 0) return timeCompare;
            return b.getId().compareTo(a.getId());
        });
        
        List<MessageDto> result = combined.stream().limit(limit).toList();
        
        String nextCursor = null;
        if (!result.isEmpty()) {
            MessageDto lastMsg = result.get(result.size() - 1);
            nextCursor = CursorCodec.encodeCursor(lastMsg.getTimestamp(), lastMsg.getId());
        }
        
        return new MessagePage(result, nextCursor);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MessagePage getPrivateMessages(Long currentUserId, Long contactUserId, String cursorStr, int limit) {
        CursorCodec.Cursor cursor = CursorCodec.decodeCursor(cursorStr);
        Instant sentAt = cursor != null ? cursor.sentAt() : null;
        Long lastId = cursor != null ? cursor.id() : null;

        List<MessageDto> redisMessages = redisMessageRepository.findPrivateMessagesBefore(
                currentUserId, contactUserId, null, sentAt, lastId, limit);

        List<MessageDto> combined = new java.util.ArrayList<>(redisMessages);
        combined.sort((a, b) -> {
            int timeCompare = b.getTimestamp().compareTo(a.getTimestamp());
            if (timeCompare != 0) return timeCompare;
            return b.getId().compareTo(a.getId());
        });
        
        List<MessageDto> result = combined.stream().limit(limit).toList();
        
        String nextCursor = null;
        if (!result.isEmpty()) {
            MessageDto lastMsg = result.get(result.size() - 1);
            nextCursor = CursorCodec.encodeCursor(lastMsg.getTimestamp(), lastMsg.getId());
        }
        
        return new MessagePage(result, nextCursor);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void markMessagesAsRead(Long senderId, Long recipientId) {
        redisMessageRepository.markMessagesAsRead(senderId, recipientId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageDto deleteMessage(Long messageId, Instant timestamp, Long currentUserId) {
        // 1. Try to delete in Redis
        boolean deletedInRedis = redisMessageRepository.deleteMessage(messageId, timestamp, currentUserId);
        if (deletedInRedis) {
            return redisMessageRepository.findMessage(messageId, timestamp, currentUserId);
        }

        // 2. Try to find in Database
        com.example.chatapp.message.model.entity.Message message = messageRepository.findById(messageId).orElse(null);

        if (message == null) {
            throw new com.example.chatapp.exception.NotFoundException("Message not found");
        }

        // 3. Ownership check
        if (message.getSenderMember() != null && message.getSenderMember().getUser() != null &&
                !message.getSenderMember().getUser().getId().equals(currentUserId)) {
            throw new com.example.chatapp.exception.UnauthorizedException("Not authorized to delete this message");
        }

        // 4. Overwrite content and mark as deleted
        message.setIsDeleted(true);
        message.setContent("Deleted message");
        message = messageRepository.save(message);

        return messageMapper.toMessageDto(message);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void clearPrivateChat(Long currentUserId, Long contactUserId) {
        redisMessageRepository.markMessagesAsRead(contactUserId, currentUserId);
    }
}
