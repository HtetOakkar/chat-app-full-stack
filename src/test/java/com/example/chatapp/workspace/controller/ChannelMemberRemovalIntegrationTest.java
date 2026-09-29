package com.example.chatapp.workspace.controller;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import com.example.chatapp.workspace.service.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Removing someone from a Private Channel must not single them out in public (#82, #96): the
 * Channel sees the same "@member left the channel" notice a voluntary departure produces, the
 * Workspace-wide event carries no remover fields, and only the removed User is told who removed
 * them, on their personal queue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelMemberRemovalIntegrationTest {

    private static final String CHANNEL_REMOVAL_QUEUE = "/queue/channel-removals";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private ChannelRepository channelRepository;
    @Autowired private ChannelMemberRepository channelMemberRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private WorkspaceInvitationRepository workspaceInvitationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private ObjectMapper objectMapper;

    @MockitoSpyBean
    private MessageBroker messageBroker;

    private String aliceToken;
    private String bobToken;
    private String carolToken;
    private User bob;
    private User carol;
    private WorkspaceDto workspace;
    private Long channelId;

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

        aliceToken = signUp("alice_rm");
        bobToken = signUp("bob_rm");
        carolToken = signUp("carol_rm");
        User alice = userRepository.findByUsername("alice_rm").orElseThrow();
        bob = userRepository.findByUsername("bob_rm").orElseThrow();
        carol = userRepository.findByUsername("carol_rm").orElseThrow();

        // Alice owns the Workspace; Bob is a Member and Carol an Admin.
        workspace = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Removal Space").build());
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(wsEntity).user(bob).role(WorkspaceRole.MEMBER).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(wsEntity).user(carol).role(WorkspaceRole.ADMIN).build());

        // Alice creates a Private Channel and adds Bob.
        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"secret-ops\", \"type\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        channelId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated());

        clearInvocations(messageBroker);
    }

    @Test
    void adminRemovalReadsLikeADepartureAndTellsOnlyTheRemovedUserWhoRemovedThem() throws Exception {
        mockMvc.perform(delete("/api/channels/" + channelId + "/members/" + bob.getId())
                        .header("Authorization", carolToken))
                .andExpect(status().isNoContent());

        assertChannelReceivedLeaveNotice("@bob_rm left the channel");

        JsonNode event = singleWorkspaceEvent();
        assertThat(fieldNames(event)).containsExactlyInAnyOrder("type", "workspaceId", "channelId", "userId", "isSelfLeave");
        assertThat(event.get("type").asText()).isEqualTo("CHANNEL_MEMBER_REMOVED");
        assertThat(event.get("workspaceId").asLong()).isEqualTo(workspace.getId());
        assertThat(event.get("channelId").asLong()).isEqualTo(channelId);
        assertThat(event.get("userId").asLong()).isEqualTo(bob.getId());
        assertThat(event.get("isSelfLeave").asBoolean()).isFalse();

        ArgumentCaptor<Object> noticeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messageBroker).publishToUser(eq(bob.getId().toString()), eq(CHANNEL_REMOVAL_QUEUE), noticeCaptor.capture());
        JsonNode notice = objectMapper.valueToTree(noticeCaptor.getValue());
        assertThat(notice.get("type").asText()).isEqualTo("REMOVED_FROM_CHANNEL");
        assertThat(notice.get("workspaceId").asLong()).isEqualTo(workspace.getId());
        assertThat(notice.get("channelId").asLong()).isEqualTo(channelId);
        assertThat(notice.get("channelName").asText()).isEqualTo("secret-ops");
        assertThat(notice.get("removedByUserId").asLong()).isEqualTo(carol.getId());
        assertThat(notice.get("removedByUsername").asText()).isEqualTo("carol_rm");
        assertThat(notice.get("removedByFullName").asText()).isEqualTo("carol_rm");

        // Nobody else is sent anything on a personal queue.
        verify(messageBroker, never()).publishToUser(argThat(id -> !bob.getId().toString().equals(id)), anyString(), any());

        // Bob has lost access to the Channel's history.
        mockMvc.perform(get("/api/channels/" + channelId + "/messages").header("Authorization", bobToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void voluntaryDepartureProducesTheSameNoticeAndNoPersonalNotice() throws Exception {
        mockMvc.perform(delete("/api/channels/" + channelId + "/members/" + bob.getId())
                        .header("Authorization", bobToken))
                .andExpect(status().isNoContent());

        assertChannelReceivedLeaveNotice("@bob_rm left the channel");

        JsonNode event = singleWorkspaceEvent();
        assertThat(fieldNames(event)).containsExactlyInAnyOrder("type", "workspaceId", "channelId", "userId", "isSelfLeave");
        assertThat(event.get("userId").asLong()).isEqualTo(bob.getId());
        assertThat(event.get("isSelfLeave").asBoolean()).isTrue();

        verify(messageBroker, never()).publishToUser(anyString(), anyString(), any());

        mockMvc.perform(get("/api/channels/" + channelId + "/messages").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("@bob_rm left the channel"));
    }

    private String signUp(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readValue(res.getResponse().getContentAsString(), Map.class).get("token");
    }

    private void assertChannelReceivedLeaveNotice(String content) {
        verify(messageBroker).publishToTopic(
                eq("/topic/workspace." + workspace.getId() + ".channel." + channelId),
                argThat(payload -> payload instanceof MessageDto notice
                        && content.equals(notice.getContent())
                        && notice.getMessageType() == MessageType.SYSTEM));
    }

    private JsonNode singleWorkspaceEvent() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messageBroker).publishToTopic(eq("/topic/workspace." + workspace.getId() + ".events"), eventCaptor.capture());
        List<Object> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(1);
        return objectMapper.valueToTree(events.get(0));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
