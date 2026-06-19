package com.example.chatapp.user.service;

import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;

import java.util.List;

public interface UserService {
    User createUser(UserSignUpRequest request);
    List<UserDto> searchUsers(Long currentUserId, String keyword);
    UserProfileResponse getUserProfile(Long userId);
    UserProfileResponse updateUserProfile(Long userId, com.example.chatapp.user.model.request.UpdateProfileRequest request);
    void verifyEmail(Long userId, String code);
    void resendVerificationCode(Long userId);
    com.example.chatapp.user.model.dto.UserSettingsDto getUserSettings(Long userId);
    com.example.chatapp.user.model.dto.UserSettingsDto updateUserSettings(Long userId, com.example.chatapp.user.model.request.UpdateUserSettingsRequest request);
}


