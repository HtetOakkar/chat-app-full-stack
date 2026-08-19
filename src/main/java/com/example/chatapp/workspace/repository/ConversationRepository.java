package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.Conversation;
import com.example.chatapp.workspace.model.entity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByChannelId(Long channelId);
    
    Optional<Conversation> findByWorkspaceIdAndChannelId(Long workspaceId, Long channelId);
    
    List<Conversation> findByWorkspaceIdAndType(Long workspaceId, ConversationType type);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Conversation c " +
           "WHERE c.workspace.id = :workspaceId AND c.type = 'DIRECT_MESSAGE' AND " +
           "c.id IN (SELECT cp1.conversation.id FROM ConversationParticipant cp1 WHERE cp1.workspaceMember.id = :member1Id) AND " +
           "c.id IN (SELECT cp2.conversation.id FROM ConversationParticipant cp2 WHERE cp2.workspaceMember.id = :member2Id)")
    Optional<Conversation> findDirectMessageConversation(
            @org.springframework.data.repository.query.Param("workspaceId") Long workspaceId,
            @org.springframework.data.repository.query.Param("member1Id") Long member1Id,
            @org.springframework.data.repository.query.Param("member2Id") Long member2Id
    );
}
