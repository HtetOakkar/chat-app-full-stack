package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public interface PresenceRepository extends JpaRepository<User, Long> {

    @Query("SELECT DISTINCT u.id FROM User u " +
           "LEFT JOIN u.settings s " +
           "WHERE u.id != :userId AND (s IS NULL OR s.sharePresence = true) " +
           "AND NOT EXISTS (SELECT 1 FROM Contact cb WHERE ((cb.owner.id = :userId AND cb.contactUser.id = u.id) OR (cb.owner.id = u.id AND cb.contactUser.id = :userId)) AND cb.status = com.example.chatapp.user.model.entity.ContactStatus.BLOCKED) " +
           "AND (" +
           "  EXISTS (SELECT 1 FROM Contact c WHERE (c.owner.id = :userId AND c.contactUser.id = u.id) OR (c.owner.id = u.id AND c.contactUser.id = :userId)) " +
           "  OR " +
           "  EXISTS (SELECT 1 FROM Message m WHERE (m.sender.id = :userId AND m.recipient.id = u.id) OR (m.sender.id = u.id AND m.recipient.id = :userId)) " +
           ")")
    Set<Long> findEligiblePresenceReceivers(@Param("userId") Long userId);

    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN u.settings s " +
           "WHERE u.id != :userId AND (s IS NULL OR s.sharePresence = true) " +
           "AND NOT EXISTS (SELECT 1 FROM Contact cb WHERE ((cb.owner.id = :userId AND cb.contactUser.id = u.id) OR (cb.owner.id = u.id AND cb.contactUser.id = :userId)) AND cb.status = com.example.chatapp.user.model.entity.ContactStatus.BLOCKED) " +
           "AND (" +
           "  EXISTS (SELECT 1 FROM Contact c WHERE (c.owner.id = :userId AND c.contactUser.id = u.id) OR (c.owner.id = u.id AND c.contactUser.id = :userId)) " +
           "  OR " +
           "  EXISTS (SELECT 1 FROM Message m WHERE (m.sender.id = :userId AND m.recipient.id = u.id) OR (m.sender.id = u.id AND m.recipient.id = :userId)) " +
           ")")
    List<User> findEligiblePresenceUsers(@Param("userId") Long userId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    void updateLastSeenAt(@Param("userId") Long userId, @Param("lastSeenAt") Instant lastSeenAt);
}
