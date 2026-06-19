package com.example.chatapp.email.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final RestTemplate restTemplate;
    private final ResendTemplateInitializer templateInitializer;
    
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Override
    public void sendVerificationEmail(String to, String code) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", "Meow Chit Chat <no-reply@mail.oakar.online>");
            payload.put("to", List.of(to));
            payload.put("subject", "Verify your email address");
            
            String templateId = templateInitializer.getTemplateId();
            if (templateId != null && !templateId.isEmpty()) {
                payload.put("template", Map.of(
                    "id", templateId,
                    "variables", Map.of("code", code)
                ));
            } else {
                // Fallback to text if template ID is somehow not initialized
                payload.put("text", "Your verification code is: " + code);
            }
            
            payload.put("tags", List.of(Map.of("name", "category", "value", "verification")));
            payload.put("headers", Map.of("X-Entity-Ref-ID", code));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails",
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to send email: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
