package com.example.chatapp.websocket;

import java.util.Set;

public interface SessionRegistry {
    void register(Long userId, String sessionId);
    void unregister(Long userId);
    String getSessionId(Long userId);
    boolean isOnline(Long userId);
    Set<Long> getOnlineUserIds();
}
