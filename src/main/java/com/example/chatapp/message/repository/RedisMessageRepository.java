package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.dto.MessageDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisMessageRepository {
    private static final String MESSAGE_KEY = "chat_messages";
    private final RedisTemplate<String, MessageDto> chatMessageRedisTemplate;
    private ListOperations<String, MessageDto> listOps;

    @PostConstruct
    public void init() {
        listOps = chatMessageRedisTemplate.opsForList();
    }

    public void saveMessage(MessageDto messageDto) {
        listOps.leftPush(MESSAGE_KEY, messageDto);
    }

    public List<MessageDto> getMessages(long size) {
        List<MessageDto> messages = listOps.rightPop(MESSAGE_KEY, size);
        if (messages == null) {
            return List.of();
        }
        return messages;
    }

    public List<MessageDto> peekMessages() {
        List<MessageDto> messages = listOps.range(MESSAGE_KEY, 0, -1);
        if (messages == null) {
            return List.of();
        }
        return messages;
    }

    public void clear() {
        chatMessageRedisTemplate.delete(MESSAGE_KEY);
    }

    public void markMessagesAsRead(Long senderId, Long recipientId) {
        List<MessageDto> messages = peekMessages();
        boolean modified = false;
        for (MessageDto msg : messages) {
            if (msg.getSenderId().equals(senderId) && msg.getRecipientId() != null && msg.getRecipientId().equals(recipientId)) {
                if (msg.getIsRead() == null || !msg.getIsRead()) {
                    msg.setIsRead(true);
                    modified = true;
                }
            }
        }
        if (modified) {
            clear();
            for (int i = messages.size() - 1; i >= 0; i--) {
                saveMessage(messages.get(i));
            }
        }
    }

    public boolean deleteMessage(Long id, java.time.Instant timestamp, Long currentUserId) {
        List<MessageDto> messages = peekMessages();
        boolean modified = false;
        for (MessageDto msg : messages) {
            boolean match = msg.getId().equals(id) || 
                    (timestamp != null && msg.getSenderId().equals(currentUserId) && msg.getTimestamp().equals(timestamp));
            if (match) {
                if (!msg.getSenderId().equals(currentUserId)) {
                    throw new com.example.chatapp.exception.UnauthorizedException("Not authorized to delete this message");
                }
                msg.setIsDeleted(true);
                msg.setContent("Deleted message");
                modified = true;
                break;
            }
        }
        if (modified) {
            clear();
            for (int i = messages.size() - 1; i >= 0; i--) {
                saveMessage(messages.get(i));
            }
        }
        return modified;
    }
}
