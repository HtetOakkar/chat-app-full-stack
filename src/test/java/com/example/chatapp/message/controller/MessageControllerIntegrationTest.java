package com.example.chatapp.message.controller;

import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;
    private String charlieToken;

    private User alice;
    private User bob;
    private User charlie;

    private Workspace workspace;
    private WorkspaceMember aliceMember;
    private WorkspaceMember bobMember;
    private Channel generalChannel;
    private Conversation channelConversation;

    @BeforeEach
    void setUp() throws Exception {
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

        // Register Alice
        MvcResult aliceRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> aliceMap = objectMapper.readValue(aliceRes.getResponse().getContentAsString(), Map.class);
        aliceToken = "Bearer " + aliceMap.get("token");
        alice = userRepository.findByUsername("alice").orElseThrow();

        // Register Bob
        MvcResult bobRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> bobMap = objectMapper.readValue(bobRes.getResponse().getContentAsString(), Map.class);
        bobToken = "Bearer " + bobMap.get("token");
        bob = userRepository.findByUsername("bob").orElseThrow();

        // Register Charlie
        MvcResult charlieRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"charlie\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> charlieMap = objectMapper.readValue(charlieRes.getResponse().getContentAsString(), Map.class);
        charlieToken = "Bearer " + charlieMap.get("token");
        charlie = userRepository.findByUsername("charlie").orElseThrow();

        workspace = workspaceRepository.save(Workspace.builder()
                .name("Acme")
                .slug("acme")
                .owner(alice)
                .build());

        aliceMember = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(alice)
                .role(WorkspaceRole.OWNER)
                .build());

        bobMember = workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());

        generalChannel = channelRepository.save(Channel.builder()
                .workspace(workspace)
                .name("general")
                .slug("general")
                .type(ChannelType.PUBLIC)
                .createdByMember(aliceMember)
                .build());

        channelConversation = conversationRepository.save(Conversation.builder()
                .workspace(workspace)
                .type(ConversationType.CHANNEL)
                .channel(generalChannel)
                .build());
    }

    @Test
    void getPublicMessagesShouldReturnPagedMessages() throws Exception {
        Instant now = Instant.now();

        com.example.chatapp.message.model.dto.MessageDto m1 = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(101L).workspaceId(workspace.getId()).conversationId(channelConversation.getId())
                .content("Pub 1").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .senderId(alice.getId()).senderMemberId(aliceMember.getId()).timestamp(now.minusSeconds(100)).build();

        com.example.chatapp.message.model.dto.MessageDto m2 = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(102L).workspaceId(workspace.getId()).conversationId(channelConversation.getId())
                .content("Pub 2").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .senderId(bob.getId()).senderMemberId(bobMember.getId()).timestamp(now.minusSeconds(50)).build();

        com.example.chatapp.message.model.dto.MessageDto m3 = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(103L).workspaceId(workspace.getId()).conversationId(channelConversation.getId())
                .content("Pub 3").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .senderId(alice.getId()).senderMemberId(aliceMember.getId()).timestamp(now).build();

        redisMessageRepository.saveMessage(m1);
        redisMessageRepository.saveMessage(m2);
        redisMessageRepository.saveMessage(m3);

        // Fetch without cursor (limit 2, newest first)
        MvcResult res = mockMvc.perform(get("/api/v1/messages/public?limit=2")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("Pub 3"))
                .andExpect(jsonPath("$.messages[1].content").value("Pub 2"))
                .andReturn();

        String json = res.getResponse().getContentAsString();
        Map<?, ?> page = objectMapper.readValue(json, Map.class);
        String nextCursor = (String) page.get("nextCursor");

        // Fetch with cursor to get Pub 1
        mockMvc.perform(get("/api/v1/messages/public?limit=2&cursor=" + nextCursor)
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("Pub 1"));
    }

    @Test
    void deletePublicMessageInRedisSuccessfully() throws Exception {
        Instant now = Instant.now();
        com.example.chatapp.message.model.dto.MessageDto redisMsg = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(9999L)
                .senderId(alice.getId())
                .senderUsername(alice.getUsername())
                .recipientId(null)
                .content("Hello public")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(now)
                .build();
        redisMessageRepository.saveMessage(redisMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/9999")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9999))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        java.util.List<com.example.chatapp.message.model.dto.MessageDto> peeked = redisMessageRepository.peekMessages();
        org.junit.jupiter.api.Assertions.assertEquals(1, peeked.size());
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", peeked.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(peeked.get(0).getIsDeleted());
    }

    @Test
    void deletePublicMessageInDbSuccessfully() throws Exception {
        Message dbMsg = Message.builder()
                .conversation(channelConversation)
                .senderMember(aliceMember)
                .content("Hello db message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .sentAt(Instant.now())
                .build();
        dbMsg = messageRepository.save(dbMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void deletePublicMessageByAnotherUserShouldBeUnauthorized() throws Exception {
        Message dbMsg = Message.builder()
                .conversation(channelConversation)
                .senderMember(aliceMember)
                .content("Hello db message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .sentAt(Instant.now())
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Bob tries to delete Alice's message
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", bobToken))
                .andExpect(status().isUnauthorized());

        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Hello db message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertFalse(updated.getIsDeleted());
    }

    @Test
    void deletePrivateMessageInRedisSuccessfully() throws Exception {
        Instant now = Instant.now();
        com.example.chatapp.message.model.dto.MessageDto redisMsg = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(7777L)
                .senderId(alice.getId())
                .senderUsername(alice.getUsername())
                .recipientId(bob.getId())
                .content("Hello Bob private")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(now)
                .build();
        redisMessageRepository.saveMessage(redisMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/7777")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7777))
                .andExpect(jsonPath("$.recipientId").value(bob.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        java.util.List<com.example.chatapp.message.model.dto.MessageDto> peeked = redisMessageRepository.peekMessages();
        org.junit.jupiter.api.Assertions.assertEquals(1, peeked.size());
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", peeked.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(peeked.get(0).getIsDeleted());
    }

    @Test
    void generatedTransientIdsShouldBeWithinJsSafeLimit() {
        for (int i = 0; i < 1000; i++) {
            com.example.chatapp.message.model.dto.MessageDto dto = com.example.chatapp.message.model.dto.MessageDto.builder()
                    .content("test")
                    .senderId(alice.getId())
                    .build();
            messageService.saveMessage(dto);
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getId());
            org.junit.jupiter.api.Assertions.assertTrue(dto.getId() >= 1L);
            org.junit.jupiter.api.Assertions.assertTrue(dto.getId() <= 9007199254740991L, 
                    "ID " + dto.getId() + " is larger than JS safe integer limit");
        }
    }
}
