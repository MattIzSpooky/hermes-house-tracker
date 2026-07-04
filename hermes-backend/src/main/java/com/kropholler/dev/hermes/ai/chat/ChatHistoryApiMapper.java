package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.openapi.ChatMessageResponse;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatSessionSummaryResponse;
import com.kropholler.dev.hermes.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(config = MapStructConfig.class)
interface ChatHistoryApiMapper {

    ChatSessionSummaryResponse toResponse(ChatSessionSummaryDto dto);

    ChatMessageResponse toResponse(ChatMessageDto dto);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
