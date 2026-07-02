package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final PriceHistoryEntryRepository priceHistoryRepository;
    private final PriceHistoryService priceHistoryService;
    private final ListingMapper mapper;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public Page<ListingDto> findAll(ListingSearchParams params, Pageable pageable) {
        if (params.isEmpty()) {
            return listingRepository.findAll(pageable).map(this::toDto);
        }
        Specification<ListingEntity> spec = params.hasRadiusSearch()
                ? ListingSpecifications.withParamsForRadius(params)
                : ListingSpecifications.withParams(params);
        if (params.hasRadiusSearch()) {
            GeocodeResult latLon = resolveRadiusCenter(params);
            if (latLon != null) {
                spec = spec.and(ListingSpecifications.withinRadius(latLon.lon(), latLon.lat(), params.radiusKm() * 1000));
            }
        }
        return listingRepository
                .findAll(spec, pageable)
                .map(this::toDto);
    }

    private GeocodeResult resolveRadiusCenter(ListingSearchParams params) {
        if (params.street() != null && !params.street().isBlank()) {
            return geocodingService.geocodeAddress(
                    params.houseNumber() != null ? params.houseNumber() : "",
                    params.street(),
                    params.city() != null ? params.city() : ""
            ).orElse(null);
        }
        return geocodingService.findOrFetchCity(params.city())
                .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null))
                .orElse(null);
    }

    private GeocodeResult resolveLatLon(String nearAddress, String nearCity) {
        if (nearAddress != null && !nearAddress.isBlank()) {
            return geocodingService.geocodeAddress(nearAddress, "", "").orElse(null);
        }
        if (nearCity != null && !nearCity.isBlank()) {
            return geocodingService.findOrFetchCity(nearCity)
                    .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null))
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
            GeocodeResult latLon = resolveLatLon(nearAddress, nearCity);
            if (latLon != null) {
                return listingRepository.searchForChatNearLocation(
                                minBedrooms, minRooms, minLivingAreaM2,
                                province, keywords, minPrice, maxPrice,
                                latLon.lon(), latLon.lat(), radiusKm * 1000)
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
                    int original = history.getFirst().price();
                    int current = history.getLast().price();
                    double drop = (original - current) * 100.0 / original;
                    return new PriceDropResult(toDto(listing), original, current, drop);
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.dropPercent(), a.dropPercent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ListingDto> findByAddress(String street, String houseNumber, String city) {
        String s = street != null ? street.strip() : null;
        String n = houseNumber != null ? houseNumber.strip() : null;
        String c = city != null && !city.isBlank() ? city.strip() : null;
        if (c != null) {
            List<ListingEntity> withCity = listingRepository
                    .findByStreetIgnoreCaseAndHouseNumberIgnoreCaseAndCityIgnoreCase(s, n, c);
            if (!withCity.isEmpty()) return Optional.of(toDto(withCity.get(0)));
        }
        return listingRepository.findByStreetIgnoreCaseAndHouseNumberIgnoreCase(s, n)
                .stream().findFirst().map(this::toDto);
    }

    public void refreshAllPriceHistory() {
        priceHistoryService.refreshAll();
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

    private ListingDto toDto(ListingEntity l) {
        Integer currentPrice = priceHistoryRepository
                .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
                .map(PriceHistoryEntryEntity::getPrice)
                .orElse(null);
        return mapper.toDto(l, currentPrice, null);
    }


}
