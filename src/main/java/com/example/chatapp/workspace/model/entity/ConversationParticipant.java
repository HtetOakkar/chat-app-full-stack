package com.example.chatapp.workspace.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversation_participants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_conv_member", columnNames = {"conversation_id", "workspace_member_id"})
}, indexes = {
        @Index(name = "idx_cp_conv_id", columnList = "conversation_id"),
        @Index(name = "idx_cp_member_id", columnList = "workspace_member_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;
}
