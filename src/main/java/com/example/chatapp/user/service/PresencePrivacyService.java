package com.example.chatapp.user.service;

import com.example.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PresencePrivacyService {
    private final UserRepository userRepository;

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

    public Set<Long> getEligiblePresenceReceivers(Long userId) {
        if (!canUserSharePresence(userId)) {
            return Collections.emptySet();
        }
        return userRepository.findEligiblePresenceReceivers(userId);
    }

    public java.util.List<com.example.chatapp.user.model.entity.User> getEligiblePresenceUsers(Long userId) {
        if (!canUserSharePresence(userId)) {
            return Collections.emptyList();
        }
        return userRepository.findEligiblePresenceUsers(userId);
    }
}
