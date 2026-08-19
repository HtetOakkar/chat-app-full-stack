package com.example.chatapp.workspace.model.dto;

import com.example.chatapp.workspace.model.entity.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDto {
    private Long id;
    private Long workspaceId;
    private Long conversationId;
    private String name;
    private String slug;
    private String description;
    private ChannelType type;
    private Boolean isArchived;
    private Boolean isMember;
    private Integer memberCount;
    private Instant createdAt;
}
