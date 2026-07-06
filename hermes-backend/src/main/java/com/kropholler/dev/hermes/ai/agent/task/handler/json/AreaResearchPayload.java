package com.kropholler.dev.hermes.ai.agent.task.handler.json;

public record AreaResearchPayload(
    Integer radiusKm,
    Integer limit,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    Integer minPrice,
    Integer maxPrice,
    String keywords,
    Double overrideLon,
    Double overrideLat
) {}
