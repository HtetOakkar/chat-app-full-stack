package com.example.chatapp.workspace.service;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class WorkspaceServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        alice = userRepository.save(User.builder()
                .username("alice_ws")
                .password("password123")
                .email("alice_ws@example.com")
                .emailVerified(true)
                .role(userRole)
                .build());

        bob = userRepository.save(User.builder()
                .username("bob_ws")
                .password("password123")
                .email("bob_ws@example.com")
                .emailVerified(true)
                .role(userRole)
                .build());
    }

    @Test
    void createWorkspaceShouldCreateWorkspaceOwnerMemberAndGeneralChannel() {
        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .name("Acme Corporation")
                .build();

        WorkspaceDto workspaceDto = workspaceService.createWorkspace(alice.getId(), request);

        assertNotNull(workspaceDto);
        assertNotNull(workspaceDto.getId());
        assertEquals("Acme Corporation", workspaceDto.getName());
        assertEquals("acme-corporation", workspaceDto.getSlug());
        assertEquals(WorkspaceRole.OWNER, workspaceDto.getRole());
        assertEquals(alice.getId(), workspaceDto.getOwnerId());

        // Verify WorkspaceMember exists
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceDto.getId());
        assertEquals(1, members.size());
        assertEquals(alice.getId(), members.get(0).getUser().getId());
        assertEquals(WorkspaceRole.OWNER, members.get(0).getRole());

        // Verify #general Channel exists
        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceDto.getId());
        assertEquals(1, channels.size());
        assertEquals("general", channels.get(0).getSlug());
        assertEquals("general", channels.get(0).getName());
        assertEquals(ChannelType.PUBLIC, channels.get(0).getType());

        // Verify Conversation exists for #general
        var convOpt = conversationRepository.findByChannelId(channels.get(0).getId());
        assertTrue(convOpt.isPresent());
        assertEquals(ConversationType.CHANNEL, convOpt.get().getType());
    }

    @Test
    void createWorkspaceWithCustomSlugShouldPreserveSlug() {
        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .name("Engineering Hub")
                .slug("custom-eng")
                .build();

        WorkspaceDto workspaceDto = workspaceService.createWorkspace(alice.getId(), request);
        assertEquals("custom-eng", workspaceDto.getSlug());
    }

    @Test
    void createWorkspaceWithDuplicateNameShouldGenerateUniqueSlug() {
        CreateWorkspaceRequest request1 = CreateWorkspaceRequest.builder().name("Alpha").build();
        CreateWorkspaceRequest request2 = CreateWorkspaceRequest.builder().name("Alpha").build();

        WorkspaceDto ws1 = workspaceService.createWorkspace(alice.getId(), request1);
        WorkspaceDto ws2 = workspaceService.createWorkspace(alice.getId(), request2);

        assertEquals("alpha", ws1.getSlug());
        assertNotEquals(ws1.getSlug(), ws2.getSlug());
        assertTrue(ws2.getSlug().startsWith("alpha-"));
    }

    @Test
    void getUserWorkspacesShouldReturnWorkspacesForUser() {
        workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Workspace A").build());
        workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Workspace B").build());

        List<WorkspaceDto> aliceWorkspaces = workspaceService.getUserWorkspaces(alice.getId());
        assertEquals(2, aliceWorkspaces.size());

        List<WorkspaceDto> bobWorkspaces = workspaceService.getUserWorkspaces(bob.getId());
        assertEquals(0, bobWorkspaces.size());
    }

    @Test
    void getWorkspaceByIdShouldEnforceMembership() {
        WorkspaceDto created = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Secret WS").build());

        // Alice (member) can access
        WorkspaceDto aliceView = workspaceService.getWorkspaceById(created.getId(), alice.getId());
        assertNotNull(aliceView);
        assertEquals("Secret WS", aliceView.getName());

        // Bob (non-member) cannot access
        assertThrows(UnauthorizedException.class, () -> workspaceService.getWorkspaceById(created.getId(), bob.getId()));
    }

    @Test
    void getWorkspaceMembersShouldReturnAllMembers() {
        WorkspaceDto created = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Team WS").build());

        // Add Bob as MEMBER
        Workspace ws = workspaceRepository.findById(created.getId()).orElseThrow();
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(ws)
                .user(bob)
                .role(WorkspaceRole.MEMBER)
                .build());

        List<WorkspaceMemberDto> members = workspaceService.getWorkspaceMembers(created.getId(), alice.getId());
        assertEquals(2, members.size());
        assertTrue(members.stream().anyMatch(m -> m.getUsername().equals("alice_ws") && m.getRole() == WorkspaceRole.OWNER));
        assertTrue(members.stream().anyMatch(m -> m.getUsername().equals("bob_ws") && m.getRole() == WorkspaceRole.MEMBER));
    }
}
