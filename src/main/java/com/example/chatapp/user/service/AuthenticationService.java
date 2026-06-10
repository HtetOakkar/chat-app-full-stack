package com.example.chatapp.user.service;

import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import com.example.chatapp.user.model.response.LoginResponse;

public interface AuthenticationService {
    LoginResponse signup(UserSignUpRequest request);

    LoginResponse login(UserLoginRequest request);

    LoginResponse verifyEmail(com.example.chatapp.user.model.request.PublicVerifyEmailRequest request);

    void resendVerificationCode(com.example.chatapp.user.model.request.PublicResendCodeRequest request);
}
