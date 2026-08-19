package com.example.chatapp.chat.service;

import com.example.chatapp.chat.model.CallSession;
import com.example.chatapp.message.model.dto.CallSignalDto;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallServiceImpl implements CallService {

    private final CallRegistry callRegistry;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final MessageBroker messageBroker;

    @Override
    public void handleUserDisconnect(Long userId) {
        if (userId == null) return;

        Optional<CallSession> sessionOpt = callRegistry.getActiveCall(userId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        CallSession session = sessionOpt.get();
        callRegistry.unregisterCall(session);

        Long peerId = session.getCallerId().equals(userId) ? session.getCalleeId() : session.getCallerId();

        long duration = 0;
        String outcome = "completed";
        String signalType = "hangup";

        if ("active".equalsIgnoreCase(session.getStatus()) && session.getStartedAt() != null) {
            duration = Duration.between(session.getStartedAt(), Instant.now()).toSeconds();
            outcome = "completed";
            signalType = "hangup";
        } else {
            outcome = "cancelled";
            signalType = "cancel";
        }

        saveCallRecord(session, outcome, duration);

        User disconnectedUser = userRepository.findById(userId).orElse(null);
        CallSignalDto peerSignal = CallSignalDto.builder()
                .senderId(userId)
                .senderUsername(disconnectedUser != null ? disconnectedUser.getUsername() : null)
                .senderFullName(disconnectedUser != null ? disconnectedUser.getFullName() : null)
                .recipientId(peerId)
                .type(signalType)
                .callType(session.getCallType())
                .build();

        messageBroker.publishToUser(peerId.toString(), "/queue/call", peerSignal);
        log.info("Cleaned up call session for user {} on disconnect. Peer {} notified with signal {}.", userId, peerId, signalType);
    }

    @Override
    public void saveCallRecord(CallSession session, String outcome, long duration) {
        User caller = userRepository.findById(session.getCallerId()).orElse(null);
        User callee = userRepository.findById(session.getCalleeId()).orElse(null);
        if (caller == null || callee == null) return;

        boolean isVideo = "VIDEO".equalsIgnoreCase(session.getCallType());
        MessageType messageType = isVideo ? MessageType.VIDEO : MessageType.AUDIO;

        String content = formatCallLabel(outcome);

        MessageDto messageDto = MessageDto.builder()
                .content(content)
                .senderId(session.getCallerId())
                .senderUsername(caller.getUsername())
                .senderFullName(caller.getFullName())
                .recipientId(session.getCalleeId())
                .messageType(messageType)
                .timestamp(Instant.now())
                .isRead(false)
                .isDeleted(false)
                .isDelivered(true)
                .callOutcome(outcome)
                .callDuration(duration)
                .videoUsed(isVideo)
                .build();

        messageService.saveMessage(messageDto);

        // Notify both parties of the new call record message
        messageBroker.publishToUser(session.getCallerId().toString(), "/queue/messages", messageDto);
        messageBroker.publishToUser(session.getCalleeId().toString(), "/queue/messages", messageDto);

        if (session.getWorkspaceId() != null) {
            String wsQueue = String.format("/queue/workspace.%d.messages", session.getWorkspaceId());
            messageBroker.publishToUser(session.getCallerId().toString(), wsQueue, messageDto);
            messageBroker.publishToUser(session.getCalleeId().toString(), wsQueue, messageDto);
        }
    }

    private String formatCallLabel(String outcome) {
        if (outcome == null) return "Call";
        return switch (outcome.toLowerCase()) {
            case "completed" -> "Call Ended";
            case "missed" -> "Missed Call";
            case "rejected" -> "Call Declined";
            case "cancelled" -> "Call Cancelled";
            default -> "Call";
        };
    }
}
