package com.example.chatapp.chat.controller;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ContactService contactService;

    @MessageMapping("/chat.public")
    public void sendPublicMessage(@Valid @Payload MessageDto messageDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        messageDto.setSenderId(authenticatedUser.getId());
        messageDto.setSenderUsername(authenticatedUser.getUsername());
        messageDto.setRecipientId(null);
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setTimestamp(Instant.now());
        messageService.saveMessage(messageDto);
        messagingTemplate.convertAndSend("/topic/public", messageDto);
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Valid @Payload MessageDto messageDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = messageDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for private messages");
        }

        messageDto.setSenderId(senderId);
        messageDto.setSenderUsername(authenticatedUser.getUsername());
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setTimestamp(Instant.now());

        // 1. Retrieve recipient's relation to sender
        Optional<Contact> recipientRelationOpt = contactRepository.findByOwnerIdAndContactUserId(recipientId, senderId);
        ContactStatus recipientStatus = recipientRelationOpt
                .map(Contact::getStatus)
                .orElse(null);

        // Case A: Sender is BLOCKED by Recipient
        if (recipientStatus == ContactStatus.BLOCKED) {
            // Save to Redis/DB with isDelivered = false
            messageDto.setIsDelivered(false);
            messageDto.setIsRead(false);
            messageService.saveMessage(messageDto);
            
            // Only send to sender's own WS queue
            messagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/messages", messageDto);
            return;
        }

        // Default: message is delivered (but check where it routes)
        messageDto.setIsDelivered(true);
        messageDto.setIsRead(false);

        // Case B: No relationship exists yet (First message)
        if (recipientStatus == null) {
            try {
                createSymmetricRelations(recipientId, senderId);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.info("Concurrent relationship insertion handled gracefully: {}", ex.getMessage());
            }
            recipientStatus = ContactStatus.PENDING_REQUEST;
        }

        // Update sender's relation to recipient if it is PENDING_REQUEST or NEGLECTED
        contactService.acceptRequestIfPending(senderId, recipientId);

        // Save the message to Redis/DB
        messageService.saveMessage(messageDto);

        // Send to sender's own queue
        messagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/messages", messageDto);

        // Route to recipient based on status
        if (recipientStatus == ContactStatus.PENDING_REQUEST || recipientStatus == ContactStatus.NEGLECTED) {
            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/requests", messageDto);
        } else { // ACCEPTED or CONTACT
            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", messageDto);
        }
    }

    @MessageMapping("/noti.status")
    public void sendOnlineStatus(@Valid @Payload OnlineStatusDto onlineStatusDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Map<String, Object> map = new HashMap<>();
        map.put("userid", authenticatedUser.getId());
        map.put("status", onlineStatusDto.getStatus());
        map.put("timestamp", Instant.now());
        map.put("username", authenticatedUser.getUsername());
        messagingTemplate.convertAndSend("/topic/online", map);
    }

    private void createSymmetricRelations(Long recipientId, Long senderId) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new com.example.chatapp.exception.NotFoundException("Recipient not found"));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new com.example.chatapp.exception.NotFoundException("Sender not found"));

        Contact recContact = Contact.builder()
                .owner(recipient)
                .contactUser(sender)
                .status(ContactStatus.PENDING_REQUEST)
                .build();

        Contact sendContact = Contact.builder()
                .owner(sender)
                .contactUser(recipient)
                .status(ContactStatus.ACCEPTED)
                .build();

        saveRelation(recContact);
        saveRelation(sendContact);
    }

    private void saveRelation(Contact contact) {
        try {
            contactRepository.save(contact);
        } catch (DataIntegrityViolationException ex) {
            log.error(ex.getMessage());
            // Handled gracefully: relation already created by a concurrent request
        }
    }

    private UserPrincipal getAuthenticatedUser(Principal principal) {
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BadRequestException("Authenticated user not found");
        }
        return userPrincipal;
    }
}
