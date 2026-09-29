package com.example.chatapp.workspace.service;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.ChannelDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.entity.ChannelType;
import com.example.chatapp.workspace.model.entity.InvitationType;
import com.example.chatapp.workspace.model.entity.WorkspaceInvitation;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import com.example.chatapp.workspace.model.request.BatchAddChannelMembersRequest;
import com.example.chatapp.workspace.model.request.CreateChannelRequest;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Clients refresh their Channel list as soon as they receive a Channel membership event, so every
 * event and notice about a membership change must reach them only once the change has committed,
 * and never when it rolls back.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChannelMembershipEventTimingIntegrationTest {

    private static final String CHANNEL_REMOVAL_QUEUE = "/queue/channel-removals";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private ChannelRepository channelRepository;
    @Autowired private ChannelMemberRepository channelMemberRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationParticipantRepository conversationParticipantRepository;
    @Autowired private WorkspaceInvitationRepository workspaceInvitationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private ChannelService channelService;
    @Autowired private InvitationService invitationService;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private MessageBroker messageBroker;

    private User alice;
    private User bob;
    private User carol;
    private WorkspaceDto workspace;
    private Long channelId;
    private TransactionTemplate tx;

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

        alice = saveUser("alice_timing");
        bob = saveUser("bob_timing");
        carol = saveUser("carol_timing");

        // Alice owns the Workspace; Bob is a Member and Carol an Admin.
        workspace = workspaceService.createWorkspace(alice.getId(), CreateWorkspaceRequest.builder().name("Timing Space").build());
        var wsEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(wsEntity).user(bob).role(WorkspaceRole.MEMBER).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(wsEntity).user(carol).role(WorkspaceRole.ADMIN).build());

        // Alice creates a Private Channel and adds Bob.
        ChannelDto channel = channelService.createChannel(workspace.getId(), alice.getId(), CreateChannelRequest.builder()
                .name("secret-timing").type(ChannelType.PRIVATE).build());
        channelId = channel.getId();
        channelService.addChannelMember(channelId, alice.getId(), bob.getId());

        tx = new TransactionTemplate(transactionManager);
        clearInvocations(messageBroker);
    }

    // --- Removal ---

    @Test
    void removalPublishesNothingUntilTheRemovalCommits() {
        tx.executeWithoutResult(status -> {
            channelService.removeChannelMember(channelId, carol.getId(), bob.getId());
            verifyNothingPublished();
        });

        verify(messageBroker).publishToTopic(eq(channelTopic()), argThat(systemNotice("@bob_timing left the channel")));
        verify(messageBroker).publishToTopic(eq(eventsTopic()), argThat(eventOfType("CHANNEL_MEMBER_REMOVED")));
        verify(messageBroker).publishToUser(eq(bob.getId().toString()), eq(CHANNEL_REMOVAL_QUEUE),
                argThat(eventOfType("REMOVED_FROM_CHANNEL")));
    }

    @Test
    void rolledBackRemovalPublishesNothing() {
        tx.executeWithoutResult(status -> {
            channelService.removeChannelMember(channelId, carol.getId(), bob.getId());
            status.setRollbackOnly();
        });

        verifyNothingPublished();
        assertThat(channelMemberRepository.isUserChannelMember(channelId, bob.getId())).isTrue();
    }

    // --- Batch addition ---

    @Test
    void batchAdditionPublishesNothingUntilTheAdditionCommits() {
        tx.executeWithoutResult(status -> {
            channelService.batchAddChannelMembers(channelId, alice.getId(), addCarol());
            verifyNothingPublished();
        });

        verify(messageBroker).publishToTopic(eq(channelTopic()),
                argThat(systemNotice("@alice_timing added @carol_timing to the channel")));
        verify(messageBroker).publishToTopic(eq(eventsTopic()), argThat(eventOfType("CHANNEL_MEMBER_ADDED")));
    }

    @Test
    void rolledBackBatchAdditionPublishesNothing() {
        tx.executeWithoutResult(status -> {
            channelService.batchAddChannelMembers(channelId, alice.getId(), addCarol());
            status.setRollbackOnly();
        });

        verifyNothingPublished();
        assertThat(channelMemberRepository.isUserChannelMember(channelId, carol.getId())).isFalse();
    }

    private BatchAddChannelMembersRequest addCarol() {
        return BatchAddChannelMembersRequest.builder().userIds(List.of(carol.getId())).build();
    }

    // --- Invitation acceptance into a Private Channel ---

    @Test
    void invitationAcceptancePublishesNothingUntilTheAcceptanceCommits() {
        User dave = saveUser("dave_timing");
        String token = invitationIntoTheChannel();

        tx.executeWithoutResult(status -> {
            invitationService.acceptInvitation(token, dave.getId());
            verifyNothingPublished();
        });

        verify(messageBroker).publishToTopic(eq(channelTopic()), argThat(systemNotice("@dave_timing joined the channel")));
        verify(messageBroker).publishToTopic(eq(eventsTopic()), argThat(eventOfType("CHANNEL_MEMBER_ADDED")));
        verify(messageBroker).publishToTopic(eq(eventsTopic()), argThat(eventOfType("MEMBER_JOINED")));
    }

    @Test
    void rolledBackInvitationAcceptancePublishesNothing() {
        User dave = saveUser("dave_timing");
        String token = invitationIntoTheChannel();

        tx.executeWithoutResult(status -> {
            invitationService.acceptInvitation(token, dave.getId());
            status.setRollbackOnly();
        });

        verifyNothingPublished();
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), dave.getId())).isFalse();
    }

    /** A shareable join link that also adds whoever accepts it to the Private Channel. */
    private String invitationIntoTheChannel() {
        String token = "timing-" + System.nanoTime();
        workspaceInvitationRepository.save(WorkspaceInvitation.builder()
                .workspace(workspaceRepository.findById(workspace.getId()).orElseThrow())
                .inviter(alice)
                .type(InvitationType.SHAREABLE_LINK)
                .token(token)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .initialChannelIds(String.valueOf(channelId))
                .build());
        return token;
    }

    private void verifyNothingPublished() {
        verify(messageBroker, never()).publishToTopic(anyString(), any());
        verify(messageBroker, never()).publishToUser(anyString(), anyString(), any());
    }

    private String channelTopic() {
        return "/topic/workspace." + workspace.getId() + ".channel." + channelId;
    }

    private String eventsTopic() {
        return "/topic/workspace." + workspace.getId() + ".events";
    }

    private static org.mockito.ArgumentMatcher<Object> systemNotice(String content) {
        return payload -> payload instanceof MessageDto notice
                && content.equals(notice.getContent())
                && notice.getMessageType() == MessageType.SYSTEM;
    }

    private static org.mockito.ArgumentMatcher<Object> eventOfType(String type) {
        return payload -> payload instanceof Map<?, ?> map && type.equals(map.get("type"));
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .password("irrelevant")
                .role(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow())
                .version(0L)
                .build());
    }
}
