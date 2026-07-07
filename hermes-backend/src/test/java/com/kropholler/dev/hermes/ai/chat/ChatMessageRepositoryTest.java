package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({
    ChatMessageRepositoryTest.Containers.class,
    FieldEncryptor.class,
    EncryptedStringConverter.class,
    EncryptionKeyVersionListener.class
})
@TestPropertySource(properties = {
    "spring.test.database.replace=none",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ChatMessageRepositoryTest {

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

    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired EntityManager em;

    private ChatMessageEntity saveMessage(UUID sessionId, UUID userId, String role, String content) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setSessionId(sessionId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        return chatMessageRepository.saveAndFlush(m);
    }

    @Test
    void content_isStoredEncryptedAtRest() {
        UUID userId = UUID.randomUUID();
        ChatMessageEntity saved = saveMessage(UUID.randomUUID(), userId, "USER", "a very secret message");
        em.clear();

        Object rawContent = em.createNativeQuery("SELECT content FROM chat_messages WHERE id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();

        assertThat(rawContent).isNotEqualTo("a very secret message");
        assertThat(chatMessageRepository.findById(saved.getId()).orElseThrow().getContent())
            .isEqualTo("a very secret message");
        assertThat(saved.getEncryptionKeyVersion()).isEqualTo(1);
    }

    @Test
    void findSessionOverviewsByUserId_groupsBySessionOrderedByMostRecentFirst() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID olderSession = UUID.randomUUID();
        UUID newerSession = UUID.randomUUID();
        saveMessage(olderSession, userId, "USER", "Older conversation");
        Thread.sleep(10); // ensure a strictly later created_at for the newer session
        saveMessage(newerSession, userId, "USER", "Newer conversation");

        List<ChatSessionOverviewProjection> result = chatMessageRepository.findSessionOverviewsByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSessionId()).isEqualTo(newerSession);
        assertThat(result.get(1).getSessionId()).isEqualTo(olderSession);
    }

    @Test
    void findSessionOverviewsByUserId_onlyReturnsCallersOwnSessions() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        saveMessage(UUID.randomUUID(), otherUserId, "USER", "Someone else's conversation");

        List<ChatSessionOverviewProjection> result = chatMessageRepository.findSessionOverviewsByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc_returnsFirstUserMessageDecrypted() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, userId, "USER", "First message in this conversation");
        saveMessage(sessionId, userId, "ASSISTANT", "A reply");
        saveMessage(sessionId, userId, "USER", "A follow-up question");

        Optional<ChatMessageEntity> result = chatMessageRepository
            .findFirstBySessionIdAndUserIdAndRoleOrderByCreatedAtAsc(sessionId, userId, "USER");

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("First message in this conversation");
    }

    @Test
    void deleteBySessionIdAndUserId_removesOnlyMatchingRows() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, userId, "USER", "Delete me");
        saveMessage(sessionId, userId, "ASSISTANT", "Delete me too");
        saveMessage(UUID.randomUUID(), otherUserId, "USER", "Untouched, different user");

        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, userId);

        assertThat(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)).isEmpty();
        assertThat(chatMessageRepository.findSessionOverviewsByUserId(otherUserId)).hasSize(1);
    }

    @Test
    void deleteBySessionIdAndUserId_wrongUserIsNoOp() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        saveMessage(sessionId, ownerId, "USER", "Not yours to delete");

        chatMessageRepository.deleteBySessionIdAndUserId(sessionId, callerId);

        assertThat(chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, ownerId)).hasSize(1);
    }
}
