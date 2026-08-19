package com.example.chatapp.workspace.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.request.CreateWorkspaceRequest;
import com.example.chatapp.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/workspaces", "/api/v1/workspaces"})
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceDto> createWorkspace(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceDto created = workspaceService.createWorkspace(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<List<WorkspaceDto>> getUserWorkspaces(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workspaceService.getUserWorkspaces(currentUser.getId()));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceDto> getWorkspaceById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspaceById(workspaceId, currentUser.getId()));
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<WorkspaceDto> getWorkspaceBySlug(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String slug) {
        return ResponseEntity.ok(workspaceService.getWorkspaceBySlug(slug, currentUser.getId()));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberDto>> getWorkspaceMembers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspaceMembers(workspaceId, currentUser.getId()));
    }
}
