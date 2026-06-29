package com.example.chatapp.message.model.dto;

import com.example.chatapp.message.model.entity.MessageType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDto {
    private Long id;

    @Size(max = 5000, message = "content cannot exceed 5000 characters")
    private String content;
    private Long senderId;
    private String senderUsername;
    private String senderFullName;
    private Long recipientId;
    private Instant timestamp;
    private Boolean isRead;
    private Boolean isDeleted;
    private Boolean isDelivered;
    private String channel;
    private MessageType messageType;

}
