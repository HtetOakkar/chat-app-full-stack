package com.example.chatapp.websocket;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.user.service.PresenceModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private PresenceModule presenceModule;

    @MockBean
    private SessionRegistry sessionRegistry;

    @Test
    void connectEventShouldUpdateSessionRegistryAndSetUserOnline() {
        // Arrange
        UserPrincipal principal = new UserPrincipal(42L, "testuser", "password123", Collections.emptyList());

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
        verify(sessionRegistry).register(eq(42L), eq("session-2"));
        verify(presenceModule).setUserOnline(eq(42L));
    }

    @Test
    void disconnectEventShouldUpdateSessionRegistryAndSetUserOffline() {
        // Arrange
        UserPrincipal principal = new UserPrincipal(42L, "testuser", "password123", Collections.emptyList());

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
        verify(sessionRegistry).unregister(eq(42L));
        verify(presenceModule).setUserOffline(eq(42L));
    }
}
