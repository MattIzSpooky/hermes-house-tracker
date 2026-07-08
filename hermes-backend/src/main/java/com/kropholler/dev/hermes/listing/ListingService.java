package com.kropholler.dev.hermes.listing;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingGeoProjection;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
        log.debug("findAll called: params={}, page={}, size={}", params, pageable.getPageNumber(), pageable.getPageSize());
        if (params.isEmpty()) {
            Page<ListingEntity> page = listingRepository.findAll(pageable);
            Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
            return page.map(l -> toDto(l, geoById));
        }
        Specification<ListingEntity> spec = params.hasRadiusSearch()
                ? ListingSpecifications.withParamsForRadius(params)
                : ListingSpecifications.withParams(params);
        if (params.hasRadiusSearch()) {
            GeocodeResult latLon = resolveRadiusCenter(params);
            if (latLon != null) {
                spec = spec.and(ListingSpecifications.withinRadius(latLon.lon(), latLon.lat(), params.radiusKm() * 1000));
            } else {
                log.warn("findAll radius search could not resolve a center for params={}", params);
            }
        }
        Page<ListingEntity> page = listingRepository.findAll(spec, pageable);
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
        return page.map(l -> toDto(l, geoById));
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
        Page<ListingEntity> page = listingRepository.findAllByDeletedAtIsNull(pageable);
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(page.getContent()));
        return page.map(l -> toDto(l, geoById));
    }

    @Transactional(readOnly = true)
    public List<ListingDto> findForChat(Integer minPrice, Integer maxPrice,
                                        Integer minBedrooms, Integer minRooms,
                                        Integer minLivingAreaM2, String province,
                                        String city, String keywords,
                                        boolean sortByPriceDesc,
                                        String nearAddress, String nearCity, Integer radiusKm,
                                        Integer limit) {
        if (radiusKm != null && (nearAddress != null || nearCity != null)) {
            GeocodeResult latLon = resolveLatLon(nearAddress, nearCity);
            if (latLon != null) {
                return searchNearLocationInternal(latLon.lon(), latLon.lat(),
                        minBedrooms, minRooms, minLivingAreaM2, province, keywords,
                        minPrice, maxPrice, radiusKm * 1000, limit);
            }
        }
        List<ListingEntity> results = listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                        province, city, keywords, minPrice, maxPrice, sortByPriceDesc, clampLimit(limit));
        log.debug("findForChat matched {} listing(s)", results.size());
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
        return results.stream().map(l -> toDto(l, geoById)).toList();
    }

    /**
     * Radius search using coordinates that are already known (e.g. from a saved, geocoded
     * user profile) — skips the string-based geocoding {@link #resolveLatLon} performs.
     */
    @Transactional(readOnly = true)
    public List<ListingDto> findNearLocation(double lon, double lat,
                                              Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
                                              String province, String keywords,
                                              Integer minPrice, Integer maxPrice,
                                              int radiusMeters, Integer limit) {
        return searchNearLocationInternal(lon, lat, minBedrooms, minRooms, minLivingAreaM2,
                province, keywords, minPrice, maxPrice, radiusMeters, limit);
    }

    private List<ListingDto> searchNearLocationInternal(double lon, double lat,
                                                          Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
                                                          String province, String keywords,
                                                          Integer minPrice, Integer maxPrice,
                                                          int radiusMeters, Integer limit) {
        List<ListingEntity> results = listingRepository.searchForChatNearLocation(
                        minBedrooms, minRooms, minLivingAreaM2,
                        province, keywords, minPrice, maxPrice,
                        lon, lat, radiusMeters, clampLimit(limit));
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
        return results.stream().map(l -> toDto(l, geoById)).toList();
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return 5;
        return Math.min(limit, 15);
    }

    @Transactional(readOnly = true)
    public List<PriceDropResult> findPriceDropListings(String city, double minDropPercent) {
        List<UUID> ids = listingRepository.findListingIdsWithPriceDrop(city, minDropPercent)
                .stream().map(UUID::fromString).toList();
        log.debug("findPriceDropListings: city={}, minDropPercent={}, candidateCount={}", city, minDropPercent, ids.size());
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
        log.info("Refreshing price history for all listings");
        priceHistoryService.refreshAll();
    }

    @Transactional
    public void deleteAllDeleted() {
        log.info("Deleting all listings marked as deleted");
        listingRepository.deleteAllByDeletedAtIsNotNull();
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryEntryDto> findPriceHistoryByListingId(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByTimestampAsc(listingId)
                .stream().map(mapper::toDto).toList();
    }

    private List<UUID> idsOf(List<ListingEntity> entities) {
        return entities.stream().map(ListingEntity::getId).toList();
    }

    /**
     * Best-effort batched geo lookup. A failure here (e.g. a transient DB issue) must never
     * break listing search or detail — it only means the affected listings won't show a
     * location on the map.
     */
    private Map<UUID, ListingGeoProjection> fetchGeoSafely(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            return listingRepository.findGeoByIds(ids).stream()
                    .collect(Collectors.toMap(ListingGeoProjection::getId, p -> p));
        } catch (Exception e) {
            log.warn("Failed to load geo data for {} listing(s); continuing without map data", ids.size(), e);
            return Map.of();
        }
    }

    private GeoLocation toGeoLocation(ListingGeoProjection p) {
        if (p == null || p.getLatitude() == null || p.getLongitude() == null) return null;
        return new GeoLocation(p.getLatitude(), p.getLongitude(),
                p.getBboxLatMin(), p.getBboxLatMax(), p.getBboxLonMin(), p.getBboxLonMax());
    }

    private ListingDto toDto(ListingEntity l, Map<UUID, ListingGeoProjection> geoById) {
        Integer currentPrice = priceHistoryRepository
                .findFirstByListingIdAndStatusOrderByTimestampDesc(l.getId(), "asking_price")
                .map(PriceHistoryEntryEntity::getPrice)
                .orElse(null);
        GeoLocation location = toGeoLocation(geoById.get(l.getId()));
        return mapper.toDto(l, currentPrice, location);
    }

    private ListingDto toDto(ListingEntity l) {
        return toDto(l, fetchGeoSafely(List.of(l.getId())));
    }
}
