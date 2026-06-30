package com.kropholler.dev.hermes.listing.data;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.async.command.FetchGeocodingCommand;
import com.kropholler.dev.hermes.listing.async.command.FetchListingDetailsCommand;
import com.kropholler.dev.hermes.listing.async.command.FetchPriceHistoryCommand;
import com.kropholler.dev.hermes.listing.async.JmsQueues;
import com.kropholler.dev.hermes.funda.ListingNotFound;
import com.kropholler.dev.hermes.funda.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final JmsTemplate jmsTemplate;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
        List<Runnable> afterCommit = new ArrayList<>();

        for (RawListing raw : event.listings()) {
            var existing = listingRepository.findByFundaId(raw.fundaId());
            boolean isNew = existing.isEmpty();
            ListingEntity listing = existing.orElseGet(() -> createListing(raw));

            Instant now = Instant.now();
            listing.setLastSeenAt(now);
            listing.setLastUpdatedAt(now);
            listing.setStatus(parseStatus(raw.status()));
            ListingEntity saved = listingRepository.saveAndFlush(listing);

            UUID savedId = saved.getId();
            String savedFundaId = saved.getFundaId();

            afterCommit.add(() -> jmsTemplate.convertAndSend(JmsQueues.LISTING_DETAILS_FETCH,
                new FetchListingDetailsCommand(savedId, savedFundaId)));

            if (isNew || event.type() == ScrapingSessionType.RESCRAPE) {
                afterCommit.add(() -> jmsTemplate.convertAndSend(JmsQueues.PRICE_HISTORY_FETCH,
                    new FetchPriceHistoryCommand(savedId, savedFundaId)));
            }

            if (isNew) {
                afterCommit.add(() -> jmsTemplate.convertAndSend(JmsQueues.GEOCODING_FETCH,
                    new FetchGeocodingCommand(savedId)));
            }
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.forEach(Runnable::run);
            }
        });
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

    private ListingEntity createListing(RawListing raw) {
        ListingEntity l = new ListingEntity();
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
