package com.example.chatapp.chat.controller;

import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    @Autowired
    private ChatController chatController;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User bob;
    private User alice;

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        // Recreate system user to prevent test pollution
        User system = User.builder()
                .username("system")
                .password("system_pass")
                .role(userRole)
                .version(0L)
                .build();
        userRepository.save(system);

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
    void sendPrivateMessageShouldPersistStatusUpdateToAccepted() {
        // 1. Setup contact relationship: Bob has a PENDING_REQUEST from Alice
        Contact bobRelation = Contact.builder()
                .owner(bob)
                .contactUser(alice)
                .status(ContactStatus.PENDING_REQUEST)
                .build();
        contactRepository.save(bobRelation);

        Contact aliceRelation = Contact.builder()
                .owner(alice)
                .contactUser(bob)
                .status(ContactStatus.ACCEPTED)
                .build();
        contactRepository.save(aliceRelation);

        // 2. Prepare message from Bob to Alice
        MessageDto messageDto = MessageDto.builder()
                .recipientId(alice.getId())
                .content("Hey Alice, replying to your request!")
                .build();

        UserPrincipal principal = UserPrincipal.create(bob);

        // 3. Send message
        chatController.sendPrivateMessage(messageDto, principal);

        // 4. Verify in DB
        Optional<Contact> updatedRelationOpt = contactRepository.findByOwnerIdAndContactUserId(bob.getId(), alice.getId());
        assertTrue(updatedRelationOpt.isPresent());
        assertEquals(ContactStatus.ACCEPTED, updatedRelationOpt.get().getStatus());
    }
}
