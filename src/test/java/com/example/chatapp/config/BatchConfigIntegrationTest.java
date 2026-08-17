package com.example.chatapp.config;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BatchConfigIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job redisToDbJob;

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.chatapp.user.repository.ContactRepository contactRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ItemProcessor<MessageDto, Message> messageProcessor;

    @Autowired
    private ItemWriter<MessageDto> messageItemWriter;

    private User sender;
    private User recipient;
    private User systemUser;

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        messageRepository.deleteAll();
        redisMessageRepository.clear();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        systemUser = userRepository.save(User.builder()
                .username("system")
                .password("system_pass")
                .role(userRole)
                .email("system@chatapp.com")
                .emailVerified(true)
                .build());

        sender = userRepository.save(User.builder()
                .username("batch_alice")
                .password("pass123")
                .role(userRole)
                .email("alice@chatapp.com")
                .emailVerified(true)
                .build());

        recipient = userRepository.save(User.builder()
                .username("batch_bob")
                .password("pass123")
                .role(userRole)
                .email("bob@chatapp.com")
                .emailVerified(true)
                .build());
    }

    @Test
    void batchExecutionPreservesSoftDeletedStateAndPlaceholderContentFromRedis() throws Exception {
        Instant now = Instant.now();

        // 1. Enqueue soft-deleted message in Redis
        MessageDto softDeletedDto = MessageDto.builder()
                .id(1001L)
                .content("Deleted message")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(recipient.getId())
                .timestamp(now)
                .isRead(false)
                .isDeleted(true)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();
        redisMessageRepository.saveMessage(softDeletedDto);

        // 2. Enqueue normal active message in Redis
        MessageDto activeDto = MessageDto.builder()
                .id(1002L)
                .content("Active message content")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(recipient.getId())
                .timestamp(now.plusSeconds(1))
                .isRead(false)
                .isDeleted(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();
        redisMessageRepository.saveMessage(activeDto);

        // 3. Enqueue public soft-deleted message in Redis
        MessageDto publicDeletedDto = MessageDto.builder()
                .id(1003L)
                .content("Deleted message")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(null)
                .timestamp(now.plusSeconds(2))
                .isRead(false)
                .isDeleted(true)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();
        redisMessageRepository.saveMessage(publicDeletedDto);

        // 4. Run Spring Batch redisToDbJob
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(redisToDbJob, jobParameters);
        assertEquals("COMPLETED", jobExecution.getStatus().toString());

        // 5. Verify database messages
        List<Message> persistedMessages = messageRepository.findAll();
        assertEquals(3, persistedMessages.size());

        Message persistedSoftDeleted = persistedMessages.stream()
                .filter(m -> m.getRecipient().getId().equals(recipient.getId()) && "Deleted message".equals(m.getContent()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Soft deleted private message not found in DB"));
        assertTrue(persistedSoftDeleted.getIsDeleted(), "Private message should have isDeleted = true");
        assertEquals("Deleted message", persistedSoftDeleted.getContent());

        Message persistedActive = persistedMessages.stream()
                .filter(m -> "Active message content".equals(m.getContent()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Active message not found in DB"));
        assertFalse(persistedActive.getIsDeleted(), "Active message should have isDeleted = false");
        assertEquals("Active message content", persistedActive.getContent());

        Message persistedPublicDeleted = persistedMessages.stream()
                .filter(m -> m.getRecipient().getId().equals(systemUser.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Soft deleted public message not found in DB"));
        assertTrue(persistedPublicDeleted.getIsDeleted(), "Public message should have isDeleted = true");
        assertEquals("Deleted message", persistedPublicDeleted.getContent());
    }

    @Test
    void messageItemWriterDirectlyPreservesIsDeletedFlag() throws Exception {
        MessageDto deletedDto = MessageDto.builder()
                .id(2001L)
                .content("Deleted message")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(recipient.getId())
                .timestamp(Instant.now())
                .isRead(true)
                .isDeleted(true)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        messageItemWriter.write(new Chunk<>(List.of(deletedDto)));

        List<Message> saved = messageRepository.findAll();
        assertEquals(1, saved.size());
        assertTrue(saved.get(0).getIsDeleted());
        assertEquals("Deleted message", saved.get(0).getContent());
    }

    @Test
    void messageProcessorDirectlyPreservesIsDeletedFlag() throws Exception {
        MessageDto deletedDto = MessageDto.builder()
                .id(3001L)
                .content("Deleted message")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .recipientId(recipient.getId())
                .timestamp(Instant.now())
                .isRead(false)
                .isDeleted(true)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        Message processed = messageProcessor.process(deletedDto);
        assertNotNull(processed);
        assertTrue(processed.getIsDeleted());
        assertEquals("Deleted message", processed.getContent());
    }
}
