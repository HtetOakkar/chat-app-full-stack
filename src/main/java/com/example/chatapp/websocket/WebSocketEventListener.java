package com.example.chatapp.websocket;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.user.service.PresenceModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceModule presenceModule;
    private final SessionRegistry sessionRegistry;

    @EventListener
    public void handleWebSocketConnectListener(org.springframework.web.socket.messaging.SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal instanceof UserPrincipal userPrincipal) {
            log.info("User '{}' connected. Registering session and setting online.", userPrincipal.getUsername());

            try {
                StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
                String sessionId = headerAccessor.getSessionId();
                sessionRegistry.register(userPrincipal.getId(), sessionId);

                presenceModule.setUserOnline(userPrincipal.getId());
            } catch (Exception e) {
                log.warn("Error handling WebSocket connect: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal instanceof UserPrincipal userPrincipal) {
            log.info("User '{}' disconnected. Unregistering session and setting offline.", userPrincipal.getUsername());
            
            try {
                sessionRegistry.unregister(userPrincipal.getId());

                presenceModule.setUserOffline(userPrincipal.getId());
            } catch (Exception e) {
                log.warn("Error handling WebSocket disconnect: {}", e.getMessage());
            }
        } else {
            log.debug("Non-authenticated or non-UserPrincipal session disconnected.");
        }
    }
}
