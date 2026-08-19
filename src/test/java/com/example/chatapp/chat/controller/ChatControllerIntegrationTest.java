package com.example.chatapp.chat.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.RoleRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.workspace.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    @Autowired
    private ChatController chatController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    private User bob;
    private User alice;

    @BeforeEach
    void setUp() {
        redisMessageRepository.clear();
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        bob = User.builder()
                .username("bob")
                .password("password123")
                .role(userRole)
                .build();
        bob = userRepository.save(bob);

        alice = User.builder()
                .username("alice")
                .password("password123")
                .role(userRole)
                .build();
        alice = userRepository.save(alice);
    }

    @Test
    void sendPrivateMessageShouldSaveToRedis() {
        MessageDto messageDto = MessageDto.builder()
                .recipientId(alice.getId())
                .content("Hey Alice!")
                .build();

        UserPrincipal principal = UserPrincipal.create(bob);

        chatController.sendPrivateMessage(messageDto, principal);

        var messages = redisMessageRepository.peekMessages();
        assertEquals(1, messages.size());
        assertEquals("Hey Alice!", messages.get(0).getContent());
        assertEquals(bob.getId(), messages.get(0).getSenderId());
        assertEquals(alice.getId(), messages.get(0).getRecipientId());
    }
}
