package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsAuthChannelInterceptorTest {

    @Mock JwtDecoder jwtDecoder;
    @Mock MessageChannel channel;

    WsAuthChannelInterceptor interceptor;

    private Jwt validJwt(UUID subject) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }

    private Message<byte[]> connectFrameWithAuthHeader(String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (headerValue != null) {
            accessor.addNativeHeader("Authorization", headerValue);
        }
        // Real STOMP frames stay mutable when decoded (StompDecoder calls setLeaveMutable(true)
        // before building the message), which is what lets our interceptor mutate the same
        // accessor instance StompSubProtocolHandler attaches its userChangeCallback to. Without
        // this, getMessageHeaders() would freeze the message, which no real CONNECT frame does.
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        interceptor = new WsAuthChannelInterceptor(jwtDecoder);
    }

    @Test
    void preSend_withValidBearerToken_setsAuthenticatedPrincipal() {
        UUID subject = UUID.randomUUID();
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt(subject));

        Message<byte[]> connectFrame = connectFrameWithAuthHeader("Bearer valid-token");
        Message<?> result = interceptor.preSend(connectFrame, channel);

        assertThat(result).isSameAs(connectFrame);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        JwtAuthenticationToken principal = (JwtAuthenticationToken) accessor.getUser();
        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo(subject.toString());
        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority())
            .contains("ROLE_USER");
    }

    @Test
    void preSend_withMissingAuthHeader_throws() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrameWithAuthHeader(null), channel))
            .isInstanceOf(org.springframework.messaging.MessagingException.class);
    }

    @Test
    void preSend_withInvalidToken_throws() {
        when(jwtDecoder.decode("garbage")).thenThrow(
            new org.springframework.security.oauth2.jwt.JwtException("invalid"));

        assertThatThrownBy(() -> interceptor.preSend(connectFrameWithAuthHeader("Bearer garbage"), channel))
            .isInstanceOf(org.springframework.messaging.MessagingException.class);
    }

    @Test
    void preSend_nonConnectFrame_passesThroughUnchanged() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }
}
