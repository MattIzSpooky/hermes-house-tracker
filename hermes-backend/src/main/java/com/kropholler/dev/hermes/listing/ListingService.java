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
    private final ListingMapper mapper;

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

    @Transactional(readOnly = true)
    public List<ListingDto> findForChat(Integer minPrice, Integer maxPrice,
                                         Integer minBedrooms, Integer minRooms,
                                         Integer minLivingAreaM2, String province,
                                         String city, String keywords) {
        return listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                        province, city, keywords, minPrice, maxPrice)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void deleteAllDeleted() {
        listingRepository.deleteAllByDeletedAtIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryEntryDto> findPriceHistoryByListingId(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId)
            .stream().map(mapper::toDto).toList();
    }

    private ListingDto toDto(Listing l) {
        Integer currentPrice = priceHistoryRepository
            .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
            .map(PriceHistoryEntry::getPrice)
            .orElse(null);
        return mapper.toDto(l, currentPrice);
    }


}
