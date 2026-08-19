package com.example.chatapp.workspace.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.workspace.model.dto.BatchInvitationResponse;
import com.example.chatapp.workspace.model.dto.InvitationDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.request.BatchInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;
import com.example.chatapp.workspace.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping({"/api/workspaces/{workspaceId}/invitations/batch", "/api/v1/workspaces/{workspaceId}/invitations/batch"})
    public ResponseEntity<BatchInvitationResponse> createBatchInvitations(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @Valid @RequestBody BatchInvitationRequest request) {
        BatchInvitationResponse response = invitationService.createBatchInvitations(workspaceId, currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/invitations/pending", "/api/v1/workspaces/{workspaceId}/invitations/pending"})
    public ResponseEntity<List<InvitationDto>> getPendingInvitations(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        List<InvitationDto> pending = invitationService.getPendingInvitations(workspaceId, currentUser.getId());
        return ResponseEntity.ok(pending);
    }

    @PostMapping({"/api/invitations/{id}/resend", "/api/v1/invitations/{id}/resend"})
    public ResponseEntity<InvitationDto> resendInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        InvitationDto dto = invitationService.resendInvitation(id, currentUser.getId());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping({"/api/invitations/{id}", "/api/v1/invitations/{id}"})
    public ResponseEntity<Void> revokeInvitation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        invitationService.revokeInvitation(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/workspaces/{workspaceId}/join-link", "/api/v1/workspaces/{workspaceId}/join-link"})
    public ResponseEntity<InvitationDto> createJoinLink(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateJoinLinkRequest request) {
        InvitationDto invitation = invitationService.createJoinLink(workspaceId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/join-link", "/api/v1/workspaces/{workspaceId}/join-link", "/api/workspaces/{workspaceId}/invitations/shareable-link", "/api/v1/workspaces/{workspaceId}/invitations/shareable-link"})
    public ResponseEntity<InvitationDto> getShareableJoinLink(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        InvitationDto invitation = invitationService.getOrCreateShareableJoinLink(workspaceId, currentUser.getId());
        return ResponseEntity.ok(invitation);
    }

    @PostMapping({"/api/workspaces/{workspaceId}/join-link/toggle", "/api/v1/workspaces/{workspaceId}/join-link/toggle", "/api/workspaces/{workspaceId}/join-link/toggle-active", "/api/v1/workspaces/{workspaceId}/join-link/toggle-active"})
    public ResponseEntity<InvitationDto> toggleShareableJoinLink(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        InvitationDto invitation = invitationService.toggleShareableJoinLink(workspaceId, currentUser.getId());
        return ResponseEntity.ok(invitation);
    }

    @PostMapping({"/api/workspaces/{workspaceId}/join-link/reset", "/api/v1/workspaces/{workspaceId}/join-link/reset", "/api/workspaces/{workspaceId}/invitations/shareable-link/reset", "/api/v1/workspaces/{workspaceId}/invitations/shareable-link/reset"})
    public ResponseEntity<InvitationDto> resetShareableJoinLink(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        InvitationDto invitation = invitationService.resetShareableJoinLink(workspaceId, currentUser.getId());
        return ResponseEntity.ok(invitation);
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
