package com.example.chatapp.chat.controller;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.OnlineStatusDto;
import com.example.chatapp.message.model.dto.TypingIndicatorDto;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.service.MessageService;
import com.example.chatapp.user.service.PresenceModule;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.service.ChannelService;
import com.example.chatapp.workspace.service.DirectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final MessageBroker messageBroker;
    private final MessageService messageService;
    private final PresenceModule presenceModule;
    private final ChannelService channelService;
    private final DirectMessageService directMessageService;

    // --- Workspace Channel Messaging ---
    @MessageMapping("/workspace.{workspaceId}.channel.{channelId}.send")
    public void sendWorkspaceChannelMessage(
            @DestinationVariable Long workspaceId,
            @DestinationVariable Long channelId,
            @Valid @Payload MessageDto messageDto,
            Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        channelService.sendChannelMessage(channelId, authenticatedUser.getId(), messageDto);
    }

    @MessageMapping("/workspace.{workspaceId}.channel.{channelId}.typing")
    public void sendWorkspaceChannelTyping(
            @DestinationVariable Long workspaceId,
            @DestinationVariable Long channelId,
            @Valid @Payload TypingIndicatorDto typingDto,
            Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        typingDto.setSenderId(authenticatedUser.getId());
        typingDto.setSenderUsername(authenticatedUser.getUsername());
        String topic = String.format("/topic/workspace.%d.channel.%d.typing", workspaceId, channelId);
        messageBroker.publishToTopic(topic, typingDto);
    }

    // --- Workspace Direct Messaging ---
    @MessageMapping("/workspace.{workspaceId}.dm.send")
    public void sendWorkspaceDirectMessage(
            @DestinationVariable Long workspaceId,
            @Valid @Payload MessageDto messageDto,
            Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long recipientId = messageDto.getRecipientId();
        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for DM send");
        }
        directMessageService.sendDirectMessage(workspaceId, authenticatedUser.getId(), recipientId, messageDto);
    }

    @MessageMapping("/workspace.{workspaceId}.dm.typing")
    public void sendWorkspaceDirectMessageTyping(
            @DestinationVariable Long workspaceId,
            @Valid @Payload TypingIndicatorDto typingDto,
            Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        typingDto.setSenderId(authenticatedUser.getId());
        typingDto.setSenderUsername(authenticatedUser.getUsername());
        Long recipientId = typingDto.getRecipientId();
        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for DM typing");
        }
        String queue = String.format("/queue/workspace.%d.typing", workspaceId);
        messageBroker.publishToUser(recipientId.toString(), queue, typingDto);
    }

    // --- Workspace Presence ---
    @MessageMapping("/workspace.{workspaceId}.presence")
    public void sendWorkspacePresence(
            @DestinationVariable Long workspaceId,
            @Valid @Payload OnlineStatusDto onlineStatusDto,
            Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Map<String, Object> map = new HashMap<>();
        map.put("workspaceId", workspaceId);
        map.put("userId", authenticatedUser.getId());
        map.put("username", authenticatedUser.getUsername());
        map.put("status", onlineStatusDto.getStatus());
        map.put("timestamp", Instant.now());

        String topic = String.format("/topic/workspace.%d.presence", workspaceId);
        messageBroker.publishToTopic(topic, map);
    }

    // --- Legacy STOMP Handlers (Maintained for Backward Compatibility) ---
    @MessageMapping("/chat.public")
    public void sendPublicMessage(@Valid @Payload MessageDto messageDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        messageDto.setSenderId(authenticatedUser.getId());
        messageDto.setSenderUsername(authenticatedUser.getUsername());
        messageDto.setSenderFullName(authenticatedUser.getFullName());
        messageDto.setRecipientId(null);
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setTimestamp(Instant.now());
        messageService.saveMessage(messageDto);
        messageBroker.publishToTopic("/topic/public", messageDto);
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Valid @Payload MessageDto messageDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Long senderId = authenticatedUser.getId();
        Long recipientId = messageDto.getRecipientId();

        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for private messages");
        }

        messageDto.setSenderId(senderId);
        messageDto.setSenderUsername(authenticatedUser.getUsername());
        messageDto.setSenderFullName(authenticatedUser.getFullName());
        messageDto.setMessageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType());
        messageDto.setTimestamp(Instant.now());
        messageDto.setIsDelivered(true);
        messageDto.setIsRead(false);

        messageService.saveMessage(messageDto);

        messageBroker.publishToUser(senderId.toString(), "/queue/messages", messageDto);
        messageBroker.publishToUser(recipientId.toString(), "/queue/messages", messageDto);
    }

    @MessageMapping("/noti.status")
    public void sendOnlineStatus(@Valid @Payload OnlineStatusDto onlineStatusDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        Map<String, Object> map = new HashMap<>();
        map.put("userId", authenticatedUser.getId());
        map.put("status", onlineStatusDto.getStatus());
        map.put("timestamp", Instant.now());
        map.put("username", authenticatedUser.getUsername());

        java.util.Set<Long> receivers = presenceModule.getEligiblePresenceReceivers(authenticatedUser.getId());
        for (Long receiverId : receivers) {
            messageBroker.publishToUser(receiverId.toString(), "/queue/online", map);
        }
        messageBroker.publishToUser(authenticatedUser.getId().toString(), "/queue/online", map);
    }

    @MessageMapping("/typing")
    public void sendTypingIndicator(@Valid @Payload TypingIndicatorDto typingDto, Principal principal) {
        UserPrincipal authenticatedUser = getAuthenticatedUser(principal);
        typingDto.setSenderId(authenticatedUser.getId());

        Long recipientId = typingDto.getRecipientId();
        if (recipientId == null) {
            throw new BadRequestException("recipientId is required for typing events");
        }

        messageBroker.publishToUser(recipientId.toString(), "/queue/typing", typingDto);
    }

    private UserPrincipal getAuthenticatedUser(Principal principal) {
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BadRequestException("Authenticated user not found");
        }
        return userPrincipal;
    }
}
