package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.ai.agent.task.openapi.AgentTaskResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskApiMapperTest {

    private final AgentTaskApiMapper mapper = Mappers.getMapper(AgentTaskApiMapper.class);

    @Test
    void toResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-10T08:00:00Z");
        Instant lastRunAt = Instant.parse("2026-05-11T08:00:00Z");

        AgentTaskDto dto = new AgentTaskDto(
            id, AgentTaskType.WATCH, AgentTaskStatus.ACTIVE,
            userId, "My watch", "0 8 * * MON", lastRunAt, null, createdAt
        );

        AgentTaskResponse response = mapper.toResponse(dto);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getName()).isEqualTo("My watch");
        assertThat(response.getSchedule()).isEqualTo("0 8 * * MON");
        assertThat(response.getCreatedAt())
            .isEqualTo(OffsetDateTime.of(2026, 5, 10, 8, 0, 0, 0, ZoneOffset.UTC));
        assertThat(response.getLastRunAt())
            .isEqualTo(OffsetDateTime.of(2026, 5, 11, 8, 0, 0, 0, ZoneOffset.UTC));
        assertThat(response.getNextRunAt()).isNull();
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}
