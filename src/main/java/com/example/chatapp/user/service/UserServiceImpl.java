package com.example.chatapp.user.service;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.UpdateProfileRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(Long currentUserId, String keyword) {
        return userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(keyword, keyword)
                .stream()
                .filter(user -> !user.getId().equals(currentUserId))
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
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthDate(user.getBirthDate())
                .emailVerified(user.isEmailVerified())
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
            System.out.println("=== EMAIL VERIFICATION CODE FOR UPDATE " + user.getUsername() + ": " + code + " ===");
            try {
                emailService.sendVerificationEmail(user.getEmail(), code);
            } catch (Exception e) {
                System.err.println("Failed to send verification email for profile update: " + e.getMessage());
            }
        }

        userRepository.save(user);
        
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthDate(user.getBirthDate())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    private String generateVerificationCode() {
        return String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
    }
}
