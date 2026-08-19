package com.example.chatapp.workspace.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.workspace.model.dto.InvitationDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;
import com.example.chatapp.workspace.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping({"/api/workspaces/{workspaceId}/invitations", "/api/v1/workspaces/{workspaceId}/invitations"})
    public ResponseEntity<InvitationDto> createEmailInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateEmailInvitationRequest request) {
        InvitationDto invitation = invitationService.createEmailInvitation(workspaceId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @PostMapping({"/api/workspaces/{workspaceId}/join-link", "/api/v1/workspaces/{workspaceId}/join-link"})
    public ResponseEntity<InvitationDto> createJoinLink(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateJoinLinkRequest request) {
        InvitationDto invitation = invitationService.createJoinLink(workspaceId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @GetMapping({"/api/invitations/{token}", "/api/v1/invitations/{token}"})
    public ResponseEntity<InvitationDto> getInvitationByToken(
            @PathVariable String token) {
        return ResponseEntity.ok(invitationService.getInvitationByToken(token));
    }

    @PostMapping({"/api/invitations/{token}/accept", "/api/v1/invitations/{token}/accept"})
    public ResponseEntity<WorkspaceDto> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String token) {
        WorkspaceDto workspace = invitationService.acceptInvitation(token, currentUser.getId());
        return ResponseEntity.ok(workspace);
    }
}
