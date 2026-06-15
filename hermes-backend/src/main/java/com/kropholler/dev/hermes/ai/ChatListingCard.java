package com.kropholler.dev.hermes.ai;

import java.util.UUID;

public record ChatListingCard(
        UUID id,
        String street,
        String houseNumber,
        String houseNumberAddition,
        String city,
        String province,
        Integer currentPrice,
        Integer bedrooms,
        Integer livingAreaM2,
        String energyLabel,
        String status,
        String url
) {}
