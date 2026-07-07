package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter @Setter @NoArgsConstructor
public class AgentTaskEntity implements EncryptionVersioned {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentTaskStatus status = AgentTaskStatus.ACTIVE;

    @Column(nullable = false)
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload = "{}";

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

    private String schedule;
    private Instant lastRunAt;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
