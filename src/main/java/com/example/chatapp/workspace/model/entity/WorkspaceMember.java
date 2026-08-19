package com.example.chatapp.workspace.model.entity;

import com.example.chatapp.user.model.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "workspace_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workspace_user", columnNames = {"workspace_id", "user_id"})
}, indexes = {
        @Index(name = "idx_wm_workspace_id", columnList = "workspace_id"),
        @Index(name = "idx_wm_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;
}
