package com.example.chatapp.user.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    @Size(max = 100, message = "Full name must be less than 100 characters")
    private String fullName;

    private LocalDate birthDate;

    @Email(message = "Invalid email format")
    private String email;
}
