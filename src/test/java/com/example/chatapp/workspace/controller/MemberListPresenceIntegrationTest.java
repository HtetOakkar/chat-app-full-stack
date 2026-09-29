package com.example.chatapp.workspace.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.websocket.SessionRegistry;
import com.example.chatapp.workspace.model.entity.ChannelType;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Presence Sharing in member lists: the online flag in Channel member, DM and Member-directory responses
 * follows each target Member's own choice. carol (Owner) views; alice shares Presence and bob does not,
 * and both are connected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberListPresenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private WorkspaceService workspaceService;

    // Same overrides as UserSettingsIntegrationTest, so both share one Spring context.
    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @MockitoBean
    private MessageBroker messageBroker;

    private Long workspaceId;
    private User carol;
    private User alice;
    private User bob;
    private String carolToken;
    private String bobToken;

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

        carolToken = signup("carol_lists");
        signup("alice_lists");
        bobToken = signup("bob_lists");
        carol = userRepository.findByUsername("carol_lists").orElseThrow();
        alice = userRepository.findByUsername("alice_lists").orElseThrow();
        bob = userRepository.findByUsername("bob_lists").orElseThrow();

        workspaceId = workspaceService.createWorkspace(carol.getId(),
                CreateWorkspaceRequest.builder().name("Lists Space").build()).getId();
        var workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        for (User member : new User[]{alice, bob}) {
            workspaceMemberRepository.save(WorkspaceMember.builder()
                    .workspace(workspace).user(member).role(WorkspaceRole.MEMBER).build());
        }

        mockMvc.perform(put("/api/v1/users/settings")
                        .header("Authorization", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharePresence\": false}"))
                .andExpect(status().isOk());

        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of(alice.getId(), bob.getId()));
        when(sessionRegistry.isOnline(alice.getId())).thenReturn(true);
        when(sessionRegistry.isOnline(bob.getId())).thenReturn(true);
    }

    // --- Channel member lists ---

    @Test
    void publicChannelMemberListShowsMemberNotSharingPresenceAsOffline() throws Exception {
        Long generalId = channelRepository.findByWorkspaceId(workspaceId).get(0).getId();

        ResultActions members = mockMvc.perform(get("/api/channels/" + generalId + "/members")
                        .header("Authorization", carolToken))
                .andExpect(status().isOk());

        expectPresence(members, "$", alice, "ONLINE");
        expectPresence(members, "$", bob, "OFFLINE");
        expectPresence(members, "$", carol, "OFFLINE");
    }

    @Test
    void privateChannelMemberListShowsMemberNotSharingPresenceAsOffline() throws Exception {
        Long channelId = createPrivateChannelWithAliceAndBob();

        ResultActions members = mockMvc.perform(get("/api/channels/" + channelId + "/members")
                        .header("Authorization", carolToken))
                .andExpect(status().isOk());

        expectPresence(members, "$", alice, "ONLINE");
        expectPresence(members, "$", bob, "OFFLINE");
    }

    @Test
    void memberNotSharingPresenceStillSeesSharingColleaguesOnlineInChannelMemberList() throws Exception {
        Long generalId = channelRepository.findByWorkspaceId(workspaceId).get(0).getId();

        ResultActions members = mockMvc.perform(get("/api/channels/" + generalId + "/members")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk());

        expectPresence(members, "$", alice, "ONLINE");
        expectPresence(members, "$", bob, "ONLINE");
    }

    // --- Member directory ---

    @Test
    void memberDirectoryShowsMemberNotSharingPresenceAsOffline() throws Exception {
        ResultActions directory = mockMvc.perform(get("/api/workspaces/" + workspaceId + "/members")
                        .header("Authorization", carolToken))
                .andExpect(status().isOk());

        expectPresence(directory, "$", alice, "ONLINE");
        expectPresence(directory, "$", bob, "OFFLINE");
        expectPresence(directory, "$", carol, "OFFLINE");
    }

    @Test
    void memberNotSharingPresenceStillSeesSharingColleaguesOnlineInMemberDirectory() throws Exception {
        ResultActions directory = mockMvc.perform(get("/api/workspaces/" + workspaceId + "/members")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk());

        expectPresence(directory, "$", alice, "ONLINE");
        expectPresence(directory, "$", bob, "ONLINE");
    }

    // --- DMs ---

    @Test
    void dmHeaderShowsMemberNotSharingPresenceAsOffline() throws Exception {
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/dm/" + bob.getId())
                        .header("Authorization", carolToken))
                .andExpect(jsonPath("$.targetMember.userId").value(bob.getId()))
                .andExpect(jsonPath("$.targetMember.presenceStatus").value("OFFLINE"));

        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/dm/" + alice.getId())
                        .header("Authorization", carolToken))
                .andExpect(jsonPath("$.targetMember.userId").value(alice.getId()))
                .andExpect(jsonPath("$.targetMember.presenceStatus").value("ONLINE"));
    }

    @Test
    void dmListShowsMemberNotSharingPresenceAsOffline() throws Exception {
        openDm(carolToken, bob);
        openDm(carolToken, alice);

        ResultActions dms = mockMvc.perform(get("/api/workspaces/" + workspaceId + "/dm")
                        .header("Authorization", carolToken))
                .andExpect(status().isOk());

        expectPresence(dms, "$[*].targetMember", alice, "ONLINE");
        expectPresence(dms, "$[*].targetMember", bob, "OFFLINE");
    }

    @Test
    void memberNotSharingPresenceStillSeesSharingColleaguesOnlineInDms() throws Exception {
        openDm(bobToken, alice);

        mockMvc.perform(get("/api/workspaces/" + workspaceId + "/dm")
                        .header("Authorization", bobToken))
                .andExpect(jsonPath("$[0].targetMember.userId").value(alice.getId()))
                .andExpect(jsonPath("$[0].targetMember.presenceStatus").value("ONLINE"));
    }

    // --- Batched lookup ---

    @Test
    void eachMemberListResolvesPresenceWithOneBatchedLookup() throws Exception {
        Long generalId = channelRepository.findByWorkspaceId(workspaceId).get(0).getId();
        openDm(carolToken, bob);
        openDm(carolToken, alice);

        for (String path : new String[]{
                "/api/channels/" + generalId + "/members",
                "/api/workspaces/" + workspaceId + "/members",
                "/api/workspaces/" + workspaceId + "/dm"}) {
            clearInvocations(sessionRegistry);

            mockMvc.perform(get(path).header("Authorization", carolToken)).andExpect(status().isOk());

            verify(sessionRegistry, times(1)).getOnlineUserIds();
            verify(sessionRegistry, never()).isOnline(any());
        }
    }

    private Long createPrivateChannelWithAliceAndBob() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/channels")
                        .header("Authorization", carolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"hidden-ops\", \"type\": \"" + ChannelType.PRIVATE + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long channelId = ((Number) objectMapper.readValue(created.getResponse().getContentAsString(), Map.class)
                .get("id")).longValue();
        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", carolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [" + alice.getId() + ", " + bob.getId() + "]}"))
                .andExpect(status().is2xxSuccessful());
        return channelId;
    }

    private void openDm(String token, User target) throws Exception {
        mockMvc.perform(post("/api/workspaces/" + workspaceId + "/dm/" + target.getId())
                        .header("Authorization", token))
                .andExpect(status().is2xxSuccessful());
    }

    private static void expectPresence(ResultActions result, String membersPath, User user, String presence)
            throws Exception {
        result.andExpect(jsonPath(membersPath + "[?(@.userId == " + user.getId() + ")].presenceStatus")
                .value(contains(presence)));
    }

    private String signup(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readValue(result.getResponse().getContentAsString(), Map.class).get("token");
    }
}
