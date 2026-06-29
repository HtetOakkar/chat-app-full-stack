package com.example.chatapp.websocket;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!clustered")
public class WebSocketSessionRegistry implements SessionRegistry {

    private final Map<Long, String> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void register(Long userId, String sessionId) {
        if (userId != null && sessionId != null) {
            sessionMap.put(userId, sessionId);
        }
    }

    @Override
    public void unregister(Long userId) {
        if (userId != null) {
            sessionMap.remove(userId);
        }
    }

    @Override
    public String getSessionId(Long userId) {
        if (userId == null) {
            return null;
        }
        return sessionMap.get(userId);
    }

    @Override
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        return sessionMap.containsKey(userId);
    }

    @Override
    public Set<Long> getOnlineUserIds() {
        return new HashSet<>(sessionMap.keySet());
    }
}
