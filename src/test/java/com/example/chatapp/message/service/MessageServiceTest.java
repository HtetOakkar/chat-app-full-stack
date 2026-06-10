package com.example.chatapp.message.service;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.chatapp.user.repository.RoleRepository roleRepository;

    @BeforeEach
    public void setUp() {
        redisMessageRepository.clear();
        messageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    public void tearDown() {
        messageRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    public void testSaveMessageGeneratesUniqueIdIfNull() {
        MessageDto msg = MessageDto.builder()
                .content("Test message without ID")
                .senderId(1L)
                .senderUsername("sender")
                .recipientId(2L)
                .messageType(MessageType.TEXT)
                .build();

        assertNull(msg.getId(), "Initially, the message ID should be null.");

        messageService.saveMessage(msg);

        assertNotNull(msg.getId(), "After saveMessage, the message ID should be populated.");
    }

    @Test
    public void testPersistPreservesSentAtIfSetAndGeneratesIfNull() {
        // Fetch initialized user role
        com.example.chatapp.user.model.entity.Role userRole = roleRepository.findByName(com.example.chatapp.user.model.entity.RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(com.example.chatapp.user.model.entity.Role.builder().name(com.example.chatapp.user.model.entity.RoleName.ROLE_USER).build()));

        // Create users
        User sender = User.builder()
                .username("sender_test")
                .password("password")
                .role(userRole)
                .version(0L)
                .build();
        User recipient = User.builder()
                .username("recipient_test")
                .password("password")
                .role(userRole)
                .version(0L)
                .build();
        sender = userRepository.save(sender);
        recipient = userRepository.save(recipient);


        // 1. Test null sentAt -> should be populated
        Message msg1 = Message.builder()
                .content("Null sentAt message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(false)
                .sender(sender)
                .recipient(recipient)
                .build();

        assertNull(msg1.getSentAt(), "Initially, sentAt should be null");
        msg1 = messageRepository.save(msg1);
        assertNotNull(msg1.getSentAt(), "After save, sentAt should be generated via @PrePersist");

        // 2. Test pre-set sentAt -> should be preserved
        Instant customTimestamp = Instant.now().minusSeconds(3600); // 1 hour ago
        Message msg2 = Message.builder()
                .content("Pre-set sentAt message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(false)
                .sender(sender)
                .recipient(recipient)
                .sentAt(customTimestamp)
                .build();

        assertEquals(customTimestamp, msg2.getSentAt(), "Initially, sentAt should be the custom timestamp");
        msg2 = messageRepository.save(msg2);
        assertEquals(customTimestamp, msg2.getSentAt(), "After save, pre-set sentAt must be preserved and not overridden");
    }
}


