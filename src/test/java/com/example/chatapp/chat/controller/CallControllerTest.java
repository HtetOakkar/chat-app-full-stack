package com.example.chatapp.chat.controller;

import com.example.chatapp.chat.service.CallRegistry;
import com.example.chatapp.chat.service.CallService;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.CallSignalDto;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.websocket.MessageBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class CallControllerTest {

    private MessageBroker messageBroker;
    private CallService callService;
    private CallRegistry callRegistry;
    private CallController callController;

    @BeforeEach
    void setUp() {
        messageBroker = mock(MessageBroker.class);
        callService = mock(CallService.class);
        callRegistry = new CallRegistry();
        callController = new CallController(
                messageBroker,
                callRegistry,
                mock(ContactRepository.class),
                callService
        );
    }

    @Test
    void followUpSignalsFromThirdUserAreDropped() {
        callRegistry.registerCall(1L, 2L, "VIDEO");
        UserPrincipal thirdUser = new UserPrincipal(3L, "charlie", "password", List.of());

        sendFollowUp("answer", thirdUser, 2L);
        sendFollowUp("ice", thirdUser, 2L);
        sendFollowUp("cancel", thirdUser, 2L);
        sendFollowUp("reject", thirdUser, 2L);
        sendFollowUp("hangup", thirdUser, 2L);

        verify(messageBroker, never()).publishToUser(eq("2"), eq("/queue/call"), any());
        verify(callService, never()).saveCallRecord(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void answerFromActiveParticipantIsForwarded() {
        callRegistry.registerCall(1L, 2L, "VIDEO");
        UserPrincipal callee = new UserPrincipal(2L, "bob", "password", List.of());

        sendFollowUp("answer", callee, 1L);

        verify(messageBroker).publishToUser(eq("1"), eq("/queue/call"), argThat(signal -> {
            CallSignalDto dto = (CallSignalDto) signal;
            return dto.getSenderId().equals(2L)
                    && dto.getRecipientId().equals(1L)
                    && dto.getType().equals("answer");
        }));
    }

    private void sendFollowUp(String type, UserPrincipal principal, Long recipientId) {
        CallSignalDto dto = CallSignalDto.builder()
                .recipientId(recipientId)
                .type(type)
                .callType("VIDEO")
                .build();
        switch (type) {
            case "answer" -> callController.processAnswer(dto, principal);
            case "ice" -> callController.processIceCandidate(dto, principal);
            case "cancel" -> callController.processCancel(dto, principal);
            case "reject" -> callController.processReject(dto, principal);
            case "hangup" -> callController.processHangup(dto, principal);
            default -> throw new IllegalArgumentException("Unsupported call signal type: " + type);
        }
    }
}
