package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.InvitationStatus;
import com.example.chatapp.workspace.model.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {
    Optional<WorkspaceInvitation> findByToken(String token);
    
    List<WorkspaceInvitation> findByWorkspaceId(Long workspaceId);
    
    List<WorkspaceInvitation> findByWorkspaceIdAndStatus(Long workspaceId, InvitationStatus status);
    
    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatus(Long workspaceId, String email, InvitationStatus status);
}
