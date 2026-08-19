package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Workspace> findByOwnerId(Long ownerId);
}
