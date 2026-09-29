package com.example.chatapp.user.service;

import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.PresenceRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.websocket.SessionRegistry;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceModuleTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PresenceRepository presenceRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private MessageBroker messageBroker;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private PresenceModuleImpl presenceModule;

    @Test
    void connectOfSharingMember_SetsRedisKeyAndBroadcastsToColleaguesAndWorkspace() {
        // Arrange
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();
        com.example.chatapp.workspace.model.entity.Workspace ws = com.example.chatapp.workspace.model.entity.Workspace.builder().id(100L).build();
        com.example.chatapp.workspace.model.entity.WorkspaceMember wm = com.example.chatapp.workspace.model.entity.WorkspaceMember.builder().workspace(ws).user(user).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L));
        when(workspaceMemberRepository.findByUserId(userId)).thenReturn(List.of(wm));

        // Act
        presenceModule.setUserOnline(userId);

        // Assert
        verify(valueOperations).set("user:presence:1", "Online");
        verify(setOperations).remove("user:presence:offline_sync", "1");
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToTopic(eq("/topic/workspace.100.presence"), any(Map.class));
    }

    @Test
    void disconnectOfSharingMember_SetsRedisKeyAndBroadcastsToColleaguesAndWorkspace() {
        // Arrange
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();
        com.example.chatapp.workspace.model.entity.Workspace ws = com.example.chatapp.workspace.model.entity.Workspace.builder().id(100L).build();
        com.example.chatapp.workspace.model.entity.WorkspaceMember wm = com.example.chatapp.workspace.model.entity.WorkspaceMember.builder().workspace(ws).user(user).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L));
        when(workspaceMemberRepository.findByUserId(userId)).thenReturn(List.of(wm));

        // Act
        presenceModule.setUserOffline(userId);

        // Assert
        verify(valueOperations).set(eq("user:presence:1"), any(String.class));
        verify(setOperations).add("user:presence:offline_sync", "1");
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToTopic(eq("/topic/workspace.100.presence"), any(Map.class));
    }

    @Test
    void connectOfMemberNotSharingPresence_PublishesNothingToWorkspaceOrColleagues() {
        Long userId = 1L;
        when(presenceRepository.findSharePresence(userId)).thenReturn(Optional.of(false));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).username("hidden").build()));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L, 3L));
        lenient().when(workspaceMemberRepository.findByUserId(userId)).thenReturn(List.of(membershipIn(100L, userId)));

        presenceModule.setUserOnline(userId);

        verify(messageBroker, never()).publishToTopic(any(), any());
        verify(messageBroker, never()).publishToUser(eq("2"), any(), any());
        verify(messageBroker, never()).publishToUser(eq("3"), any(), any());
    }

    @Test
    void disconnectOfMemberNotSharingPresence_PublishesNothingToWorkspaceOrColleagues() {
        Long userId = 1L;
        when(presenceRepository.findSharePresence(userId)).thenReturn(Optional.of(false));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).username("hidden").build()));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L, 3L));
        lenient().when(workspaceMemberRepository.findByUserId(userId)).thenReturn(List.of(membershipIn(100L, userId)));

        presenceModule.setUserOffline(userId);

        verify(messageBroker, never()).publishToTopic(any(), any());
        verify(messageBroker, never()).publishToUser(eq("2"), any(), any());
        verify(messageBroker, never()).publishToUser(eq("3"), any(), any());
    }

    @Test
    void usersAppearingOnline_FollowEachTargetsOwnPresenceSharing_InOneBatchedLookup() {
        Long hiddenViewer = 1L;    // connected, Presence Sharing off
        Long sharingOnline = 2L;   // connected, sharing
        Long hiddenOnline = 3L;    // connected, Presence Sharing off
        Long sharingOffline = 4L;  // not connected, sharing
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of(hiddenViewer, sharingOnline, hiddenOnline));
        when(presenceRepository.findUserIdsNotSharingPresence(any())).thenAnswer(inv -> {
            Collection<Long> asked = inv.getArgument(0);
            Set<Long> hidden = new HashSet<>(asked);
            hidden.retainAll(Set.of(hiddenViewer, hiddenOnline));
            return hidden;
        });

        Set<Long> visibleToHiddenViewer = presenceModule.findUsersAppearingOnlineTo(
                hiddenViewer, List.of(hiddenViewer, sharingOnline, hiddenOnline, sharingOffline));
        Set<Long> visibleToSharingViewer = presenceModule.findUsersAppearingOnlineTo(
                sharingOnline, List.of(hiddenViewer, sharingOnline, hiddenOnline, sharingOffline));

        // A viewer always sees themselves; others follow their own choice, not the viewer's.
        assertEquals(Set.of(hiddenViewer, sharingOnline), visibleToHiddenViewer);
        assertEquals(Set.of(sharingOnline), visibleToSharingViewer);
        verify(presenceRepository, times(2)).findUserIdsNotSharingPresence(any());
        verify(presenceRepository, never()).findSharePresence(any());
        verify(sessionRegistry, never()).isOnline(any());
    }

    @Test
    void usersAppearingOnline_SkipsTheSharingLookupWhenNoTargetIsConnected() {
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of());

        Set<Long> visible = presenceModule.findUsersAppearingOnlineTo(1L, List.of(2L, 3L));

        assertTrue(visible.isEmpty());
        verify(presenceRepository, never()).findUserIdsNotSharingPresence(any());
    }

    private static com.example.chatapp.workspace.model.entity.WorkspaceMember membershipIn(Long workspaceId, Long userId) {
        return com.example.chatapp.workspace.model.entity.WorkspaceMember.builder()
                .workspace(com.example.chatapp.workspace.model.entity.Workspace.builder().id(workspaceId).build())
                .user(User.builder().id(userId).build())
                .build();
    }

    @Test
    void getOnlineUsers_ShouldReturnOnlineUsers() {
        // Arrange
        Long currentUserId = 1L;
        User otherUser = User.builder().id(2L).username("other").build();
        
        when(presenceRepository.findEligiblePresenceUsers(currentUserId)).thenReturn(List.of(otherUser));
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of(2L));

        // Act
        List<OnlineStatusDto> onlineUsers = presenceModule.getOnlineUsers(currentUserId);

        // Assert
        assertEquals(1, onlineUsers.size());
        assertEquals("ONLINE", onlineUsers.get(0).getStatus());
        assertEquals("2", onlineUsers.get(0).getUserId());
        assertEquals("other", onlineUsers.get(0).getUsername());
    }

    @Test
    void getOnlineUsers_ShouldUseOneOnlineUserSnapshot() {
        Long currentUserId = 1L;
        User onlineUser = User.builder().id(2L).username("online").build();
        User offlineUser = User.builder().id(3L).username("offline").build();

        when(presenceRepository.findEligiblePresenceUsers(currentUserId)).thenReturn(List.of(onlineUser, offlineUser));
        when(sessionRegistry.getOnlineUserIds()).thenReturn(Set.of(2L));

        List<OnlineStatusDto> onlineUsers = presenceModule.getOnlineUsers(currentUserId);

        assertEquals(1, onlineUsers.size());
        assertEquals("2", onlineUsers.get(0).getUserId());
        assertEquals("online", onlineUsers.get(0).getUsername());
        verify(sessionRegistry).getOnlineUserIds();
        verify(sessionRegistry, never()).isOnline(any());
    }

    @Test
    void syncPresenceToDatabase_ShouldSyncAndRemoveFromQueue() {
        // Arrange
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.members("user:presence:offline_sync")).thenReturn(Set.of("1", "2"));
        
        Instant now1 = Instant.now().minusSeconds(60);
        Instant now2 = Instant.now().minusSeconds(120);

        when(valueOperations.get("user:presence:1")).thenReturn(now1.toString());
        when(valueOperations.get("user:presence:2")).thenReturn(now2.toString());

        // Act
        presenceModule.syncPresenceToDatabase();

        // Assert
        verify(presenceRepository).updateLastSeenAt(1L, now1);
        verify(presenceRepository).updateLastSeenAt(2L, now2);
        verify(setOperations).remove("user:presence:offline_sync", "1");
        verify(setOperations).remove("user:presence:offline_sync", "2");
    }

    @Test
    void scheduledSyncAllPresenceViaScan_ShouldSkipOfflineSyncQueueKey() {
        // Arrange
        when(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn(Set.of("user:presence:offline_sync", "user:presence:123"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant now = Instant.now().minusSeconds(180);
        when(valueOperations.get("user:presence:123")).thenReturn(now.toString());

        // Act
        presenceModule.scheduledSyncAllPresenceViaScan();

        // Assert
        verify(presenceRepository).updateLastSeenAt(123L, now);
        verify(presenceRepository, never()).updateLastSeenAt(eq(null), any());
    }

    @Test
    void setPresenceSharing_ShouldUpdateSettingsAndBroadcastOffline() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L));

        com.example.chatapp.user.model.dto.UserSettingsDto result = presenceModule.setPresenceSharing(userId, false);

        assertFalse(result.isSharePresence());
        verify(userRepository).save(user);
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
    }

    @Test
    void setPresenceSharing_ShouldBroadcastOnlineIfUserIsOnline() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRegistry.isOnline(userId)).thenReturn(true);
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Set.of(2L));

        com.example.chatapp.user.model.dto.UserSettingsDto result = presenceModule.setPresenceSharing(userId, true);

        assertTrue(result.isSharePresence());
        verify(userRepository).save(user);
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/online"), any(Map.class));
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
    }

    @Test
    void getPrivacySettings_ShouldReturnDefaultWhenNoSettings() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        com.example.chatapp.user.model.dto.UserSettingsDto result = presenceModule.getPrivacySettings(userId);

        assertTrue(result.isSharePresence());
    }

    @Test
    void getPrivacySettings_ShouldReturnCustomValueWhenSettingsExist() {
        Long userId = 1L;
        com.example.chatapp.user.model.entity.UserSettings settings = com.example.chatapp.user.model.entity.UserSettings.builder()
                .sharePresence(false)
                .build();
        User user = User.builder().id(userId).username("testuser").settings(settings).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        com.example.chatapp.user.model.dto.UserSettingsDto result = presenceModule.getPrivacySettings(userId);

        assertFalse(result.isSharePresence());
    }
}
