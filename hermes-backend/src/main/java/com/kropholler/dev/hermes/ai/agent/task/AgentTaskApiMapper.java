package com.kropholler.dev.hermes.ai.agent.task;

import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
interface AgentTaskApiMapper {

    AgentTaskResponse toResponse(AgentTaskDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
