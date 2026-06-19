package com.kropholler.dev.hermes.agent.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.agent.AgentTaskType;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchTaskHandler implements AgentTaskHandler {

    private final ListingService listingService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentTaskType getType() { return AgentTaskType.WATCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTask task) {
        WatchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), WatchPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize WatchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        List<ListingDto> matches = listingService.findForChat(
            payload.minPrice(), payload.maxPrice(),
            payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
            payload.province(), payload.city(), payload.keywords(),
            false, null, payload.nearCity(), payload.radiusKm()
        );

        Instant since = task.getLastRunAt() != null ? task.getLastRunAt() : task.getCreatedAt();
        List<ListingDto> newListings = matches.stream()
            .filter(l -> l.firstSeenAt() != null && l.firstSeenAt().isAfter(since))
            .toList();

        if (newListings.isEmpty()) {
            log.info("Watch task {}: no new listings found", task.getId());
            return Optional.empty();
        }

        String title = newListings.size() + " new listing(s) match watch: " + task.getName();
        StringBuilder body = new StringBuilder();
        for (ListingDto l : newListings) {
            body.append("- ").append(l.street()).append(" ").append(l.houseNumber())
                .append(", ").append(l.city());
            if (l.currentPrice() != null)
                body.append(" — €").append(String.format("%,d", l.currentPrice()).replace(",", "."));
            body.append("\n");
        }

        List<UUID> ids = newListings.stream().map(ListingDto::id).toList();
        return Optional.of(new NotificationContent(title, body.toString(), ids));
    }
}
