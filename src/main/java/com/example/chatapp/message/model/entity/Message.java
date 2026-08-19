package com.example.chatapp.message.model.entity;

import com.example.chatapp.workspace.model.entity.Conversation;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_conv_sent", columnList = "conversation_id, sent_at"),
        @Index(name = "idx_messages_sender_id", columnList = "sender_member_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_member_id")
    private WorkspaceMember senderMember;

    @Column(nullable = false, length = 5000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "call_outcome", length = 32)
    private String callOutcome;

    @Column(name = "call_duration")
    private Long callDuration;

    @Column(name = "video_used")
    private Boolean videoUsed;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }
}
