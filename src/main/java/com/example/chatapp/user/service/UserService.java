package com.example.chatapp.user.service;

import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.request.UpdateProfileRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;

import java.util.List;

public interface UserService {
    List<UserDto> searchUsers(Long currentUserId, String keyword);
    List<UserDto> searchUsers(Long currentUserId, String keyword, Integer limit);
    UserProfileResponse getUserProfile(Long userId);
    UserProfileResponse getUserProfile(Long viewerUserId, Long profileUserId);
    UserProfileResponse updateUserProfile(Long userId, UpdateProfileRequest request);
}
