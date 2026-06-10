package com.example.chatapp.user.controller;

import com.example.chatapp.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @org.springframework.web.bind.annotation.GetMapping("/search")
    public org.springframework.http.ResponseEntity<java.util.List<com.example.chatapp.user.model.dto.UserDto>> searchUsers(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.chatapp.jwt.UserPrincipal currentUser,
            @org.springframework.web.bind.annotation.RequestParam("keyword") String keyword) {
        return org.springframework.http.ResponseEntity.ok(userService.searchUsers(currentUser.getId(), keyword));
    }

    @org.springframework.web.bind.annotation.GetMapping("/profile")
    public org.springframework.http.ResponseEntity<com.example.chatapp.user.model.response.UserProfileResponse> getUserProfile(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.chatapp.jwt.UserPrincipal currentUser) {
        return org.springframework.http.ResponseEntity.ok(userService.getUserProfile(currentUser.getId()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/profile")
    public org.springframework.http.ResponseEntity<com.example.chatapp.user.model.response.UserProfileResponse> updateUserProfile(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.chatapp.jwt.UserPrincipal currentUser,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.example.chatapp.user.model.request.UpdateProfileRequest request) {
        return org.springframework.http.ResponseEntity.ok(userService.updateUserProfile(currentUser.getId(), request));
    }

    @org.springframework.web.bind.annotation.PostMapping("/profile/verify-email")
    public org.springframework.http.ResponseEntity<Void> verifyEmail(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.chatapp.jwt.UserPrincipal currentUser,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.example.chatapp.user.model.request.VerifyEmailRequest request) {
        userService.verifyEmail(currentUser.getId(), request.getCode());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/profile/resend-code")
    public org.springframework.http.ResponseEntity<Void> resendVerificationCode(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.chatapp.jwt.UserPrincipal currentUser) {
        userService.resendVerificationCode(currentUser.getId());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/{userId}/profile")
    public org.springframework.http.ResponseEntity<com.example.chatapp.user.model.response.UserProfileResponse> getUserProfileById(
            @org.springframework.web.bind.annotation.PathVariable("userId") Long userId) {
        return org.springframework.http.ResponseEntity.ok(userService.getUserProfile(userId));
    }
}


