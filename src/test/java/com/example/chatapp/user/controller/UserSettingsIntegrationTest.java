package com.example.chatapp.user.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.message.repository.MessageRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsIntegrationTest {

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

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private com.example.chatapp.websocket.SessionRegistry sessionRegistry;

    private String userToken;

    @BeforeEach
    void setup() throws Exception {
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
                  "username": "testuser",
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
    }

    @Test
    void getSettingsShouldReturnDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/v1/users/settings")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharePresence").value(true));
    }

    @Test
    void updateSettingsShouldModifySharePresence() throws Exception {
        String updateBody = """
                {
                  "sharePresence": false
                }
                """;

        mockMvc.perform(put("/api/v1/users/settings")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharePresence").value(false));

        // Verify that subsequent GET returns the updated settings
        mockMvc.perform(get("/api/v1/users/settings")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharePresence").value(false));
    }

    @Test
    void updateSettingsWithInvalidBodyShouldReturnBadRequest() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(put("/api/v1/users/settings")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/users/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharePresence\": true}"))
                .andExpect(status().isUnauthorized());
    }
}
