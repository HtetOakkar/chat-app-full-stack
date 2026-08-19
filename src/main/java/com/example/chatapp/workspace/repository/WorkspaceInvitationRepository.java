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

    List<WorkspaceInvitation> findByWorkspaceIdAndType(Long workspaceId, com.example.chatapp.workspace.model.entity.InvitationType type);

    List<WorkspaceInvitation> findByWorkspaceIdAndTypeAndStatus(Long workspaceId, com.example.chatapp.workspace.model.entity.InvitationType type, InvitationStatus status);

    List<WorkspaceInvitation> findByWorkspaceIdAndTypeOrderByCreatedAtDesc(Long workspaceId, com.example.chatapp.workspace.model.entity.InvitationType type);

    List<WorkspaceInvitation> findByWorkspaceIdAndTypeAndStatusOrderByCreatedAtDesc(Long workspaceId, com.example.chatapp.workspace.model.entity.InvitationType type, InvitationStatus status);
}
