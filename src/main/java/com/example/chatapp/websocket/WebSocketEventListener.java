package com.example.chatapp.websocket;

import com.example.chatapp.jwt.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal instanceof UserPrincipal userPrincipal) {
            log.info("User '{}' disconnected. Broadcasting OFFLINE status.", userPrincipal.getUsername());

            Map<String, Object> map = new HashMap<>();
            map.put("userid", userPrincipal.getId());
            map.put("status", "OFFLINE");
            map.put("timestamp", Instant.now());
            map.put("username", userPrincipal.getUsername());

            messagingTemplate.convertAndSend("/topic/online", map);
        } else {
            log.debug("Non-authenticated or non-UserPrincipal session disconnected.");
        }
    }
}
