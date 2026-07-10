package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.dto.MessageDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisMessageRepository {
    private static final String MESSAGE_KEY = "chat_messages";
    private static final String PUBLIC_HISTORY_KEY = "chat_messages:public";
    private static final String PRIVATE_HISTORY_PREFIX = "chat_messages:private:";
    private static final String MESSAGE_LOOKUP_KEY = "chat_messages:by_id";
    private final RedisTemplate<String, MessageDto> chatMessageRedisTemplate;
    private ListOperations<String, MessageDto> listOps;
    private ZSetOperations<String, MessageDto> zSetOps;

    @PostConstruct
    public void init() {
        listOps = chatMessageRedisTemplate.opsForList();
        zSetOps = chatMessageRedisTemplate.opsForZSet();
    }

    public void saveMessage(MessageDto messageDto) {
        listOps.leftPush(MESSAGE_KEY, messageDto);
        indexMessage(messageDto);
    }

    public List<MessageDto> getMessages(long size) {
        List<MessageDto> messages = listOps.rightPop(MESSAGE_KEY, size);
        if (messages == null) {
            return List.of();
        }
        messages.forEach(this::removeFromIndexes);
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
        chatMessageRedisTemplate.delete(PUBLIC_HISTORY_KEY);
        chatMessageRedisTemplate.delete(MESSAGE_LOOKUP_KEY);
        Set<String> privateKeys = chatMessageRedisTemplate.keys(PRIVATE_HISTORY_PREFIX + "*");
        if (!privateKeys.isEmpty()) {
            chatMessageRedisTemplate.delete(privateKeys);
        }
    }

    public List<MessageDto> findPublicMessagesBefore(Instant sentAt, Long lastId, int limit) {
        return historyBefore(PUBLIC_HISTORY_KEY, sentAt, lastId, limit).stream()
                .filter(message -> message.getRecipientId() == null)
                .toList();
    }

    public List<MessageDto> findPrivateMessagesBefore(Long userId1, Long userId2, Instant clearedAt, Instant sentAt, Long lastId, int limit) {
        return historyBefore(privateHistoryKey(userId1, userId2), sentAt, lastId, limit).stream()
                .filter(message -> isPrivateBetween(message, userId1, userId2))
                .filter(message -> clearedAt == null || message.getTimestamp().isAfter(clearedAt))
                .toList();
    }

    public MessageDto findMessage(Long id, Instant timestamp, Long currentUserId) {
        if (id != null) {
            MessageDto byId = (MessageDto) chatMessageRedisTemplate.opsForHash().get(MESSAGE_LOOKUP_KEY, String.valueOf(id));
            if (byId != null) {
                return byId;
            }
        }
        if (timestamp == null || currentUserId == null) {
            return null;
        }
        return peekMessages().stream()
                .filter(msg -> msg.getSenderId().equals(currentUserId) && msg.getTimestamp().equals(timestamp))
                .findFirst()
                .orElse(null);
    }

    public void markMessagesAsRead(Long senderId, Long recipientId) {
        String key = privateHistoryKey(senderId, recipientId);
        Set<MessageDto> messages = zSetOps.range(key, 0, -1);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (MessageDto msg : messages) {
            if (msg.getSenderId().equals(senderId) && recipientId.equals(msg.getRecipientId())) {
                if (msg.getIsRead() == null || !msg.getIsRead()) {
                    removeFromIndexes(msg);
                    msg.setIsRead(true);
                    indexMessage(msg);
                    replaceInPendingQueue(msg);
                }
            }
        }
    }

    public boolean deleteMessage(Long id, java.time.Instant timestamp, Long currentUserId) {
        MessageDto msg = findMessage(id, timestamp, currentUserId);
        if (msg == null) {
            return false;
        }
        if (!msg.getSenderId().equals(currentUserId)) {
            throw new com.example.chatapp.exception.UnauthorizedException("Not authorized to delete this message");
        }
        removeFromIndexes(msg);
        msg.setIsDeleted(true);
        msg.setContent("Deleted message");
        indexMessage(msg);
        replaceInPendingQueue(msg);
        return true;
    }

    private List<MessageDto> historyBefore(String key, Instant sentAt, Long lastId, int limit) {
        double maxScore = sentAt == null ? Double.POSITIVE_INFINITY : Math.nextDown(score(sentAt, lastId));
        Set<MessageDto> values = zSetOps.reverseRangeByScore(key, Double.NEGATIVE_INFINITY, maxScore, 0, limit);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<MessageDto> messages = new ArrayList<>(values);
        messages.sort(historyComparator());
        return messages.stream().limit(limit).toList();
    }

    private void indexMessage(MessageDto messageDto) {
        if (messageDto.getId() != null) {
            chatMessageRedisTemplate.opsForHash().put(MESSAGE_LOOKUP_KEY, String.valueOf(messageDto.getId()), messageDto);
        }
        String historyKey = messageDto.getRecipientId() == null
                ? PUBLIC_HISTORY_KEY
                : privateHistoryKey(messageDto.getSenderId(), messageDto.getRecipientId());
        zSetOps.add(historyKey, messageDto, score(messageDto));
    }

    private void removeFromIndexes(MessageDto messageDto) {
        if (messageDto.getId() != null) {
            chatMessageRedisTemplate.opsForHash().delete(MESSAGE_LOOKUP_KEY, String.valueOf(messageDto.getId()));
        }
        zSetOps.remove(PUBLIC_HISTORY_KEY, messageDto);
        if (messageDto.getRecipientId() != null) {
            zSetOps.remove(privateHistoryKey(messageDto.getSenderId(), messageDto.getRecipientId()), messageDto);
        }
    }

    private void replaceInPendingQueue(MessageDto updatedMessage) {
        List<MessageDto> messages = peekMessages();
        for (int i = 0; i < messages.size(); i++) {
            MessageDto existing = messages.get(i);
            if (existing.getId() != null && existing.getId().equals(updatedMessage.getId())) {
                listOps.set(MESSAGE_KEY, i, updatedMessage);
                return;
            }
        }
    }

    private String privateHistoryKey(Long userId1, Long userId2) {
        long first = Math.min(userId1, userId2);
        long second = Math.max(userId1, userId2);
        return PRIVATE_HISTORY_PREFIX + first + ":" + second;
    }

    private boolean isPrivateBetween(MessageDto message, Long userId1, Long userId2) {
        return message.getRecipientId() != null &&
                ((message.getSenderId().equals(userId1) && message.getRecipientId().equals(userId2)) ||
                 (message.getSenderId().equals(userId2) && message.getRecipientId().equals(userId1)));
    }

    private double score(MessageDto messageDto) {
        return score(messageDto.getTimestamp(), messageDto.getId());
    }

    private double score(Instant timestamp, Long id) {
        long safeId = id == null ? 0L : Math.floorMod(id, 1_000L);
        return (timestamp.toEpochMilli() * 1_000d) + safeId;
    }

    private Comparator<MessageDto> historyComparator() {
        return Comparator.comparing(MessageDto::getTimestamp, Comparator.reverseOrder())
                .thenComparing(MessageDto::getId, Comparator.reverseOrder());
    }
}
