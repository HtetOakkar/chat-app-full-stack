package com.example.chatapp.workspace.mapper;

import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;

import java.util.List;

/**
 * Maps Workspace Members for a viewer. Each Member's Presence is what that viewer may see, honouring
 * the Member's Presence Sharing, and is resolved with one batched Presence lookup per call.
 */
public interface WorkspaceMemberMapper {
    List<WorkspaceMemberDto> toDtos(Long viewerId, List<WorkspaceMember> members);

    WorkspaceMemberDto toDto(Long viewerId, WorkspaceMember member);
}
