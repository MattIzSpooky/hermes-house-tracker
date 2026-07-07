package com.kropholler.dev.hermes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real, full {@code @SpringBootApplication} context (not a test slice) against
 * real Postgres. Test slices like {@code @DataJpaTest} restrict component scanning and can
 * mask wiring bugs that only surface under the application's actual full component scan —
 * this caught two real production failures: {@code EncryptionProperties} binding correctly
 * in every slice-based test but failing at real startup, and a duplicate {@code @Primary}
 * {@code ObjectMapper}/{@code JsonMapper} bean ambiguity. {@code JwtDecoder} is mocked
 * (established pattern elsewhere in this codebase) since the real one requires network
 * access to a live Keycloak instance, which is unrelated to what this test verifies.
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

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context.getBean(HermesBackendApplication.class)).isNotNull();
    }
}
