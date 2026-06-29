package com.example.chatapp.websocket;

import com.example.chatapp.message.model.dto.MessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisMessageBrokerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SimpUserRegistry simpUserRegistry;

    @Mock
    private RedisSerializer<Object> valueSerializer;

    private ObjectMapper objectMapper;
    private RedisMessageBroker redisMessageBroker;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Support Java 8 Date/Time API if any
        redisMessageBroker = new RedisMessageBroker(redisTemplate, messagingTemplate, simpUserRegistry, objectMapper);
    }

    @Test
    void publishToTopicShouldSerializeAndSendToRedis() throws Exception {
        // Arrange
        String destination = "/topic/public";
        MessageDto payload = MessageDto.builder()
                .content("Hello World")
                .senderId(1L)
                .build();

        // Act
        redisMessageBroker.publishToTopic(destination, payload);

        // Assert
        ArgumentCaptor<RedisWebSocketMessage> captor = ArgumentCaptor.forClass(RedisWebSocketMessage.class);
        verify(redisTemplate).convertAndSend(eq("websocket:messages"), captor.capture());

        RedisWebSocketMessage sentMsg = captor.getValue();
        assertNotNull(sentMsg);
        assertEquals(destination, sentMsg.getDestination());
        assertNull(sentMsg.getUserId());
        assertFalse(sentMsg.isUserMessage());
        assertEquals(MessageDto.class.getName(), sentMsg.getPayloadClassName());

        MessageDto deserializedPayload = objectMapper.readValue(sentMsg.getPayloadJson(), MessageDto.class);
        assertEquals(payload.getContent(), deserializedPayload.getContent());
        assertEquals(payload.getSenderId(), deserializedPayload.getSenderId());
    }

    @Test
    void publishToUserShouldSerializeAndSendToRedis() throws Exception {
        // Arrange
        String userId = "42";
        String destination = "/queue/messages";
        MessageDto payload = MessageDto.builder()
                .content("Secret Message")
                .senderId(1L)
                .build();

        // Act
        redisMessageBroker.publishToUser(userId, destination, payload);

        // Assert
        ArgumentCaptor<RedisWebSocketMessage> captor = ArgumentCaptor.forClass(RedisWebSocketMessage.class);
        verify(redisTemplate).convertAndSend(eq("websocket:messages"), captor.capture());

        RedisWebSocketMessage sentMsg = captor.getValue();
        assertNotNull(sentMsg);
        assertEquals(destination, sentMsg.getDestination());
        assertEquals(userId, sentMsg.getUserId());
        assertTrue(sentMsg.isUserMessage());
        assertEquals(MessageDto.class.getName(), sentMsg.getPayloadClassName());
    }

    @Test
    void onMessageShouldRouteTopicMessageToLocalSubscribers() throws Exception {
        // Arrange
        String destination = "/topic/public";
        MessageDto payload = MessageDto.builder()
                .content("Topic Broadcast")
                .senderId(1L)
                .build();

        RedisWebSocketMessage envelope = RedisWebSocketMessage.builder()
                .destination(destination)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadClassName(MessageDto.class.getName())
                .userMessage(false)
                .build();

        byte[] bodyBytes = objectMapper.writeValueAsBytes(envelope);
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(bodyBytes);

        // Act
        redisMessageBroker.onMessage(redisMsg, new byte[0]);

        // Assert
        verify(messagingTemplate).convertAndSend(eq(destination), any(MessageDto.class));
    }

    @Test
    void onMessageShouldRouteUserMessageIfUserIsConnectedLocally() throws Exception {
        // Arrange
        String userId = "42";
        String destination = "/queue/messages";
        MessageDto payload = MessageDto.builder()
                .content("Direct message")
                .senderId(1L)
                .build();

        RedisWebSocketMessage envelope = RedisWebSocketMessage.builder()
                .userId(userId)
                .destination(destination)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadClassName(MessageDto.class.getName())
                .userMessage(true)
                .build();

        byte[] bodyBytes = objectMapper.writeValueAsBytes(envelope);
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(bodyBytes);

        // Mock local session check
        SimpUser simpUser = mock(SimpUser.class);
        when(simpUserRegistry.getUser(userId)).thenReturn(simpUser);

        // Act
        redisMessageBroker.onMessage(redisMsg, new byte[0]);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(eq(userId), eq(destination), any(MessageDto.class));
    }

    @Test
    void onMessageShouldNotRouteUserMessageIfUserIsNotConnectedLocally() throws Exception {
        // Arrange
        String userId = "42";
        String destination = "/queue/messages";
        MessageDto payload = MessageDto.builder()
                .content("Direct message")
                .senderId(1L)
                .build();

        RedisWebSocketMessage envelope = RedisWebSocketMessage.builder()
                .userId(userId)
                .destination(destination)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadClassName(MessageDto.class.getName())
                .userMessage(true)
                .build();

        byte[] bodyBytes = objectMapper.writeValueAsBytes(envelope);
        Message redisMsg = mock(Message.class);
        when(redisMsg.getBody()).thenReturn(bodyBytes);

        // Mock local session check returning null (not online on this node)
        when(simpUserRegistry.getUser(userId)).thenReturn(null);

        // Act
        redisMessageBroker.onMessage(redisMsg, new byte[0]);

        // Assert
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
