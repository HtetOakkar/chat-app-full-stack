package com.example.chatapp.user.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.ConflictException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.jwt.service.JwtService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.request.PublicResendCodeRequest;
import com.example.chatapp.user.model.request.PublicVerifyEmailRequest;
import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.LoginResponse;
import com.example.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    
    private final UserService userService;
    
    private final JwtService jwtService;
    
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expirationMs}")
    private Long jwtExpirationInMs;

    @Override
    public LoginResponse signup(UserSignUpRequest request) {
        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
        if (userOptional.isPresent()) {
            throw new ConflictException("Username is already taken!");
        }
        User user = userService.createUser(request);
        Date expiration = new Date(new Date().getTime() + jwtExpirationInMs);
        String jwt = jwtService.generateTokenFromUser(user);
        return LoginResponse.builder().token(jwt).expiration(expiration).build();
    }

    @Override
    public LoginResponse login(UserLoginRequest request) {
        String identifier = request.getUsername();
        if (identifier.contains("@")) {
            Optional<User> userOpt = userRepository.findByEmail(identifier);
            if (userOpt.isPresent() && !userOpt.get().isEmailVerified()) {
                throw new BadRequestException("Email is not verified. Please verify your email first.");
            }
        }
        return authenticate(identifier, request.getPassword());
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public LoginResponse verifyEmail(PublicVerifyEmailRequest request) {
        User user = findUserByUsernameOrEmail(request.getUsernameOrEmail());
        userService.verifyEmail(user.getId(), request.getCode());
        if (user.getRole() != null) {
            user.getRole().getName();
        }
        Date expiration = new Date(new Date().getTime() + jwtExpirationInMs);
        String jwt = jwtService.generateTokenFromUser(user);
        return LoginResponse.builder().token(jwt).expiration(expiration).build();
    }

    @Override
    public void resendVerificationCode(PublicResendCodeRequest request) {
        User user = findUserByUsernameOrEmail(request.getUsernameOrEmail());
        userService.resendVerificationCode(user.getId());
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
