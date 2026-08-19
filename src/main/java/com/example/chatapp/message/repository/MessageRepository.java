package com.example.chatapp.message.repository;

import com.example.chatapp.message.model.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m " +
           "JOIN FETCH m.conversation c " +
           "LEFT JOIN FETCH m.senderMember sm " +
           "LEFT JOIN FETCH sm.user " +
           "WHERE c.channel.id = :channelId AND " +
           "(:sentAt IS NULL OR m.sentAt < :sentAt OR (m.sentAt = :sentAt AND m.id < :lastId)) " +
           "ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findChannelMessages(
            @Param("channelId") Long channelId,
            @Param("sentAt") Instant sentAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m " +
           "JOIN FETCH m.conversation c " +
           "LEFT JOIN FETCH m.senderMember sm " +
           "LEFT JOIN FETCH sm.user " +
           "WHERE c.id = :conversationId AND " +
           "(:clearedAt IS NULL OR m.sentAt > :clearedAt) AND " +
           "(:sentAt IS NULL OR m.sentAt < :sentAt OR (m.sentAt = :sentAt AND m.id < :lastId)) " +
           "ORDER BY m.sentAt DESC, m.id DESC")
    List<Message> findConversationMessages(
            @Param("conversationId") Long conversationId,
            @Param("clearedAt") Instant clearedAt,
            @Param("sentAt") Instant sentAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m " +
           "WHERE m.senderMember.id = :senderMemberId AND m.sentAt = :sentAt")
    Optional<Message> findBySenderMemberIdAndSentAt(@Param("senderMemberId") Long senderMemberId, @Param("sentAt") Instant sentAt);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation.id = :conversationId AND (m.senderMember.id != :memberId OR m.senderMember IS NULL) AND m.isRead = false")
    int markConversationMessagesAsRead(@Param("conversationId") Long conversationId, @Param("memberId") Long memberId);
}
