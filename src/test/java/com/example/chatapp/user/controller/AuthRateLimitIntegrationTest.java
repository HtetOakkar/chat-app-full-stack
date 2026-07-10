package com.example.chatapp.user.controller;

import com.example.chatapp.email.service.EmailService;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_rate_limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "auth.rate-limit.login.max-attempts=2",
        "auth.rate-limit.signup.max-attempts=2",
        "auth.rate-limit.verify-email.max-attempts=2",
        "auth.rate-limit.resend-code.max-attempts=1",
        "auth.rate-limit.window-seconds=300",
        "resend.template.verification.id=test-template"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void repeatedFailedLoginAttemptsAreRateLimited() throws Exception {
        signup("ratelogin", null);

        String loginBody = """
                {
                  "username": "ratelogin",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void signupBurstsAreRateLimitedBySource() throws Exception {
        signup("signupburst1", null);
        signup("signupburst2", null);

        String thirdSignup = """
                {
                  "username": "signupburst3",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(thirdSignup))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void repeatedPublicVerificationAttemptsAreRateLimited() throws Exception {
        signup("verifylimit", "verifylimit@example.com");

        String wrongCodeBody = """
                {
                  "usernameOrEmail": "verifylimit",
                  "code": "000000"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(wrongCodeBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(wrongCodeBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(wrongCodeBody))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void repeatedPublicResendRequestsAreRateLimited() throws Exception {
        signup("resendlimit", "resendlimit@example.com");
        User user = userRepository.findByUsername("resendlimit").orElseThrow();
        user.setLastCodeRequestedAt(Instant.now().minus(Duration.ofSeconds(65)));
        userRepository.saveAndFlush(user);

        String resendBody = """
                {
                  "usernameOrEmail": "resendlimit"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/resend-code").contentType(MediaType.APPLICATION_JSON).content(resendBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/resend-code").contentType(MediaType.APPLICATION_JSON).content(resendBody))
                .andExpect(status().isTooManyRequests());
    }

    private void signup(String username, String email) throws Exception {
        String emailField = email == null ? "" : ",\n  \"email\": \"%s\"".formatted(email);
        String signupBody = """
                {
                  "username": "%s",
                  "password": "password123"%s
                }
                """.formatted(username, emailField);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());
    }
}
