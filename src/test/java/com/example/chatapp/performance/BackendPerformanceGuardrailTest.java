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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
