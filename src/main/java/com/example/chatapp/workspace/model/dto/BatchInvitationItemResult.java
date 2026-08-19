package com.example.chatapp.workspace.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInvitationItemResult {
    private String email;
    private String status; // "sent", "already_member", "already_invited", "invalid_email"
    private Long invitationId;
    private String message;
}
