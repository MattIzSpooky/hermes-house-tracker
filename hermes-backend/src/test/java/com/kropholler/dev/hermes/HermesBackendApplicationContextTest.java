package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.config.WsAuthChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Boots the real, full {@code @SpringBootApplication} context (not a test slice) against
 * real Postgres. Test slices like {@code @DataJpaTest} restrict component scanning and can
 * mask wiring bugs that only surface under the application's actual full component scan —
 * this caught three real production failures: {@code EncryptionProperties} binding correctly
 * in every slice-based test but failing at real startup, a duplicate {@code @Primary}
 * {@code ObjectMapper}/{@code JsonMapper} bean ambiguity, and Spring Modulith's observability
 * AOP wrapping crashing on {@code WsAuthChannelInterceptor.preSend}'s {@code Message<?>}
 * parameter (silently dropping every chat message). {@code JwtDecoder} is mocked (established
 * pattern elsewhere in this codebase) since the real one requires network access to a live
 * Keycloak instance, which is unrelated to what this test verifies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(HermesBackendApplicationContextTest.Containers.class)
@TestPropertySource(properties = "spring.flyway.enabled=true")
class HermesBackendApplicationContextTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
            );
        }
    }

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    JavaMailSender javaMailSender;

    @Autowired
    WsAuthChannelInterceptor wsAuthChannelInterceptor;

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context.getBean(HermesBackendApplication.class)).isNotNull();
    }

    @Test
    void wsAuthChannelInterceptor_preSendOnRealBean_doesNotThrow() {
        // Exercises the ACTUAL Spring-managed bean (potentially AOP-proxied by Spring Modulith's
        // observability instrumentation), not a plain `new WsAuthChannelInterceptor(...)` like
        // WsAuthChannelInterceptorTest uses. That distinction is exactly what let a real
        // production bug through: Modulith's observability tries to render this method's
        // `Message<?>` parameter for tracing and NPEs on the unbounded wildcard, silently
        // dropping every message sent through this channel (chat SEND included) - a plain
        // unproxied unit test can never see that failure mode.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> sendFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        assertThatCode(() -> wsAuthChannelInterceptor.preSend(sendFrame, channel)).doesNotThrowAnyException();
    }
}
