package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByUsernameContainingIgnoreCase(String keyword);
    List<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(String usernameKeyword, String fullNameKeyword);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT u.id FROM User u " +
           "LEFT JOIN u.settings s " +
           "WHERE u.id != :userId AND (s IS NULL OR s.sharePresence = true) " +
           "AND (" +
           "  EXISTS (SELECT 1 FROM Contact c WHERE (c.owner.id = :userId AND c.contactUser.id = u.id) OR (c.owner.id = u.id AND c.contactUser.id = :userId)) " +
           "  OR " +
           "  EXISTS (SELECT 1 FROM Message m WHERE (m.sender.id = :userId AND m.recipient.id = u.id) OR (m.sender.id = u.id AND m.recipient.id = :userId)) " +
           ")")
    java.util.Set<Long> findEligiblePresenceReceivers(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN u.settings s " +
           "WHERE u.id != :userId AND (s IS NULL OR s.sharePresence = true) " +
           "AND (" +
           "  EXISTS (SELECT 1 FROM Contact c WHERE (c.owner.id = :userId AND c.contactUser.id = u.id) OR (c.owner.id = u.id AND c.contactUser.id = :userId)) " +
           "  OR " +
           "  EXISTS (SELECT 1 FROM Message m WHERE (m.sender.id = :userId AND m.recipient.id = u.id) OR (m.sender.id = u.id AND m.recipient.id = :userId)) " +
           ")")
    java.util.List<User> findEligiblePresenceUsers(@org.springframework.data.repository.query.Param("userId") Long userId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    void updateLastSeenAt(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("lastSeenAt") java.time.Instant lastSeenAt);
}

