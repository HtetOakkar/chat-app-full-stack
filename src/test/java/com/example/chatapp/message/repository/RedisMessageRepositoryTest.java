package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.repository.*;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
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

        redisMessageRepository.saveMessage(message);

        List<MessageDto> messages = redisMessageRepository.getMessages(1);
        assertNotNull(messages);
        assertFalse(messages.isEmpty());
        assertEquals("TDD message", messages.get(0).getContent());
        assertNotNull(messages.get(0).getTimestamp());
        assertEquals(2L, messages.get(0).getRecipientId());
    }

    @Test
    public void publicHistoryShouldUseCursorOrderAcrossPendingMessages() {
        Instant timestamp = Instant.parse("2026-07-10T06:00:00Z");
        MessageDto first = MessageDto.builder()
                .id(100L)
                .content("newer tie")
                .senderId(1L)
                .senderUsername("alice")
                .recipientId(null)
                .timestamp(timestamp)
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();
        MessageDto second = MessageDto.builder()
                .id(99L)
                .content("older tie")
                .senderId(1L)
                .senderUsername("alice")
                .recipientId(null)
                .timestamp(timestamp)
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        redisMessageRepository.saveMessage(second);
        redisMessageRepository.saveMessage(first);

        List<MessageDto> firstPage = redisMessageRepository.findPublicMessagesBefore(null, null, 1);
        assertEquals(1, firstPage.size());
        assertEquals(100L, firstPage.get(0).getId());

        List<MessageDto> secondPage = redisMessageRepository.findPublicMessagesBefore(timestamp, 100L, 1);
        assertEquals(1, secondPage.size());
        assertEquals(99L, secondPage.get(0).getId());
    }

    @Test
    public void privateHistoryShouldOnlyReturnTheRequestedConversation() {
        Instant now = Instant.parse("2026-07-10T06:00:00Z");
        redisMessageRepository.saveMessage(MessageDto.builder()
                .id(200L)
                .content("alice to bob")
                .senderId(1L)
                .senderUsername("alice")
                .recipientId(2L)
                .timestamp(now)
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build());
        redisMessageRepository.saveMessage(MessageDto.builder()
                .id(201L)
                .content("alice to charlie")
                .senderId(1L)
                .senderUsername("alice")
                .recipientId(3L)
                .timestamp(now.plusSeconds(1))
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build());

        List<MessageDto> messages = redisMessageRepository.findPrivateMessagesBefore(1L, 2L, null, null, null, 10);

        assertEquals(1, messages.size());
        assertEquals("alice to bob", messages.get(0).getContent());
    }

    @Test
    public void testMessageProcessorForPublicMessage() throws Exception {
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        User sender = userRepository.findByUsername("tdd_user_proc").orElseGet(() -> {
            User user = User.builder()
                    .username("tdd_user_proc")
                    .password("pass")
                    .role(userRole)
                    .email("proc@chatapp.com")
                    .emailVerified(true)
                    .version(0L)
                    .build();
            return userRepository.save(user);
        });

        Workspace ws = workspaceRepository.save(Workspace.builder()
                .name("Test WS")
                .slug("test-ws-proc")
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

        MessageDto publicMessageDto = MessageDto.builder()
                .workspaceId(ws.getId())
                .conversationId(conv.getId())
                .channelId(channel.getId())
                .content("Public text message")
                .senderId(sender.getId())
                .senderMemberId(member.getId())
                .senderUsername(sender.getUsername())
                .timestamp(Instant.now())
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .build();

        Message processedMessage = messageProcessor.process(publicMessageDto);

        assertNotNull(processedMessage);
        assertNotNull(processedMessage.getConversation());
        assertEquals(conv.getId(), processedMessage.getConversation().getId());
    }
}
