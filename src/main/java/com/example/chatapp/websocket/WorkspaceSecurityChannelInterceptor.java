package com.example.chatapp.websocket;

import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.workspace.model.entity.Channel;
import com.example.chatapp.workspace.model.entity.ChannelType;
import com.example.chatapp.workspace.repository.ChannelMemberRepository;
import com.example.chatapp.workspace.repository.ChannelRepository;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSecurityChannelInterceptor implements ChannelInterceptor {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    private static final Pattern WORKSPACE_CHANNEL_TOPIC = Pattern.compile("^/topic/workspace\\.(\\d+)\\.channel\\.(\\d+)(?:\\.typing)?$");
    private static final Pattern WORKSPACE_TOPIC = Pattern.compile("^/topic/workspace\\.(\\d+)\\.(?:presence|events)$");
    private static final Pattern WORKSPACE_QUEUE = Pattern.compile("^/(?:user/)?queue/workspace\\.(\\d+)(?:\\..+)?$");

    private static final Pattern INBOUND_CHANNEL = Pattern.compile("^/app/workspace\\.(\\d+)\\.channel\\.(\\d+)(?:\\..+)?$");
    private static final Pattern INBOUND_WORKSPACE = Pattern.compile("^/app/workspace\\.(\\d+)(?:\\..+)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        Principal principal = accessor.getUser();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return message;
        }

        Long userId = userPrincipal.getId();
        String destination = accessor.getDestination();

        if (destination == null) return message;

        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(destination, userId);
        } else if (StompCommand.SEND.equals(command)) {
            authorizeSend(destination, userId);
        }

        return message;
    }

    private void authorizeSubscription(String destination, Long userId) {
        Matcher channelMatcher = WORKSPACE_CHANNEL_TOPIC.matcher(destination);
        if (channelMatcher.matches()) {
            Long workspaceId = Long.parseLong(channelMatcher.group(1));
            Long channelId = Long.parseLong(channelMatcher.group(2));
            validateWorkspaceAndChannelAccess(workspaceId, channelId, userId);
            return;
        }

        Matcher wsMatcher = WORKSPACE_TOPIC.matcher(destination);
        if (wsMatcher.matches()) {
            Long workspaceId = Long.parseLong(wsMatcher.group(1));
            validateWorkspaceAccess(workspaceId, userId);
            return;
        }

        Matcher queueMatcher = WORKSPACE_QUEUE.matcher(destination);
        if (queueMatcher.matches()) {
            Long workspaceId = Long.parseLong(queueMatcher.group(1));
            validateWorkspaceAccess(workspaceId, userId);
        }
    }

    private void authorizeSend(String destination, Long userId) {
        Matcher inboundChannelMatcher = INBOUND_CHANNEL.matcher(destination);
        if (inboundChannelMatcher.matches()) {
            Long workspaceId = Long.parseLong(inboundChannelMatcher.group(1));
            Long channelId = Long.parseLong(inboundChannelMatcher.group(2));
            validateWorkspaceAndChannelAccess(workspaceId, channelId, userId);
            return;
        }

        Matcher inboundWsMatcher = INBOUND_WORKSPACE.matcher(destination);
        if (inboundWsMatcher.matches()) {
            Long workspaceId = Long.parseLong(inboundWsMatcher.group(1));
            validateWorkspaceAccess(workspaceId, userId);
        }
    }

    private void validateWorkspaceAccess(Long workspaceId, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            log.warn("Access denied: User {} is not a member of workspace {}", userId, workspaceId);
            throw new UnauthorizedException("Access Denied: Not a workspace member");
        }
    }

    private void validateWorkspaceAndChannelAccess(Long workspaceId, Long channelId, Long userId) {
        validateWorkspaceAccess(workspaceId, userId);

        Optional<Channel> channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isPresent()) {
            Channel ch = channelOpt.get();
            if (ch.getType() == ChannelType.PRIVATE) {
                if (!channelMemberRepository.isUserChannelMember(channelId, userId)) {
                    log.warn("Access denied: User {} is not a member of private channel {}", userId, channelId);
                    throw new UnauthorizedException("Access Denied: Not a member of this private channel");
                }
            }
        }
    }
}
