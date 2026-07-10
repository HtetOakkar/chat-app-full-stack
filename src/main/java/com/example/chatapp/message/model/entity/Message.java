package com.example.chatapp.message.model.entity;

import com.example.chatapp.user.model.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_public_recipient_sent_id", columnList = "recipient_id, sent_at, id"),
        @Index(name = "idx_messages_private_sender_recipient_sent_id", columnList = "sender_id, recipient_id, sent_at, id"),
        @Index(name = "idx_messages_private_recipient_sender_sent_id", columnList = "recipient_id, sender_id, sent_at, id"),
        @Index(name = "idx_messages_unread_sender_recipient_read_sent", columnList = "sender_id, recipient_id, is_read, sent_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isRead;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isDeleted = false;

    @Column(name = "is_delivered", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isDelivered;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = Instant.now();
        }
    }

    @Column
    private Instant deliveredAt;

    @Column(name = "call_outcome")
    private String callOutcome;

    @Column(name = "call_duration")
    private Integer callDuration;

    @Column(name = "video_used")
    private Boolean videoUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", referencedColumnName = "id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", referencedColumnName = "id", nullable = false)
    private User recipient;

}
