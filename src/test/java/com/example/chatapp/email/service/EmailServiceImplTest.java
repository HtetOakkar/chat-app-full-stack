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
}
