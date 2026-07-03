package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.ai.agent.task.handler.WatchTaskHandler;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.PriceHistoryEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchTaskHandlerTest {

    @Mock ListingService listingService;
    @Spy ObjectMapper objectMapper;
    @InjectMocks
    WatchTaskHandler handler;

    @Test
    void returnsEmptyWhenNoNewListings() throws Exception {
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto old = listing(Instant.now().minusSeconds(7200)); // seen before lastRunAt
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(old));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsNotificationWhenNewListingFound() throws Exception {
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto newListing = listing(Instant.now().minusSeconds(60)); // seen after lastRunAt
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(newListing));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 new listing");
        assertThat(result.get().listingIds()).hasSize(1);
    }

    @Test
    void handle_lastRunAtNull_usesCreatedAt() throws Exception {
        // When lastRunAt is null, createdAt is used as the "since" baseline
        AgentTaskEntity task = watchTask(null);
        task.setCreatedAt(Instant.now().minusSeconds(3600));
        ListingDto newListing = listing(Instant.now().minusSeconds(60)); // seen after createdAt
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(newListing));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 new listing");
    }

    @Test
    void handle_firstSeenAtNull_noRecentPriceChange_returnsEmpty() throws Exception {
        // firstSeenAt == null → doesn't count as new, falls to price history check
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto noDate = listing(null); // firstSeenAt is null
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(noDate));
        when(listingService.findPriceHistoryByListingId(any())).thenReturn(List.of());

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    @Test
    void handle_priceHistoryWithOldAndNullTimestamps_notCountedAsChange() throws Exception {
        // Exercises anyMatch lambda branches: timestamp==null (false) and timestamp!=null+!isAfter (false)
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto old = listing(Instant.now().minusSeconds(7200));
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(old));
        PriceHistoryEntryDto nullTs = new PriceHistoryEntryDto(UUID.randomUUID(), 350000, "asking_price", null, null, null);
        PriceHistoryEntryDto oldTs  = new PriceHistoryEntryDto(UUID.randomUUID(), 340000, "asking_price", null, null, Instant.now().minusSeconds(7200));
        when(listingService.findPriceHistoryByListingId(any())).thenReturn(List.of(nullTs, oldTs));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    @Test
    void handle_priceChangedListing_returnsNotification() throws Exception {
        // Listing is old (not new), but has a recent asking_price entry → priceChanged = true
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto old = listing(Instant.now().minusSeconds(7200));
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(old));
        // null timestamp (false) then recent timestamp (true → anyMatch short-circuits)
        PriceHistoryEntryDto nullTs   = new PriceHistoryEntryDto(UUID.randomUUID(), 360000, "asking_price", null, null, null);
        PriceHistoryEntryDto recentTs = new PriceHistoryEntryDto(UUID.randomUUID(), 340000, "asking_price", null, null, Instant.now().minusSeconds(30));
        when(listingService.findPriceHistoryByListingId(any())).thenReturn(List.of(nullTs, recentTs));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 price change(s)");
        assertThat(result.get().body()).contains("Price changes:");
    }

    @Test
    void handle_mixedNewAndPriceChanged_returnsNotification() throws Exception {
        // Both a new listing and a price-changed listing exist → title includes both
        Instant since = Instant.now().minusSeconds(3600);
        AgentTaskEntity task = watchTask(since);
        ListingDto newListing = listing(Instant.now().minusSeconds(60));
        ListingDto oldListing = listing(Instant.now().minusSeconds(7200));
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(newListing, oldListing));
        PriceHistoryEntryDto recentTs = new PriceHistoryEntryDto(UUID.randomUUID(), 330000, "asking_price", null, null, Instant.now().minusSeconds(30));
        when(listingService.findPriceHistoryByListingId(any())).thenReturn(List.of(recentTs));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 new listing(s)").contains("1 price change(s)");
    }

    @Test
    void handle_newListingWithNullCurrentPrice_formatsWithoutPrice() throws Exception {
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        ListingDto noPrice = new ListingDto(
            UUID.randomUUID(), "funda1", "http://x.com", "Kerkstraat", "5", null,
            "1234AB", "Utrecht", "Utrecht", Instant.now().minusSeconds(60), Instant.now(),
            null, ListingStatus.FOR_SALE, null, 90, 3, 2, null, null, null);
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(noPrice));

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isPresent();
        assertThat(result.get().body()).doesNotContain("€");
    }

    @Test
    void handle_invalidPayload_returnsEmpty() throws Exception {
        AgentTaskEntity task = watchTask(Instant.now().minusSeconds(3600));
        task.setPayload("{not valid json");

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
    }

    private AgentTaskEntity watchTask(Instant lastRunAt) throws Exception {
        WatchPayload payload = new WatchPayload("Utrecht", null, null, 400000, 3, null, null, null, null, null);
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.WATCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setUserId(UUID.randomUUID());
        task.setName("Utrecht 3-bed");
        task.setPayload(new ObjectMapper().writeValueAsString(payload));
        task.setLastRunAt(lastRunAt);
        task.setNextRunAt(Instant.now());
        return task;
    }

    private ListingDto listing(Instant firstSeenAt) {
        return new ListingDto(
            UUID.randomUUID(),      // id
            "fundaId",              // fundaId
            "http://example.com",   // url
            "Herenstraat",          // street
            "10",                   // houseNumber
            null,                   // houseNumberAddition
            "3500AA",               // zipCode
            "Utrecht",              // city
            "Utrecht",              // province
            firstSeenAt,            // firstSeenAt
            Instant.now(),          // lastSeenAt
            350000,                 // currentPrice
            ListingStatus.FOR_SALE, // status
            null,                   // description
            120,                    // livingAreaM2
            5,                      // rooms
            3,                      // bedrooms
            null,                   // energyLabel
            null,                   // plotAreaM2
            null                    // location
        );
    }
}
