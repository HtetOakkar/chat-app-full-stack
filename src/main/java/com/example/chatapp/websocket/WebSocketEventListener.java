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
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final com.example.chatapp.user.repository.UserRepository userRepository;
    private final com.example.chatapp.user.service.PresencePrivacyService presencePrivacyService;

    private static final String PRESENCE_KEY_PREFIX = "user:presence:";
    private static final String OFFLINE_SYNC_QUEUE = "user:presence:offline_sync";

    @EventListener
    public void handleWebSocketConnectListener(org.springframework.web.socket.messaging.SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal instanceof UserPrincipal userPrincipal) {
            log.info("User '{}' connected. Broadcasting ONLINE status.", userPrincipal.getUsername());

            // Update Redis
            redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userPrincipal.getId(), "Online");
            redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, String.valueOf(userPrincipal.getId()));

            // Broadcast
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userPrincipal.getId());
            map.put("status", "ONLINE");
            map.put("timestamp", Instant.now());
            map.put("username", userPrincipal.getUsername());

            java.util.Set<Long> receivers = presencePrivacyService.getEligiblePresenceReceivers(userPrincipal.getId());
            for (Long receiverId : receivers) {
                messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/online", map);
            }
            messagingTemplate.convertAndSendToUser(userPrincipal.getId().toString(), "/queue/online", map);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal instanceof UserPrincipal userPrincipal) {
            log.info("User '{}' disconnected. Broadcasting OFFLINE status.", userPrincipal.getUsername());
            
            Instant now = Instant.now();

            // Update Redis presence and add to sync queue
            redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userPrincipal.getId(), now.toString());
            redisTemplate.opsForSet().add(OFFLINE_SYNC_QUEUE, String.valueOf(userPrincipal.getId()));

            // Broadcast
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userPrincipal.getId());
            map.put("status", "OFFLINE");
            map.put("timestamp", now);
            map.put("username", userPrincipal.getUsername());

            java.util.Set<Long> receivers = presencePrivacyService.getEligiblePresenceReceivers(userPrincipal.getId());
            for (Long receiverId : receivers) {
                messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/online", map);
            }
            messagingTemplate.convertAndSendToUser(userPrincipal.getId().toString(), "/queue/online", map);
        } else {
            log.debug("Non-authenticated or non-UserPrincipal session disconnected.");
        }
    }
}
