package com.example.chatapp.workspace.model.dto;

import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberDto {
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String username;
    private String fullName;
    private WorkspaceRole role;
    private Instant joinedAt;
    private String presenceStatus; // "ONLINE" / "OFFLINE"
}
