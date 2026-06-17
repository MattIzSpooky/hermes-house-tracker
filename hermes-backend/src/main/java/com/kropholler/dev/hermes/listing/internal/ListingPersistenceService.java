package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.ListingNotFound;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final JmsTemplate jmsTemplate;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
        for (RawListing raw : event.listings()) {
            boolean isNew = listingRepository.findByFundaId(raw.fundaId()).isEmpty();
            Listing listing = listingRepository.findByFundaId(raw.fundaId())
                .orElseGet(() -> createListing(raw));

            listing.setLastSeenAt(Instant.now());
            listing.setLastUpdatedAt(Instant.now());
            listing.setStatus(parseStatus(raw.status()));
            Listing saved = listingRepository.save(listing);

            // Always enqueue detail fetch — runs on both initial scrape and rescrape
            jmsTemplate.convertAndSend(JmsQueues.LISTING_DETAILS_FETCH,
                new FetchListingDetailsCommand(saved.getId(), saved.getFundaId()));

            if (isNew || event.type() == ScrapingSessionType.RESCRAPE) {
                jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
                    new FetchPriceHistoryCommand(saved.getId(), saved.getFundaId()));
            }

            if (isNew) {
                jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH,
                    new FetchGeocodingCommand(saved.getId()));
            }
        }
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingNotFound(ListingNotFound event) {
        listingRepository.findByFundaId(event.fundaId()).ifPresent(listing -> {
            listing.setStatus(ListingStatus.DELETED);
            listing.setDeletedAt(Instant.now());
            listing.setLastUpdatedAt(Instant.now());
            listingRepository.save(listing);
        });
    }

    private Listing createListing(RawListing raw) {
        Listing l = new Listing();
        l.setFundaId(raw.fundaId());
        l.setUrl(raw.url());
        l.setStreet(raw.street());
        l.setHouseNumber(raw.houseNumber());
        l.setHouseNumberAddition(raw.houseNumberAddition());
        l.setZipCode(raw.zipCode());
        l.setCity(raw.city());
        l.setProvince(raw.province());
        return l;
    }

    private ListingStatus parseStatus(String status) {
        try {
            return ListingStatus.valueOf(status);
        } catch (Exception e) {
            return ListingStatus.FOR_SALE;
        }
    }
}
