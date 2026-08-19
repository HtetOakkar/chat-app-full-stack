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
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.repository.*;
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
    private RoleRepository roleRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ItemProcessor<MessageDto, Message> messageProcessor;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private ItemWriter<MessageDto> messageItemWriter;

    private User sender;
    private Workspace workspace;
    private WorkspaceMember senderMember;
    private Channel channel;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        redisMessageRepository.clear();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        sender = userRepository.save(User.builder()
                .username("batch_alice")
                .password("pass123")
                .role(userRole)
                .email("alice@chatapp.com")
                .emailVerified(true)
                .build());

        workspace = workspaceRepository.save(Workspace.builder()
                .name("Batch Workspace")
                .slug("batch-ws")
                .owner(sender)
                .build());

        senderMember = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(sender)
                .role(WorkspaceRole.OWNER)
                .build());

        channel = channelRepository.save(Channel.builder()
                .workspace(workspace)
                .name("general")
                .slug("general")
                .type(ChannelType.PUBLIC)
                .createdByMember(senderMember)
                .build());

        conversation = conversationRepository.save(Conversation.builder()
                .workspace(workspace)
                .type(ConversationType.CHANNEL)
                .channel(channel)
                .build());
    }

    @Test
    void batchExecutionPreservesSoftDeletedStateAndPlaceholderContentFromRedis() throws Exception {
        Instant now = Instant.now();

        // 1. Enqueue soft-deleted message in Redis
        MessageDto softDeletedDto = MessageDto.builder()
                .id(1001L)
                .workspaceId(workspace.getId())
                .conversationId(conversation.getId())
                .channelId(channel.getId())
                .content("Deleted message")
                .senderId(sender.getId())
                .senderMemberId(senderMember.getId())
                .senderUsername(sender.getUsername())
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
                .workspaceId(workspace.getId())
                .conversationId(conversation.getId())
                .channelId(channel.getId())
                .content("Active message content")
                .senderId(sender.getId())
                .senderMemberId(senderMember.getId())
                .senderUsername(sender.getUsername())
                .timestamp(now.plusSeconds(1))
                .isRead(false)
                .isDeleted(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();
        redisMessageRepository.saveMessage(activeDto);

        // 3. Run Spring Batch redisToDbJob
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(redisToDbJob, jobParameters);
        assertEquals("COMPLETED", jobExecution.getStatus().toString());

        // 4. Verify database messages
        List<Message> persistedMessages = messageRepository.findAll();
        assertEquals(2, persistedMessages.size());

        Message persistedSoftDeleted = persistedMessages.stream()
                .filter(m -> "Deleted message".equals(m.getContent()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Soft deleted message not found in DB"));
        assertTrue(persistedSoftDeleted.getIsDeleted(), "Message should have isDeleted = true");
        assertEquals("Deleted message", persistedSoftDeleted.getContent());

        Message persistedActive = persistedMessages.stream()
                .filter(m -> "Active message content".equals(m.getContent()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Active message not found in DB"));
        assertFalse(persistedActive.getIsDeleted(), "Active message should have isDeleted = false");
        assertEquals("Active message content", persistedActive.getContent());
    }

    @Test
    void messageItemWriterDirectlyPreservesIsDeletedFlag() throws Exception {
        MessageDto deletedDto = MessageDto.builder()
                .id(2001L)
                .workspaceId(workspace.getId())
                .conversationId(conversation.getId())
                .channelId(channel.getId())
                .content("Deleted message")
                .senderId(sender.getId())
                .senderMemberId(senderMember.getId())
                .senderUsername(sender.getUsername())
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
                .workspaceId(workspace.getId())
                .conversationId(conversation.getId())
                .channelId(channel.getId())
                .content("Deleted message")
                .senderId(sender.getId())
                .senderMemberId(senderMember.getId())
                .senderUsername(sender.getUsername())
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
