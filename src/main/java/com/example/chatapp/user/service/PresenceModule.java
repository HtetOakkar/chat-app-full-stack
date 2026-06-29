package com.example.chatapp.user.service;

import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.user.model.dto.UserSettingsDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PresenceModule {
    void setUserOnline(Long userId);
    void setUserOffline(Long userId);
    List<OnlineStatusDto> getOnlineUsers(Long currentUserId);
    Map<String, String> getPresenceMap();
    void syncPresenceToDatabase();
    UserSettingsDto togglePresenceSharing(Long userId, boolean sharePresence);
    UserSettingsDto getPrivacySettings(Long userId);

    boolean canUserSharePresence(Long userId);
    Set<Long> getEligiblePresenceReceivers(Long userId);
    java.util.List<com.example.chatapp.user.model.entity.User> getEligiblePresenceUsers(Long userId);
}
