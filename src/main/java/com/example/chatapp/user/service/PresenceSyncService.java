package com.example.chatapp.user.service;

import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String PRESENCE_KEY_PREFIX = "user:presence:";
    private static final String OFFLINE_SYNC_QUEUE = "user:presence:offline_sync";

    @Scheduled(fixedDelayString = "${presence.sync.delay:60000}")
    @org.springframework.transaction.annotation.Transactional
    public void syncOfflinePresence() {
        Set<Object> userIds = redisTemplate.opsForSet().members(OFFLINE_SYNC_QUEUE);
        
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        log.info("Starting sync of offline presence for {} users", userIds.size());

        for (Object userIdObj : userIds) {
            String userIdStr = String.valueOf(userIdObj);
            try {
                Long userId = Long.parseLong(userIdStr);
                Object presenceVal = redisTemplate.opsForValue().get(PRESENCE_KEY_PREFIX + userId);
                
                if (presenceVal != null && !presenceVal.toString().equalsIgnoreCase("Online")) {
                    Instant lastSeenAt = Instant.parse(presenceVal.toString());
                    userRepository.updateLastSeenAt(userId, lastSeenAt);
                }
                
                // Remove from sync queue
                redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, userIdStr);
            } catch (NumberFormatException | DateTimeParseException e) {
                log.error("Failed to parse presence data for user {}", userIdStr, e);
                // Remove invalid entries so they don't block the queue
                redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, userIdStr);
            } catch (Exception e) {
                log.error("Error syncing offline presence for user {}", userIdStr, e);
            }
        }
        
        log.info("Completed sync of offline presence");
    }
}
