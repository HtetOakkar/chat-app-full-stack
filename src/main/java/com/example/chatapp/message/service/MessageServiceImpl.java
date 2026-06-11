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

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.model.entity.Contact;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;

    private final MessageMapper messageMapper;

    private final RedisMessageRepository redisMessageRepository;

    private final ContactRepository contactRepository;

    @Override
    public void saveMessage(MessageDto messageDto) {
        if (messageDto.getId() == null) {
            messageDto.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 9007199254740991L));
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

        Instant clearedAt = contactRepository.findByOwnerIdAndContactUserId(currentUserId, contactUserId)
                .map(Contact::getClearedAt)
                .orElse(null);

        Pageable pageable = PageRequest.of(0, limit);
        List<MessageDto> dbMessages = messageRepository.findPrivateMessages(currentUserId, contactUserId, clearedAt, sentAt, lastId, pageable).stream()
                .map(messageMapper::toMessageDto)
                .toList();

        List<MessageDto> redisMessages = redisMessageRepository.peekMessages().stream()
                .filter(msg -> (msg.getSenderId().equals(currentUserId) && contactUserId.equals(msg.getRecipientId())) ||
                               (msg.getSenderId().equals(contactUserId) && currentUserId.equals(msg.getRecipientId())))
                .filter(msg -> clearedAt == null || msg.getTimestamp().isAfter(clearedAt))
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

    @Override
    @Transactional
    public MessageDto deleteMessage(Long messageId, Instant timestamp, Long currentUserId) {
        // 1. Try to delete in Redis
        boolean deletedInRedis = redisMessageRepository.deleteMessage(messageId, timestamp, currentUserId);
        if (deletedInRedis) {
            return redisMessageRepository.peekMessages().stream()
                    .filter(msg -> msg.getId().equals(messageId) || 
                            (timestamp != null && msg.getSenderId().equals(currentUserId) && msg.getTimestamp().equals(timestamp)))
                    .findFirst()
                    .orElse(null);
        }

        // 2. Try to find in Database
        com.example.chatapp.message.model.entity.Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null && timestamp != null) {
            message = messageRepository.findBySenderIdAndSentAt(currentUserId, timestamp).orElse(null);
        }

        if (message == null) {
            throw new NotFoundException("Message not found");
        }

        // 3. Ownership check
        if (!message.getSender().getId().equals(currentUserId)) {
            throw new UnauthorizedException("Not authorized to delete this message");
        }

        // 4. Overwrite content and mark as deleted
        message.setIsDeleted(true);
        message.setContent("Deleted message");
        message = messageRepository.save(message);

        return messageMapper.toMessageDto(message);
    }

    @Override
    @Transactional
    public void clearPrivateChat(Long currentUserId, Long contactUserId) {
        Contact contact = contactRepository.findByOwnerIdAndContactUserId(currentUserId, contactUserId)
                .orElseThrow(() -> new NotFoundException("Contact relationship not found"));
        contact.setClearedAt(Instant.now());
        contactRepository.save(contact);

        messageRepository.markMessagesAsRead(contactUserId, currentUserId);
        redisMessageRepository.markMessagesAsRead(contactUserId, currentUserId);
    }
}
