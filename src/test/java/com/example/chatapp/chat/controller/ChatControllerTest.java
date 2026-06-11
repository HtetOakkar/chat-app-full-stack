package com.example.chatapp.chat.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ChatController chatController;

    private UserPrincipal senderPrincipal;
    private User senderUser;
    private User recipientUser;

    @BeforeEach
    void setUp() {
        senderPrincipal = new UserPrincipal(1L, "bob", "password", List.of());
        senderUser = User.builder().id(1L).username("bob").build();
        recipientUser = User.builder().id(2L).username("alice").build();
    }

    @Test
    void sendPrivateMessageShouldDelegateToContactService() {
        // Arrange
        MessageDto messageDto = MessageDto.builder()
                .recipientId(2L)
                .content("Hello alice")
                .build();

        // The recipient's relation to sender: recipient's status with sender is ACCEPTED
        Contact recipientRelation = Contact.builder()
                .owner(recipientUser)
                .contactUser(senderUser)
                .status(ContactStatus.ACCEPTED)
                .build();

        when(contactRepository.findByOwnerIdAndContactUserId(2L, 1L))
                .thenReturn(Optional.of(recipientRelation));

        // Act
        chatController.sendPrivateMessage(messageDto, senderPrincipal);

        // Assert
        verify(contactService, times(1)).acceptRequestIfPending(1L, 2L);
    }
}
