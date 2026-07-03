package com.kropholler.dev.hermes.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@RequiredArgsConstructor
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        Assert.state(accessor != null, "No StompHeaderAccessor found on message");
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Missing or malformed Authorization header on STOMP CONNECT");
        }

        String token = authHeader.substring("Bearer ".length());
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new MessagingException("Invalid bearer token on STOMP CONNECT", e);
        }

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, SecurityConfig.realmRoleAuthorities(jwt));
        // Must mutate this exact accessor instance (not a wrap()'d copy): StompSubProtocolHandler
        // attaches a userChangeCallback to it during CONNECT, and only calling setUser() on that
        // same instance notifies it so the principal is remembered for the rest of the STOMP session.
        accessor.setUser(authentication);
        return message;
    }
}
