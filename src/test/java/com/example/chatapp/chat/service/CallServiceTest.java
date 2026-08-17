package com.example.chatapp.chat.service;

import com.example.chatapp.chat.model.CallSession;
import com.example.chatapp.message.model.dto.CallSignalDto;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.websocket.MessageBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallServiceTest {

    private CallRegistry callRegistry;
    private UserRepository userRepository;
    private MessageService messageService;
    private MessageBroker messageBroker;
    private CallService callService;

    private User caller;
    private User callee;

    @BeforeEach
    void setUp() {
        callRegistry = new CallRegistry();
        userRepository = mock(UserRepository.class);
        messageService = mock(MessageService.class);
        messageBroker = mock(MessageBroker.class);
        callService = new CallServiceImpl(callRegistry, userRepository, messageService, messageBroker);

        caller = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice Smith")
                .build();
        callee = User.builder()
                .id(2L)
                .username("bob")
                .fullName("Bob Jones")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(callee));
    }

    @Test
    void handleUserDisconnect_whenNotInCall_doesNothing() {
        callService.handleUserDisconnect(1L);

        verify(messageBroker, never()).publishToUser(any(), any(), any());
        verify(messageService, never()).saveMessage(any());
    }

    @Test
    void handleUserDisconnect_whenInDialingCall_unregistersSessionAndPublishesCancelSignalAndSavesCancelledRecord() {
        callRegistry.registerCall(1L, 2L, "AUDIO");
        assertTrue(callRegistry.isBusy(1L));
        assertTrue(callRegistry.isBusy(2L));

        callService.handleUserDisconnect(1L);

        // 1. Session removed from registry
        assertFalse(callRegistry.isBusy(1L));
        assertFalse(callRegistry.isBusy(2L));
        assertTrue(callRegistry.getActiveCall(1L).isEmpty());
        assertTrue(callRegistry.getActiveCall(2L).isEmpty());

        // 2. Cancel signal published to peer (User 2)
        ArgumentCaptor<CallSignalDto> signalCaptor = ArgumentCaptor.forClass(CallSignalDto.class);
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/call"), signalCaptor.capture());
        CallSignalDto capturedSignal = signalCaptor.getValue();
        assertEquals(1L, capturedSignal.getSenderId());
        assertEquals("alice", capturedSignal.getSenderUsername());
        assertEquals("Alice Smith", capturedSignal.getSenderFullName());
        assertEquals(2L, capturedSignal.getRecipientId());
        assertEquals("cancel", capturedSignal.getType());
        assertEquals("AUDIO", capturedSignal.getCallType());

        // 3. Call record saved and published to both
        ArgumentCaptor<MessageDto> messageCaptor = ArgumentCaptor.forClass(MessageDto.class);
        verify(messageService).saveMessage(messageCaptor.capture());
        MessageDto savedRecord = messageCaptor.getValue();
        assertEquals(1L, savedRecord.getSenderId());
        assertEquals(2L, savedRecord.getRecipientId());
        assertEquals("cancelled", savedRecord.getCallOutcome());
        assertEquals(0, savedRecord.getCallDuration());
        assertEquals(MessageType.AUDIO, savedRecord.getMessageType());
        assertEquals("Call Cancelled", savedRecord.getContent());
        assertFalse(savedRecord.getVideoUsed());

        verify(messageBroker).publishToUser(eq("1"), eq("/queue/messages"), any(MessageDto.class));
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/messages"), any(MessageDto.class));
    }

    @Test
    void handleUserDisconnect_whenInActiveCall_unregistersSessionAndPublishesHangupSignalAndSavesCompletedRecord() {
        callRegistry.registerCall(1L, 2L, "VIDEO");
        CallSession session = callRegistry.getActiveCall(1L).orElseThrow();
        session.setStatus("active");
        session.setStartedAt(Instant.now().minusSeconds(10));

        // Callee (User 2) disconnects
        callService.handleUserDisconnect(2L);

        // 1. Session removed from registry
        assertFalse(callRegistry.isBusy(1L));
        assertFalse(callRegistry.isBusy(2L));

        // 2. Hangup signal published to peer (User 1)
        ArgumentCaptor<CallSignalDto> signalCaptor = ArgumentCaptor.forClass(CallSignalDto.class);
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/call"), signalCaptor.capture());
        CallSignalDto capturedSignal = signalCaptor.getValue();
        assertEquals(2L, capturedSignal.getSenderId());
        assertEquals("bob", capturedSignal.getSenderUsername());
        assertEquals("Bob Jones", capturedSignal.getSenderFullName());
        assertEquals(1L, capturedSignal.getRecipientId());
        assertEquals("hangup", capturedSignal.getType());
        assertEquals("VIDEO", capturedSignal.getCallType());

        // 3. Call record saved with outcome="completed" and duration >= 10
        ArgumentCaptor<MessageDto> messageCaptor = ArgumentCaptor.forClass(MessageDto.class);
        verify(messageService).saveMessage(messageCaptor.capture());
        MessageDto savedRecord = messageCaptor.getValue();
        assertEquals(1L, savedRecord.getSenderId());
        assertEquals(2L, savedRecord.getRecipientId());
        assertEquals("completed", savedRecord.getCallOutcome());
        assertTrue(savedRecord.getCallDuration() >= 10);
        assertEquals(MessageType.VIDEO, savedRecord.getMessageType());
        assertEquals("Call Ended", savedRecord.getContent());
        assertTrue(savedRecord.getVideoUsed());

        verify(messageBroker).publishToUser(eq("1"), eq("/queue/messages"), any(MessageDto.class));
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/messages"), any(MessageDto.class));
    }

    @Test
    void saveCallRecord_persistsAndPublishesToBothParties() {
        CallSession session = CallSession.builder()
                .callerId(1L)
                .calleeId(2L)
                .callType("AUDIO")
                .status("active")
                .startedAt(Instant.now().minusSeconds(45))
                .build();

        callService.saveCallRecord(session, "completed", 45);

        ArgumentCaptor<MessageDto> messageCaptor = ArgumentCaptor.forClass(MessageDto.class);
        verify(messageService).saveMessage(messageCaptor.capture());
        MessageDto savedRecord = messageCaptor.getValue();
        assertEquals("Call Ended", savedRecord.getContent());
        assertEquals("completed", savedRecord.getCallOutcome());
        assertEquals(45, savedRecord.getCallDuration());
        assertEquals(MessageType.AUDIO, savedRecord.getMessageType());
        assertFalse(savedRecord.getVideoUsed());

        verify(messageBroker, times(1)).publishToUser(eq("1"), eq("/queue/messages"), eq(savedRecord));
        verify(messageBroker, times(1)).publishToUser(eq("2"), eq("/queue/messages"), eq(savedRecord));
    }
}
