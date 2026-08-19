package com.example.chatapp.email.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final RestTemplate restTemplate;
    private final ResendTemplateInitializer templateInitializer;
    
    @Setter
    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Setter
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl = "http://localhost:3000";

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

    @Override
    public void sendWorkspaceInvitation(String to, String workspaceName, String token) {
        sendWorkspaceInvitation(to, workspaceName, null, token);
    }

    @Override
    public void sendWorkspaceInvitation(String to, String workspaceName, String inviterName, String token) {
        try {
            String baseUrl = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "http://localhost:3000";
            String joinUrl = baseUrl + "/join/" + token;

            boolean hasInviter = inviterName != null && !inviterName.trim().isEmpty();
            String subject = hasInviter
                    ? inviterName + " invited you to join " + workspaceName
                    : "You have been invited to join " + workspaceName;

            String inviterHtml = hasInviter
                    ? "<strong>" + escapeHtml(inviterName) + "</strong> has invited you to collaborate in <strong>" + escapeHtml(workspaceName) + "</strong> on Meow Chit Chat."
                    : "You have been invited to collaborate in <strong>" + escapeHtml(workspaceName) + "</strong> on Meow Chit Chat.";

            String inviterText = hasInviter
                    ? inviterName + " has invited you to collaborate in " + workspaceName + " on Meow Chit Chat."
                    : "You have been invited to collaborate in " + workspaceName + " on Meow Chit Chat.";

            String htmlBody = "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>" + escapeHtml(subject) + "</title>\n" +
                    "</head>\n" +
                    "<body style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f7f6; margin: 0; padding: 0; color: #333333;\">\n" +
                    "    <div style=\"max-width: 600px; margin: 40px auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); text-align: center;\">\n" +
                    "        <h1 style=\"color: #1a1a1a; margin-bottom: 8px; font-size: 24px; font-weight: 700;\">Meow Chit Chat</h1>\n" +
                    "        <div style=\"width: 48px; height: 3px; background-color: #007bff; margin: 12px auto 24px; border-radius: 2px;\"></div>\n" +
                    "        <h2 style=\"color: #333333; margin-bottom: 16px; font-size: 20px; font-weight: 600;\">You're invited to join <span style=\"color: #007bff;\">" + escapeHtml(workspaceName) + "</span></h2>\n" +
                    "        <p style=\"color: #555555; font-size: 16px; line-height: 1.6; margin-bottom: 28px;\">\n" +
                    "            " + inviterHtml + "\n" +
                    "        </p>\n" +
                    "        <div style=\"margin: 32px 0;\">\n" +
                    "            <a href=\"" + joinUrl + "\" style=\"display: inline-block; background-color: #007bff; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; box-shadow: 0 2px 6px rgba(0,123,255,0.3);\">Accept Invitation &amp; Join " + escapeHtml(workspaceName) + "</a>\n" +
                    "        </div>\n" +
                    "        <div style=\"margin-top: 36px; padding-top: 24px; border-top: 1px solid #eeeeee; text-align: left;\">\n" +
                    "            <p style=\"color: #777777; font-size: 13px; line-height: 1.5; margin-bottom: 8px;\">\n" +
                    "                If you're having trouble with the button above, copy and paste this link into your browser:\n" +
                    "            </p>\n" +
                    "            <p style=\"margin: 0; font-size: 13px; word-break: break-all;\">\n" +
                    "                <a href=\"" + joinUrl + "\" style=\"color: #007bff; text-decoration: underline;\">" + joinUrl + "</a>\n" +
                    "            </p>\n" +
                    "        </div>\n" +
                    "        <p style=\"color: #999999; font-size: 12px; margin-top: 32px; margin-bottom: 0;\">\n" +
                    "            If you did not expect this invitation, you can safely ignore this email.\n" +
                    "        </p>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";

            String textBody = inviterText + "\n\n" +
                    "Accept the invitation and join by visiting the link below:\n" +
                    joinUrl + "\n\n" +
                    "If you did not expect this invitation, you can safely ignore this email.";

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", "Meow Chit Chat <no-reply@mail.oakar.online>");
            payload.put("to", List.of(to));
            payload.put("subject", subject);
            payload.put("html", htmlBody);
            payload.put("text", textBody);
            payload.put("tags", List.of(Map.of("name", "category", "value", "invitation")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to send invitation email: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception while sending workspace invitation email", e);
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

