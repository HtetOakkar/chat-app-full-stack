package com.example.chatapp.user.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.user.model.dto.UserSettingsDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.UserSettings;
import com.example.chatapp.user.repository.PresenceRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.repository.UserSettingsRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.websocket.SessionRegistry;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserSettingsRepository userSettingsRepository;

    private static final String PRESENCE_KEY_PREFIX = "user:presence:";
    private static final String OFFLINE_SYNC_QUEUE = "user:presence:offline_sync";
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_OFFLINE = "OFFLINE";

    /**
     * Presence Sharing is cached next to the Presence keys, so connect and disconnect decide it without
     * reading the database (ADR-0002). Changes write through after commit; the TTL only bounds how long
     * a failed refresh could leave a stale choice behind.
     */
    private static final String SHARING_KEY_PREFIX = PRESENCE_KEY_PREFIX + "sharing:";
    private static final Duration SHARING_CACHE_TTL = Duration.ofHours(1);

    @Override
    public void setUserOnline(Long userId, String username) {
        redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, "Online");
        redisTemplate.opsForSet().remove(OFFLINE_SYNC_QUEUE, String.valueOf(userId));

        announcePresence(userId, username, STATUS_ONLINE, Instant.now());
    }

    @Override
    public void setUserOffline(Long userId, String username) {
        Instant now = Instant.now();
        redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, now.toString());
        redisTemplate.opsForSet().add(OFFLINE_SYNC_QUEUE, String.valueOf(userId));

        announcePresence(userId, username, STATUS_OFFLINE, now);
    }

    /**
     * Tells the User's colleagues about a Presence change, unless the User has Presence Sharing off.
     * The User's own queue is always told, so their other sessions stay consistent.
     */
    private void announcePresence(Long userId, String username, String status, Instant timestamp) {
        publishPresence(presenceAnnouncement(userId, username, status, timestamp, sharesPresence(userId)));
    }

    /**
     * Resolves who hears about a Presence change. Reads the database, so inside a transaction
     * it must run before commit; publishing it is a separate step.
     */
    private PresenceAnnouncement presenceAnnouncement(Long userId, String username, String status,
                                                      Instant timestamp, boolean toColleagues) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("status", status);
        payload.put("timestamp", timestamp);
        payload.put("username", username);

        if (!toColleagues) {
            return new PresenceAnnouncement(userId, payload, Set.of(), List.of());
        }

        Set<Long> receivers = presenceRepository.findEligiblePresenceReceivers(userId);
        List<Long> workspaceIds = new ArrayList<>();
        if (workspaceMemberRepository != null) {
            try {
                for (WorkspaceMember wm : workspaceMemberRepository.findByUserId(userId)) {
                    if (wm.getWorkspace() != null) {
                        workspaceIds.add(wm.getWorkspace().getId());
                    }
                }
            } catch (Exception e) {
                log.warn("Error resolving workspaces for presence broadcast: {}", e.getMessage());
            }
        }
        return new PresenceAnnouncement(userId, payload, receivers, workspaceIds);
    }

    private void publishPresence(PresenceAnnouncement announcement) {
        for (Long receiverId : announcement.receiverIds()) {
            messageBroker.publishToUser(receiverId.toString(), "/queue/online", announcement.payload());
        }
        messageBroker.publishToUser(announcement.userId().toString(), "/queue/online", announcement.payload());
        for (Long workspaceId : announcement.workspaceIds()) {
            messageBroker.publishToTopic("/topic/workspace." + workspaceId + ".presence", announcement.payload());
        }
    }

    private record PresenceAnnouncement(Long userId, Map<String, Object> payload,
                                        Set<Long> receiverIds, List<Long> workspaceIds) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<OnlineStatusDto> getOnlineUsers(Long currentUserId) {
        List<User> colleagues = presenceRepository.findEligiblePresenceUsers(currentUserId);
        Set<Long> appearingOnline = findUsersAppearingOnlineTo(
                currentUserId, colleagues.stream().map(User::getId).toList());
        return colleagues.stream()
                .filter(user -> appearingOnline.contains(user.getId()))
                .map(user -> {
                    OnlineStatusDto dto = new OnlineStatusDto();
                    dto.setStatus(STATUS_ONLINE);
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
                if (!isLastSeenKey(key)) {
                    continue;
                }
                String uId = key.substring(PRESENCE_KEY_PREFIX.length());
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    map.put(uId, value.toString());
                }
            }
        }
        return map;
    }

    /** A per-User "online or last seen" key, as opposed to the sync queue or a Presence Sharing entry. */
    private static boolean isLastSeenKey(String key) {
        return !OFFLINE_SYNC_QUEUE.equals(key) && !key.startsWith(SHARING_KEY_PREFIX);
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
    public UserSettingsDto setPresenceSharing(Long userId, boolean sharePresence) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> UserSettings.builder().user(user).build());
        settings.setSharePresence(sharePresence);
        userSettingsRepository.save(settings);

        // Turning sharing off hides the User at once; turning it on reveals them only if connected.
        PresenceAnnouncement announcement = null;
        if (!sharePresence) {
            announcement = presenceAnnouncement(userId, user.getUsername(), STATUS_OFFLINE, Instant.now(), true);
        } else if (sessionRegistry.isOnline(userId)) {
            announcement = presenceAnnouncement(userId, user.getUsername(), STATUS_ONLINE, Instant.now(), true);
        }
        PresenceAnnouncement toPublish = announcement;
        afterCommit(() -> {
            refreshCachedSharing(userId, sharePresence);
            if (toPublish != null) {
                publishPresence(toPublish);
            }
        });

        return UserSettingsDto.builder()
                .sharePresence(settings.isSharePresence())
                .build();
    }

    /**
     * Colleagues must never see, and the cache must never hold, a Presence Sharing change that did not
     * commit.
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettingsDto getPrivacySettings(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        return UserSettingsDto.builder()
                .sharePresence(presenceRepository.findSharePresence(userId).orElse(true))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findUsersAppearingOnlineTo(Long viewerId, Collection<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> connected = new HashSet<>(targetUserIds);
        connected.retainAll(sessionRegistry.getOnlineUserIds());

        // Presence Sharing is the target's choice; a viewer always sees their own Presence.
        Set<Long> othersConnected = new HashSet<>(connected);
        othersConnected.remove(viewerId);
        if (!othersConnected.isEmpty()) {
            connected.removeAll(findUsersNotSharingPresence(othersConnected));
        }
        return connected;
    }

    @Override
    public boolean sharesPresence(Long userId) {
        Boolean cached = null;
        try {
            cached = toSharing(redisTemplate.opsForValue().get(sharingKey(userId)));
        } catch (RuntimeException e) {
            log.warn("Presence Sharing cache unavailable; reading the stored choice: {}", e.getMessage());
        }
        if (cached != null) {
            return cached;
        }
        boolean stored = presenceRepository.findSharePresence(userId).orElse(true);
        cacheSharingIfAbsent(userId, stored);
        return stored;
    }

    /**
     * Of the given Users, those who turned Presence Sharing off: one cache round trip, then one
     * batched database lookup for the Users whose choice isn't cached yet.
     */
    private Set<Long> findUsersNotSharingPresence(Collection<Long> userIds) {
        List<Long> ids = List.copyOf(userIds);
        List<Object> cached = null;
        try {
            cached = redisTemplate.opsForValue().multiGet(ids.stream().map(PresenceModuleImpl::sharingKey).toList());
        } catch (RuntimeException e) {
            log.warn("Presence Sharing cache unavailable; reading the stored choices: {}", e.getMessage());
        }

        Set<Long> notSharing = new HashSet<>();
        List<Long> misses = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Boolean sharing = cached == null || i >= cached.size() ? null : toSharing(cached.get(i));
            if (sharing == null) {
                misses.add(ids.get(i));
            } else if (!sharing) {
                notSharing.add(ids.get(i));
            }
        }
        if (!misses.isEmpty()) {
            Set<Long> storedNotSharing = presenceRepository.findUserIdsNotSharingPresence(misses);
            notSharing.addAll(storedNotSharing);
            for (Long userId : misses) {
                cacheSharingIfAbsent(userId, !storedNotSharing.contains(userId));
            }
        }
        return notSharing;
    }

    private static String sharingKey(Long userId) {
        return SHARING_KEY_PREFIX + userId;
    }

    private static Boolean toSharing(Object cached) {
        return cached == null ? null : Boolean.valueOf(cached.toString());
    }

    /**
     * Fills the cache from the stored choice on a miss. It only fills an empty entry, so a read that
     * raced a Presence Sharing change can't overwrite the newer value that change wrote.
     */
    private void cacheSharingIfAbsent(Long userId, boolean sharing) {
        try {
            redisTemplate.opsForValue().setIfAbsent(sharingKey(userId), sharing, SHARING_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("Could not cache Presence Sharing for user {}: {}", userId, e.getMessage());
        }
    }

    /** Writes a committed Presence Sharing change through, so the next connect or disconnect honours it. */
    private void refreshCachedSharing(Long userId, boolean sharing) {
        try {
            redisTemplate.opsForValue().set(sharingKey(userId), sharing, SHARING_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("Could not refresh cached Presence Sharing for user {}: {}", userId, e.getMessage());
            try {
                redisTemplate.delete(sharingKey(userId));
            } catch (RuntimeException ignored) {
                // Reads fall back to the stored choice while the cache is unavailable.
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getEligiblePresenceReceivers(Long userId) {
        if (!sharesPresence(userId)) {
            return Collections.emptySet();
        }
        return presenceRepository.findEligiblePresenceReceivers(userId);
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
            if (!isLastSeenKey(key)) {
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
