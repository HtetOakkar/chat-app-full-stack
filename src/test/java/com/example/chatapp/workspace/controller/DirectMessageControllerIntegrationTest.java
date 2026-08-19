package com.example.chatapp.workspace.controller;

import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import com.example.chatapp.workspace.service.WorkspaceService;
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

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectMessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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
    private MessageRepository messageRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;
    private User alice;
    private User bob;
    private WorkspaceDto workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspaceInvitationRepository.deleteAll();
        messageRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // Alice (Owner)
        MvcResult resAlice = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice_dm\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        aliceToken = "Bearer " + objectMapper.readValue(resAlice.getResponse().getContentAsString(), Map.class).get("token");
        alice = userRepository.findByUsername("alice_dm").orElseThrow();

        // Bob
        MvcResult resBob = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob_dm\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        bobToken = "Bearer " + objectMapper.readValue(resBob.getResponse().getContentAsString(), Map.class).get("token");
        bob = userRepository.findByUsername("bob_dm").orElseThrow();

        // Create Workspace
        workspace = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Dev Space DM").build());

        // Enroll Bob into Workspace
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(wsEntity)
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());
    }

    @Test
    void getOrCreateDirectMessageAndExchangeMessages() throws Exception {
        // Alice starts DM with Bob
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/dm/" + bob.getId())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNumber())
                .andExpect(jsonPath("$.targetMember.username").value("bob_dm"));

        // Alice sends message to Bob
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/dm/" + bob.getId() + "/messages")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hey Bob, reviewing the PR now.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hey Bob, reviewing the PR now."))
                .andExpect(jsonPath("$.senderUsername").value("alice_dm"));

        // Bob reads messages
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/dm/" + alice.getId() + "/messages")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("Hey Bob, reviewing the PR now."));
    }
}
