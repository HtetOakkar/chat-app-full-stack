package com.example.chatapp.message.model.dto;

import java.util.List;

public record MessagePage(List<MessageDto> messages, String nextCursor) {}
