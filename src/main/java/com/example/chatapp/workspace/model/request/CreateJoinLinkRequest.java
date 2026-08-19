package com.example.chatapp.workspace.model.request;

import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJoinLinkRequest {

    private Integer maxUses;

    @Builder.Default
    private Integer expiresInHours = 168; // 7 days

    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;
}
