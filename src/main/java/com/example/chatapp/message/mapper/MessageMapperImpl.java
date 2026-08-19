package com.example.chatapp.message.mapper;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import org.springframework.stereotype.Component;

@Component
public class MessageMapperImpl implements MessageMapper {

    @Override
    public MessageDto toMessageDto(Message message) {
        if (message == null) {
            return null;
        }
        WorkspaceMember senderMember = message.getSenderMember();
        User senderUser = senderMember != null ? senderMember.getUser() : null;

        Long workspaceId = null;
        Long channelId = null;
        if (message.getConversation() != null) {
            if (message.getConversation().getWorkspace() != null) {
                workspaceId = message.getConversation().getWorkspace().getId();
            }
            if (message.getConversation().getChannel() != null) {
                channelId = message.getConversation().getChannel().getId();
            }
        }

        return MessageDto.builder()
                .id(message.getId())
                .workspaceId(workspaceId)
                .conversationId(message.getConversation() != null ? message.getConversation().getId() : null)
                .channelId(channelId)
                .content(message.getContent())
                .messageType(message.getMessageType())
                .senderMemberId(senderMember != null ? senderMember.getId() : null)
                .senderId(senderUser != null ? senderUser.getId() : null)
                .senderUsername(senderUser != null ? senderUser.getUsername() : null)
                .senderFullName(senderUser != null ? senderUser.getFullName() : null)
                .timestamp(message.getSentAt())
                .isRead(message.getIsRead())
                .isDeleted(message.getIsDeleted())
                .callOutcome(message.getCallOutcome())
                .callDuration(message.getCallDuration())
                .videoUsed(message.getVideoUsed())
                .build();
    }

    @Override
    public Message toMessage(MessageDto messageDto) {
        if (messageDto == null) {
            return null;
        }
        return Message.builder()
                .content(messageDto.getContent())
                .messageType(messageDto.getMessageType())
                .isDeleted(messageDto.getIsDeleted() != null ? messageDto.getIsDeleted() : false)
                .isRead(messageDto.getIsRead() != null ? messageDto.getIsRead() : false)
                .callOutcome(messageDto.getCallOutcome())
                .callDuration(messageDto.getCallDuration())
                .videoUsed(messageDto.getVideoUsed())
                .build();
    }
}
