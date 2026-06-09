package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshot;
import com.kropholler.dev.hermes.listing.internal.ListingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(Pageable pageable) {
        return listingRepository.findAll(pageable).map(this::toDtoWithLatestSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findById(UUID id) {
        return listingRepository.findById(id).map(this::toDtoWithLatestSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByFundaId(String fundaId) {
        return listingRepository.findByFundaId(fundaId).map(this::toDtoWithLatestSnapshot);
    }

    @Transactional(readOnly = true)
    public List<ListingSnapshotDto> findSnapshotsByListingId(UUID listingId) {
        return snapshotRepository.findByListingIdOrderByScrapedAtAsc(listingId)
            .stream().map(this::toSnapshotDto).toList();
    }

    private ListingDto toDtoWithLatestSnapshot(Listing l) {
        ListingSnapshotDto latestSnapshot = snapshotRepository
            .findTopByListingIdOrderByScrapedAtDesc(l.getId())
            .map(this::toSnapshotDto)
            .orElse(null);
        return new ListingDto(l.getId(), l.getFundaId(), l.getUrl(),
            l.getStreet(), l.getHouseNumber(), l.getHouseNumberAddition(),
            l.getZipCode(), l.getCity(), l.getProvince(),
            l.getFirstSeenAt(), l.getLastSeenAt(), latestSnapshot);
    }

    private ListingSnapshotDto toSnapshotDto(ListingSnapshot s) {
        return new ListingSnapshotDto(s.getId(), s.getScrapedAt(), s.getAskingPrice(),
            s.getLivingAreaM2(), s.getRooms(), s.getEnergyLabel(),
            s.getListedOnFundaSince(), s.getStatus());
    }
}
