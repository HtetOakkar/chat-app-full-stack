package com.example.chatapp.workspace.service;

import com.example.chatapp.workspace.model.dto.InvitationDto;
import com.example.chatapp.workspace.model.dto.WorkspaceDto;
import com.example.chatapp.workspace.model.request.CreateEmailInvitationRequest;
import com.example.chatapp.workspace.model.request.CreateJoinLinkRequest;

public interface InvitationService {
    InvitationDto createEmailInvitation(Long workspaceId, Long userId, CreateEmailInvitationRequest request);
    InvitationDto createJoinLink(Long workspaceId, Long userId, CreateJoinLinkRequest request);
    InvitationDto getInvitationByToken(String token);
    WorkspaceDto acceptInvitation(String token, Long userId);
}
