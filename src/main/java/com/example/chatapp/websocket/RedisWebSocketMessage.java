package com.example.chatapp.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisWebSocketMessage {
    private String destination;
    private String userId;
    private String payloadJson;
    private String payloadClassName;
    private boolean userMessage;
}
