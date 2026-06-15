package com.kropholler.dev.hermes.listing;

public record ListingSearchParams(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String province,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    String energyLabel
) {
    public boolean isEmpty() {
        return isBlank(street) && isBlank(houseNumber) && isBlank(houseNumberAddition)
            && isBlank(zipCode) && isBlank(province)
            && minBedrooms == null && minRooms == null && minLivingAreaM2 == null
            && isBlank(energyLabel);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
