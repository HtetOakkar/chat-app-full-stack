package com.example.chatapp.email.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EmailServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    private EmailServiceImpl emailService;

    @Mock
    private ResendTemplateInitializer templateInitializer;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(restTemplate, templateInitializer);
    }

    @Test
    void sendVerificationEmail_ShouldCallResendWithCorrectParameters() throws Exception {
        // Arrange
        String to = "test@example.com";
        String code = "123456";

        when(templateInitializer.getTemplateId()).thenReturn("test_template_id");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Act
        emailService.sendVerificationEmail(to, code);

        // Assert
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), entityCaptor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> capturedEntity = entityCaptor.getValue();
        Map<String, Object> payload = capturedEntity.getBody();
        
        List<String> toList = (List<String>) payload.get("to");
        assertEquals("test@example.com", toList.get(0));
        assertEquals("Verify your email address", payload.get("subject"));
        
        // Assert it uses template instead of hardcoded HTML
        org.junit.jupiter.api.Assertions.assertNull(payload.get("html"), "HTML template should NOT be sent directly");
        
        Map<String, Object> template = (Map<String, Object>) payload.get("template");
        org.junit.jupiter.api.Assertions.assertNotNull(template, "Template object must be provided");
        assertEquals("test_template_id", template.get("id"));
        
        Map<String, Object> variables = (Map<String, Object>) template.get("variables");
        org.junit.jupiter.api.Assertions.assertNotNull(variables, "Template variables must be provided");
        assertEquals("123456", variables.get("code"));
    }

    @Test
    void sendWorkspaceInvitation_withInviterName_ShouldDispatchBrandedHtmlEmailWithDirectJoinLinkAndPlainTextFallback() {
        // Arrange
        String to = "bob@example.com";
        String workspaceName = "Acme Team";
        String inviterName = "Alice Smith";
        String token = "tok12345";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Act
        emailService.sendWorkspaceInvitation(to, workspaceName, inviterName, token);

        // Assert
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), entityCaptor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> capturedEntity = entityCaptor.getValue();
        Map<String, Object> payload = capturedEntity.getBody();

        org.junit.jupiter.api.Assertions.assertNotNull(payload);
        List<String> toList = (List<String>) payload.get("to");
        assertEquals("bob@example.com", toList.get(0));
        org.junit.jupiter.api.Assertions.assertTrue(((String) payload.get("subject")).contains("Acme Team"));

        String html = (String) payload.get("html");
        org.junit.jupiter.api.Assertions.assertNotNull(html, "HTML content must be provided");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Acme Team"), "HTML should contain workspace name");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Alice Smith"), "HTML should contain inviter info");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Accept Invitation &amp; Join") || html.contains("Accept Invitation & Join"), "HTML should contain Accept Invitation & Join CTA");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("http://localhost:3000/join/tok12345"), "HTML should contain direct join URL");

        String text = (String) payload.get("text");
        org.junit.jupiter.api.Assertions.assertNotNull(text, "Text content must be provided");
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("Acme Team"), "Text should contain workspace name");
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("Alice Smith"), "Text should contain inviter info");
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("http://localhost:3000/join/tok12345"), "Text should contain direct join URL");

        List<Map<String, String>> tags = (List<Map<String, String>>) payload.get("tags");
        org.junit.jupiter.api.Assertions.assertNotNull(tags);
        org.junit.jupiter.api.Assertions.assertEquals("invitation", tags.get(0).get("value"));
    }

    @Test
    void sendWorkspaceInvitation_withCustomFrontendUrl_ShouldTargetConfiguredUrl() {
        // Arrange
        emailService.setFrontendUrl("https://app.customdomain.com/");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Act
        emailService.sendWorkspaceInvitation("bob@example.com", "Acme Team", "tok12345");

        // Assert
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), entityCaptor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> capturedEntity = entityCaptor.getValue();
        Map<String, Object> payload = capturedEntity.getBody();

        String html = (String) payload.get("html");
        String text = (String) payload.get("text");

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("https://app.customdomain.com/join/tok12345"));
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("https://app.customdomain.com/join/tok12345"));
    }

    @Test
    void sendWorkspaceInvitation_withoutInviterName_ShouldDispatchAppropriateSubjectAndBody() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Act
        emailService.sendWorkspaceInvitation("bob@example.com", "Acme Team", null, "tok12345");

        // Assert
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), entityCaptor.capture(), eq(String.class));

        Map<String, Object> payload = entityCaptor.getValue().getBody();
        org.junit.jupiter.api.Assertions.assertEquals("You have been invited to join Acme Team", payload.get("subject"));

        String html = (String) payload.get("html");
        String text = (String) payload.get("text");

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("You have been invited to collaborate in <strong>Acme Team</strong>"));
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("You have been invited to collaborate in Acme Team"));
    }

    @Test
    void sendWorkspaceInvitation_whenHttpFails_ShouldCatchAndNotThrowException() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert (should not throw)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                emailService.sendWorkspaceInvitation("bob@example.com", "Acme Team", "Alice", "tok12345")
        );
    }
}
