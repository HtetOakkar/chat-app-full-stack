package com.example.chatapp.workspace.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.workspace.model.dto.DirectMessageDto;
import com.example.chatapp.workspace.service.DirectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @PostMapping({"/api/workspaces/{workspaceId}/dm/{targetUserId}", "/api/v1/workspaces/{workspaceId}/dm/{targetUserId}"})
    public ResponseEntity<DirectMessageDto> getOrCreateDirectMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @PathVariable Long targetUserId) {
        DirectMessageDto dm = directMessageService.getOrCreateDirectMessage(workspaceId, currentUser.getId(), targetUserId);
        return ResponseEntity.ok(dm);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/dm", "/api/v1/workspaces/{workspaceId}/dm"})
    public ResponseEntity<List<DirectMessageDto>> getUserDirectMessages(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(directMessageService.getUserDirectMessages(workspaceId, currentUser.getId()));
    }

    @GetMapping({"/api/workspaces/{workspaceId}/dm/{targetUserId}/messages", "/api/v1/workspaces/{workspaceId}/dm/{targetUserId}/messages"})
    public ResponseEntity<MessagePage> getDirectMessageMessages(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @PathVariable Long targetUserId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(directMessageService.getDirectMessageMessages(workspaceId, currentUser.getId(), targetUserId, cursor, limit));
    }

    @PostMapping({"/api/workspaces/{workspaceId}/dm/{targetUserId}/messages", "/api/v1/workspaces/{workspaceId}/dm/{targetUserId}/messages"})
    public ResponseEntity<MessageDto> sendDirectMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @PathVariable Long targetUserId,
            @Valid @RequestBody MessageDto messageDto) {
        MessageDto sent = directMessageService.sendDirectMessage(workspaceId, currentUser.getId(), targetUserId, messageDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sent);
    }
}
