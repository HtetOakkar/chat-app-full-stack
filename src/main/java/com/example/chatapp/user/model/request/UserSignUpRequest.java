package com.example.chatapp.user.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserSignUpRequest {
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
    private String password;

    @jakarta.validation.constraints.Email(message = "Invalid email format")
    private String email;

    private String fullName;

    private java.time.LocalDate birthDate;
}

