package com.example.chatapp.workspace.model.dto;

import com.example.chatapp.message.model.dto.MessageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageDto {
    private Long conversationId;
    private Long workspaceId;
    private WorkspaceMemberDto targetMember;
    private MessageDto lastMessage;
    private Integer unreadCount;
    private Instant createdAt;
    private Instant updatedAt;
}
