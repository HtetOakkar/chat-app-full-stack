package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    List<ConversationParticipant> findByConversationId(Long conversationId);
    
    List<ConversationParticipant> findByWorkspaceMemberId(Long workspaceMemberId);
    
    Optional<ConversationParticipant> findByConversationIdAndWorkspaceMemberId(Long conversationId, Long workspaceMemberId);
    
    boolean existsByConversationIdAndWorkspaceMemberId(Long conversationId, Long workspaceMemberId);
}
