package com.example.chatapp.email.service;

public interface EmailService {
    void sendVerificationEmail(String to, String code);
    void sendWorkspaceInvitation(String to, String workspaceName, String token);
    void sendWorkspaceInvitation(String to, String workspaceName, String inviterName, String token);
    void sendWorkspaceInvitation(String to, String workspaceName, String inviterName, String token, String customMessage);
}
