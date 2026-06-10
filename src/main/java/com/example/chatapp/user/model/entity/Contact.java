package com.example.chatapp.user.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "contacts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"owner_id", "contact_id"})
        },
        indexes = {
                @Index(name = "idx_contacts_owner_id", columnList = "owner_id")
        })
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private User contactUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStatus status;

    @CreationTimestamp
    @Column(name="created_at", updatable = false)
    private Instant createdAt;
}
