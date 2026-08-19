package com.example.chatapp.workspace.service;

import com.example.chatapp.workspace.model.dto.InvitationDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;

import com.example.chatapp.workspace.model.dto.BatchInvitationResponse;
import com.example.chatapp.workspace.model.request.BatchInvitationRequest;

import java.util.List;

public interface InvitationService {
    InvitationDto createEmailInvitation(Long workspaceId, Long userId, CreateEmailInvitationRequest request);
    BatchInvitationResponse createBatchInvitations(Long workspaceId, Long userId, BatchInvitationRequest request);
    InvitationDto createJoinLink(Long workspaceId, Long userId, CreateJoinLinkRequest request);
    InvitationDto getOrCreateShareableJoinLink(Long workspaceId, Long userId);
    InvitationDto toggleShareableJoinLink(Long workspaceId, Long userId);
    InvitationDto resetShareableJoinLink(Long workspaceId, Long userId);
    List<InvitationDto> getPendingInvitations(Long workspaceId, Long userId);
    InvitationDto resendInvitation(Long invitationId, Long userId);
    void revokeInvitation(Long invitationId, Long userId);
    InvitationDto getInvitationByToken(String token);
    WorkspaceDto acceptInvitation(String token, Long userId);
}
