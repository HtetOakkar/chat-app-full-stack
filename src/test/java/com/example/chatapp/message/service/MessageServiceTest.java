package com.example.chatapp.message.service;

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
    private RoleRepository roleRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @BeforeEach
    public void setUp() {
        cleanAll();
    }

    @AfterEach
    public void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        redisMessageRepository.clear();
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
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
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        User sender = userRepository.save(User.builder()
                .username("sender_test")
                .password("password")
                .role(userRole)
                .email("sender_test@chatapp.com")
                .emailVerified(true)
                .version(0L)
                .build());

        Workspace ws = workspaceRepository.save(Workspace.builder()
                .name("Test WS")
                .slug("test-ws")
                .owner(sender)
                .build());

        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(ws)
                .user(sender)
                .role(WorkspaceRole.OWNER)
                .build());

        Channel channel = channelRepository.save(Channel.builder()
                .workspace(ws)
                .name("general")
                .slug("general")
                .type(ChannelType.PUBLIC)
                .createdByMember(member)
                .build());

        Conversation conv = conversationRepository.save(Conversation.builder()
                .workspace(ws)
                .type(ConversationType.CHANNEL)
                .channel(channel)
                .build());

        // 1. Test null sentAt -> should be populated
        Message msg1 = Message.builder()
                .conversation(conv)
                .senderMember(member)
                .content("Null sentAt message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .build();

        assertNull(msg1.getSentAt(), "Initially, sentAt should be null");
        msg1 = messageRepository.save(msg1);
        assertNotNull(msg1.getSentAt(), "After save, sentAt should be generated via @PrePersist");

        // 2. Test pre-set sentAt -> should be preserved
        Instant customTimestamp = Instant.now().minusSeconds(3600); // 1 hour ago
        Message msg2 = Message.builder()
                .conversation(conv)
                .senderMember(member)
                .content("Pre-set sentAt message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .sentAt(customTimestamp)
                .build();

        assertEquals(customTimestamp, msg2.getSentAt(), "Initially, sentAt should be the custom timestamp");
        msg2 = messageRepository.save(msg2);
        assertEquals(customTimestamp, msg2.getSentAt(), "After save, pre-set sentAt must be preserved and not overridden");
    }
}
