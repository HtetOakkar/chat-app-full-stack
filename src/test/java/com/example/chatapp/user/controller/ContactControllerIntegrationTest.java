package com.example.chatapp.user.controller;

import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private com.example.chatapp.message.repository.RedisMessageRepository redisMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;

    private User aliceUser;
    private User bobUser;

    @BeforeEach
    void setup() throws Exception {
        contactRepository.deleteAll();
        messageRepository.deleteAll();
        redisMessageRepository.clear();
        userRepository.deleteAll();

        // 1. Sign up Alice
        String aliceSignup = """
                {
                  "username": "alice",
                  "password": "password123"
                }
                """;
        MvcResult aliceRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aliceSignup))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> aliceMap = objectMapper.readValue(aliceRes.getResponse().getContentAsString(), Map.class);
        aliceToken = "Bearer " + aliceMap.get("token");

        // 2. Sign up Bob
        String bobSignup = """
                {
                  "username": "bob",
                  "password": "password123"
                }
                """;
        MvcResult bobRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bobSignup))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> bobMap = objectMapper.readValue(bobRes.getResponse().getContentAsString(), Map.class);
        bobToken = "Bearer " + bobMap.get("token");

        // Retrieve actual entity objects for ID matching
        aliceUser = userRepository.findByUsername("alice").orElseThrow();
        bobUser = userRepository.findByUsername("bob").orElseThrow();
    }

    @Test
    void addContactShouldCreateContactWithContactStatus() throws Exception {
        String addContactReq = String.format("""
                {
                  "username": "%s"
                }
                """, bobUser.getUsername());

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addContactReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactUserId").value(bobUser.getId()))
                .andExpect(jsonPath("$.contactUsername").value("bob"))
                .andExpect(jsonPath("$.status").value("CONTACT"));

        // Verify standard list works and filters contacts
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactUsername").value("bob"))
                .andExpect(jsonPath("$[0].status").value("CONTACT"));
    }

    @Test
    void pendingRequestFlowShouldWorkCorrectly() throws Exception {
        // Pre-create a request from Alice to Bob in DB (Simulates Alice messaging Bob first time)
        Contact requestContact = Contact.builder()
                .owner(bobUser)
                .contactUser(aliceUser)
                .status(ContactStatus.PENDING_REQUEST)
                .build();
        contactRepository.save(requestContact);

        // Bob checks requests list
        mockMvc.perform(get("/api/v1/contacts/requests")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactUsername").value("alice"))
                .andExpect(jsonPath("$[0].status").value("PENDING_REQUEST"));

        // Bob accepts Alice's request
        mockMvc.perform(put("/api/v1/contacts/" + aliceUser.getId() + "/accept")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Bob should no longer have Alice in requests list
        mockMvc.perform(get("/api/v1/contacts/requests")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Bob should now have Alice in his contacts list
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactUsername").value("alice"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void neglectRequestFlowShouldWorkCorrectly() throws Exception {
        // Pre-create a request from Alice to Bob
        Contact requestContact = Contact.builder()
                .owner(bobUser)
                .contactUser(aliceUser)
                .status(ContactStatus.PENDING_REQUEST)
                .build();
        contactRepository.save(requestContact);

        // Bob neglects Alice's request
        mockMvc.perform(put("/api/v1/contacts/" + aliceUser.getId() + "/neglect")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGLECTED"));

        // Check requests list: neglected stays in request tab (silently)
        mockMvc.perform(get("/api/v1/contacts/requests")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // Wait: we said getPendingRequests only returns PENDING_REQUEST
    }

    @Test
    void blockingUserFlowShouldWorkCorrectly() throws Exception {
        // Bob blocks Alice
        mockMvc.perform(put("/api/v1/contacts/" + aliceUser.getId() + "/block")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // Verify DB entry exists with BLOCKED status
        assertTrue(contactRepository.existsByOwnerIdAndContactUserIdAndStatus(
                bobUser.getId(), aliceUser.getId(), ContactStatus.BLOCKED));
    }

    @Test
    void getBlockedContactsShouldReturnOnlyBlockedUsers() throws Exception {
        // Bob blocks Alice
        mockMvc.perform(put("/api/v1/contacts/" + aliceUser.getId() + "/block")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk());

        // Get blocked contacts
        mockMvc.perform(get("/api/v1/contacts/blocked")
                        .header("Authorization", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contactUsername").value("alice"))
                .andExpect(jsonPath("$[0].status").value("BLOCKED"));
    }

    @Test
    void getContactsShouldReturnLastMessageAndUnreadCount() throws Exception {
        // 1. Establish Bob and Alice as mutually accepted contacts
        Contact contactAlice = Contact.builder()
                .owner(aliceUser)
                .contactUser(bobUser)
                .status(ContactStatus.ACCEPTED)
                .build();
        Contact contactBob = Contact.builder()
                .owner(bobUser)
                .contactUser(aliceUser)
                .status(ContactStatus.ACCEPTED)
                .build();
        contactRepository.save(contactAlice);
        contactRepository.save(contactBob);

        // 2. Save a message from Bob to Alice (stored in DB)
        Message messageDb = Message.builder()
                .sender(bobUser)
                .recipient(aliceUser)
                .content("Hello from DB")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .sentAt(Instant.now().minusSeconds(10))
                .build();
        messageRepository.save(messageDb);

        // 3. Save a message from Bob to Alice (stored in Redis)
        com.example.chatapp.message.model.dto.MessageDto messageRedis = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(9999L)
                .senderId(bobUser.getId())
                .senderUsername(bobUser.getUsername())
                .recipientId(aliceUser.getId())
                .content("Hello from Redis")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(Instant.now())
                .build();
        redisMessageRepository.saveMessage(messageRedis);

        // 4. Retrieve Alice's contacts - should show unread count = 2, and last message = "Hello from Redis"
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactUsername").value("bob"))
                .andExpect(jsonPath("$[0].unreadCount").value(2))
                .andExpect(jsonPath("$[0].lastMessageContent").value("Hello from Redis"))
                .andExpect(jsonPath("$[0].lastMessageSenderId").value(bobUser.getId()))
                .andExpect(jsonPath("$[0].lastMessageTimestamp").exists());
    }

    @Test
    void getContactsShouldReturnLastMessageAndSenderIdFromDatabaseWhenNoRedisMessage() throws Exception {
        // 1. Establish Bob and Alice as mutually accepted contacts
        Contact contactAlice = Contact.builder()
                .owner(aliceUser)
                .contactUser(bobUser)
                .status(ContactStatus.ACCEPTED)
                .build();
        Contact contactBob = Contact.builder()
                .owner(bobUser)
                .contactUser(aliceUser)
                .status(ContactStatus.ACCEPTED)
                .build();
        contactRepository.save(contactAlice);
        contactRepository.save(contactBob);

        // 2. Save a message from Bob to Alice (stored in DB only, no Redis message)
        Message messageDb = Message.builder()
                .sender(bobUser)
                .recipient(aliceUser)
                .content("Hello from DB only")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .sentAt(Instant.now().minusSeconds(10))
                .build();
        messageRepository.save(messageDb);

        // 3. Retrieve Alice's contacts - should show last message content from DB and correct sender ID
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactUsername").value("bob"))
                .andExpect(jsonPath("$[0].lastMessageContent").value("Hello from DB only"))
                .andExpect(jsonPath("$[0].lastMessageSenderId").value(bobUser.getId()))
                .andExpect(jsonPath("$[0].lastMessageTimestamp").exists());
    }
}
