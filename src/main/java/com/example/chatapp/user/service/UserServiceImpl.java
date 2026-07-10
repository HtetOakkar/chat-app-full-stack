package com.example.chatapp.user.service;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.UpdateProfileRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(Long currentUserId, String keyword) {
        return searchUsers(currentUserId, keyword, DEFAULT_SEARCH_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(Long currentUserId, String keyword, Integer limit) {
        int boundedLimit = limit == null ? DEFAULT_SEARCH_LIMIT : Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        return userRepository.searchUsersExcludingCurrent(currentUserId, keyword, PageRequest.of(0, boundedLimit))
                .stream()
                .map(user -> UserDto.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return privateProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long viewerUserId, Long profileUserId) {
        User user = userRepository.findById(profileUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (profileUserId.equals(viewerUserId)) {
            return privateProfile(user);
        }
        return publicProfile(user);
    }

    private UserProfileResponse privateProfile(User user) {
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthDate(user.getBirthDate())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    private UserProfileResponse publicProfile(User user) {
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .fullName(user.getFullName())
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        user.setFullName(request.getFullName());
        user.setBirthDate(request.getBirthDate());
        
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // Check email uniqueness if email is changing
            userRepository.findByEmail(request.getEmail()).ifPresent(otherUser -> {
                if (!otherUser.getId().equals(userId)) {
                    throw new com.example.chatapp.exception.ConflictException("Email is already registered!");
                }
            });
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);

            String code = generateVerificationCode();
            user.setEmailVerificationCode(code);
            user.setEmailVerificationExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(15)));
            user.setVerificationAttempts(0);
            user.setLastCodeRequestedAt(java.time.Instant.now());
            log.info("Verification email requested for profile update userId={} username={}", user.getId(), user.getUsername());
            try {
                emailService.sendVerificationEmail(user.getEmail(), code);
            } catch (Exception e) {
                log.error("Failed to send verification email for profile update userId={}", user.getId(), e);
            }
        }

        userRepository.save(user);
        
        return privateProfile(user);
    }

    private String generateVerificationCode() {
        return String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
    }
}
