package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    
    @Query("SELECT wm FROM WorkspaceMember wm WHERE wm.workspace.slug = :slug AND wm.user.id = :userId")
    Optional<WorkspaceMember> findByWorkspaceSlugAndUserId(@Param("slug") String slug, @Param("userId") Long userId);
    
    /** Resolves many Workspace Members in one query, fetching each Member's User alongside. */
    @Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.user u WHERE wm.workspace.id = :workspaceId AND u.id IN :userIds")
    List<WorkspaceMember> findWithUserByWorkspaceIdAndUserIdIn(@Param("workspaceId") Long workspaceId,
                                                              @Param("userIds") Collection<Long> userIds);

    List<WorkspaceMember> findByUserId(Long userId);
    
    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);
    
    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    
    @Query("SELECT COUNT(wm) > 0 FROM WorkspaceMember wm WHERE wm.workspace.slug = :slug AND wm.user.id = :userId")
    boolean existsByWorkspaceSlugAndUserId(@Param("slug") String slug, @Param("userId") Long userId);

    int countByWorkspaceId(Long workspaceId);
}
