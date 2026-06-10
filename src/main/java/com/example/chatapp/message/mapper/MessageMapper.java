package com.example.chatapp.message.mapper;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;

public interface MessageMapper {
    MessageDto toMessageDto(Message message);
    Message toMessage(MessageDto messageDto);
}