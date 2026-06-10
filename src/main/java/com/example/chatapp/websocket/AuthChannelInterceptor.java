package com.example.chatapp.websocket;

import com.example.chatapp.exception.UnauthorizedException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.jwt.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("CONNECT frame received. Processing authentication...");
            String bearerToken = accessor.getFirstNativeHeader("Authorization");

            try {
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                    String jwt = bearerToken.substring(7);

                    if (!jwtService.validateToken(jwt)) {
                        log.warn("JWT validation failed during WebSocket CONNECT");
                        throw new UnauthorizedException("Invalid JWT token");
                    }

                    String username = jwtService.extractUsername(jwt);
                    log.info("Token valid. Authenticating user: {}", username);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (userDetails instanceof UserPrincipal userPrincipalDetails) {
                        accessor.setUser(userPrincipalDetails);
                        log.info("User ID '{}' authenticated successfully and principal set.",
                                userPrincipalDetails.getName());
                    } else {
                        throw new UnauthorizedException("Principal is not of type UserPrincipal");
                    }
                } else {
                    log.error("CONNECT frame lacks an Authorization header or Bearer token.");
                    throw new UnauthorizedException("Authorization header is missing or invalid.");
                }
            } catch (Exception e) {
                log.error("Authentication error during WebSocket connect: {}", e.getMessage());
                throw new UnauthorizedException("Authentication failed: " + e.getMessage());
            }
        }
        return message;
    }
}
