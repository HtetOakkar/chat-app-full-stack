package com.example.chatapp.workspace.service;

import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;

import java.util.List;

public interface WorkspaceService {
    WorkspaceDto createWorkspace(Long userId, CreateWorkspaceRequest request);
    List<WorkspaceDto> getUserWorkspaces(Long userId);
    WorkspaceDto getWorkspaceById(Long workspaceId, Long userId);
    WorkspaceDto getWorkspaceBySlug(String slug, Long userId);
    List<WorkspaceMemberDto> getWorkspaceMembers(Long workspaceId, Long userId);
    WorkspaceMemberDto getWorkspaceMember(Long workspaceId, Long userId);
}
