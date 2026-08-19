package com.example.chatapp.workspace.controller;

import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
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

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceControllerIntegrationTest {

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
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private User currentUser;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        String signupBody = """
                {
                  "username": "alice_ctrl",
                  "password": "password123"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        userToken = "Bearer " + responseMap.get("token");
        currentUser = userRepository.findByUsername("alice_ctrl").orElseThrow();
    }

    @Test
    void createWorkspaceShouldReturn201WithWorkspaceDetails() throws Exception {
        String reqBody = """
                {
                  "name": "Design Systems Inc",
                  "slug": "design-systems"
                }
                """;

        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Design Systems Inc"))
                .andExpect(jsonPath("$.slug").value("design-systems"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.channelCount").value(1));
    }

    @Test
    void getUserWorkspacesShouldReturnListOfWorkspaces() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Workspace One\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/workspaces/me")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Workspace One"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void getWorkspaceBySlugShouldReturnWorkspaceDetails() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Frontend Team\", \"slug\": \"frontend-team\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/workspaces/by-slug/frontend-team")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("frontend-team"))
                .andExpect(jsonPath("$.name").value("Frontend Team"));
    }
}
