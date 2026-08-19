package com.example.chatapp.workspace.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "channel_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_channel_member", columnNames = {"channel_id", "workspace_member_id"})
}, indexes = {
        @Index(name = "idx_cm_channel_id", columnList = "channel_id"),
        @Index(name = "idx_cm_member_id", columnList = "workspace_member_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String role = "MEMBER";

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;
}
