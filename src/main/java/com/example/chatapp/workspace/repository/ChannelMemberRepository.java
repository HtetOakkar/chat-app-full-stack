package com.example.chatapp.workspace.repository;

import com.example.chatapp.workspace.model.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    List<ChannelMember> findByChannelId(Long channelId);
    
    Optional<ChannelMember> findByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);
    
    boolean existsByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);
    
    void deleteByChannelIdAndWorkspaceMemberId(Long channelId, Long workspaceMemberId);

    int countByChannelId(Long channelId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(cm) > 0 FROM ChannelMember cm WHERE cm.channel.id = :channelId AND cm.workspaceMember.user.id = :userId")
    boolean isUserChannelMember(@org.springframework.data.repository.query.Param("channelId") Long channelId, @org.springframework.data.repository.query.Param("userId") Long userId);
}
