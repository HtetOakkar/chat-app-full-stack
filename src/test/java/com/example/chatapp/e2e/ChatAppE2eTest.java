package com.example.chatapp.e2e;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.user.model.entity.*;
import com.example.chatapp.user.model.dto.*;
import com.example.chatapp.user.model.request.*;
import com.example.chatapp.user.model.response.*;
import com.example.chatapp.user.repository.*;
import com.example.chatapp.workspace.model.entity.*;
import com.example.chatapp.workspace.repository.*;
import com.example.chatapp.message.model.entity.*;
import com.example.chatapp.message.model.dto.*;
import com.example.chatapp.message.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.sockjs.client.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ChatAppE2eTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RedisMessageRepository redisMessageRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        messageRepository.deleteAll();
        workspaceInvitationRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        channelMemberRepository.deleteAll();
        channelRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
        redisMessageRepository.clear();

        Set<String> keys = redisTemplate.keys("user:presence:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_USER).build()));

        if (!userRepository.findByUsername("system").isPresent()) {
            userRepository.save(User.builder()
                    .username("system")
                    .password("system_pass")
                    .role(userRole)
                    .email("system@chatapp.com")
                    .emailVerified(true)
                    .build());
        }
    }

    public static class StompTestSession {
        public final StompSession session;
        public final LinkedBlockingQueue<MessageDto> messages = new LinkedBlockingQueue<>();
        public final LinkedBlockingQueue<MessageDto> publicMessages = new LinkedBlockingQueue<>();

        public StompTestSession(StompSession session) {
            this.session = session;
        }

        public void subscribeAll() {
            session.subscribe("/user/queue/messages", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return MessageDto.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) { messages.add((MessageDto) payload); }
            });

            session.subscribe("/topic/public", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return MessageDto.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) { publicMessages.add((MessageDto) payload); }
            });
        }

        public void disconnect() {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private StompTestSession connectWebSocket(String token) throws Exception {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        );

        StompSession session = future.get(8, TimeUnit.SECONDS);
        StompTestSession testSession = new StompTestSession(session);
        testSession.subscribeAll();
        return testSession;
    }

    private String signup(String username, String email, String password) {
        UserSignUpRequest request = new UserSignUpRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName(username + " Full");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity("/api/v1/auth/signup", request, LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().getToken();
    }

    private void verifyEmail(String username, String code) {
        PublicVerifyEmailRequest verifyRequest = new PublicVerifyEmailRequest();
        verifyRequest.setUsernameOrEmail(username);
        verifyRequest.setCode(code);
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity("/api/v1/auth/verify-email", verifyRequest, LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String signupAndVerify(String username, String email, String password) {
        signup(username, email, password);
        User user = userRepository.findByUsername(username).orElseThrow();
        verifyEmail(username, user.getEmailVerificationCode());
        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity("/api/v1/auth/login", loginRequest, LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().getToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private <T> ResponseEntity<T> get(String url, String token, Class<T> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    }

    private <R, T> ResponseEntity<T> put(String url, String token, R body, Class<T> responseType) {
        HttpEntity<R> entity = new HttpEntity<>(body, authHeaders(token));
        return restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);
    }

    @Test
    public void testEmailVerificationFlow() {
        signup("ev_user", "ev@example.com", "password123");
        User user = userRepository.findByUsername("ev_user").orElseThrow();
        assertFalse(user.isEmailVerified());
        verifyEmail("ev_user", user.getEmailVerificationCode());
        assertTrue(userRepository.findByUsername("ev_user").orElseThrow().isEmailVerified());
    }

    @Test
    public void testUpdateProfile() {
        String token = signupAndVerify("profile_user", "profile@example.com", "password123");
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Updated Profile Full");
        req.setBirthDate(LocalDate.of(1995, 5, 20));
        ResponseEntity<UserProfileResponse> res = put("/api/v1/users/profile", token, req, UserProfileResponse.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Updated Profile Full", res.getBody().getFullName());
    }

    @Test
    public void testWebSocketStompMessaging() throws Exception {
        String aliceToken = signupAndVerify("alice_e2e", "alice_e2e@example.com", "password123");
        String bobToken = signupAndVerify("bob_e2e", "bob_e2e@example.com", "password123");
        User bob = userRepository.findByUsername("bob_e2e").orElseThrow();

        StompTestSession sessionAlice = connectWebSocket(aliceToken);
        StompTestSession sessionBob = connectWebSocket(bobToken);

        MessageDto msg = MessageDto.builder()
                .recipientId(bob.getId())
                .content("Hello Bob E2E")
                .build();

        sessionAlice.session.send("/app/chat.private", msg);

        MessageDto received = sessionBob.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("Hello Bob E2E", received.getContent());

        sessionAlice.disconnect();
        sessionBob.disconnect();
    }
}
