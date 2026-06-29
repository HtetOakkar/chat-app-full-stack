package com.example.chatapp.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("clustered")
@RequiredArgsConstructor
public class RedisSessionRegistry implements SessionRegistry {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String HASH_KEY = "ws:sessions";

    @Override
    public void register(Long userId, String sessionId) {
        if (userId != null && sessionId != null) {
            redisTemplate.opsForHash().put(HASH_KEY, String.valueOf(userId), sessionId);
        }
    }

    @Override
    public void unregister(Long userId) {
        if (userId != null) {
            redisTemplate.opsForHash().delete(HASH_KEY, String.valueOf(userId));
        }
    }

    @Override
    public String getSessionId(Long userId) {
        if (userId == null) {
            return null;
        }
        Object sessionId = redisTemplate.opsForHash().get(HASH_KEY, String.valueOf(userId));
        return sessionId != null ? sessionId.toString() : null;
    }

    @Override
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        return redisTemplate.opsForHash().hasKey(HASH_KEY, String.valueOf(userId));
    }

    @Override
    public Set<Long> getOnlineUserIds() {
        Set<Object> keys = redisTemplate.opsForHash().keys(HASH_KEY);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        return keys.stream()
                .map(key -> Long.valueOf(key.toString()))
                .collect(Collectors.toSet());
    }
}
