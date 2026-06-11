package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySenderIdAndSentAt(Long senderId, Instant sentAt);

    @Query("SELECT m FROM Message m WHERE " +
           "(m.recipient IS NULL OR m.recipient.username = 'system') AND " +
           "(:sentAt IS NULL OR m.sentAt < :sentAt OR (m.sentAt = :sentAt AND m.id < :lastId)) " +
           "ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findPublicMessages(
            @Param("sentAt") Instant sentAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m WHERE " +
           "((m.sender.id = :userId1 AND m.recipient.id = :userId2) OR " +
           "(m.sender.id = :userId2 AND m.recipient.id = :userId1)) AND " +
           "(:clearedAt IS NULL OR m.sentAt > :clearedAt) AND " +
           "(:sentAt IS NULL OR m.sentAt < :sentAt OR (m.sentAt = :sentAt AND m.id < :lastId)) " +
           "ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findPrivateMessages(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("clearedAt") Instant clearedAt,
            @Param("sentAt") Instant sentAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    boolean existsBySenderIdAndRecipientId(Long senderId, Long recipientId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender.id = :senderId AND m.recipient.id = :recipientId AND m.isRead = false AND (:clearedAt IS NULL OR m.sentAt > :clearedAt)")
    long countUnreadMessages(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId, @Param("clearedAt") Instant clearedAt);

    @Query("SELECT m FROM Message m WHERE " +
           "((m.sender.id = :userId1 AND m.recipient.id = :userId2) OR " +
           "(m.sender.id = :userId2 AND m.recipient.id = :userId1)) AND " +
           "(:clearedAt IS NULL OR m.sentAt > :clearedAt) " +
           "ORDER BY m.sentAt DESC")
    List<Message> findLatestMessageBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("clearedAt") Instant clearedAt,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.sender.id = :senderId AND m.recipient.id = :recipientId AND m.isRead = false")
    int markMessagesAsRead(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);
}
