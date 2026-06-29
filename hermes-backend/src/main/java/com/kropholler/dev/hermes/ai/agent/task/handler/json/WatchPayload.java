package com.kropholler.dev.hermes.ai.agent.task.handler.json;

public record WatchPayload(
    String city,
    String province,
    Integer minPrice,
    Integer maxPrice,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    String keywords,
    String nearCity,
    Integer radiusKm
) {}
