package com.example.chatapp.user.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.service.PresenceModule;
import com.example.chatapp.websocket.MessageBroker;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @MockitoBean
    private MessageBroker messageBroker;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private PresenceModule presenceModule;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    // --- Presence Sharing: the online-user list and live broadcasts ---

    /** carol (Owner, sharing), alice (sharing) and bob (Presence Sharing off) share one Workspace; alice and bob are connected. */
    private record PresenceWorkspace(Long workspaceId, User carol, String carolToken, User alice, String aliceToken,
                                     User bob, String bobToken) {
    }

    private PresenceWorkspace workspaceWithSharingAndHiddenMembers() throws Exception {
        String carolToken = signup("carol_presence");
        String aliceToken = signup("alice_presence");
        String bobToken = signup("bob_presence");
        User carol = userRepository.findByUsername("carol_presence").orElseThrow();
        User alice = userRepository.findByUsername("alice_presence").orElseThrow();
        User bob = userRepository.findByUsername("bob_presence").orElseThrow();

        Long workspaceId = workspaceService.createWorkspace(carol.getId(),
                CreateWorkspaceRequest.builder().name("Presence Space").build()).getId();
        var workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        for (User member : new User[]{alice, bob}) {
            workspaceMemberRepository.save(WorkspaceMember.builder()
                    .workspace(workspace).user(member).role(WorkspaceRole.MEMBER).build());
        }

        updateSharePresence(bobToken, false);
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of(alice.getId(), bob.getId()));
        when(sessionRegistry.isOnline(alice.getId())).thenReturn(true);
        when(sessionRegistry.isOnline(bob.getId())).thenReturn(true);
        clearInvocations(messageBroker);
        return new PresenceWorkspace(workspaceId, carol, carolToken, alice, aliceToken, bob, bobToken);
    }

    @Test
    void onlineUserListLeavesOutMembersNotSharingPresence() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();

        mockMvc.perform(get("/api/v1/users/online").header("Authorization", ws.carolToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(String.valueOf(ws.alice().getId())))
                .andExpect(jsonPath("$[0].status").value("ONLINE"));
    }

    @Test
    void memberNotSharingPresenceStillSeesSharingColleaguesOnline() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();

        mockMvc.perform(get("/api/v1/users/online").header("Authorization", ws.bobToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", containsInAnyOrder(String.valueOf(ws.alice().getId()))));
    }

    @Test
    void turningOffPresenceSharingWhileOnlineBroadcastsOfflineToTheWorkspace() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();

        updateSharePresence(ws.aliceToken(), false);

        verify(messageBroker).publishToTopic(eq("/topic/workspace." + ws.workspaceId() + ".presence"),
                argThat(presenceOf(ws.alice(), "OFFLINE")));
        verify(messageBroker).publishToUser(eq(String.valueOf(ws.carol().getId())), eq("/queue/online"),
                argThat(presenceOf(ws.alice(), "OFFLINE")));
        mockMvc.perform(get("/api/v1/users/online").header("Authorization", ws.carolToken()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void turningPresenceSharingBackOnWhileOnlineBroadcastsOnlineToTheWorkspace() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();

        updateSharePresence(ws.bobToken(), true);

        verify(messageBroker).publishToTopic(eq("/topic/workspace." + ws.workspaceId() + ".presence"),
                argThat(presenceOf(ws.bob(), "ONLINE")));
        verify(messageBroker).publishToUser(eq(String.valueOf(ws.carol().getId())), eq("/queue/online"),
                argThat(presenceOf(ws.bob(), "ONLINE")));
        mockMvc.perform(get("/api/v1/users/online").header("Authorization", ws.carolToken()))
                .andExpect(jsonPath("$[*].userId", containsInAnyOrder(
                        String.valueOf(ws.alice().getId()), String.valueOf(ws.bob().getId()))));
    }

    @Test
    void updatingPresenceSharingOnlyAffectsTheAuthenticatedUser() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();

        mockMvc.perform(put("/api/v1/users/settings")
                        .header("Authorization", ws.aliceToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharePresence\": false, \"userId\": " + ws.carol().getId() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/settings").header("Authorization", ws.carolToken()))
                .andExpect(jsonPath("$.sharePresence").value(true));
        mockMvc.perform(get("/api/v1/users/settings").header("Authorization", ws.aliceToken()))
                .andExpect(jsonPath("$.sharePresence").value(false));
        mockMvc.perform(get("/api/v1/users/settings").header("Authorization", ws.bobToken()))
                .andExpect(jsonPath("$.sharePresence").value(false));
    }

    @Test
    void presenceSharingBroadcastIsPublishedOnlyAfterTheChangeCommits() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            presenceModule.setPresenceSharing(ws.alice().getId(), false);
            verify(messageBroker, never()).publishToTopic(anyString(), any());
            verify(messageBroker, never()).publishToUser(anyString(), anyString(), any());
        });

        verify(messageBroker).publishToTopic(eq("/topic/workspace." + ws.workspaceId() + ".presence"),
                argThat(presenceOf(ws.alice(), "OFFLINE")));
        assertFalse(presenceModule.sharesPresence(ws.alice().getId()));
    }

    @Test
    void rolledBackPresenceSharingChangePublishesNothing() throws Exception {
        PresenceWorkspace ws = workspaceWithSharingAndHiddenMembers();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            presenceModule.setPresenceSharing(ws.alice().getId(), false);
            status.setRollbackOnly();
        });

        verify(messageBroker, never()).publishToTopic(anyString(), any());
        verify(messageBroker, never()).publishToUser(anyString(), anyString(), any());
        assertTrue(presenceModule.sharesPresence(ws.alice().getId()));
    }

    private static org.mockito.ArgumentMatcher<Object> presenceOf(User user, String status) {
        return payload -> payload instanceof Map<?, ?> map
                && user.getId().equals(map.get("userId"))
                && status.equals(map.get("status"));
    }

    private String signup(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readValue(result.getResponse().getContentAsString(), Map.class).get("token");
    }

    private void updateSharePresence(String token, boolean sharePresence) throws Exception {
        mockMvc.perform(put("/api/v1/users/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharePresence\": " + sharePresence + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharePresence").value(sharePresence));
    }
}
