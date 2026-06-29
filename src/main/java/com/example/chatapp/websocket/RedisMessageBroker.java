package com.example.chatapp.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
@Profile("clustered")
@RequiredArgsConstructor
@Slf4j
public class RedisMessageBroker implements MessageBroker, MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void publishToTopic(String destination, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            RedisWebSocketMessage msg = RedisWebSocketMessage.builder()
                    .destination(destination)
                    .payloadJson(payloadJson)
                    .payloadClassName(payload.getClass().getName())
                    .userMessage(false)
                    .build();
            redisTemplate.convertAndSend("websocket:messages", msg);
        } catch (Exception e) {
            log.error("Failed to publish message to topic {}", destination, e);
            throw new RuntimeException("Failed to publish message to topic", e);
        }
    }

    @Override
    public void publishToUser(String userId, String destination, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            RedisWebSocketMessage msg = RedisWebSocketMessage.builder()
                    .userId(userId)
                    .destination(destination)
                    .payloadJson(payloadJson)
                    .payloadClassName(payload.getClass().getName())
                    .userMessage(true)
                    .build();
            redisTemplate.convertAndSend("websocket:messages", msg);
        } catch (Exception e) {
            log.error("Failed to publish message to user {} at destination {}", userId, destination, e);
            throw new RuntimeException("Failed to publish message to user", e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RedisWebSocketMessage redisMsg;
            if (redisTemplate.getValueSerializer() != null) {
                Object deserialized = redisTemplate.getValueSerializer().deserialize(message.getBody());
                if (deserialized instanceof RedisWebSocketMessage) {
                    redisMsg = (RedisWebSocketMessage) deserialized;
                } else {
                    redisMsg = objectMapper.convertValue(deserialized, RedisWebSocketMessage.class);
                }
            } else {
                redisMsg = objectMapper.readValue(message.getBody(), RedisWebSocketMessage.class);
            }

            if (redisMsg == null) {
                log.warn("Received empty Redis message");
                return;
            }

            Class<?> payloadClass = Class.forName(redisMsg.getPayloadClassName());
            Object payload = objectMapper.readValue(redisMsg.getPayloadJson(), payloadClass);

            if (redisMsg.isUserMessage()) {
                if (simpUserRegistry.getUser(redisMsg.getUserId()) != null) {
                    messagingTemplate.convertAndSendToUser(redisMsg.getUserId(), redisMsg.getDestination(), payload);
                }
            } else {
                messagingTemplate.convertAndSend(redisMsg.getDestination(), payload);
            }
        } catch (Exception e) {
            log.error("Error handling Redis message", e);
        }
    }
}
