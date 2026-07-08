package com.kropholler.dev.hermes.ai.agent.task.handler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.listing.ListingChatSearchCriteria;
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

        log.info("Watch task {} started: userId={}, city={}, province={}, nearCity={}, radiusKm={}, minPrice={}, maxPrice={}, minBedrooms={}",
                task.getId(), task.getUserId(), payload.city(), payload.province(), payload.nearCity(),
                payload.radiusKm(), payload.minPrice(), payload.maxPrice(), payload.minBedrooms());

        List<ListingDto> matches = listingService.findForChat(ListingChatSearchCriteria.builder()
                .minPrice(payload.minPrice()).maxPrice(payload.maxPrice())
                .minBedrooms(payload.minBedrooms()).minRooms(payload.minRooms())
                .minLivingAreaM2(payload.minLivingAreaM2())
                .province(payload.province()).city(payload.city()).keywords(payload.keywords())
                .nearCity(payload.nearCity()).radiusKm(payload.radiusKm())
                .build());
        log.info("Watch task {}: {} total match(es) before new/price-changed filtering", task.getId(), matches.size());

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
        log.info("Watch task {} completed: {} new, {} price-changed listing(s) in notification",
                task.getId(), newListings.size(), priceChangedListings.size());
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
