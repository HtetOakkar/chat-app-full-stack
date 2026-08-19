package com.example.chatapp.workspace.controller;

import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
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
class ChannelControllerIntegrationTest {

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
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private MessageRepository messageRepository;

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
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // Alice (Owner)
        MvcResult resAlice = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice_chan\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        aliceToken = "Bearer " + objectMapper.readValue(resAlice.getResponse().getContentAsString(), Map.class).get("token");
        alice = userRepository.findByUsername("alice_chan").orElseThrow();

        // Bob
        MvcResult resBob = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob_chan\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        bobToken = "Bearer " + objectMapper.readValue(resBob.getResponse().getContentAsString(), Map.class).get("token");
        bob = userRepository.findByUsername("bob_chan").orElseThrow();

        // Create Workspace
        workspace = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Dev Space").build());

        // Enroll Bob into Workspace
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(wsEntity)
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());
    }

    @Test
    void createPublicChannelShouldBeVisibleToAllMembers() throws Exception {
        String reqBody = """
                {
                  "name": "engineering",
                  "type": "PUBLIC"
                }
                """;

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("engineering"))
                .andExpect(jsonPath("$.slug").value("engineering"))
                .andExpect(jsonPath("$.type").value("PUBLIC"));

        // Bob can list it
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // general + engineering
                .andExpect(jsonPath("$[?(@.name == 'engineering')]").exists());
    }

    @Test
    void createPrivateChannelShouldBeHiddenFromNonMembers() throws Exception {
        String reqBody = """
                {
                  "name": "executives",
                  "type": "PRIVATE"
                }
                """;

        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("executives"))
                .andExpect(jsonPath("$.type").value("PRIVATE"))
                .andReturn();

        Long channelId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Alice sees it
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'executives')]").exists());

        // Bob does NOT see it
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'executives')]").doesNotExist());

        // Alice adds Bob to the private channel
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated());

        // Now Bob sees it
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'executives')]").exists());
    }

    @Test
    void sendAndGetChannelMessages() throws Exception {
        // Post message to general channel
        Long generalId = channelRepository.findByWorkspaceIdAndSlug(workspace.getId(), "general").orElseThrow().getId();

        mockMvc.perform(post("/api/channels/" + generalId + "/messages")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Welcome to Acme general channel!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Welcome to Acme general channel!"))
                .andExpect(jsonPath("$.senderUsername").value("alice_chan"));

        // Bob fetches message history
        mockMvc.perform(get("/api/channels/" + generalId + "/messages")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("Welcome to Acme general channel!"));
    }
}
