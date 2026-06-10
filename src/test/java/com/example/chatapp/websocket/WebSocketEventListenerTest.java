package com.example.chatapp.websocket;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.user.model.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @Test
    void disconnectEventShouldBroadcastOfflineStatus() {
        // Arrange
        UserPrincipal principal = new UserPrincipal(42L, "testuser", "password123", java.util.Collections.emptyList());

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
        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/online"), mapCaptor.capture());

        Map<String, Object> broadcasted = mapCaptor.getValue();
        assertNotNull(broadcasted);
        assertEquals(42L, broadcasted.get("userid"));
        assertEquals("OFFLINE", broadcasted.get("status"));
        assertEquals("testuser", broadcasted.get("username"));
    }
}
