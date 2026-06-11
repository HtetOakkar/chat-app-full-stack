package com.example.chatapp.message.service;

import com.example.chatapp.message.model.dto.MessageDto;

import java.time.Instant;
import java.util.List;

import com.example.chatapp.message.model.dto.MessagePage;

public interface MessageService {
    void saveMessage(MessageDto messageDto);
    List<MessageDto> getMessages();
    MessagePage getPublicMessages(String cursor, int limit);
    MessagePage getPrivateMessages(Long currentUserId, Long contactUserId, String cursor, int limit);
    void markMessagesAsRead(Long senderId, Long recipientId);
    MessageDto deleteMessage(Long messageId, Instant timestamp, Long currentUserId);
    void clearPrivateChat(Long currentUserId, Long contactUserId);
}
