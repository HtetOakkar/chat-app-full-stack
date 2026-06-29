package com.example.chatapp.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SessionRegistryMultiDeviceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisSessionRegistry redisSessionRegistry;
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        redisSessionRegistry = new RedisSessionRegistry(redisTemplate);
        webSocketSessionRegistry = new WebSocketSessionRegistry();
    }

    @Test
    void webSocketSessionRegistryShouldOverwritesAndLoseSessionOnMultiDeviceDisconnect() {
        Long userId = 42L;
        String session1 = "session-device-1";
        String session2 = "session-device-2";

        // 1. Connect first device
        webSocketSessionRegistry.register(userId, session1);
        assertEquals(session1, webSocketSessionRegistry.getSessionId(userId));
        assertTrue(webSocketSessionRegistry.isOnline(userId));

        // 2. Connect second device (overwrites first session)
        webSocketSessionRegistry.register(userId, session2);
        assertEquals(session2, webSocketSessionRegistry.getSessionId(userId));
        assertTrue(webSocketSessionRegistry.isOnline(userId));

        // 3. Disconnect first device (calls unregister)
        // In the current implementation, unregister just removes the userId key entirely
        webSocketSessionRegistry.unregister(userId);

        // 4. Verify user is now marked offline and second session is lost!
        assertNull(webSocketSessionRegistry.getSessionId(userId));
        assertFalse(webSocketSessionRegistry.isOnline(userId));
    }

    @Test
    void redisSessionRegistryShouldOverwritesAndLoseSessionOnMultiDeviceDisconnect() {
        Long userId = 42L;
        String session1 = "session-device-1";
        String session2 = "session-device-2";

        // 1. Connect first device
        redisSessionRegistry.register(userId, session1);
        verify(hashOperations).put("ws:sessions", "42", session1);

        // 2. Connect second device (overwrites)
        redisSessionRegistry.register(userId, session2);
        verify(hashOperations).put("ws:sessions", "42", session2);

        // Mock behaviors for lookup
        when(hashOperations.get("ws:sessions", "42")).thenReturn(session2);
        assertEquals(session2, redisSessionRegistry.getSessionId(userId));

        // 3. Disconnect first device (unregister)
        redisSessionRegistry.unregister(userId);
        verify(hashOperations).delete("ws:sessions", "42");

        // Mock behaviors after delete
        when(hashOperations.get("ws:sessions", "42")).thenReturn(null);
        when(hashOperations.hasKey("ws:sessions", "42")).thenReturn(false);

        // 4. Verify user is marked offline and second session is lost!
        assertNull(redisSessionRegistry.getSessionId(userId));
        assertFalse(redisSessionRegistry.isOnline(userId));
    }
}
