package com.example.chatapp.workspace.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.InvitationType;
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
class InvitationControllerIntegrationTest {

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
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private EmailService emailService;

    private String aliceToken;
    private String bobToken;
    private User alice;
    private User bob;
    private WorkspaceDto workspace;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // Alice (Owner)
        MvcResult resAlice = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice_inv\",\"email\":\"alice_inv@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        aliceToken = "Bearer " + objectMapper.readValue(resAlice.getResponse().getContentAsString(), Map.class).get("token");
        alice = userRepository.findByUsername("alice_inv").orElseThrow();

        // Bob
        MvcResult resBob = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob_inv\",\"email\":\"bob_inv@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        bobToken = "Bearer " + objectMapper.readValue(resBob.getResponse().getContentAsString(), Map.class).get("token");
        bob = userRepository.findByUsername("bob_inv").orElseThrow();

        // Create Workspace
        workspace = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Acme Collab").build());
    }

    @Test
    void createAndAcceptEmailInvitation() throws Exception {
        String inviteReq = """
                {
                  "email": "bob_inv@example.com",
                  "role": "MEMBER"
                }
                """;

        MvcResult inviteRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.email").value("bob_inv@example.com"))
                .andReturn();

        String token = (String) objectMapper.readValue(inviteRes.getResponse().getContentAsString(), Map.class).get("token");

        // Preview token
        mockMvc.perform(get("/api/invitations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Acme Collab"));

        // Bob accepts token
        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspace.getId()))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void createAndAcceptJoinLink() throws Exception {
        String joinReq = """
                {
                  "maxUses": 5,
                  "role": "MEMBER"
                }
                """;

        MvcResult joinRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/join-link")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.type").value("SHAREABLE_LINK"))
                .andReturn();

        String token = (String) objectMapper.readValue(joinRes.getResponse().getContentAsString(), Map.class).get("token");

        // Bob accepts join link
        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspace.getId()))
                .andExpect(jsonPath("$.memberCount").value(2));
    }
}
