package com.example.chatapp.user.service;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.user.model.dto.UserSettingsDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.UserSettings;
import com.example.chatapp.user.repository.PresenceRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.websocket.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
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
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private PresenceModuleImpl presenceModule;

    @Test
    void setUserOnline_ShouldSetRedisKeyAndBroadcast() {
        // Arrange
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Collections.emptySet());

        // Act
        presenceModule.setUserOnline(userId);

        // Assert
        verify(valueOperations).set("user:presence:1", "Online");
        verify(setOperations).remove("user:presence:offline_sync", "1");
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
    }

    @Test
    void setUserOffline_ShouldSetRedisKeyAndBroadcast() {
        // Arrange
        Long userId = 1L;
        User user = User.builder().id(userId).username("testuser").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(presenceRepository.findEligiblePresenceReceivers(userId)).thenReturn(Collections.emptySet());

        // Act
        presenceModule.setUserOffline(userId);

        // Assert
        verify(valueOperations).set(eq("user:presence:1"), any(String.class));
        verify(setOperations).add("user:presence:offline_sync", "1");
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/online"), any(Map.class));
    }

    @Test
    void getOnlineUsers_ShouldReturnOnlineUsers() {
        // Arrange
        Long currentUserId = 1L;
        User otherUser = User.builder().id(2L).username("other").build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
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

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(User.builder().id(currentUserId).build()));
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
    void togglePresenceSharing_ShouldUpdateSettingsAndSave() {
        // Arrange
        Long userId = 1L;
        User user = User.builder().id(userId).username("test").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        UserSettingsDto result = presenceModule.togglePresenceSharing(userId, false);

        // Assert
        assertFalse(result.isSharePresence());
        assertNotNull(user.getSettings());
        assertFalse(user.getSettings().isSharePresence());
        verify(userRepository).save(user);
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
}
