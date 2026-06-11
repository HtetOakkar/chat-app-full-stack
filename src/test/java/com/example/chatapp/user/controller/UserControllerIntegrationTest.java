package com.example.chatapp.user.controller;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import com.example.chatapp.email.service.EmailService;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;


import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private MockMvc mockMvc;


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;

    @MockBean
    private EmailService emailService;

    @MockBean
    private org.springframework.messaging.simp.user.SimpUserRegistry simpUserRegistry;


    @BeforeEach
    void setup() throws Exception {
        userRepository.deleteAll();

        String signupBody = """
                {
                  "username": "testuser",
                  "password": "password123"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        userToken = "Bearer " + responseMap.get("token");
    }

    @Test
    void getProfileShouldReturnUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.fullName").value((Object) null))
                .andExpect(jsonPath("$.birthDate").value((Object) null))
                .andExpect(jsonPath("$.email").value((Object) null))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void updateProfileShouldModifyUserProfileFields() throws Exception {
        String updateBody = """
                {
                  "fullName": "John Doe",
                  "birthDate": "1990-01-01",
                  "email": "john@chatapp.com"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.email").value("john@chatapp.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        // Verify that subsequent GET returns the updated profile
        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.email").value("john@chatapp.com"));
    }

    @Test
    void emailVerificationFlowShouldSucceedWithCorrectCode() throws Exception {
        String updateBody = """
                {
                  "email": "verify@chatapp.com"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("testuser").orElseThrow();
        String generatedCode = user.getEmailVerificationCode();
        assertNotNull(generatedCode);
        assertEquals(6, generatedCode.length());
        
        Mockito.verify(emailService).sendVerificationEmail(ArgumentMatchers.eq("verify@chatapp.com"), ArgumentMatchers.eq(generatedCode));

        String verifyBody = String.format("""
                {
                  "code": "%s"
                }
                """, generatedCode);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users/profile/verify-email")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void emailVerificationShouldLockoutAfterThreeFailures() throws Exception {
        String updateBody = """
                {
                  "email": "lockout@chatapp.com"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        String wrongCodeBody = """
                {
                  "code": "000000"
                }
                """;

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users/profile/verify-email")
                            .header("Authorization", userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCodeBody))
                    .andExpect(status().isBadRequest());
        }

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("testuser").orElseThrow();
        assertNull(user.getEmailVerificationCode());
    }

    @Test
    void resendVerificationCodeShouldEnforceCooldown() throws Exception {
        String updateBody = """
                {
                  "email": "resend@chatapp.com"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users/profile/resend-code")
                        .header("Authorization", userToken))
                .andExpect(status().isBadRequest());

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("testuser").orElseThrow();
        user.setLastCodeRequestedAt(java.time.Instant.now().minus(java.time.Duration.ofSeconds(65)));
        userRepository.saveAndFlush(user);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users/profile/resend-code")
                        .header("Authorization", userToken))
                .andExpect(status().isNoContent());
                
        com.example.chatapp.user.model.entity.User updatedUser = userRepository.findByUsername("testuser").orElseThrow();
        Mockito.verify(emailService).sendVerificationEmail(ArgumentMatchers.eq("resend@chatapp.com"), ArgumentMatchers.eq(updatedUser.getEmailVerificationCode()));
    }

    @Test
    void loginWithEmailFlowShouldVerifyState() throws Exception {
        String signupBody = """
                {
                  "username": "emailuser",
                  "password": "password123",
                  "email": "user@chatapp.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String loginUnverifiedBody = """
                {
                  "username": "user@chatapp.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginUnverifiedBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is not verified. Please verify your email first."));

        com.example.chatapp.user.model.entity.User user = userRepository.findByUsername("emailuser").orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginUnverifiedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void searchUsersShouldMatchUsernameOrFullName() throws Exception {
        String signupAlice = """
                {
                  "username": "alpha",
                  "password": "password123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupAlice))
                .andExpect(status().isOk());

        com.example.chatapp.user.model.entity.User alice = userRepository.findByUsername("alpha").orElseThrow();
        alice.setFullName("Alice Smith");
        userRepository.saveAndFlush(alice);

        String signupBob = """
                {
                  "username": "beta",
                  "password": "password123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBob))
                .andExpect(status().isOk());

        com.example.chatapp.user.model.entity.User bob = userRepository.findByUsername("beta").orElseThrow();
        bob.setFullName("Bob Alpha");
        userRepository.saveAndFlush(bob);

        mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", userToken)
                        .param("keyword", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.username == 'alpha')].fullName").value("Alice Smith"))
                .andExpect(jsonPath("$[?(@.username == 'beta')].fullName").value("Bob Alpha"));
    }

    @Test
    void searchUsersShouldNotIncludeCurrentUser() throws Exception {
        // testuser is the current user. Let's set their fullName to include "testuser" just in case.
        com.example.chatapp.user.model.entity.User currentUser = userRepository.findByUsername("testuser").orElseThrow();
        currentUser.setFullName("Test User Name");
        userRepository.saveAndFlush(currentUser);

        // create another user matching "test"
        String signupOther = """
                {
                  "username": "testother",
                  "password": "password123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupOther))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", userToken)
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[?(@.username == 'testother')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'testuser')]").doesNotExist());
    }

    @Test
    void getUserProfileByIdShouldReturnProfile() throws Exception {
        String signupBob = """
                {
                  "username": "bobprofile",
                  "password": "password123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBob))
                .andExpect(status().isOk());

        com.example.chatapp.user.model.entity.User bob = userRepository.findByUsername("bobprofile").orElseThrow();
        bob.setFullName("Bob Profile");
        bob.setBirthDate(java.time.LocalDate.of(1995, 5, 15));
        bob.setEmail("bob@example.com");
        userRepository.saveAndFlush(bob);

        mockMvc.perform(get("/api/v1/users/" + bob.getId() + "/profile")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bobprofile"))
                .andExpect(jsonPath("$.fullName").value("Bob Profile"))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.birthDate").value("1995-05-15"));
    }

    @Test
    void getOnlineUsersShouldReturnEmptyListWhenNoActiveSessions() throws Exception {
        Mockito.when(simpUserRegistry.getUsers()).thenReturn(java.util.Collections.emptySet());

        mockMvc.perform(get("/api/v1/users/online")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getOnlineUsersShouldReturnActiveUsers() throws Exception {
        org.springframework.messaging.simp.user.SimpUser mockUser = Mockito.mock(org.springframework.messaging.simp.user.SimpUser.class);
        Mockito.when(mockUser.getName()).thenReturn("100");

        com.example.chatapp.jwt.UserPrincipal onlinePrincipal = new com.example.chatapp.jwt.UserPrincipal(
                100L, "onlineuser", "password", java.util.Collections.emptyList()
        );
        Mockito.when(mockUser.getPrincipal()).thenReturn(onlinePrincipal);

        Mockito.when(simpUserRegistry.getUsers()).thenReturn(java.util.Collections.singleton(mockUser));

        mockMvc.perform(get("/api/v1/users/online")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("100"))
                .andExpect(jsonPath("$[0].username").value("onlineuser"))
                .andExpect(jsonPath("$[0].status").value("ONLINE"));
    }

    @Test
    void getOnlineUsersShouldReturnActiveUsersWhenPrincipalIsWrappedInAuthentication() throws Exception {
        org.springframework.messaging.simp.user.SimpUser mockUser = Mockito.mock(org.springframework.messaging.simp.user.SimpUser.class);
        Mockito.when(mockUser.getName()).thenReturn("200");

        com.example.chatapp.jwt.UserPrincipal onlinePrincipal = new com.example.chatapp.jwt.UserPrincipal(
                200L, "wrappeduser", "password", java.util.Collections.emptyList()
        );
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        onlinePrincipal, null, onlinePrincipal.getAuthorities()
                );
        Mockito.when(mockUser.getPrincipal()).thenReturn(authentication);

        Mockito.when(simpUserRegistry.getUsers()).thenReturn(java.util.Collections.singleton(mockUser));

        mockMvc.perform(get("/api/v1/users/online")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("200"))
                .andExpect(jsonPath("$[0].username").value("wrappeduser"))
                .andExpect(jsonPath("$[0].status").value("ONLINE"));
    }
}





