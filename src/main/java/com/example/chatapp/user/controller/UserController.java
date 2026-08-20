package com.example.chatapp.user.controller;

import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.dto.UserSettingsDto;
import com.example.chatapp.user.model.request.UpdateProfileRequest;
import com.example.chatapp.user.model.request.UpdateUserSettingsRequest;
import com.example.chatapp.user.model.request.VerifyEmailRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;
import com.example.chatapp.user.service.AuthModule;
import com.example.chatapp.user.service.PresenceModule;
import com.example.chatapp.user.service.UserService;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthModule authModule;
    private final PresenceModule presenceModule;

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BadRequestException("Keyword cannot be empty or blank");
        }
        return ResponseEntity.ok(userService.searchUsers(currentUser.getId(), keyword, limit));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(currentUser.getId(), request));
    }

    @PostMapping("/profile/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody VerifyEmailRequest request) {
        authModule.verifyEmail(currentUser.getId(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/resend-code")
    public ResponseEntity<Void> resendVerificationCode(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        authModule.resendVerificationCode(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfileById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getId(), userId));
    }

    @GetMapping("/online")
    public ResponseEntity<List<OnlineStatusDto>> getOnlineUsers(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(presenceModule.getOnlineUsers(currentUser.getId()));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsDto> getUserSettings(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(presenceModule.getPrivacySettings(currentUser.getId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsDto> updateUserSettings(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ResponseEntity.ok(presenceModule.togglePresenceSharing(currentUser.getId(), request.getSharePresence()));
    }
}
