package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.internal.Listing;
import com.kropholler.dev.hermes.listing.internal.ListingRepository;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntry;
import com.kropholler.dev.hermes.listing.internal.PriceHistoryEntryRepository;
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
    private final PriceHistoryEntryRepository priceHistoryRepository;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
        if (params.isEmpty()) {
            return listingRepository.findAll(pageable).map(this::toDto);
        }
        return listingRepository.findAll(ListingSpecifications.withParams(params), pageable)
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findById(UUID id) {
        return listingRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByFundaId(String fundaId) {
        return listingRepository.findByFundaId(fundaId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ListingDto> findAllActive(Pageable pageable) {
        return listingRepository.findAllByDeletedAtIsNull(pageable).map(this::toDto);
    }

    @Transactional
    public void deleteAllDeleted() {
        listingRepository.deleteAllByDeletedAtIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryEntryDto> findPriceHistoryByListingId(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId)
            .stream().map(this::toPriceHistoryDto).toList();
    }

    private ListingDto toDto(Listing l) {
        Integer currentPrice = priceHistoryRepository
            .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
            .map(PriceHistoryEntry::getPrice)
            .orElse(null);
        return new ListingDto(
            l.getId(), l.getFundaId(), l.getUrl(),
            l.getStreet(), l.getHouseNumber(), l.getHouseNumberAddition(),
            l.getZipCode(), l.getCity(), l.getProvince(),
            l.getFirstSeenAt(), l.getLastSeenAt(), currentPrice, l.getStatus(),
            l.getDescription(), l.getLivingAreaM2(), l.getRooms(),
            l.getBedrooms(), l.getEnergyLabel(), l.getPlotAreaM2()
        );
    }

    private PriceHistoryEntryDto toPriceHistoryDto(PriceHistoryEntry e) {
        return new PriceHistoryEntryDto(e.getId(), e.getPrice(), e.getStatus(),
            e.getSource(), e.getDate(), e.getTimestamp());
    }
}
