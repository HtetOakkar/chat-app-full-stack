package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.Channel;
import com.example.chatapp.workspace.model.entity.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceId(Long workspaceId);
    
    List<Channel> findByWorkspaceIdAndType(Long workspaceId, ChannelType type);
    
    Optional<Channel> findByWorkspaceIdAndSlug(Long workspaceId, String slug);
    
    @Query("SELECT c FROM Channel c WHERE c.workspace.slug = :workspaceSlug AND c.slug = :channelSlug")
    Optional<Channel> findByWorkspaceSlugAndSlug(@Param("workspaceSlug") String workspaceSlug, @Param("channelSlug") String channelSlug);
    
    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    int countByWorkspaceId(Long workspaceId);
}
