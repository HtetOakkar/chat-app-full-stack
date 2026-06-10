package com.example.chatapp.email.service;

public interface EmailService {
    void sendVerificationEmail(String to, String code);
}
