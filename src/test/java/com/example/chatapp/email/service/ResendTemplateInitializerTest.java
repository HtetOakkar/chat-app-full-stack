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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResendTemplateInitializerTest {

    @Mock
    private RestTemplate restTemplate;

    private ResendTemplateInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new ResendTemplateInitializer(restTemplate);
        initializer.setResendApiKey("test_key");
    }

    @Test
    void shouldCreateTemplateIfIdIsEmpty() throws Exception {
        initializer.setTemplateId("");
        
        String mockResponse = "{\"id\": \"new_template_id\"}";
        when(restTemplate.postForEntity(eq("https://api.resend.com/templates"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        initializer.run(null);

        assertEquals("new_template_id", initializer.getTemplateId());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/templates"), captor.capture(), eq(String.class));

        Map<String, Object> payload = captor.getValue().getBody();
        assertEquals("Meow Chit Chat Verification", payload.get("name"));
        assertTrue(payload.get("html").toString().contains("{{{code}}}"));
        
        java.util.List<Map<String, String>> variables = (java.util.List<Map<String, String>>) payload.get("variables");
        org.junit.jupiter.api.Assertions.assertNotNull(variables);
        assertEquals("code", variables.get(0).get("key"));
        assertEquals("string", variables.get(0).get("type"));
    }

    @Test
    void shouldNotCreateTemplateIfIdIsNotEmpty() throws Exception {
        initializer.setTemplateId("existing_template_id");

        initializer.run(null);

        assertEquals("existing_template_id", initializer.getTemplateId());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }
}
