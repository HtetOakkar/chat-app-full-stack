package com.example.chatapp.chat.controller;

import com.example.chatapp.chat.model.CallSession;
import com.example.chatapp.chat.service.CallRegistry;
import com.example.chatapp.chat.service.CallService;
import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.CallSignalDto;
import com.example.chatapp.websocket.MessageBroker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final MessageBroker messageBroker;
    private final CallRegistry callRegistry;
    private final CallService callService;

    @MessageMapping({"/call.offer", "/workspace.{workspaceId}.call.offer"})
    public void processOffer(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call offer");
        }

        // Check if this is an in-call renegotiation/upgrade between the two users
        if (callRegistry.isExistingCall(senderId, recipientId)) {
            log.info("In-call renegotiation offer from {} to {} with callType {}", senderId, recipientId, signalDto.getCallType());
            callRegistry.updateCallType(senderId, signalDto.getCallType());
            signalDto.setType("offer");
            enrichSenderInfo(signalDto, authenticatedUser);
            publishCallSignal(recipientId, signalDto);
            return;
        }

        // Check if either is busy
        if (callRegistry.isBusy(senderId) || callRegistry.isBusy(recipientId)) {
            log.info("Call busy: sender {} or recipient {} is busy", senderId, recipientId);
            CallSignalDto busySignal = CallSignalDto.builder()
                    .workspaceId(signalDto.getWorkspaceId())
                    .senderId(recipientId)
                    .recipientId(senderId)
                    .type("busy")
                    .build();
            publishCallSignal(senderId, busySignal);
            return;
        }

        // Register call
        callRegistry.registerCall(senderId, recipientId, signalDto.getCallType());

        // Forward offer
        signalDto.setType("offer");
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    @MessageMapping({"/call.answer", "/workspace.{workspaceId}.call.answer"})
    public void processAnswer(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call answer");
        }

        Optional<CallSession> sessionOpt = getAuthorizedActiveCall(senderId, recipientId, "answer");
        if (sessionOpt.isEmpty()) return;

        CallSession session = sessionOpt.get();
        session.setStatus("active");
        session.setStartedAt(Instant.now());

        signalDto.setType("answer");
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    @MessageMapping({"/call.ice", "/workspace.{workspaceId}.call.ice"})
    public void processIceCandidate(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call ice");
        }

        if (getAuthorizedActiveCall(senderId, recipientId, "ice").isEmpty()) return;

        signalDto.setType("ice");
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    @MessageMapping({"/call.cancel", "/workspace.{workspaceId}.call.cancel"})
    public void processCancel(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call cancel");
        }

        Optional<CallSession> sessionOpt = getAuthorizedActiveCall(senderId, recipientId, "cancel");
        if (sessionOpt.isEmpty()) return;

        CallSession session = sessionOpt.get();
        callRegistry.unregisterCall(session);
        String outcome = "missed".equalsIgnoreCase(signalDto.getType()) ? "missed" : "cancelled";
        callService.saveCallRecord(session, outcome, 0);

        if (signalDto.getType() == null) {
            signalDto.setType("cancel");
        }
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    @MessageMapping({"/call.reject", "/workspace.{workspaceId}.call.reject"})
    public void processReject(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call reject");
        }

        Optional<CallSession> sessionOpt = getAuthorizedActiveCall(senderId, recipientId, "reject");
        if (sessionOpt.isEmpty()) return;

        CallSession session = sessionOpt.get();
        callRegistry.unregisterCall(session);
        callService.saveCallRecord(session, "rejected", 0);

        signalDto.setType("reject");
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    @MessageMapping({"/call.hangup", "/workspace.{workspaceId}.call.hangup"})
    public void processHangup(@Valid @Payload CallSignalDto signalDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = signalDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for call hangup");
        }

        Optional<CallSession> sessionOpt = getAuthorizedActiveCall(senderId, recipientId, "hangup");
        if (sessionOpt.isEmpty()) return;

        CallSession session = sessionOpt.get();
        callRegistry.unregisterCall(session);

        long duration = 0;
        String outcome = "completed";
        if ("active".equals(session.getStatus()) && session.getStartedAt() != null) {
            duration = java.time.Duration.between(session.getStartedAt(), Instant.now()).toSeconds();
        } else {
            outcome = "cancelled";
        }
        callService.saveCallRecord(session, outcome, duration);

        signalDto.setType("hangup");
        enrichSenderInfo(signalDto, authenticatedUser);
        publishCallSignal(recipientId, signalDto);
    }

    private void publishCallSignal(Long recipientId, CallSignalDto signalDto) {
        if (signalDto.getWorkspaceId() != null) {
            String wsQueue = String.format("/queue/workspace.%d.calls", signalDto.getWorkspaceId());
            messageBroker.publishToUser(recipientId.toString(), wsQueue, signalDto);
        }
        messageBroker.publishToUser(recipientId.toString(), "/queue/call", signalDto);
    }

    private Optional<CallSession> getAuthorizedActiveCall(Long senderId, Long recipientId, String signalType) {
        Optional<CallSession> sessionOpt = callRegistry.getActiveCall(senderId);
        if (sessionOpt.isEmpty()) {
            log.warn("Call {} ignored: sender {} is not in an active call", signalType, senderId);
            return Optional.empty();
        }

        CallSession session = sessionOpt.get();
        boolean senderIsCaller = senderId.equals(session.getCallerId()) && recipientId.equals(session.getCalleeId());
        boolean senderIsCallee = senderId.equals(session.getCalleeId()) && recipientId.equals(session.getCallerId());
        if (!senderIsCaller && !senderIsCallee) {
            log.warn("Call {} ignored: sender {} and recipient {} do not match active call participants", signalType, senderId, recipientId);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    private UserPrincipal getAuthenticatedUser(Principal principal) {
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BadRequestException("Authenticated user not found");
        }
        return userPrincipal;
    }

    private void enrichSenderInfo(CallSignalDto signalDto, UserPrincipal user) {
        signalDto.setSenderId(user.getId());
        signalDto.setSenderUsername(user.getUsername());
        signalDto.setSenderFullName(user.getFullName());
    }
}
