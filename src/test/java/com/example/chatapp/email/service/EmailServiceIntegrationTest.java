package com.example.chatapp.email.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EmailServiceIntegrationTest {

    @Autowired
    private EmailService emailService;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    void sendWorkspaceInvitation_SpringContextProperties_ShouldDispatchHtmlAndPlainTextWithCorrectJoinUrl() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Act
        emailService.sendWorkspaceInvitation("charlie@example.com", "Engineering Hub", "Alice", "invite-token-abc");

        // Assert
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), captor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> captured = captor.getValue();
        Map<String, Object> body = captured.getBody();
        assertNotNull(body);

        List<String> to = (List<String>) body.get("to");
        assertEquals("charlie@example.com", to.get(0));
        assertEquals("Alice invited you to join Engineering Hub", body.get("subject"));

        String html = (String) body.get("html");
        assertNotNull(html);
        assertTrue(html.contains("Engineering Hub"));
        assertTrue(html.contains("Alice"));
        assertTrue(html.contains("Accept Invitation &amp; Join Engineering Hub"));
        assertTrue(html.contains("http://localhost:3000/join/invite-token-abc"));

        String text = (String) body.get("text");
        assertNotNull(text);
        assertTrue(text.contains("Engineering Hub"));
        assertTrue(text.contains("Alice"));
        assertTrue(text.contains("http://localhost:3000/join/invite-token-abc"));
    }
}
