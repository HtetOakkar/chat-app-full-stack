package com.example.chatapp.message.controller;

import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.websocket.MessageBroker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageRepository messageRepository;
    private final MessageBroker messageBroker;

    @GetMapping("/public")
    public MessagePage getPublicMessages(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        return messageService.getPublicMessages(cursor, limit);
    }

    @GetMapping("/private/{contactUserId}")
    public MessagePage getPrivateMessages(
            @PathVariable Long contactUserId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }

        Long currentUserId = currentUser.getId();
        return messageService.getPrivateMessages(currentUserId, contactUserId, cursor, limit);
    }

    @PutMapping("/read/{contactUserId}")
    public void markMessagesAsRead(
            @PathVariable Long contactUserId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        messageService.markMessagesAsRead(contactUserId, currentUser.getId());
    }

    @DeleteMapping("/{messageId}")
    public MessageDto deleteMessage(
            @PathVariable Long messageId,
            @RequestParam(required = false) String timestamp,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        Instant ts = null;
        if (timestamp != null && !timestamp.isBlank() && !"null".equals(timestamp) && !"undefined".equals(timestamp)) {
            try {
                ts = Instant.parse(timestamp);
            } catch (java.time.format.DateTimeParseException e) {
                ts = java.time.OffsetDateTime.parse(timestamp).toInstant();
            }
        }
        MessageDto deletedMessage = messageService.deleteMessage(messageId, ts, currentUser.getId());

        if (deletedMessage.getRecipientId() == null) {
            messageBroker.publishToTopic("/topic/public", deletedMessage);
        } else {
            messageBroker.publishToUser(deletedMessage.getSenderId().toString(), "/queue/messages", deletedMessage);
            messageBroker.publishToUser(deletedMessage.getRecipientId().toString(), "/queue/messages", deletedMessage);
        }

        return deletedMessage;
    }

    @DeleteMapping("/private/{contactUserId}")
    public ResponseEntity<Void> clearPrivateChat(
            @PathVariable Long contactUserId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        messageService.clearPrivateChat(currentUser.getId(), contactUserId);
        return ResponseEntity.noContent().build();
    }
}
