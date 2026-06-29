package com.example.chatapp.message.mapper;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapperImpl implements MessageMapper {

    @Override
    public MessageDto toMessageDto(Message message) {
        if (message == null) {
            return null;
        }
        return MessageDto.builder()
                .id(message.getId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .recipientId(message.getRecipient() != null && !"system".equals(message.getRecipient().getUsername()) ? message.getRecipient().getId() : null)
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .senderFullName(message.getSender().getFullName())
                .timestamp(message.getSentAt())
                .isDelivered(message.getIsDelivered())
                .isRead(message.getIsRead())
                .isDeleted(message.getIsDeleted())
                .build();
    }

    @Override
    public Message toMessage(MessageDto messageDto) {
        return Message.builder()
                .messageType(messageDto.getMessageType())
                .content(messageDto.getContent())
                .isDeleted(messageDto.getIsDeleted() != null ? messageDto.getIsDeleted() : false)
                .build();
    }
}
