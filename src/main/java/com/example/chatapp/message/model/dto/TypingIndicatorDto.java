package com.example.chatapp.message.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorDto {
    @NotNull(message = "recipientId is required")
    private Long recipientId;

    @NotNull(message = "isTyping is required")
    private Boolean isTyping;

    private Long senderId;
    
    public TypingIndicatorDto(Long recipientId, Boolean isTyping) {
        this.recipientId = recipientId;
        this.isTyping = isTyping;
    }
}
