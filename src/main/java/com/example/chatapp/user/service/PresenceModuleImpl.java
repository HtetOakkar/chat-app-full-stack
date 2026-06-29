package com.example.chatapp.user.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.user.model.dto.UserSettingsDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.UserSettings;
import com.example.chatapp.user.repository.PresenceRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.websocket.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceModuleImpl implements PresenceModule {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final PresenceRepository presenceRepository;
    private final SessionRegistry sessionRegistry;
    private final MessageBroker messageBroker;

    private static final String PRESENCE_KEY_PREFIX = "user:presence:";
    private static final String OFFLINE_SYNC_QUEUE = "user:presence:offline_sync";

    @Override
    public void setUserOnline(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Register session logic should be in event listener or here, let's keep it safe
        redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, "Online");
        redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, String.valueOf(userId));

        // Broadcast ONLINE status
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("status", "ONLINE");
        map.put("timestamp", Instant.now());
        map.put("username", user.getUsername());

        Set<Long> receivers = getEligiblePresenceReceivers(userId);
        for (Long receiverId : receivers) {
            messageBroker.publishToUser(receiverId.toString(), "/queue/online", map);
        }
        messageBroker.publishToUser(userId.toString(), "/queue/online", map);
    }

    @Override
    public void setUserOffline(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Instant now = Instant.now();
        redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, now.toString());
        redisTemplate.opsForSet().add(OFFLINE_SYNC_QUEUE, String.valueOf(userId));

        // Broadcast OFFLINE status
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("status", "OFFLINE");
        map.put("timestamp", now);
        map.put("username", user.getUsername());

        Set<Long> receivers = getEligiblePresenceReceivers(userId);
        for (Long receiverId : receivers) {
            messageBroker.publishToUser(receiverId.toString(), "/queue/online", map);
        }
        messageBroker.publishToUser(userId.toString(), "/queue/online", map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OnlineStatusDto> getOnlineUsers(Long currentUserId) {
        List<User> visibleUsers = getEligiblePresenceUsers(currentUserId);
        return visibleUsers.stream()
                .filter(user -> sessionRegistry.isOnline(user.getId()))
                .map(user -> {
                    OnlineStatusDto dto = new OnlineStatusDto();
                    dto.setStatus("ONLINE");
                    dto.setUserId(String.valueOf(user.getId()));
                    dto.setUsername(user.getUsername());
                    return dto;
                })
                .toList();
    }

    @Override
    public Map<String, String> getPresenceMap() {
        Set<String> keys = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
            Set<String> scannedKeys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match("user:presence:*").count(1000).build())) {
                while (cursor.hasNext()) {
                    scannedKeys.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                log.error("Error scanning user:presence:* keys", e);
            }
            return scannedKeys;
        });

        Map<String, String> map = new HashMap<>();
        if (keys != null) {
            for (String key : keys) {
                String uId = key.substring(PRESENCE_KEY_PREFIX.length());
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    map.put(uId, value.toString());
                }
            }
        }
        return map;
    }

    @Override
    public void syncPresenceToDatabase() {
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
                    presenceRepository.updateLastSeenAt(userId, lastSeenAt);
                }
                
                redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, userIdStr);
            } catch (NumberFormatException | DateTimeParseException e) {
                log.error("Failed to parse presence data for user {}", userIdStr, e);
                redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, userIdStr);
            } catch (Exception e) {
                log.error("Error syncing offline presence for user {}", userIdStr, e);
            }
        }
        
        log.info("Completed sync of offline presence");
    }

    @Override
    @Transactional
    public UserSettingsDto togglePresenceSharing(Long userId, boolean sharePresence) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        Set<Long> receiversBefore = getEligiblePresenceReceivers(userId);
        
        UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = UserSettings.builder()
                    .user(user)
                    .sharePresence(sharePresence)
                    .build();
            user.setSettings(settings);
        } else {
            settings.setSharePresence(sharePresence);
        }
        
        userRepository.save(user);
        
        if (!sharePresence) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("status", "OFFLINE");
            map.put("timestamp", Instant.now());
            map.put("username", user.getUsername());

            for (Long receiverId : receiversBefore) {
                messageBroker.publishToUser(receiverId.toString(), "/queue/online", map);
            }
            messageBroker.publishToUser(userId.toString(), "/queue/online", map);
        } else {
            if (sessionRegistry.isOnline(userId)) {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", userId);
                map.put("status", "ONLINE");
                map.put("timestamp", Instant.now());
                map.put("username", user.getUsername());

                Set<Long> receiversAfter = getEligiblePresenceReceivers(userId);
                for (Long receiverId : receiversAfter) {
                    messageBroker.publishToUser(receiverId.toString(), "/queue/online", map);
                }
                messageBroker.publishToUser(userId.toString(), "/queue/online", map);
            }
        }
        
        return UserSettingsDto.builder()
                .sharePresence(settings.isSharePresence())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettingsDto getPrivacySettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        return UserSettingsDto.builder()
                .sharePresence(user.getSettings() != null ? user.getSettings().isSharePresence() : true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserSharePresence(Long userId) {
        return userRepository.findById(userId)
                .map(u -> {
                    if (u.getSettings() != null) {
                        return u.getSettings().isSharePresence();
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getEligiblePresenceReceivers(Long userId) {
        if (!canUserSharePresence(userId)) {
            return Collections.emptySet();
        }
        return presenceRepository.findEligiblePresenceReceivers(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getEligiblePresenceUsers(Long userId) {
        if (!canUserSharePresence(userId)) {
            return Collections.emptyList();
        }
        return presenceRepository.findEligiblePresenceUsers(userId);
    }

    @Scheduled(fixedDelayString = "${presence.sync.delay:60000}")
    public void scheduledSyncOfflinePresence() {
        syncPresenceToDatabase();
    }

    @Scheduled(fixedDelayString = "${presence.scan.delay:300000}")
    public void scheduledSyncAllPresenceViaScan() {
        log.info("Starting sync of all presence keys via Redis SCAN");
        Set<String> keys = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
            Set<String> scannedKeys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match("user:presence:*").count(250).build())) {
                while (cursor.hasNext()) {
                    scannedKeys.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                log.error("Error scanning user:presence:* keys", e);
            }
            try (Cursor<byte[]> cursor2 = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match("presence:*").count(250).build())) {
                while (cursor2.hasNext()) {
                    scannedKeys.add(new String(cursor2.next()));
                }
            } catch (Exception e) {
                log.error("Error scanning presence:* keys", e);
            }
            return scannedKeys;
        });

        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            if (OFFLINE_SYNC_QUEUE.equals(key)) {
                continue;
            }
            try {
                Long userId = null;
                if (key.startsWith(PRESENCE_KEY_PREFIX)) {
                    userId = Long.parseLong(key.substring(PRESENCE_KEY_PREFIX.length()));
                } else if (key.startsWith("presence:")) {
                    userId = Long.parseLong(key.substring("presence:".length()));
                }

                if (userId != null) {
                    Object presenceVal = redisTemplate.opsForValue().get(key);
                    if (presenceVal != null && !presenceVal.toString().equalsIgnoreCase("Online")) {
                        Instant lastSeenAt = Instant.parse(presenceVal.toString());
                        presenceRepository.updateLastSeenAt(userId, lastSeenAt);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing presence key {}", key, e);
            }
        }
        log.info("Completed sync of presence keys via Redis SCAN");
    }
}
