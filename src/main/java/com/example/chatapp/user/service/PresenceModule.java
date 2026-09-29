package com.example.chatapp.user.service;

import com.example.chatapp.message.model.dto.OnlineStatusDto;

import com.example.chatapp.user.model.dto.UserSettingsDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns Presence and its visibility. Presence Sharing is the target User's choice: a User with it off
 * appears offline to every other Member, while still seeing colleagues who share theirs.
 */
public interface PresenceModule {
    /** Connect: records the User online and tells colleagues, unless the User has Presence Sharing off. */
    void setUserOnline(Long userId);

    /** Disconnect: records "last seen" and tells colleagues, unless the User has Presence Sharing off. */
    void setUserOffline(Long userId);

    /** Workspace colleagues of the viewer who appear online to them. */
    List<OnlineStatusDto> getOnlineUsers(Long currentUserId);

    Map<String, String> getPresenceMap();

    void syncPresenceToDatabase();

    /**
     * Sets the User's Presence Sharing to the given value. The resulting offline (turned off) or online
     * (turned on while connected) broadcast is published only after the change commits.
     */
    UserSettingsDto setPresenceSharing(Long userId, boolean sharePresence);

    UserSettingsDto getPrivacySettings(Long userId);

    /** The User's Presence Sharing choice; on for Users who never set it. */
    boolean sharesPresence(Long userId);

    /**
     * Which of the target Users appear online to the viewer, resolved in one batched lookup: a target
     * appears online when connected and sharing Presence. The viewer always sees their own Presence.
     * Member lists use this instead of reading the session registry directly.
     */
    Set<Long> findUsersAppearingOnlineTo(Long viewerId, Collection<Long> targetUserIds);

    /** Colleagues who should hear about the User's Presence; empty when the User has Presence Sharing off. */
    Set<Long> getEligiblePresenceReceivers(Long userId);
}
