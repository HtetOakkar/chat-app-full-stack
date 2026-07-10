package com.example.chatapp.user.service;

import com.example.chatapp.exception.RateLimitExceededException;
import com.example.chatapp.user.model.request.PublicResendCodeRequest;
import com.example.chatapp.user.model.request.PublicVerifyEmailRequest;
import com.example.chatapp.user.model.request.UserLoginRequest;
import com.example.chatapp.user.model.request.UserSignUpRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int loginMaxAttempts;
    private final int signupMaxAttempts;
    private final int verifyEmailMaxAttempts;
    private final int resendCodeMaxAttempts;
    private final Duration window;

    public AuthRateLimiter(
            @Value("${auth.rate-limit.login.max-attempts:10}") int loginMaxAttempts,
            @Value("${auth.rate-limit.signup.max-attempts:30}") int signupMaxAttempts,
            @Value("${auth.rate-limit.verify-email.max-attempts:10}") int verifyEmailMaxAttempts,
            @Value("${auth.rate-limit.resend-code.max-attempts:5}") int resendCodeMaxAttempts,
            @Value("${auth.rate-limit.window-seconds:900}") long windowSeconds) {
        this.loginMaxAttempts = loginMaxAttempts;
        this.signupMaxAttempts = signupMaxAttempts;
        this.verifyEmailMaxAttempts = verifyEmailMaxAttempts;
        this.resendCodeMaxAttempts = resendCodeMaxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void checkLogin(UserLoginRequest request, HttpServletRequest servletRequest) {
        check("login:" + sourceIp(servletRequest) + ":" + normalize(request.getUsername()), loginMaxAttempts);
    }

    public void checkSignup(UserSignUpRequest request, HttpServletRequest servletRequest) {
        check("signup:" + sourceIp(servletRequest), signupMaxAttempts);
    }

    public void checkVerifyEmail(PublicVerifyEmailRequest request, HttpServletRequest servletRequest) {
        check("verify:" + sourceIp(servletRequest) + ":" + normalize(request.getUsernameOrEmail()), verifyEmailMaxAttempts);
    }

    public void checkResendCode(PublicResendCodeRequest request, HttpServletRequest servletRequest) {
        check("resend:" + sourceIp(servletRequest) + ":" + normalize(request.getUsernameOrEmail()), resendCodeMaxAttempts);
    }

    private synchronized void check(String key, int maxAttempts) {
        Instant now = Instant.now();
        Bucket bucket = buckets.get(key);
        if (bucket == null || !bucket.resetAt().isAfter(now)) {
            buckets.put(key, new Bucket(1, now.plus(window)));
            return;
        }

        int attempts = bucket.attempts() + 1;
        buckets.put(key, new Bucket(attempts, bucket.resetAt()));
        if (attempts > maxAttempts) {
            throw new RateLimitExceededException("Too many requests. Please try again later.");
        }
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Bucket(int attempts, Instant resetAt) {
    }
}
