package com.example.chatapp.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionRegistryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisSessionRegistry registry;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        registry = new RedisSessionRegistry(redisTemplate);
    }

    @Test
    void testRegister() {
        registry.register(42L, "session-123");
        verify(hashOperations).put("ws:sessions", "42", "session-123");
    }

    @Test
    void testUnregister() {
        registry.unregister(42L);
        verify(hashOperations).delete("ws:sessions", "42");
    }

    @Test
    void testGetSessionId() {
        when(hashOperations.get("ws:sessions", "42")).thenReturn("session-123");
        assertEquals("session-123", registry.getSessionId(42L));
        
        when(hashOperations.get("ws:sessions", "42")).thenReturn(null);
        assertNull(registry.getSessionId(42L));
    }

    @Test
    void testIsOnline() {
        when(hashOperations.hasKey("ws:sessions", "42")).thenReturn(true);
        assertTrue(registry.isOnline(42L));

        when(hashOperations.hasKey("ws:sessions", "42")).thenReturn(false);
        assertFalse(registry.isOnline(42L));
    }

    @Test
    void testGetOnlineUserIds() {
        when(hashOperations.keys("ws:sessions")).thenReturn(Set.of("42", "43"));
        Set<Long> onlineUsers = registry.getOnlineUserIds();
        assertEquals(2, onlineUsers.size());
        assertTrue(onlineUsers.contains(42L));
        assertTrue(onlineUsers.contains(43L));
    }

    @Test
    void testNullChecks() {
        registry.register(null, "session-123");
        registry.register(42L, null);
        verify(hashOperations, never()).put(any(), any(), any());

        assertFalse(registry.isOnline(null));
        assertNull(registry.getSessionId(null));
        
        registry.unregister(null);
        verify(hashOperations, never()).delete(any(), any());
    }
}
