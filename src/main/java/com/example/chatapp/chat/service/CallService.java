package com.example.chatapp.chat.service;

import com.example.chatapp.chat.model.CallSession;

public interface CallService {
    void handleUserDisconnect(Long userId);
    void saveCallRecord(CallSession session, String outcome, long duration);
}
