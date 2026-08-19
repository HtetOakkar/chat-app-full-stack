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
public class WorkspaceDto {
    private Long id;
    private String name;
    private String slug;
    private String iconUrl;
    private Long ownerId;
    private WorkspaceRole role;
    private Integer memberCount;
    private Integer channelCount;
    private Instant createdAt;
    private Instant updatedAt;
}
