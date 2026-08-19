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

    @Query("SELECT DISTINCT wm2.user.id FROM WorkspaceMember wm1 " +
           "JOIN WorkspaceMember wm2 ON wm1.workspace.id = wm2.workspace.id " +
           "WHERE wm1.user.id = :userId AND wm2.user.id != :userId")
    Set<Long> findEligiblePresenceReceivers(@Param("userId") Long userId);

    @Query("SELECT DISTINCT wm2.user FROM WorkspaceMember wm1 " +
           "JOIN WorkspaceMember wm2 ON wm1.workspace.id = wm2.workspace.id " +
           "WHERE wm1.user.id = :userId AND wm2.user.id != :userId")
    List<User> findEligiblePresenceUsers(@Param("userId") Long userId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    void updateLastSeenAt(@Param("userId") Long userId, @Param("lastSeenAt") Instant lastSeenAt);
}
