package com.example.chatapp.chat.service;

import com.example.chatapp.chat.model.CallSession;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CallRegistry {
    private final Map<Long, CallSession> activeCalls = new ConcurrentHashMap<>();

    public synchronized Optional<CallSession> registerCall(Long callerId, Long calleeId, String callType) {
        if (isBusy(callerId) || isBusy(calleeId)) {
            return Optional.empty();
        }
        CallSession session = CallSession.builder()
                .callerId(callerId)
                .calleeId(calleeId)
                .callType(callType)
                .startedAt(java.time.Instant.now())
                .status("dialing")
                .build();
        activeCalls.put(callerId, session);
        activeCalls.put(calleeId, session);
        return Optional.of(session);
    }

    public synchronized void unregisterCall(CallSession session) {
        if (session != null) {
            activeCalls.remove(session.getCallerId());
            activeCalls.remove(session.getCalleeId());
        }
    }

    public synchronized Optional<CallSession> getActiveCall(Long userId) {
        return Optional.ofNullable(activeCalls.get(userId));
    }

    public boolean isBusy(Long userId) {
        return activeCalls.containsKey(userId);
    }

    public synchronized boolean isExistingCall(Long userA, Long userB) {
        CallSession sessionA = activeCalls.get(userA);
        if (sessionA != null) {
            return (sessionA.getCallerId().equals(userA) && sessionA.getCalleeId().equals(userB)) ||
                   (sessionA.getCallerId().equals(userB) && sessionA.getCalleeId().equals(userA));
        }
        return false;
    }

    public synchronized void updateCallType(Long userId, String newCallType) {
        CallSession session = activeCalls.get(userId);
        if (session != null && newCallType != null) {
            session.setCallType(newCallType);
        }
    }
}

