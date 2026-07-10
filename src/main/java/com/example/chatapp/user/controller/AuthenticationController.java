package com.example.chatapp.user.controller;

import com.example.chatapp.user.model.request.PublicResendCodeRequest;
import com.example.chatapp.user.model.request.PublicVerifyEmailRequest;
import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.LoginResponse;
import com.example.chatapp.user.service.AuthModule;
import com.example.chatapp.user.service.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthModule authModule;
    private final AuthRateLimiter authRateLimiter;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody UserLoginRequest request,
                               HttpServletRequest servletRequest) {
        authRateLimiter.checkLogin(request, servletRequest);
        return authModule.login(request);
    }

    @PostMapping("/signup")
    public LoginResponse signup(@Valid @RequestBody UserSignUpRequest request,
                                HttpServletRequest servletRequest) {
        authRateLimiter.checkSignup(request, servletRequest);
        return authModule.signup(request);
    }

    @PostMapping("/verify-email")
    public LoginResponse verifyEmail(@Valid @RequestBody PublicVerifyEmailRequest request,
                                     HttpServletRequest servletRequest) {
        authRateLimiter.checkVerifyEmail(request, servletRequest);
        return authModule.verifyEmail(request);
    }

    @PostMapping("/resend-code")
    public void resendCode(@Valid @RequestBody PublicResendCodeRequest request,
                           HttpServletRequest servletRequest) {
        authRateLimiter.checkResendCode(request, servletRequest);
        authModule.resendVerificationCode(request);
    }
}
