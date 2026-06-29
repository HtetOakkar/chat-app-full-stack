package com.example.chatapp.user.model.dto;

import com.example.chatapp.user.model.entity.ContactStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDto {
    private Long id;
    private Long contactUserId;
    private String contactUsername;
    private String contactFullName;
    private ContactStatus status;
    private Instant createdAt;
    private String lastMessageContent;
    private Instant lastMessageTimestamp;
    private Long lastMessageSenderId;
    private Long unreadCount;
    private Instant clearedAt;
}
