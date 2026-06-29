package com.example.chatapp.message.controller;

import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private com.example.chatapp.user.repository.ContactRepository contactRepository;

    @Autowired
    private com.example.chatapp.message.repository.RedisMessageRepository redisMessageRepository;

    @Autowired
    private com.example.chatapp.message.service.MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;
    private String charlieToken;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        redisMessageRepository.clear();
        userRepository.deleteAll();

        // Register Alice
        MvcResult aliceRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> aliceMap = objectMapper.readValue(aliceRes.getResponse().getContentAsString(), Map.class);
        aliceToken = "Bearer " + aliceMap.get("token");
        alice = userRepository.findByUsername("alice").orElseThrow();

        // Register Bob
        MvcResult bobRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> bobMap = objectMapper.readValue(bobRes.getResponse().getContentAsString(), Map.class);
        bobToken = "Bearer " + bobMap.get("token");
        bob = userRepository.findByUsername("bob").orElseThrow();

        // Register Charlie
        MvcResult charlieRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"charlie\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> charlieMap = objectMapper.readValue(charlieRes.getResponse().getContentAsString(), Map.class);
        charlieToken = "Bearer " + charlieMap.get("token");
        alice = userRepository.findByUsername("alice").orElseThrow();
        bob = userRepository.findByUsername("bob").orElseThrow();
        charlie = userRepository.findByUsername("charlie").orElseThrow();

        User system = User.builder()
                .username("system")
                .password("system_pass")
                .role(alice.getRole())
                .version(0L)
                .build();
        userRepository.save(system);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        contactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void getPublicMessagesShouldReturnPagedMessages() throws Exception {
        User system = userRepository.findByUsername("system").orElseThrow();
        Instant now = Instant.now();
        
        // Save 3 public messages
        Message m1 = Message.builder().content("Pub 1").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(alice).recipient(system).build();
        Message m2 = Message.builder().content("Pub 2").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(bob).recipient(system).build();
        Message m3 = Message.builder().content("Pub 3").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(alice).recipient(system).build();

        m1 = messageRepository.save(m1);
        m2 = messageRepository.save(m2);
        m3 = messageRepository.save(m3);

        // Update timestamps directly in DB to bypass @CreationTimestamp
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now.minus(10, ChronoUnit.MINUTES)), m1.getId());
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now.minus(5, ChronoUnit.MINUTES)), m2.getId());
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now), m3.getId());

        // Fetch without cursor (should get all 3, newest first)
        MvcResult res = mockMvc.perform(get("/api/v1/messages/public?limit=2")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("Pub 3"))
                .andExpect(jsonPath("$.messages[1].content").value("Pub 2"))
                .andReturn();

        String json = res.getResponse().getContentAsString();
        Map<?, ?> page = objectMapper.readValue(json, Map.class);
        String nextCursor = (String) page.get("nextCursor");

        // Fetch with cursor to get Pub 1
        mockMvc.perform(get("/api/v1/messages/public?limit=2&cursor=" + nextCursor)
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("Pub 1"));
    }

    @Test
    void getPrivateMessagesShouldReturnOnlyAuthorizedAndPaged() throws Exception {
        Instant now = Instant.now();

        // Messages between Alice and Bob
        Message ab1 = Message.builder().content("A to B 1").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(alice).recipient(bob).build();
        Message ba1 = Message.builder().content("B to A 1").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(bob).recipient(alice).build();
        
        // Message between Alice and Charlie
        Message ac1 = Message.builder().content("A to C 1").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(alice).recipient(charlie).build();

        ab1 = messageRepository.save(ab1);
        ba1 = messageRepository.save(ba1);
        ac1 = messageRepository.save(ac1);

        // Update timestamps directly in DB to bypass @CreationTimestamp
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now.minus(10, ChronoUnit.MINUTES)), ab1.getId());
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now.minus(5, ChronoUnit.MINUTES)), ba1.getId());
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(now), ac1.getId());

        // Alice fetches messages with Bob
        mockMvc.perform(get("/api/v1/messages/private/" + bob.getId() + "?limit=10")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("B to A 1"))
                .andExpect(jsonPath("$.messages[1].content").value("A to B 1"));

        // Charlie tries to fetch messages between Alice and Bob -> should fail with 401 Unauthorized
        mockMvc.perform(get("/api/v1/messages/private/" + bob.getId() + "?limit=10")
                        .header("Authorization", charlieToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPrivateMessagesForNewContactShouldReturnEmptyList() throws Exception {
        // Save messages between Alice and Bob
        Message ab = Message.builder().content("A to B").messageType(MessageType.TEXT).isRead(false).isDelivered(true)
                .sender(alice).recipient(bob).build();
        messageRepository.save(ab);

        // Charlie adds Bob as contact, so hasRelation is true
        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", charlieToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isCreated());

        // Charlie fetches history with Bob -> should succeed (200 OK) but return an empty list (no messages between Charlie and Bob)
        mockMvc.perform(get("/api/v1/messages/private/" + bob.getId() + "?limit=10")
                        .header("Authorization", charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    void markMessagesAsReadShouldUpdateDbAndRedis() throws Exception {
        // 1. Establish Bob and Alice as contacts (required for security check of private messages)
        com.example.chatapp.user.model.entity.Contact contactAlice = com.example.chatapp.user.model.entity.Contact.builder()
                .owner(alice)
                .contactUser(bob)
                .status(com.example.chatapp.user.model.entity.ContactStatus.ACCEPTED)
                .build();
        contactRepository.save(contactAlice);

        // 2. Save a message from Bob to Alice in DB (unread)
        Message messageDb = Message.builder()
                .sender(bob)
                .recipient(alice)
                .content("DB Unread")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .sentAt(Instant.now().minusSeconds(10))
                .build();
        messageRepository.save(messageDb);

        // 3. Save a message from Bob to Alice in Redis (unread)
        com.example.chatapp.message.model.dto.MessageDto messageRedis = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(8888L)
                .senderId(bob.getId())
                .senderUsername(bob.getUsername())
                .recipientId(alice.getId())
                .content("Redis Unread")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(Instant.now())
                .build();
        redisMessageRepository.saveMessage(messageRedis);

        // Verify unread count is 2 first via GET /api/v1/contacts
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(2));

        // 4. Alice marks messages from Bob as read via PUT /api/v1/messages/read/{contactUserId}
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/messages/read/" + bob.getId())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk());

        // 5. Verify unread count is now 0 via GET /api/v1/contacts
        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
    }

    @Test
    void deletePublicMessageInRedisSuccessfully() throws Exception {
        Instant now = Instant.now();
        com.example.chatapp.message.model.dto.MessageDto redisMsg = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(9999L)
                .senderId(alice.getId())
                .senderUsername(alice.getUsername())
                .recipientId(null)
                .content("Hello public")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(now)
                .build();
        redisMessageRepository.saveMessage(redisMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/9999")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9999))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify it was updated in Redis in-place
        java.util.List<com.example.chatapp.message.model.dto.MessageDto> peeked = redisMessageRepository.peekMessages();
        org.junit.jupiter.api.Assertions.assertEquals(1, peeked.size());
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", peeked.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(peeked.get(0).getIsDeleted());
    }

    @Test
    void deletePublicMessageInDbSuccessfully() throws Exception {
        User system = userRepository.findByUsername("system").orElseThrow();
        Message dbMsg = Message.builder()
                .content("Hello db message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(system)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify database state
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void deletePublicMessageWithFallbackLookupSuccessfully() throws Exception {
        User system = userRepository.findByUsername("system").orElseThrow();
        Instant timestamp = Instant.parse("2026-06-11T12:00:00Z");

        Message dbMsg = Message.builder()
                .content("Hello fallback message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(system)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Update timestamp directly in DB to bypass @CreationTimestamp or PrePersist
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(timestamp), dbMsg.getId());

        // Perform DELETE with a non-existent ID (e.g. 123456) but matching timestamp
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/123456?timestamp=" + timestamp.toString())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify database state
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void deletePublicMessageByAnotherUserShouldBeUnauthorized() throws Exception {
        User system = userRepository.findByUsername("system").orElseThrow();
        Message dbMsg = Message.builder()
                .content("Hello db message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(system)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Bob tries to delete Alice's message
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", bobToken))
                .andExpect(status().isUnauthorized());

        // Verify message content in DB was NOT modified
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Hello db message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertFalse(updated.getIsDeleted());
    }

    @Test
    void deletePrivateMessageInRedisSuccessfully() throws Exception {
        Instant now = Instant.now();
        com.example.chatapp.message.model.dto.MessageDto redisMsg = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(7777L)
                .senderId(alice.getId())
                .senderUsername(alice.getUsername())
                .recipientId(bob.getId())
                .content("Hello Bob private")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(now)
                .build();
        redisMessageRepository.saveMessage(redisMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/7777")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7777))
                .andExpect(jsonPath("$.recipientId").value(bob.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify it was updated in Redis in-place
        java.util.List<com.example.chatapp.message.model.dto.MessageDto> peeked = redisMessageRepository.peekMessages();
        org.junit.jupiter.api.Assertions.assertEquals(1, peeked.size());
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", peeked.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(peeked.get(0).getIsDeleted());
    }

    @Test
    void deletePrivateMessageInDbSuccessfully() throws Exception {
        Message dbMsg = Message.builder()
                .content("Hello Bob private db")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(bob)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.recipientId").value(bob.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify database state
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void deletePrivateMessageWithFallbackLookupSuccessfully() throws Exception {
        Instant timestamp = Instant.parse("2026-06-11T12:30:00Z");
        Message dbMsg = Message.builder()
                .content("Hello Bob private fallback")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(bob)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Update timestamp directly in DB to bypass @CreationTimestamp or PrePersist
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(timestamp), dbMsg.getId());

        // Perform DELETE with a non-existent ID but matching timestamp
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/999999?timestamp=" + timestamp.toString())
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.recipientId").value(bob.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify database state
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void deletePrivateMessageByAnotherUserShouldBeUnauthorized() throws Exception {
        Message dbMsg = Message.builder()
                .content("Hello Bob private secure")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(bob)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Bob tries to delete Alice's private message to him -> should be unauthorized
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/" + dbMsg.getId())
                        .header("Authorization", bobToken))
                .andExpect(status().isUnauthorized());

        // Verify message content in DB was NOT modified
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Hello Bob private secure", updated.getContent());
        org.junit.jupiter.api.Assertions.assertFalse(updated.getIsDeleted());
    }

    @Test
    void deleteMessageWithInvalidTimestampShouldReturnBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/999999?timestamp=invalid-date")
                        .header("Authorization", aliceToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMessageWithOffsetTimestampSuccessfully() throws Exception {
        User system = userRepository.findByUsername("system").orElseThrow();
        // 2026-06-11T12:30:00Z is 2026-06-11T19:00:00+06:30
        Instant utcInstant = Instant.parse("2026-06-11T12:30:00Z");
        
        Message dbMsg = Message.builder()
                .content("Hello offset message")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .isDelivered(true)
                .sender(alice)
                .recipient(system)
                .build();
        dbMsg = messageRepository.save(dbMsg);

        // Update sent_at timestamp directly in DB to match our test instant
        jdbcTemplate.update("UPDATE messages SET sent_at = ? WHERE id = ?", java.sql.Timestamp.from(utcInstant), dbMsg.getId());

        // Perform DELETE with non-existent ID but passing offset timestamp in query parameter
        // 2026-06-11T19:00:00+06:30 must be URL-encoded as 2026-06-11T19:00:00%2B06:30
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/999999")
                        .queryParam("timestamp", "2026-06-11T19:00:00+06:30")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dbMsg.getId()))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify database state
        Message updated = messageRepository.findById(dbMsg.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", updated.getContent());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getIsDeleted());
    }

    @Test
    void generatedTransientIdsShouldBeWithinJsSafeLimit() {
        for (int i = 0; i < 1000; i++) {
            com.example.chatapp.message.model.dto.MessageDto dto = com.example.chatapp.message.model.dto.MessageDto.builder()
                    .content("test")
                    .senderId(alice.getId())
                    .build();
            messageService.saveMessage(dto);
            org.junit.jupiter.api.Assertions.assertNotNull(dto.getId());
            org.junit.jupiter.api.Assertions.assertTrue(dto.getId() >= 1L);
            org.junit.jupiter.api.Assertions.assertTrue(dto.getId() <= 9007199254740991L, 
                    "ID " + dto.getId() + " is larger than JS safe integer limit");
        }
    }

    @Test
    void deleteMessageInRedisWithFallbackSuccessfully() throws Exception {
        Instant timestamp = Instant.parse("2026-06-11T12:00:00Z");
        com.example.chatapp.message.model.dto.MessageDto redisMsg = com.example.chatapp.message.model.dto.MessageDto.builder()
                .id(9123456789012345678L) // exceeds JS safe integer limit (simulates browser precision loss/mismatch)
                .senderId(alice.getId())
                .senderUsername(alice.getUsername())
                .recipientId(bob.getId())
                .content("Hello Bob Redis fallback")
                .isRead(false)
                .isDelivered(true)
                .messageType(MessageType.TEXT)
                .timestamp(timestamp)
                .build();
        redisMessageRepository.saveMessage(redisMsg);

        // Perform DELETE with non-matching ID (simulating precision loss e.g. ending in 000) but matching timestamp
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/messages/9123456789012346000")
                        .queryParam("timestamp", "2026-06-11T12:00:00Z")
                        .header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9123456789012345678L))
                .andExpect(jsonPath("$.content").value("Deleted message"))
                .andExpect(jsonPath("$.isDeleted").value(true));

        // Verify it was updated in Redis in-place
        java.util.List<com.example.chatapp.message.model.dto.MessageDto> peeked = redisMessageRepository.peekMessages();
        org.junit.jupiter.api.Assertions.assertEquals(1, peeked.size());
        org.junit.jupiter.api.Assertions.assertEquals("Deleted message", peeked.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertTrue(peeked.get(0).getIsDeleted());
    }
}





