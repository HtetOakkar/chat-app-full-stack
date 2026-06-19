package com.example.chatapp.websocket;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.user.model.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class WebSocketEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @org.mockito.Mock
    private org.springframework.data.redis.core.ValueOperations<String, Object> valueOperations;

    @org.mockito.Mock
    private org.springframework.data.redis.core.SetOperations<String, Object> setOperations;

    @MockBean
    private com.example.chatapp.user.repository.UserRepository userRepository;

    @MockBean
    private com.example.chatapp.user.service.PresencePrivacyService presencePrivacyService;

    @org.junit.jupiter.api.BeforeEach
    void setupRedis() {
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void connectEventShouldUpdateRedisAndBroadcast() {
        // Arrange
        UserPrincipal principal = new UserPrincipal(42L, "testuser", "password123", java.util.Collections.emptyList());

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("session-2");
        accessor.setUser(principal);
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        org.springframework.web.socket.messaging.SessionConnectedEvent event = new org.springframework.web.socket.messaging.SessionConnectedEvent(
                this, message, principal
        );

        // Act
        eventPublisher.publishEvent(event);

        // Assert
        verify(valueOperations).set(eq("user:presence:42"), eq("Online"));
        verify(setOperations).remove(eq("user:presence:offline_sync"), eq("42"));

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/queue/online"), mapCaptor.capture());

        Map<String, Object> broadcasted = mapCaptor.getValue();
        assertNotNull(broadcasted);
        assertEquals(42L, broadcasted.get("userId"));
        assertEquals("ONLINE", broadcasted.get("status"));
        assertEquals("testuser", broadcasted.get("username"));
    }

    @Test
    void disconnectEventShouldUpdateRedisAndDbAndBroadcastOfflineStatus() {
        // Arrange
        UserPrincipal principal = new UserPrincipal(42L, "testuser", "password123", java.util.Collections.emptyList());

        User user = new User();
        user.setId(42L);
        user.setUsername("testuser");
        org.mockito.Mockito.when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("session-1");
        accessor.setUser(principal);
        Message<byte[]> message = new GenericMessage<>(new byte[0], accessor.getMessageHeaders());

        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this, message, "session-1", null
        );

        // Act
        eventPublisher.publishEvent(event);

        // Assert
        // Verify redis updated with timestamp
        ArgumentCaptor<Object> redisValueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq("user:presence:42"), redisValueCaptor.capture());
        assertNotNull(redisValueCaptor.getValue());
        
        // Verify user added to sync queue
        verify(setOperations).add(eq("user:presence:offline_sync"), eq("42"));

        // Verify broadcast
        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/queue/online"), mapCaptor.capture());

        Map<String, Object> broadcasted = mapCaptor.getValue();
        assertNotNull(broadcasted);
        assertEquals(42L, broadcasted.get("userId"));
        assertEquals("OFFLINE", broadcasted.get("status"));
        assertEquals("testuser", broadcasted.get("username"));
    }
}
