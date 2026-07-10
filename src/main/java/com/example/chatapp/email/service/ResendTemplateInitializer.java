package com.example.chatapp.email.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResendTemplateInitializer implements ApplicationRunner {

    private final RestTemplate restTemplate;

    @Setter
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Getter
    @Setter
    @Value("${resend.template.verification.id:}")
    private String templateId;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (templateId == null || templateId.trim().isEmpty()) {
            log.info("No Resend verification template ID found in properties. Creating a new template...");
            createTemplate();
        } else {
            log.info("Using existing Resend template ID: {}", templateId);
        }
    }

    private void createTemplate() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", "Meow Chit Chat Verification");

            String htmlTemplate = "<!DOCTYPE html>" +
                    "<html lang=\"en\">" +
                    "<head>" +
                    "<meta charset=\"UTF-8\">" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                    "<title>Verify Your Email</title>" +
                    "</head>" +
                    "<body style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7f6; margin: 0; padding: 0;\">" +
                    "<div style=\"max-width: 600px; margin: 40px auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); text-align: center;\">" +
                    "<h1 style=\"color: #333333; margin-bottom: 20px; font-size: 28px;\">Welcome to Meow Chit Chat!</h1>" +
                    "<p style=\"color: #666666; font-size: 16px; line-height: 1.6; margin-bottom: 30px;\">Please use the verification code below to complete your registration. This code is valid for 15 minutes.</p>" +
                    "<div style=\"background-color: #f8f9fa; border: 2px dashed #007bff; border-radius: 8px; padding: 20px; margin-bottom: 30px;\">" +
                    "<h2 style=\"margin: 0; color: #007bff; font-size: 36px; letter-spacing: 4px;\">{{{code}}}</h2>" +
                    "</div>" +
                    "<p style=\"color: #999999; font-size: 14px; margin-top: 40px;\">If you didn't request this email, you can safely ignore it.</p>" +
                    "</div>" +
                    "</body>" +
                    "</html>";

            payload.put("html", htmlTemplate);
            payload.put("text", "Your verification code is: {{{code}}}");
            payload.put("variables", java.util.List.of(
                java.util.Map.of("key", "code", "type", "string")
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/templates",
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                this.templateId = root.path("id").asText();
                log.info("Successfully created Resend Template. IMPORTANT: Please add this to your .env file: RESEND_TEMPLATE_VERIFICATION_ID={}", this.templateId);
            } else {
                log.error("Failed to create Resend Template: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception while creating Resend Template", e);
        }
    }
}
