package com.kropholler.dev.hermes.ai.agent.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_tasks")
@Getter @Setter @NoArgsConstructor
public class AgentTaskEntity {

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

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload = "{}";

    private String schedule;
    private Instant lastRunAt;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
