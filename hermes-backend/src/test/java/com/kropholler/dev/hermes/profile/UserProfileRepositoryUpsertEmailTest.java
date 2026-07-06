package com.kropholler.dev.hermes.profile;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UserProfileRepositoryUpsertEmailTest.Containers.class)
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class UserProfileRepositoryUpsertEmailTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            );
        }
    }

    @Autowired UserProfileRepository repository;
    @Autowired EntityManager em;

    @Test
    void upsertEmail_noExistingRow_createsBareRowWithEmail() {
        UUID userId = UUID.randomUUID();

        repository.upsertEmail(userId, "user@hermes.local");
        em.flush();
        em.clear();

        UserProfileEntity saved = repository.findById(userId).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("user@hermes.local");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStreet()).isNull();
    }

    @Test
    void upsertEmail_existingRow_updatesEmailOnly() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setStreet("Dorpstraat");
        existing.setEmail("old@hermes.local");
        Instant originalUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        existing.setUpdatedAt(originalUpdatedAt);
        repository.saveAndFlush(existing);
        em.clear();

        repository.upsertEmail(userId, "new@hermes.local");
        em.flush();
        em.clear();

        UserProfileEntity reloaded = repository.findById(userId).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@hermes.local");
        assertThat(reloaded.getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }
}
