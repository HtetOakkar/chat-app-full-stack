package com.example.chatapp.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void testRegisterAndGetSessionId() {
        registry.register(42L, "session-123");
        assertEquals("session-123", registry.getSessionId(42L));
        assertTrue(registry.isOnline(42L));
    }

    @Test
    void testUnregister() {
        registry.register(42L, "session-123");
        registry.unregister(42L);
        assertNull(registry.getSessionId(42L));
        assertFalse(registry.isOnline(42L));
    }

    @Test
    void testGetOnlineUserIds() {
        registry.register(42L, "session-123");
        registry.register(43L, "session-456");
        Set<Long> onlineUsers = registry.getOnlineUserIds();
        assertEquals(2, onlineUsers.size());
        assertTrue(onlineUsers.contains(42L));
        assertTrue(onlineUsers.contains(43L));
    }

    @Test
    void testNullChecks() {
        registry.register(null, "session-123");
        registry.register(42L, null);
        assertFalse(registry.isOnline(null));
        assertNull(registry.getSessionId(null));
        
        registry.unregister(null); // Should not throw
    }
}
