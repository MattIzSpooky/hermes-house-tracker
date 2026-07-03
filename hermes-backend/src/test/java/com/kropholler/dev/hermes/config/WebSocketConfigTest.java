package com.kropholler.dev.hermes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    private final WsAuthChannelInterceptor wsAuthChannelInterceptor = mock(WsAuthChannelInterceptor.class);
    private final WebSocketConfig config = new WebSocketConfig(wsAuthChannelInterceptor);

    @Test
    void configureMessageBroker_enablesTopicAndQueueBrokerAndSetsPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void configureClientInboundChannel_registersWsAuthChannelInterceptor() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(wsAuthChannelInterceptor);
    }

    @Test
    void registerStompEndpoints_addsWsChatWithOpenOrigins() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws/chat")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registration).setAllowedOriginPatterns("*");
    }

    @Test
    void configureMessageConverters_addsJacksonConverterAndReturnsFalse() {
        List<MessageConverter> converters = new ArrayList<>();

        boolean result = config.configureMessageConverters(converters);

        assertThat(result).isFalse();
        assertThat(converters).hasSize(1);
        assertThat(converters.get(0)).isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
