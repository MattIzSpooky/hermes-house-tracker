package com.kropholler.dev.hermes.profile;

public record AddressDto(
    String street,
    String houseNumber,
    String houseNumberAddition,
    String zipCode,
    String city,
    String province,
    Double latitude,
    Double longitude
) {
    static AddressDto empty() {
        return new AddressDto(null, null, null, null, null, null, null, null);
    }
}
