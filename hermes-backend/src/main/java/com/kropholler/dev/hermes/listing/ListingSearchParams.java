package com.kropholler.dev.hermes.listing;

public record ListingSearchParams(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    String energyLabel,
    Integer radiusKm
) {
    public boolean isEmpty() {
        return isBlank(street) && isBlank(houseNumber) && isBlank(houseNumberAddition)
            && isBlank(zipCode) && isBlank(city) && isBlank(province)
            && minBedrooms == null && minRooms == null && minLivingAreaM2 == null
            && isBlank(energyLabel) && !hasRadiusSearch();
    }

    public boolean hasRadiusSearch() {
        return radiusKm != null && (!isBlank(street) || !isBlank(city));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
