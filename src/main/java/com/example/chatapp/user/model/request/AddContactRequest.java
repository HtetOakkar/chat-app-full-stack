package com.example.chatapp.user.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddContactRequest {
    @NotBlank(message = "Username is required")
    private String username;
}
