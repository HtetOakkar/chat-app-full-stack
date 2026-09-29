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
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    void singleMemberAddPostsActivityNoticeAndGrantsAccess() throws Exception {
        // Alice creates private channel
        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"secret-ops\", \"type\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long channelId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Alice sends an initial message
        mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Classified info before Bob joined\"}"))
                .andExpect(status().isCreated());

        // Alice adds Bob
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated());

        // Bob fetches messages and sees past message AND the automated system activity notice (newest first)
        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("@alice_chan added @bob_chan to the channel"))
                .andExpect(jsonPath("$.messages[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[1].content").value("Classified info before Bob joined"));
    }


    @Test
    void batchMemberAdditionPostsConsolidatedActivityNotice() throws Exception {
        // Create Carol & David
        MvcResult resCarol = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol_chan\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        User carol = userRepository.findByUsername("carol_chan").orElseThrow();

        MvcResult resDavid = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"david_chan\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        User david = userRepository.findByUsername("david_chan").orElseThrow();

        // Add Carol & David to workspace
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(wsEntity).user(carol).role(WorkspaceRole.MEMBER).build());
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(wsEntity).user(david).role(WorkspaceRole.MEMBER).build());

        // Alice creates private channel
        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"squad-launch\", \"type\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long channelId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Alice batch-adds Bob, Carol, and David in one atomic call
        String batchBody = String.format("{\"userIds\": [%d, %d, %d]}", bob.getId(), carol.getId(), david.getId());
        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added", contains(bob.getId().intValue(), carol.getId().intValue(), david.getId().intValue())))
                .andExpect(jsonPath("$.alreadyMembers", empty()))
                .andExpect(jsonPath("$.notWorkspaceMembers", empty()));

        // Verify single consolidated activity notice in channel
        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("@alice_chan added @bob_chan, @carol_chan, and @david_chan to the channel"))
                .andExpect(jsonPath("$.messages[0].messageType").value("SYSTEM"));
    }

    @Test
    void channelDepartureAndRemovalPostsUniformLeaveNoticeAndRevokesAccess() throws Exception {
        // Alice creates private channel
        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"alpha-team\", \"type\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long channelId = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Add Bob
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated());

        // Bob voluntarily leaves the channel
        mockMvc.perform(delete("/api/channels/" + channelId + "/members/" + bob.getId())
                        .header("Authorization", bobToken))
                .andExpect(status().isNoContent());

        // Bob can no longer read or send messages in the channel
        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", bobToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/channels/" + channelId + "/messages")
                        .header("Authorization", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Should fail\"}"))
                .andExpect(status().isUnauthorized());

        // Alice checks channel messages and sees the uniform leave notice
        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2))) // leave notice (newest) + add notice (older)
                .andExpect(jsonPath("$.messages[0].content").value("@bob_chan left the channel"))
                .andExpect(jsonPath("$.messages[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[1].content").value("@alice_chan added @bob_chan to the channel"))
                .andExpect(jsonPath("$.messages[1].messageType").value("SYSTEM"));
    }


    // --- Batch Channel Member addition (#80, #81, #97) ---

    private User signUp(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        return userRepository.findByUsername(username).orElseThrow();
    }

    private User signUpWorkspaceMember(String username) throws Exception {
        User user = signUp(username);
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(wsEntity).user(user).role(WorkspaceRole.MEMBER).build());
        return user;
    }

    private Long createPrivateChannelAsAlice(String name) throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/channels")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"type\": \"PRIVATE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();
    }

    @Test
    void batchAdditionWithMixedInputReportsEachCategoryAndNamesOnlyAddedMembers() throws Exception {
        User carol = signUpWorkspaceMember("carol_chan");
        User outsider = signUp("outsider_chan");
        Long channelId = createPrivateChannelAsAlice("mixed-bag");

        // Bob is already a Channel Member before the batch.
        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated());

        String body = String.format("{\"userIds\": [%d, %d, %d]}", carol.getId(), bob.getId(), outsider.getId());
        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added", contains(carol.getId().intValue())))
                .andExpect(jsonPath("$.alreadyMembers", contains(bob.getId().intValue())))
                .andExpect(jsonPath("$.notWorkspaceMembers", contains(outsider.getId().intValue())));

        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("@alice_chan added @carol_chan to the channel"))
                .andExpect(jsonPath("$.messages[1].content").value("@alice_chan added @bob_chan to the channel"));
    }

    @Test
    void singleAdditionOfNonWorkspaceMemberReturnsCategorizedResultInsteadOfBadRequest() throws Exception {
        User outsider = signUp("outsider_chan");
        Long channelId = createPrivateChannelAsAlice("single-miss");

        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + outsider.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", empty()))
                .andExpect(jsonPath("$.alreadyMembers", empty()))
                .andExpect(jsonPath("$.notWorkspaceMembers", contains(outsider.getId().intValue())));
    }

    @Test
    void singleAdditionReportsTheAddedMemberWithCreated() throws Exception {
        Long channelId = createPrivateChannelAsAlice("single-hit");

        mockMvc.perform(post("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + bob.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added", contains(bob.getId().intValue())))
                .andExpect(jsonPath("$.alreadyMembers", empty()))
                .andExpect(jsonPath("$.notWorkspaceMembers", empty()));
    }

    @Test
    void additionThatAddsNobodyReturnsOkAndPostsNoNotice() throws Exception {
        User outsider = signUp("outsider_chan");
        Long channelId = createPrivateChannelAsAlice("no-op");

        // Alice is already a Channel Member (she created it); the outsider isn't in the Workspace.
        String body = String.format("{\"userIds\": [%d, %d]}", alice.getId(), outsider.getId());
        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", empty()))
                .andExpect(jsonPath("$.alreadyMembers", contains(alice.getId().intValue())))
                .andExpect(jsonPath("$.notWorkspaceMembers", contains(outsider.getId().intValue())));

        mockMvc.perform(get("/api/channels/" + channelId + "/messages")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", empty()));
    }

    @Test
    void batchAdditionRejectsMoreThanFiftyUserIds() throws Exception {
        Long channelId = createPrivateChannelAsAlice("too-many");
        String ids = LongStream.rangeClosed(1, 50)
                .mapToObj(i -> Long.toString(1_000_000 + i))
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [" + bob.getId() + "," + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can add at most 50 colleagues at a time"));

        assertOnlyAliceIsChannelMember(channelId);
    }

    @Test
    void batchAdditionAcceptsExactlyFiftyUserIds() throws Exception {
        Long channelId = createPrivateChannelAsAlice("just-enough");
        String ids = LongStream.rangeClosed(1, 49)
                .mapToObj(i -> Long.toString(1_000_000 + i))
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [" + bob.getId() + "," + ids + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.added", contains(bob.getId().intValue())))
                .andExpect(jsonPath("$.notWorkspaceMembers", hasSize(49)));
    }

    @Test
    void batchAdditionRejectsNullUserId() throws Exception {
        Long channelId = createPrivateChannelAsAlice("null-id");

        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": [" + bob.getId() + ", null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userIds must not contain null"));

        assertOnlyAliceIsChannelMember(channelId);
    }

    @Test
    void batchAdditionRejectsEmptyList() throws Exception {
        Long channelId = createPrivateChannelAsAlice("empty-list");

        mockMvc.perform(post("/api/channels/" + channelId + "/members/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\": []}"))
                .andExpect(status().isBadRequest());
    }

    private void assertOnlyAliceIsChannelMember(Long channelId) throws Exception {
        mockMvc.perform(get("/api/channels/" + channelId + "/members")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", contains(alice.getId().intValue())));
    }
}

