package com.example.chatapp.performance;

import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.service.MessageServiceImpl;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.dto.BatchAddChannelMembersResponse;
import com.example.chatapp.workspace.model.entity.Channel;
import com.example.chatapp.workspace.model.entity.ChannelType;
import com.example.chatapp.workspace.model.entity.Workspace;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.model.request.BatchAddChannelMembersRequest;
import com.example.chatapp.workspace.repository.ChannelMemberRepository;
import com.example.chatapp.workspace.repository.ChannelRepository;
import com.example.chatapp.workspace.repository.ConversationParticipantRepository;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import com.example.chatapp.workspace.service.ChannelActivityNoticePublisher;
import com.example.chatapp.workspace.service.ChannelServiceImpl;
import com.example.chatapp.user.repository.PresenceRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.repository.UserSettingsRepository;
import com.example.chatapp.user.service.PresenceModuleImpl;
import com.example.chatapp.websocket.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackendPerformanceGuardrailTest {

    // Collaborators for the Channel service case. Mockito builds the service through its
    // constructor, so unrelated constructor changes don't break this guardrail.
    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelMemberRepository channelMemberRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private ChannelActivityNoticePublisher noticePublisher;
    @Mock private MessageBroker messageBroker;
    @InjectMocks private ChannelServiceImpl channelService;

    @Test
    void publicHistoryUsesBoundedRedisCursorLookupInsteadOfFullPendingScan() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        RedisMessageRepository redisMessageRepository = mock(RedisMessageRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ConversationParticipantRepository conversationParticipantRepository = mock(ConversationParticipantRepository.class);
        MessageServiceImpl service = new MessageServiceImpl(
                messageRepository,
                messageMapper,
                redisMessageRepository,
                workspaceMemberRepository,
                conversationParticipantRepository);

        when(redisMessageRepository.findPublicMessagesBefore(isNull(), isNull(), eq(25))).thenReturn(List.of());

        MessagePage page = service.getPublicMessages(null, 25);

        assertNotNull(page);
        verify(redisMessageRepository).findPublicMessagesBefore(isNull(), isNull(), eq(25));
        verify(redisMessageRepository, never()).peekMessages();
    }

    @Test
    void batchChannelMemberAdditionUsesBatchedLookupsRegardlessOfBatchSize() {
        Workspace workspace = Workspace.builder().id(1L).build();
        Channel channel = Channel.builder().id(10L).workspace(workspace).type(ChannelType.PRIVATE).build();
        WorkspaceMember requester = workspaceMember(workspace, 100L, 1L);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(channelMemberRepository.isUserChannelMember(10L, 1L)).thenReturn(true);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L)).thenReturn(Optional.of(requester));

        // 50 requested IDs: 48 Workspace Members to add, one existing Channel Member, one outsider.
        List<Long> requestedUserIds = LongStream.rangeClosed(1001, 1050).boxed().toList();
        List<WorkspaceMember> resolved = requestedUserIds.subList(0, 49).stream()
                .map(userId -> workspaceMember(workspace, userId + 5000, userId))
                .toList();
        when(workspaceMemberRepository.findWithUserByWorkspaceIdAndUserIdIn(eq(1L), anyCollection())).thenReturn(resolved);
        when(channelMemberRepository.findExistingWorkspaceMemberIds(eq(10L), anyCollection())).thenReturn(Set.of(1049L + 5000));

        BatchAddChannelMembersResponse result = channelService.batchAddChannelMembers(10L, 1L,
                BatchAddChannelMembersRequest.builder().userIds(requestedUserIds).build());

        assertEquals(48, result.getAdded().size());
        assertEquals(List.of(1049L), result.getAlreadyMembers());
        assertEquals(List.of(1050L), result.getNotWorkspaceMembers());

        // One lookup per concern, never one per requested ID.
        verify(workspaceMemberRepository, times(1)).findWithUserByWorkspaceIdAndUserIdIn(eq(1L), anyCollection());
        verify(workspaceMemberRepository, times(1)).findByWorkspaceIdAndUserId(anyLong(), anyLong()); // the requester only
        verify(channelMemberRepository, times(1)).findExistingWorkspaceMemberIds(eq(10L), anyCollection());
        verify(channelMemberRepository, never()).existsByChannelIdAndWorkspaceMemberId(anyLong(), anyLong());
        // One saveAll call, not one save per Member. It is not one SQL statement: ChannelMember uses an
        // IDENTITY id and JDBC batching is off, so Hibernate sends one INSERT per added Member (at most 50).
        verify(channelMemberRepository, times(1)).saveAll(anyIterable());
        verify(channelMemberRepository, never()).save(any());
        verify(noticePublisher, times(1)).membersAdded(eq(channel), eq(requester), anyList());
    }

    private static WorkspaceMember workspaceMember(Workspace workspace, Long memberId, Long userId) {
        return WorkspaceMember.builder()
                .id(memberId)
                .workspace(workspace)
                .user(User.builder().id(userId).username("user" + userId).build())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectAndDisconnectOfMemberNotSharingPresenceWithCachedChoiceIssueNoDatabaseReads() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        PresenceRepository presenceRepository = mock(PresenceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
        PresenceModuleImpl presenceModule = new PresenceModuleImpl(redisTemplate, userRepository, presenceRepository,
                mock(SessionRegistry.class), mock(MessageBroker.class), workspaceMemberRepository, userSettingsRepository);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("user:presence:sharing:7")).thenReturn(false);

        presenceModule.setUserOnline(7L, "hidden");
        presenceModule.setUserOffline(7L, "hidden");

        verifyNoInteractions(userRepository, presenceRepository, workspaceMemberRepository, userSettingsRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectAndDisconnectOfMemberSharingPresenceWithCachedChoiceReadOnlyReceiversAndMemberships() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        UserRepository userRepository = mock(UserRepository.class);
        PresenceRepository presenceRepository = mock(PresenceRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
        PresenceModuleImpl presenceModule = new PresenceModuleImpl(redisTemplate, userRepository, presenceRepository,
                mock(SessionRegistry.class), mock(MessageBroker.class), workspaceMemberRepository, userSettingsRepository);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("user:presence:sharing:7")).thenReturn(true);
        when(presenceRepository.findEligiblePresenceReceivers(7L)).thenReturn(Set.of(8L, 9L));
        when(workspaceMemberRepository.findByUserId(7L)).thenReturn(List.of());

        // Presence Sharing comes from the cache, never the database. What remains is pinned so a
        // regression or an improvement shows up here: each event reads the receivers and the
        // memberships once, and nothing else.
        presenceModule.setUserOnline(7L, "sharer");
        verify(presenceRepository, times(1)).findEligiblePresenceReceivers(7L);
        verify(workspaceMemberRepository, times(1)).findByUserId(7L);

        presenceModule.setUserOffline(7L, "sharer");
        verify(presenceRepository, times(2)).findEligiblePresenceReceivers(7L);
        verify(workspaceMemberRepository, times(2)).findByUserId(7L);

        verify(presenceRepository, never()).findSharePresence(any());
        verifyNoInteractions(userRepository, userSettingsRepository);
        verifyNoMoreInteractions(presenceRepository, workspaceMemberRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void presenceSharingForManyTargetsIsResolvedInOneBatchedLookup() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        PresenceRepository presenceRepository = mock(PresenceRepository.class);
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        PresenceModuleImpl presenceModule = new PresenceModuleImpl(redisTemplate, mock(UserRepository.class),
                presenceRepository, sessionRegistry, mock(MessageBroker.class),
                mock(WorkspaceMemberRepository.class), mock(UserSettingsRepository.class));

        List<Long> targets = LongStream.rangeClosed(2, 51).boxed().toList();
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.copyOf(targets));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyCollection())).thenAnswer(inv -> {
            Collection<String> keys = inv.getArgument(0);
            return keys.stream().map(key -> (Object) null).toList();
        });
        when(presenceRepository.findUserIdsNotSharingPresence(any())).thenReturn(Set.of());

        Set<Long> visible = presenceModule.findUsersAppearingOnlineTo(1L, targets);

        assertEquals(50, visible.size());
        verify(presenceRepository, times(1)).findUserIdsNotSharingPresence(any());
        verify(presenceRepository, never()).findSharePresence(any());
        verify(valueOperations, times(1)).multiGet(anyCollection());
        verify(valueOperations, never()).get(any());
    }
}
