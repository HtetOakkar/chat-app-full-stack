package com.example.chatapp.chat.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.TypingIndicatorDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.service.PresenceModule;
import com.example.chatapp.websocket.MessageBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private MessageBroker messageBroker;

    @Mock
    private MessageService messageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PresenceModule presenceModule;

    @InjectMocks
    private ChatController chatController;

    private UserPrincipal senderPrincipal;

    @BeforeEach
    void setUp() {
        senderPrincipal = new UserPrincipal(1L, "bob", "password", List.of());
    }

    @Test
    void sendPrivateMessageShouldPublishToBothQueues() {
        MessageDto messageDto = MessageDto.builder()
                .recipientId(2L)
                .content("Hello alice")
                .build();

        chatController.sendPrivateMessage(messageDto, senderPrincipal);

        verify(messageService).saveMessage(any(MessageDto.class));
        verify(messageBroker).publishToUser(eq("1"), eq("/queue/messages"), any(MessageDto.class));
        verify(messageBroker).publishToUser(eq("2"), eq("/queue/messages"), any(MessageDto.class));
    }

    @Test
    void sendPublicMessageShouldPublishToTopic() {
        MessageDto messageDto = MessageDto.builder()
                .content("Hello world")
                .build();

        chatController.sendPublicMessage(messageDto, senderPrincipal);

        verify(messageService).saveMessage(any(MessageDto.class));
        verify(messageBroker).publishToTopic(eq("/topic/public"), any(MessageDto.class));
    }

    @Test
    void typingEventShouldBeBroadcastToRecipientQueue() {
        TypingIndicatorDto typingDto = new TypingIndicatorDto(2L, true);

        chatController.sendTypingIndicator(typingDto, senderPrincipal);

        verify(messageBroker).publishToUser(
                eq("2"),
                eq("/queue/typing"),
                argThat(msg -> {
                    TypingIndicatorDto dto = (TypingIndicatorDto) msg;
                    return dto.getSenderId().equals(1L) && dto.getIsTyping();
                })
        );
    }

    @Test
    void workspacePresenceFromMemberNotSharingPresenceIsNotBroadcast() {
        when(presenceModule.sharesPresence(1L)).thenReturn(false);
        com.example.chatapp.message.model.dto.OnlineStatusDto online = new com.example.chatapp.message.model.dto.OnlineStatusDto();
        online.setStatus("ONLINE");

        chatController.sendWorkspacePresence(100L, online, senderPrincipal);

        verify(messageBroker, never()).publishToTopic(any(), any());
    }

    @Test
    void workspacePresenceFromSharingMemberIsBroadcastToTheWorkspace() {
        when(presenceModule.sharesPresence(1L)).thenReturn(true);
        com.example.chatapp.message.model.dto.OnlineStatusDto online = new com.example.chatapp.message.model.dto.OnlineStatusDto();
        online.setStatus("ONLINE");

        chatController.sendWorkspacePresence(100L, online, senderPrincipal);

        verify(messageBroker).publishToTopic(eq("/topic/workspace.100.presence"), any());
    }
}
