package com.kropholler.dev.hermes.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
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

// ROLE_INFRASTRUCTURE opts this bean out of Spring Modulith's observability AOP wrapping
// (ModuleObservabilityBeanPostProcessor skips infrastructure-role beans). Without it, Modulith
// throws a NullPointerException trying to render preSend's Message<?> parameter for tracing -
// ResolvableType.resolve() returns null for the unbounded wildcard, and Modulith's
// FormattableType.of() doesn't null-check before calling getTypeName(). That NPE aborts preSend
// entirely, silently dropping every message sent through this channel (chat SEND included).
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
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
