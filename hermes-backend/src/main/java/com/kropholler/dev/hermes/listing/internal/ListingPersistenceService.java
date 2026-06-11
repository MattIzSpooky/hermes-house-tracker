package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingSnapshotsCreated;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.scraping.RawListing;
import com.kropholler.dev.hermes.scraping.ScrapingSessionCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingPersistenceService {

    private final ListingRepository listingRepository;
    private final ListingSnapshotRepository snapshotRepository;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScrapingSessionCompleted(ScrapingSessionCompleted event) {
        List<UUID> affectedListingIds = new ArrayList<>();

        for (RawListing raw : event.listings()) {
            Listing listing = listingRepository.findByFundaId(raw.fundaId())
                .orElseGet(() -> createListing(raw));

            listing.setLastSeenAt(Instant.now());
            listing = listingRepository.save(listing);

            ListingSnapshot snapshot = createSnapshot(listing.getId(), raw);
            snapshotRepository.save(snapshot);
            affectedListingIds.add(listing.getId());
        }

        if (!affectedListingIds.isEmpty()) {
            eventPublisher.publishEvent(new ListingSnapshotsCreated(affectedListingIds));
        }
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

    private ListingSnapshot createSnapshot(UUID listingId, RawListing raw) {
        ListingSnapshot s = new ListingSnapshot();
        s.setListingId(listingId);
        s.setAskingPrice(raw.askingPrice());
        s.setLivingAreaM2(raw.livingAreaM2());
        s.setRooms(raw.rooms());
        s.setEnergyLabel(raw.energyLabel());
        s.setListedOnFundaSince(raw.listedOnFundaSince());
        s.setStatus(parseStatus(raw.status()));
        return s;
    }

    private ListingStatus parseStatus(String status) {
        try {
            return ListingStatus.valueOf(status);
        } catch (Exception e) {
            return ListingStatus.FOR_SALE;
        }
    }
}
