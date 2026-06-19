package com.example.chatapp.user.service;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.UserProfileResponse;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.chatapp.email.service.EmailService;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final RoleRepository roleRepository;
    
    private final EmailService emailService;

    private String generateVerificationCode() {
        return String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
    }

    @Override
    public User createUser(UserSignUpRequest request) {
        Role role = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new NotFoundException("Default role not found"));
        User.UserBuilder userBuilder = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .fullName(request.getFullName())
                .birthDate(request.getBirthDate());

        if (request.getEmail() != null) {
            userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
                throw new com.example.chatapp.exception.BadRequestException("Email is already registered!");
            });

            String code = generateVerificationCode();
            userBuilder.email(request.getEmail())
                    .emailVerified(false)
                    .emailVerificationCode(code)
                    .emailVerificationExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(15)))
                    .verificationAttempts(0)
                    .lastCodeRequestedAt(java.time.Instant.now());
            System.out.println("=== EMAIL VERIFICATION CODE FOR SIGNUP " + request.getUsername() + ": " + code + " ===");
            emailService.sendVerificationEmail(request.getEmail(), code);
        }


        User user = userBuilder.build();
        
        com.example.chatapp.user.model.entity.UserSettings settings = com.example.chatapp.user.model.entity.UserSettings.builder()
                .user(user)
                .sharePresence(true)
                .build();
        user.setSettings(settings);

        return userRepository.saveAndFlush(user);
    }

    @Override
    public java.util.List<com.example.chatapp.user.model.dto.UserDto> searchUsers(Long currentUserId, String keyword) {
        return userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(keyword, keyword)
                .stream()
                .filter(user -> !user.getId().equals(currentUserId))
                .map(user -> com.example.chatapp.user.model.dto.UserDto.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        // Omit password for security
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return com.example.chatapp.user.model.response.UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthDate(user.getBirthDate())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, com.example.chatapp.user.model.request.UpdateProfileRequest request) {
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
            emailService.sendVerificationEmail(user.getEmail(), code);
        }

        
        userRepository.save(user);
        
        return com.example.chatapp.user.model.response.UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthDate(user.getBirthDate())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = com.example.chatapp.exception.BadRequestException.class)
    public void verifyEmail(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (user.getEmailVerificationCode() == null) {
            throw new com.example.chatapp.exception.BadRequestException("No active verification code found.");
        }
        
        if (user.getEmailVerificationExpiresAt().isBefore(java.time.Instant.now())) {
            throw new com.example.chatapp.exception.BadRequestException("Verification code has expired.");
        }
        
        if (!user.getEmailVerificationCode().equals(code)) {
            int attempts = user.getVerificationAttempts() + 1;
            user.setVerificationAttempts(attempts);
            if (attempts >= 3) {
                user.setEmailVerificationCode(null);
                user.setEmailVerificationExpiresAt(null);
                user.setVerificationAttempts(0);
                userRepository.save(user);
                throw new com.example.chatapp.exception.BadRequestException("Verification failed. Too many failed attempts. Please request a new code.");
            }
            userRepository.save(user);
            throw new com.example.chatapp.exception.BadRequestException("Invalid verification code.");
        }
        
        user.setEmailVerified(true);
        user.setEmailVerificationCode(null);
        user.setEmailVerificationExpiresAt(null);
        user.setVerificationAttempts(0);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerificationCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (user.getEmail() == null) {
            throw new com.example.chatapp.exception.BadRequestException("No email associated with this profile.");
        }
        
        java.time.Instant now = java.time.Instant.now();
        if (user.getLastCodeRequestedAt() != null) {
            java.time.Duration duration = java.time.Duration.between(user.getLastCodeRequestedAt(), now);
            if (duration.getSeconds() < 60) {
                throw new com.example.chatapp.exception.BadRequestException("Please wait " + (60 - duration.getSeconds()) + " seconds before requesting a new code.");
            }
        }
        
        String code = generateVerificationCode();
        user.setEmailVerificationCode(code);
        user.setEmailVerificationExpiresAt(now.plus(java.time.Duration.ofMinutes(15)));
        user.setVerificationAttempts(0);
        user.setLastCodeRequestedAt(now);
        userRepository.save(user);
        
        System.out.println("=== EMAIL VERIFICATION CODE RESEND " + user.getUsername() + ": " + code + " ===");
        emailService.sendVerificationEmail(user.getEmail(), code);
    }

    @Override
    public com.example.chatapp.user.model.dto.UserSettingsDto getUserSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        return com.example.chatapp.user.model.dto.UserSettingsDto.builder()
                .sharePresence(user.getSettings() != null ? user.getSettings().isSharePresence() : true)
                .build();
    }

    @Override
    @Transactional
    public com.example.chatapp.user.model.dto.UserSettingsDto updateUserSettings(Long userId, com.example.chatapp.user.model.request.UpdateUserSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        com.example.chatapp.user.model.entity.UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = com.example.chatapp.user.model.entity.UserSettings.builder()
                    .user(user)
                    .sharePresence(request.getSharePresence())
                    .build();
            user.setSettings(settings);
        } else {
            settings.setSharePresence(request.getSharePresence());
        }
        
        userRepository.save(user);
        
        return com.example.chatapp.user.model.dto.UserSettingsDto.builder()
                .sharePresence(settings.isSharePresence())
                .build();
    }
}

