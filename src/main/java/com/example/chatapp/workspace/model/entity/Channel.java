package com.example.chatapp.workspace.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "channels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workspace_channel_slug", columnNames = {"workspace_id", "slug"})
}, indexes = {
        @Index(name = "idx_channels_workspace_id", columnList = "workspace_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ChannelType type = ChannelType.PUBLIC;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private WorkspaceMember createdByMember;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean isArchived = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
