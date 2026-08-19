package com.example.chatapp.workspace.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.workspace.model.dto.ChannelDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.request.AddChannelMemberRequest;
import com.example.chatapp.workspace.model.request.CreateChannelRequest;
import com.example.chatapp.workspace.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping({"/api/workspaces/{workspaceId}/channels", "/api/v1/workspaces/{workspaceId}/channels"})
    public ResponseEntity<ChannelDto> createChannel(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateChannelRequest request) {
        ChannelDto created = channelService.createChannel(workspaceId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/channels", "/api/v1/workspaces/{workspaceId}/channels"})
    public ResponseEntity<List<ChannelDto>> getWorkspaceChannels(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(channelService.getWorkspaceChannels(workspaceId, currentUser.getId()));
    }

    @GetMapping({"/api/channels/{channelId}", "/api/v1/channels/{channelId}"})
    public ResponseEntity<ChannelDto> getChannelById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId) {
        return ResponseEntity.ok(channelService.getChannelById(channelId, currentUser.getId()));
    }

    @PostMapping({"/api/channels/{channelId}/members", "/api/v1/channels/{channelId}/members"})
    public ResponseEntity<Void> addChannelMember(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId,
            @Valid @RequestBody AddChannelMemberRequest request) {
        channelService.addChannelMember(channelId, currentUser.getId(), request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping({"/api/channels/{channelId}/members/{userId}", "/api/v1/channels/{channelId}/members/{userId}"})
    public ResponseEntity<Void> removeChannelMember(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId,
            @PathVariable Long userId) {
        channelService.removeChannelMember(channelId, currentUser.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/api/channels/{channelId}/members", "/api/v1/channels/{channelId}/members"})
    public ResponseEntity<List<WorkspaceMemberDto>> getChannelMembers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId) {
        return ResponseEntity.ok(channelService.getChannelMembers(channelId, currentUser.getId()));
    }

    @GetMapping({"/api/channels/{channelId}/messages", "/api/v1/channels/{channelId}/messages"})
    public ResponseEntity<MessagePage> getChannelMessages(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(channelService.getChannelMessages(channelId, currentUser.getId(), cursor, limit));
    }

    @PostMapping({"/api/channels/{channelId}/messages", "/api/v1/channels/{channelId}/messages"})
    public ResponseEntity<MessageDto> sendChannelMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long channelId,
            @Valid @RequestBody MessageDto messageDto) {
        MessageDto sent = channelService.sendChannelMessage(channelId, currentUser.getId(), messageDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sent);
    }
}
