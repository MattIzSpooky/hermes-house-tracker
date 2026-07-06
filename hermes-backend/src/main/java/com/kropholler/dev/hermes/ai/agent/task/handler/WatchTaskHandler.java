package com.kropholler.dev.hermes.ai.agent.task.handler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class WatchTaskHandler implements AgentTaskHandler {

    private final ListingService listingService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentTaskType getType() { return AgentTaskType.WATCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTaskEntity task) {
        WatchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), WatchPayload.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize WatchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        List<ListingDto> matches = listingService.findForChat(
            payload.minPrice(), payload.maxPrice(),
            payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
            payload.province(), payload.city(), payload.keywords(),
            false, null, payload.nearCity(), payload.radiusKm(), null
        );

        Instant since = task.getLastRunAt() != null ? task.getLastRunAt() : task.getCreatedAt();

        List<ListingDto> newListings = new ArrayList<>();
        List<ListingDto> priceChangedListings = new ArrayList<>();
        for (ListingDto l : matches) {
            if (l.firstSeenAt() != null && l.firstSeenAt().isAfter(since)) {
                newListings.add(l);
            } else {
                boolean priceChanged = listingService.findPriceHistoryByListingId(l.id())
                    .stream()
                    .filter(e -> "asking_price".equals(e.status()))
                    .anyMatch(e -> e.timestamp() != null && e.timestamp().isAfter(since));
                if (priceChanged) priceChangedListings.add(l);
            }
        }

        if (newListings.isEmpty() && priceChangedListings.isEmpty()) {
            log.info("Watch task {}: no new or price-changed listings found", task.getId());
            return Optional.empty();
        }

        List<ListingDto> all = new ArrayList<>(newListings);
        all.addAll(priceChangedListings);

        StringBuilder title = new StringBuilder();
        if (!newListings.isEmpty()) title.append(newListings.size()).append(" new listing(s)");
        if (!priceChangedListings.isEmpty()) {
            if (!title.isEmpty()) title.append(", ");
            title.append(priceChangedListings.size()).append(" price change(s)");
        }
        title.append(" for watch: ").append(task.getName());

        StringBuilder body = new StringBuilder();
        if (!newListings.isEmpty()) {
            body.append("New listings:\n");
            for (ListingDto l : newListings) appendLine(body, l);
        }
        if (!priceChangedListings.isEmpty()) {
            body.append("Price changes:\n");
            for (ListingDto l : priceChangedListings) appendLine(body, l);
        }

        List<UUID> ids = all.stream().map(ListingDto::id).toList();
        return Optional.of(new NotificationContent(title.toString(), body.toString(), ids));
    }

    private void appendLine(StringBuilder sb, ListingDto l) {
        sb.append("- ").append(l.street()).append(" ").append(l.houseNumber())
            .append(", ").append(l.city());
        if (l.currentPrice() != null)
            sb.append(" — €").append(String.format("%,d", l.currentPrice()).replace(",", "."));
        sb.append("\n");
    }
}
