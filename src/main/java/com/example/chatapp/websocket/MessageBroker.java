package com.example.chatapp.websocket;

public interface MessageBroker {
    void publishToTopic(String destination, Object payload);
    void publishToUser(String userId, String destination, Object payload);
}
