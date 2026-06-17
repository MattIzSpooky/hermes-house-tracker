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
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
        if (params.hasRadiusSearch()) {
            double[] latLon = resolveLatLon(params.nearAddress(), params.nearCity());
            if (latLon != null) {
                return listingRepository.findNearby(latLon[1], latLon[0], params.radiusKm() * 1000, pageable)
                    .map(this::toDto);
            }
        }
        if (params.isEmpty()) {
            return listingRepository.findAll(pageable).map(this::toDto);
        }
        return listingRepository.findAll(ListingSpecifications.withParams(params), pageable)
            .map(this::toDto);
    }

    private double[] resolveLatLon(String nearAddress, String nearCity) {
        if (nearAddress != null && !nearAddress.isBlank()) {
            String[] parts = nearAddress.split(",", 3);
            String houseNumber = parts.length > 0 ? parts[0].strip() : "";
            String street      = parts.length > 1 ? parts[1].strip() : "";
            String city        = parts.length > 2 ? parts[2].strip() : "";
            return geocodingService.geocodeAddress(houseNumber, street, city).orElse(null);
        }
        if (nearCity != null && !nearCity.isBlank()) {
            return geocodingService.findOrFetchCity(nearCity)
                .map(c -> new double[]{c.getLatitude(), c.getLongitude()})
                .orElse(null);
        }
        return null;
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
                                         String city, String keywords,
                                         boolean sortByPriceDesc,
                                         String nearAddress, String nearCity, Integer radiusKm) {
        if (radiusKm != null && (nearAddress != null || nearCity != null)) {
            double[] latLon = resolveLatLon(nearAddress, nearCity);
            if (latLon != null) {
                return listingRepository.searchForChatNearLocation(
                        minBedrooms, minRooms, minLivingAreaM2,
                        province, keywords, minPrice, maxPrice,
                        latLon[1], latLon[0], radiusKm * 1000)
                    .stream().map(this::toDto).toList();
            }
        }
        return listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                        province, city, keywords, minPrice, maxPrice, sortByPriceDesc)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceDropResult> findPriceDropListings(String city, double minDropPercent) {
        List<UUID> ids = listingRepository.findListingIdsWithPriceDrop(city, minDropPercent)
                .stream().map(UUID::fromString).toList();
        return listingRepository.findByIdIn(ids).stream()
                .map(listing -> {
                    List<PriceHistoryEntryDto> history = priceHistoryRepository
                            .findByListingIdOrderByTimestampAsc(listing.getId())
                            .stream()
                            .filter(e -> "asking_price".equals(e.getStatus()))
                            .map(mapper::toDto)
                            .toList();
                    if (history.size() < 2) return null;
                    int original = history.get(0).price();
                    int current = history.get(history.size() - 1).price();
                    double drop = (original - current) * 100.0 / original;
                    return new PriceDropResult(toDto(listing), original, current, drop);
                })
                .filter(r -> r != null)
                .sorted((a, b) -> Double.compare(b.dropPercent(), a.dropPercent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByAddress(String street, String houseNumber, String city) {
        String s = street != null ? street.strip() : null;
        String n = houseNumber != null ? houseNumber.strip() : null;
        String c = city != null && !city.isBlank() ? city.strip() : null;
        if (c != null) {
            List<Listing> withCity = listingRepository
                    .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(s, n, c);
            if (!withCity.isEmpty()) return Optional.of(toDto(withCity.get(0)));
        }
        return listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(s, n)
                .stream().findFirst().map(this::toDto);
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
