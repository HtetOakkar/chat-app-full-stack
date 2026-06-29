package com.example.chatapp.user.controller;

import com.example.chatapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import com.example.chatapp.email.service.EmailService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.chatapp.user.repository.ContactRepository contactRepository;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupShouldReturnToken() throws Exception {
        String requestBody = """
                {
                  "username": "alice",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiration").exists());
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() throws Exception {
        String signupBody = """
                {
                  "username": "bob",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String loginBody = """
                {
                  "username": "bob",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiration").exists());
    }

    @Test
    void loginShouldReturnUnauthorizedForWrongPassword() throws Exception {
        String signupBody = """
                {
                  "username": "charlie",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String loginBody = """
                {
                  "username": "charlie",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void signupShouldValidatePayload() throws Exception {
        String requestBody = """
                {
                  "username": "ab",
                  "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void signupWithProfileDetailsShouldSaveToDatabase() throws Exception {
        String requestBody = """
                {
                  "username": "doug",
                  "password": "password123",
                  "fullName": "Doug Funny",
                  "birthDate": "1995-10-25"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        com.example.chatapp.user.model.entity.User savedUser = userRepository.findByUsername("doug").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Doug Funny", savedUser.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(1995, 10, 25), savedUser.getBirthDate());
    }

    @Test
    void publicEmailVerificationFlowShouldSucceedWithCorrectCode() throws Exception {
        String signupBody = """
                {
                  "username": "publicverifyuser",
                  "password": "password123",
                  "email": "publicverify@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("publicverifyuser").orElseThrow();
        String generatedCode = user.getEmailVerificationCode();
        org.junit.jupiter.api.Assertions.assertNotNull(generatedCode);
        org.junit.jupiter.api.Assertions.assertEquals(6, generatedCode.length());
        
        Mockito.verify(emailService).sendVerificationEmail(ArgumentMatchers.eq("publicverify@chatapp.com"), ArgumentMatchers.eq(generatedCode));

        String verifyBody = String.format("""
                {
                  "usernameOrEmail": "publicverifyuser",
                  "code": "%s"
                }
                """, generatedCode);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiration").exists());

        com.example.chatapp.user.model.entity.User verifiedUser = userRepository.findByUsername("publicverifyuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(verifiedUser.isEmailVerified());
    }

    @Test
    void publicEmailVerificationShouldLockoutAfterThreeFailures() throws Exception {
        String signupBody = """
                {
                  "username": "publiclockoutuser",
                  "password": "password123",
                  "email": "publiclockout@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String wrongCodeBody = """
                {
                  "usernameOrEmail": "publiclockoutuser",
                  "code": "000000"
                }
                """;

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCodeBody))
                    .andExpect(status().isBadRequest());
        }

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("publiclockoutuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(user.getEmailVerificationCode());
    }

    @Test
    void publicResendVerificationCodeShouldEnforceCooldown() throws Exception {
        String signupBody = """
                {
                  "username": "publicresenduser",
                  "password": "password123",
                  "email": "publicresend@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String resendBody = """
                {
                  "usernameOrEmail": "publicresenduser"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/resend-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendBody))
                .andExpect(status().isBadRequest());

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("publicresenduser").orElseThrow();
        user.setLastCodeRequestedAt(java.time.Instant.now().minus(java.time.Duration.ofSeconds(65)));
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/resend-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendBody))
                .andExpect(status().isOk());
    }

    @Test
    void signupWithDuplicateEmailShouldReturnBadRequest() throws Exception {
        String signupBody1 = """
                {
                  "username": "user1",
                  "password": "password123",
                  "email": "duplicate@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody1))
                .andExpect(status().isOk());

        String signupBody2 = """
                {
                  "username": "user2",
                  "password": "password123",
                  "email": "duplicate@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered!"));
    }

    @Test
    void loginWithUnverifiedEmailByUsernameShouldReturnBadRequest() throws Exception {
        String signupBody = """
                {
                  "username": "unverifieduser",
                  "password": "password123",
                  "email": "unverified@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String loginBody = """
                {
                  "username": "unverifieduser",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is not verified. Please verify your email first."));
    }
}

