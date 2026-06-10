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
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;

    private final MessageMapper messageMapper;

    private final RedisMessageRepository redisMessageRepository;

    @Override
    public void saveMessage(MessageDto messageDto) {
        if (messageDto.getId() == null) {
            messageDto.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
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

        Pageable pageable = PageRequest.of(0, limit);
        List<MessageDto> dbMessages = messageRepository.findPublicMessages(sentAt, lastId, pageable).stream()
                .map(messageMapper::toMessageDto)
                .toList();

        List<MessageDto> redisMessages = redisMessageRepository.peekMessages().stream()
                .filter(msg -> msg.getRecipientId() == null)
                .filter(msg -> {
                    if (sentAt == null) return true;
                    if (msg.getTimestamp().isBefore(sentAt)) return true;
                    if (msg.getTimestamp().equals(sentAt) && msg.getId() < lastId) return true;
                    return false;
                })
                .toList();

        List<MessageDto> combined = new java.util.ArrayList<>();
        combined.addAll(redisMessages);
        combined.addAll(dbMessages);
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
    @Transactional(readOnly = true)
    public MessagePage getPrivateMessages(Long currentUserId, Long contactUserId, String cursorStr, int limit) {

        CursorCodec.Cursor cursor = CursorCodec.decodeCursor(cursorStr);
        Instant sentAt = cursor != null ? cursor.sentAt() : null;
        Long lastId = cursor != null ? cursor.id() : null;

        Pageable pageable = PageRequest.of(0, limit);
        List<MessageDto> dbMessages = messageRepository.findPrivateMessages(currentUserId, contactUserId, sentAt, lastId, pageable).stream()
                .map(messageMapper::toMessageDto)
                .toList();

        List<MessageDto> redisMessages = redisMessageRepository.peekMessages().stream()
                .filter(msg -> (msg.getSenderId().equals(currentUserId) && contactUserId.equals(msg.getRecipientId())) ||
                               (msg.getSenderId().equals(contactUserId) && currentUserId.equals(msg.getRecipientId())))
                .filter(msg -> {
                    if (sentAt == null) return true;
                    if (msg.getTimestamp().isBefore(sentAt)) return true;
                    if (msg.getTimestamp().equals(sentAt) && msg.getId() < lastId) return true;
                    return false;
                })
                .toList();

        List<MessageDto> combined = new java.util.ArrayList<>();
        combined.addAll(redisMessages);
        combined.addAll(dbMessages);
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
    @Transactional
    public void markMessagesAsRead(Long senderId, Long recipientId) {
        messageRepository.markMessagesAsRead(senderId, recipientId);
        redisMessageRepository.markMessagesAsRead(senderId, recipientId);
    }
}
