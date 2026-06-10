package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RedisMessageRepositoryTest {

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    @Autowired
    private org.springframework.batch.item.ItemProcessor<MessageDto, Message> messageProcessor;

    @Autowired
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        redisMessageRepository.clear();
    }

    @Test
    public void testSaveAndGetMessageWithInstant() {
        MessageDto message = MessageDto.builder()
                .content("TDD message")
                .senderId(1L)
                .senderUsername("tdd_user")
                .recipientId(2L)
                .timestamp(Instant.now())
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        // Under current RedisConfig, this will fail with a SerializationException.
        redisMessageRepository.saveMessage(message);

        List<MessageDto> messages = redisMessageRepository.getMessages(1);
        assertNotNull(messages);
        assertFalse(messages.isEmpty());
        assertEquals("TDD message", messages.get(0).getContent());
        assertNotNull(messages.get(0).getTimestamp());
        assertEquals(2L, messages.get(0).getRecipientId());
    }

    @Test
    public void testMessageProcessorForPublicMessage() throws Exception {
        User system = userRepository.findByUsername("system")
                .orElseThrow(() -> new IllegalStateException("System user should be seeded."));

        User sender = userRepository.findByUsername("tdd_user_processor").orElseGet(() -> {
            User user = User.builder()
                    .username("tdd_user_processor")
                    .password("pass")
                    .role(system.getRole())
                    .version(0L)
                    .build();
            return userRepository.save(user);
        });

        MessageDto publicMessageDto = MessageDto.builder()
                .content("Public text message")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(null) // public message
                .timestamp(Instant.now())
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        Message processedMessage = messageProcessor.process(publicMessageDto);

        assertNotNull(processedMessage);
        assertNotNull(processedMessage.getRecipient());
        assertEquals("system", processedMessage.getRecipient().getUsername());
    }
}
