package com.example.chatapp.user.service;

import com.example.chatapp.user.model.request.PublicResendCodeRequest;
import com.example.chatapp.user.model.request.PublicVerifyEmailRequest;
import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.LoginResponse;

public interface AuthModule {
    LoginResponse signup(UserSignUpRequest request);
    LoginResponse login(UserLoginRequest request);
    LoginResponse verifyEmail(PublicVerifyEmailRequest request);
    void verifyEmail(Long userId, String code);
    void resendVerificationCode(PublicResendCodeRequest request);
    void resendVerificationCode(Long userId);
    void changePassword(Long userId, String oldPassword, String newPassword);
}
