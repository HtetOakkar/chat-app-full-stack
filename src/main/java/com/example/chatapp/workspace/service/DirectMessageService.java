package com.example.chatapp.workspace.service;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.workspace.model.dto.DirectMessageDto;

import java.util.List;

public interface DirectMessageService {
    DirectMessageDto getOrCreateDirectMessage(Long workspaceId, Long userId, Long targetUserId);
    List<DirectMessageDto> getUserDirectMessages(Long workspaceId, Long userId);
    MessagePage getDirectMessageMessages(Long workspaceId, Long userId, Long targetUserId, String cursor, int limit);
    MessageDto sendDirectMessage(Long workspaceId, Long senderId, Long targetUserId, MessageDto messageDto);
}
