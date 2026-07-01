package com.kropholler.dev.hermes.ai.tool;

import com.kropholler.dev.hermes.ai.chat.ChatListingCard;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.favorites.FavoriteDto;
import com.kropholler.dev.hermes.favorites.FavoriteService;
import com.kropholler.dev.hermes.listing.ListingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GetFavouriteListingsTool {

    private final UUID clientId;
    private final FavoriteService favoriteService;
    private final ListingService listingService;
    private final ChatListingCardMapper mapper;
    private final AtomicReference<List<ChatListingCard>> resultHolder;
    private final Counter callCounter;

    public GetFavouriteListingsTool(UUID clientId,
                                     FavoriteService favoriteService,
                                     ListingService listingService,
                                     ChatListingCardMapper mapper,
                                     AtomicReference<List<ChatListingCard>> resultHolder,
                                     MeterRegistry meterRegistry) {
        this.clientId = clientId;
        this.favoriteService = favoriteService;
        this.listingService = listingService;
        this.mapper = mapper;
        this.resultHolder = resultHolder;
        this.callCounter = meterRegistry.counter("hermes.ai.tool.calls", "tool", "getFavouriteListings");
    }

    @Tool(description = "Get the user's saved (favourited) listings. "
            + "Call this when the user asks to see their saved properties, favourites, or wishlist.")
    public String getFavouriteListings() {
        log.info("getFavouriteListings called: clientId={}", clientId);
        callCounter.increment();

        List<FavoriteDto> favourites = favoriteService.findByClientId(clientId);
        if (favourites.isEmpty()) {
            return "You have no saved listings yet. You can save a listing by clicking the heart icon on its detail page.";
        }

        List<ChatListingCard> cards = favourites.stream()
                .map(f -> listingService.findById(f.listingId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> mapper.toChatListingCard(opt.get()))
                .toList();
        resultHolder.set(cards);

        if (cards.isEmpty()) {
            return "Your saved listings could not be found — they may have been removed.";
        }

        StringBuilder sb = new StringBuilder("You have ").append(cards.size()).append(" saved listing(s):\n\n");
        for (ChatListingCard c : cards) {
            sb.append("- ").append(c.street()).append(" ").append(c.houseNumber());
            if (c.houseNumberAddition() != null) sb.append(c.houseNumberAddition());
            sb.append(", ").append(c.city());
            if (c.currentPrice() != null) sb.append(" — €").append(String.format("%,d", c.currentPrice()).replace(",", "."));
            sb.append("\n");
        }
        return sb.toString();
    }
}
