package com.example.chatapp.workspace.model.dto;

import com.example.chatapp.workspace.model.entity.InvitationStatus;
import com.example.chatapp.workspace.model.entity.InvitationType;
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
public class InvitationDto {
    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private String workspaceSlug;
    private String token;
    private InvitationType type;
    private String email;
    private WorkspaceRole role;
    private InvitationStatus status;
    private Instant expiresAt;
    private Integer maxUses;
    private Integer usesCount;
    private String inviterName;
    private String customMessage;
    private java.util.List<Long> initialChannelIds;
    private java.util.List<ChannelDto> initialChannels;
    private Boolean isActive;
    private Instant createdAt;
}
