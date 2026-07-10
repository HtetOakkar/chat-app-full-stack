package com.example.chatapp.user.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private String username;
    private String email;
    private String fullName;
    private LocalDate birthDate;
    @JsonProperty("emailVerified")
    private Boolean emailVerified;

    @JsonIgnore
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }
}
