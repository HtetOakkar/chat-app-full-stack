package com.example.chatapp.websocket;

import com.example.chatapp.message.model.dto.MessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisMessageBrokerEdgeCaseTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SimpUserRegistry simpUserRegistry;

    private ObjectMapper objectMapper;
    private RedisMessageBroker redisMessageBroker;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        redisMessageBroker = new RedisMessageBroker(redisTemplate, messagingTemplate, simpUserRegistry, objectMapper);
    }

    @Test
    void publishToTopicShouldThrowRuntimeExceptionWhenRedisIsUnavailable() {
        // Arrange
        String destination = "/topic/public";
        MessageDto payload = MessageDto.builder().content("Test").build();

        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(redisTemplate).convertAndSend(eq("websocket:messages"), any(RedisWebSocketMessage.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            redisMessageBroker.publishToTopic(destination, payload);
        });

        assertTrue(exception.getMessage().contains("Failed to publish message to topic"));
        assertEquals(RedisConnectionFailureException.class, exception.getCause().getClass());
    }

    @Test
    void publishToUserShouldThrowRuntimeExceptionWhenRedisIsUnavailable() {
        // Arrange
        String userId = "123";
        String destination = "/queue/messages";
        MessageDto payload = MessageDto.builder().content("Test").build();

        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(redisTemplate).convertAndSend(eq("websocket:messages"), any(RedisWebSocketMessage.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            redisMessageBroker.publishToUser(userId, destination, payload);
        });

        assertTrue(exception.getMessage().contains("Failed to publish message to user"));
        assertEquals(RedisConnectionFailureException.class, exception.getCause().getClass());
    }

    @Test
    void onMessageShouldHandleClassNotFoundExceptionGracefully() throws Exception {
        // Arrange
        RedisWebSocketMessage envelope = RedisWebSocketMessage.builder()
                .destination("/topic/public")
                .payloadJson("{\"content\":\"test\"}")
                .payloadClassName("com.example.chatapp.NonExistentClass")
                .userMessage(false)
                .build();

        byte[] bodyBytes = objectMapper.writeValueAsBytes(envelope);
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(bodyBytes);

        // Act & Assert
        // This should not throw any exception as it should be caught and logged inside onMessage
        assertDoesNotThrow(() -> {
            redisMessageBroker.onMessage(redisMsg, new byte[0]);
        });

        // Verify that messagingTemplate was never called because deserialization failed
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void onMessageShouldHandleInvalidJsonGracefully() throws Exception {
        // Arrange
        byte[] bodyBytes = "invalid-json".getBytes();
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(bodyBytes);

        // Act & Assert
        assertDoesNotThrow(() -> {
            redisMessageBroker.onMessage(redisMsg, new byte[0]);
        });

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void onMessageShouldHandleNullMessageGracefully() {
        // Arrange
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(null);

        // Act & Assert
        assertDoesNotThrow(() -> {
            redisMessageBroker.onMessage(redisMsg, new byte[0]);
        });

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
