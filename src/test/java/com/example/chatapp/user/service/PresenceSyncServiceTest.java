package com.example.chatapp.user.service;

import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceSyncServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private PresenceSyncService presenceSyncService;

    @Test
    void syncOfflinePresence_ShouldSyncAndRemoveFromQueue() {
        // Arrange
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(setOperations.members("user:presence:offline_sync")).thenReturn(Set.of("1", "2"));
        
        Instant now1 = Instant.now().minusSeconds(60);
        Instant now2 = Instant.now().minusSeconds(120);

        when(valueOperations.get("user:presence:1")).thenReturn(now1.toString());
        when(valueOperations.get("user:presence:2")).thenReturn(now2.toString());

        // Act
        presenceSyncService.syncOfflinePresence();

        // Assert
        verify(userRepository).updateLastSeenAt(1L, now1);
        verify(userRepository).updateLastSeenAt(2L, now2);

        verify(setOperations).remove("user:presence:offline_sync", "1");
        verify(setOperations).remove("user:presence:offline_sync", "2");
    }

    @Test
    void syncOfflinePresence_ShouldSkipOnlineUsersAndRemoveFromQueue() {
        // Arrange
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(setOperations.members("user:presence:offline_sync")).thenReturn(Set.of("1"));
        
        when(valueOperations.get("user:presence:1")).thenReturn("Online");

        // Act
        presenceSyncService.syncOfflinePresence();

        // Assert
        verify(userRepository, never()).updateLastSeenAt(any(), any());
        verify(setOperations).remove("user:presence:offline_sync", "1");
    }
}
