package com.example.chatapp.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!clustered")
@RequiredArgsConstructor
public class InMemoryMessageBroker implements MessageBroker {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishToTopic(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    @Override
    public void publishToUser(String userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(userId, destination, payload);
    }
}
