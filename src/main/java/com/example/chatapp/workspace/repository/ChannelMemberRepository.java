package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    List<ChannelMember> findByChannelId(Long channelId);

    Optional<ChannelMember> findByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);

    boolean existsByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);

    /** Which of the given Workspace Members already belong to the Channel, in one query. */
    @Query("SELECT cm.workspaceMember.id FROM ChannelMember cm WHERE cm.channel.id = :channelId AND cm.workspaceMember.id IN :workspaceMemberIds")
    Set<Long> findExistingWorkspaceMemberIds(@Param("channelId") Long channelId,
                                             @Param("workspaceMemberIds") Collection<Long> workspaceMemberIds);

    void deleteByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);

    int countByChannelId(Long channelId);

    @Query("SELECT COUNT(cm) > 0 FROM ChannelMember cm WHERE cm.channel.id = :channelId AND cm.workspaceMember.user.id = :userId")
    boolean isUserChannelMember(@Param("channelId") Long channelId, @Param("userId") Long userId);
}
