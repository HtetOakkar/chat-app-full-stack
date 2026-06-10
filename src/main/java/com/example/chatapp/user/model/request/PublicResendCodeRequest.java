package com.example.chatapp.user.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicResendCodeRequest {
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;
}
