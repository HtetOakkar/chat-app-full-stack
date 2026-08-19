package com.example.chatapp.workspace.model.request;

import com.example.chatapp.workspace.model.entity.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailInvitationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Builder.Default
    private Integer expiresInHours = 72;
}
