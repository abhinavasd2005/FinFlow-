package com.finflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class EmailNotificationProvider {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.resend.com")
            .build();

    private final String apiKey;
    private final String from;

    public EmailNotificationProvider(
            @Value("${notification.resend.api-key:}") String apiKey,
            @Value("${notification.resend.from:}") String from) {
        this.apiKey = apiKey;
        this.from = from;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank() && !from.isBlank();
    }

    public String send(String recipient, String subject, String text, String html, String idempotencyKey) {
        JsonNode response = restClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", new String[]{recipient},
                        "subject", subject,
                        "text", text,
                        "html", html
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("id").isMissingNode()) {
            throw new IllegalStateException("Email provider returned no message ID");
        }

        return response.path("id").asText();
    }
}
