package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.notification.openapi.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationApiMapperTest {

    private final NotificationApiMapper mapper = Mappers.getMapper(NotificationApiMapper.class);

    @Test
    void toResponse_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");

        NotificationDto dto = new NotificationDto(
            id, taskId, userId, "Price drop", "Amsterdam dropped 8%",
            List.of(listingId), false, createdAt, null
        );

        NotificationResponse response = mapper.toResponse(dto);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getTaskId()).isEqualTo(taskId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getTitle()).isEqualTo("Price drop");
        assertThat(response.getBody()).isEqualTo("Amsterdam dropped 8%");
        assertThat(response.getListingIds()).containsExactly(listingId);
        assertThat(response.getRead()).isFalse();
        assertThat(response.getCreatedAt())
            .isEqualTo(OffsetDateTime.of(2026, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(response.getEmailSentAt()).isNull();
    }

    @Test
    void toResponse_nullListingIds_defaultsToEmptyList() {
        UUID id = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(
            id, null, UUID.randomUUID(), "Title", "Body",
            null, true, Instant.now(), null
        );

        NotificationResponse response = mapper.toResponse(dto);

        assertThat(response.getListingIds()).isNotNull().isEmpty();
    }

    @Test
    void toOffsetDateTime_returnsNullForNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }
}
