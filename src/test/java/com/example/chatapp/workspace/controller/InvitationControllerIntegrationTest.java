package com.example.chatapp.workspace.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.ChannelDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.ChannelType;
import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import com.example.chatapp.workspace.model.request.CreateChannelRequest;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import com.example.chatapp.workspace.service.ChannelService;
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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private EmailService emailService;

    private String aliceToken;
    private String bobToken;
    private String charlieToken;
    private User alice;
    private User bob;
    private User charlie;
    private WorkspaceDto workspace;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
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

        // Bob (User / Member)
        MvcResult resBob = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob_inv\",\"email\":\"bob_inv@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        bobToken = "Bearer " + objectMapper.readValue(resBob.getResponse().getContentAsString(), Map.class).get("token");
        bob = userRepository.findByUsername("bob_inv").orElseThrow();

        // Charlie (External / Joining User)
        MvcResult resCharlie = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"charlie_inv\",\"email\":\"charlie_inv@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        charlieToken = "Bearer " + objectMapper.readValue(resCharlie.getResponse().getContentAsString(), Map.class).get("token");
        charlie = userRepository.findByUsername("charlie_inv").orElseThrow();

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

        org.mockito.Mockito.verify(emailService).sendWorkspaceInvitation(
                org.mockito.ArgumentMatchers.eq("bob_inv@example.com"),
                org.mockito.ArgumentMatchers.eq("Acme Collab"),
                org.mockito.ArgumentMatchers.eq("alice_inv"),
                org.mockito.ArgumentMatchers.eq(token),
                org.mockito.ArgumentMatchers.isNull()
        );

        // Preview token
        mockMvc.perform(get("/api/invitations/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Acme Collab"))
                .andExpect(jsonPath("$.inviterName").value("alice_inv"));

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

    @Test
    void createBatchInvitations_withGranularOutcomes_andCustomMessage() throws Exception {
        // Setup: Bob is already accepted into workspace
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(workspaceRepository.findById(workspace.getId()).orElseThrow())
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());

        // Setup: pre-existing pending invite for david
        String preInviteReq = """
                {
                  "email": "david_pending@example.com",
                  "role": "MEMBER"
                }
                """;
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preInviteReq))
                .andExpect(status().isCreated());

        // Batch invite request containing:
        // 1. charlie_inv@example.com (Valid, not member -> sent)
        // 2. bob_inv@example.com (Already member -> already_member)
        // 3. david_pending@example.com (Already pending -> already_invited)
        // 4. invalid-email-format (Invalid -> invalid_email)
        String batchReq = """
                {
                  "emails": [
                    "charlie_inv@example.com",
                    "bob_inv@example.com",
                    "david_pending@example.com",
                    "invalid-email-format"
                  ],
                  "role": "MEMBER",
                  "customMessage": "Hey team, excited to collaborate!"
                }
                """;

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(3))
                .andExpect(jsonPath("$.results[?(@.email == 'charlie_inv@example.com')].status").value("sent"))
                .andExpect(jsonPath("$.results[?(@.email == 'bob_inv@example.com')].status").value("already_member"))
                .andExpect(jsonPath("$.results[?(@.email == 'david_pending@example.com')].status").value("already_invited"))
                .andExpect(jsonPath("$.results[?(@.email == 'invalid-email-format')].status").value("invalid_email"));

        // Verify email was dispatched with custom message
        org.mockito.Mockito.verify(emailService).sendWorkspaceInvitation(
                org.mockito.ArgumentMatchers.eq("charlie_inv@example.com"),
                org.mockito.ArgumentMatchers.eq("Acme Collab"),
                org.mockito.ArgumentMatchers.eq("alice_inv"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("Hey team, excited to collaborate!")
        );
    }

    @Test
    void createBatchInvitations_memberRoleRestriction_cannotInviteAdmin() throws Exception {
        // Bob joins workspace as regular member
        workspaceMemberRepository.save(com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(workspaceRepository.findById(workspace.getId()).orElseThrow())
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());

        // Bob attempts to invite someone as ADMIN -> should fail with 401/403
        String adminInviteReq = """
                {
                  "emails": ["someone@example.com"],
                  "role": "ADMIN"
                }
                """;
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations/batch")
                        .header("Authorization", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminInviteReq))
                .andExpect(status().isUnauthorized());

        // Bob invites someone as MEMBER -> succeeds
        String memberInviteReq = """
                {
                  "emails": ["someone@example.com"],
                  "role": "MEMBER"
                }
                """;
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations/batch")
                        .header("Authorization", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberInviteReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(1));
    }

    @Test
    void createBatchInvitations_withInitialChannels_autoJoinsOnAccept() throws Exception {
        // Alice creates a private channel in workspace
        ChannelDto privateChannel = channelService.createChannel(workspace.getId(), alice.getId(),
                CreateChannelRequest.builder().name("secret-ops").type(ChannelType.PRIVATE).build());

        // Alice batch-invites Charlie designating the private channel
        String batchReq = String.format("""
                {
                  "emails": ["charlie_inv@example.com"],
                  "role": "MEMBER",
                  "customMessage": "Join our secret ops channel!",
                  "initialChannelIds": [%d]
                }
                """, privateChannel.getId());

        MvcResult batchRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(1))
                .andReturn();

        // Get invitation record
        var inv = workspaceInvitationRepository.findByWorkspaceIdAndEmailAndStatus(workspace.getId(), "charlie_inv@example.com", com.example.chatapp.workspace.model.entity.InvitationStatus.PENDING).orElseThrow();

        // Preview token renders initial channels and custom message
        mockMvc.perform(get("/api/invitations/" + inv.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customMessage").value("Join our secret ops channel!"))
                .andExpect(jsonPath("$.initialChannels", hasSize(1)))
                .andExpect(jsonPath("$.initialChannels[0].name").value("secret-ops"));

        // Charlie accepts invitation
        mockMvc.perform(post("/api/invitations/" + inv.getToken() + "/accept")
                        .header("Authorization", charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspace.getId()));

        // Charlie now has access to the private channel
        mockMvc.perform(get("/api/channels/" + privateChannel.getId())
                        .header("Authorization", charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMember").value(true));
    }

    @Test
    void shareableJoinLink_lifecycle_getToggleReset() throws Exception {
        // 1. Get or create canonical shareable join link
        MvcResult getRes = mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/join-link")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SHAREABLE_LINK"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn();

        String token1 = (String) objectMapper.readValue(getRes.getResponse().getContentAsString(), Map.class).get("token");

        // 2. Toggle link to inactive
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/join-link/toggle")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.isActive").value(false));

        // 3. Attempting to preview deactivated link returns 400 Bad Request
        mockMvc.perform(get("/api/invitations/" + token1))
                .andExpect(status().isBadRequest());

        // 4. Toggle link back to active
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/join-link/toggle")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(true));

        // 5. Reset link
        MvcResult resetRes = mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/join-link/reset")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token", not(equalTo(token1))))
                .andReturn();

        String token2 = (String) objectMapper.readValue(resetRes.getResponse().getContentAsString(), Map.class).get("token");

        // Old token1 is now revoked
        mockMvc.perform(get("/api/invitations/" + token1))
                .andExpect(status().isBadRequest());

        // New token2 is active
        mockMvc.perform(get("/api/invitations/" + token2))
                .andExpect(status().isOk());
    }

    @Test
    void pendingInvitations_adminOnly_resendAndRevoke() throws Exception {
        // Alice creates pending invitation for Charlie
        String batchReq = """
                {
                  "emails": ["charlie_inv@example.com"],
                  "role": "MEMBER",
                  "customMessage": "Pending test"
                }
                """;
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/invitations/batch")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchReq))
                .andExpect(status().isOk());

        // List pending invitations
        MvcResult pendingRes = mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/invitations/pending")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("charlie_inv@example.com"))
                .andReturn();

        List<Map<String, Object>> pendingList = objectMapper.readValue(pendingRes.getResponse().getContentAsString(), List.class);
        Number inviteId = (Number) pendingList.get(0).get("id");

        // Resend invitation
        mockMvc.perform(post("/api/invitations/" + inviteId + "/resend")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inviteId.longValue()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Revoke invitation
        mockMvc.perform(delete("/api/invitations/" + inviteId)
                        .header("Authorization", aliceToken))
                .andExpect(status().isNoContent());

        // Pending list is now empty
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/invitations/pending")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
