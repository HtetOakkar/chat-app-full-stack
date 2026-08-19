package com.example.chatapp.workspace.model.request;

import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInvitationRequest {

    @NotEmpty(message = "Emails list cannot be empty")
    private List<String> emails;

    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    private String customMessage;

    private List<Long> initialChannelIds;

    @Builder.Default
    private Integer expiresInHours = 720; // 30 days
}
