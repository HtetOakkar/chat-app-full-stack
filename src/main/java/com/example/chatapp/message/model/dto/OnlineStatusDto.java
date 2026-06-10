package com.example.chatapp.message.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OnlineStatusDto {
    @NotBlank(message = "status is required")
    private String status;
    private String userId;
    private String username;
}
