package com.example.chatapp.user.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.ConflictException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.jwt.service.JwtService;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.PublicResendCodeRequest;
import com.example.chatapp.user.model.request.PublicVerifyEmailRequest;
import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.LoginResponse;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthModuleImpl implements AuthModule {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expirationMs}")
    private Long jwtExpirationInMs;

    private String generateVerificationCode() {
        return String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
    }

    @Override
    @Transactional
    public LoginResponse signup(UserSignUpRequest request) {
        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
        if (userOptional.isPresent()) {
            throw new ConflictException("Username is already taken!");
        }

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
                throw new BadRequestException("Email is already registered!");
            });

            String code = generateVerificationCode();
            userBuilder.email(request.getEmail())
                    .emailVerified(false)
                    .emailVerificationCode(code)
                    .emailVerificationExpiresAt(Instant.now().plus(java.time.Duration.ofMinutes(15)))
                    .verificationAttempts(0)
                    .lastCodeRequestedAt(Instant.now());
            log.info("Verification email requested for signup username={}", request.getUsername());
            try {
                emailService.sendVerificationEmail(request.getEmail(), code);
            } catch (Exception e) {
                log.error("Failed to send verification email for signup", e);
            }
        }

        User user = userBuilder.build();
        
        com.example.chatapp.user.model.entity.UserSettings settings = com.example.chatapp.user.model.entity.UserSettings.builder()
                .user(user)
                .sharePresence(true)
                .build();
        user.setSettings(settings);

        User savedUser = userRepository.saveAndFlush(user);

        Date expiration = new Date(new Date().getTime() + jwtExpirationInMs);
        String jwt = jwtService.generateTokenFromUser(savedUser);
        return LoginResponse.builder().token(jwt).expiration(expiration).build();
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(UserLoginRequest request) {
        String identifier = request.getUsername();
        if ("system".equalsIgnoreCase(identifier)) {
            throw new UnauthorizedException("Invalid username or password");
        }

        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);

        if (userOpt.isEmpty()) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (userOpt.isPresent() && userOpt.get().getEmail() != null && !userOpt.get().isEmailVerified()) {
            throw new BadRequestException("Email is not verified. Please verify your email first.");
        }
        return authenticate(identifier, request.getPassword());
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public LoginResponse verifyEmail(PublicVerifyEmailRequest request) {
        User user = findUserByUsernameOrEmail(request.getUsernameOrEmail());
        verifyEmail(user.getId(), request.getCode());
        if (user.getRole() != null) {
            user.getRole().getName();
        }
        Date expiration = new Date(new Date().getTime() + jwtExpirationInMs);
        String jwt = jwtService.generateTokenFromUser(user);
        return LoginResponse.builder().token(jwt).expiration(expiration).build();
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyEmail(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (user.getEmailVerificationCode() == null) {
            throw new BadRequestException("No active verification code found.");
        }
        
        if (user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Verification code has expired.");
        }
        
        if (!user.getEmailVerificationCode().equals(code)) {
            int attempts = user.getVerificationAttempts() + 1;
            user.setVerificationAttempts(attempts);
            if (attempts >= 3) {
                user.setEmailVerificationCode(null);
                user.setEmailVerificationExpiresAt(null);
                user.setVerificationAttempts(0);
                userRepository.save(user);
                throw new BadRequestException("Verification failed. Too many failed attempts. Please request a new code.");
            }
            userRepository.save(user);
            throw new BadRequestException("Invalid verification code.");
        }
        
        user.setEmailVerified(true);
        user.setEmailVerificationCode(null);
        user.setEmailVerificationExpiresAt(null);
        user.setVerificationAttempts(0);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerificationCode(PublicResendCodeRequest request) {
        User user = findUserByUsernameOrEmail(request.getUsernameOrEmail());
        resendVerificationCode(user.getId());
    }

    @Override
    @Transactional
    public void resendVerificationCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (user.getEmail() == null) {
            throw new BadRequestException("No email associated with this profile.");
        }
        
        Instant now = Instant.now();
        if (user.getLastCodeRequestedAt() != null) {
            java.time.Duration duration = java.time.Duration.between(user.getLastCodeRequestedAt(), now);
            if (duration.getSeconds() < 60) {
                throw new BadRequestException("Please wait " + (60 - duration.getSeconds()) + " seconds before requesting a new code.");
            }
        }
        
        String code = generateVerificationCode();
        user.setEmailVerificationCode(code);
        user.setEmailVerificationExpiresAt(now.plus(java.time.Duration.ofMinutes(15)));
        user.setVerificationAttempts(0);
        user.setLastCodeRequestedAt(now);
        userRepository.save(user);
        
        log.info("Verification email resend requested for userId={} username={}", user.getId(), user.getUsername());
        emailService.sendVerificationEmail(user.getEmail(), code);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadRequestException("Invalid old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findUserByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail.contains("@")) {
            return userRepository.findByEmail(usernameOrEmail)
                    .orElseThrow(() -> new NotFoundException("User not found with email: " + usernameOrEmail));
        } else {
            return userRepository.findByUsername(usernameOrEmail)
                    .orElseThrow(() -> new NotFoundException("User not found with username: " + usernameOrEmail));
        }
    }

    private LoginResponse authenticate(String username, String password) {
        Date expiration = new Date(new Date().getTime() + jwtExpirationInMs);
        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtService.generateToken(authentication);
            return LoginResponse.builder().token(jwt).expiration(expiration).build();
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid username or password");
        }
    }
}
