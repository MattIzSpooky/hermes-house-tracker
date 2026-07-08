package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@Slf4j
class SaveAreaResearchTool extends TaskTool {

    private final UserProfileRepository userProfileRepository;
    private final GeocodingService geocodingService;

    protected SaveAreaResearchTool(UUID userId, AgentTaskService agentTaskService,
                                    UserProfileRepository userProfileRepository,
                                    GeocodingService geocodingService, String email) {
        super(userId, agentTaskService, email);
        this.userProfileRepository = userProfileRepository;
        this.geocodingService = geocodingService;
    }

    @Tool(description = "Set up a recurring daily search that finds and researches the best available "
        + "listings within a radius of your home address (or another address/city), ranked by an AI "
        + "reviewing price, size, bedrooms, and value. Call this when the user wants ongoing curated "
        + "recommendations near a location, e.g. 'find me the best 10 houses within 15km of my address "
        + "every day'. Use limit to control how many listings to rank (default 5, max 15).")
    @SuppressWarnings("java:S107") // flat scalar params required so Spring AI's tool schema has no wrapper object
    public String saveAreaResearch(
            @ToolParam(required = false, description = "Friendly name for this search, e.g. 'Best homes near me'") String name,
            @ToolParam(required = false, description = "Search radius in kilometres from the target location") Integer radiusKm,
            @ToolParam(required = false, description = "Number of listings to rank, default 5, max 15") Integer limit,
            @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
            @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
            @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
            @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
            @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
            @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
            @ToolParam(required = false, description = "Address to search near instead of the user's home address, format: 'houseNumber, street, city'") String nearAddress,
            @ToolParam(required = false, description = "City to search near instead of the user's home address") String nearCity) {
        log.info("saveAreaResearch called: userId={}, radiusKm={}, limit={}, nearAddress={}, nearCity={}",
            userId, radiusKm, limit, nearAddress, nearCity);
        if (!hasEmail()) {
            log.warn("saveAreaResearch rejected for user {}: no email on file", userId);
            return "Please make sure your account has an email address before setting up notifications.";
        }

        Double overrideLon = null;
        Double overrideLat = null;

        if (hasOverride(nearAddress, nearCity)) {
            GeocodeResult geocoded = geocodeOverride(nearAddress, nearCity);
            if (geocoded == null) {
                log.warn("saveAreaResearch could not geocode nearAddress={}, nearCity={} for user {}",
                    nearAddress, nearCity, userId);
                return "Could not find that location — we could not find a match for that address or city, please try again.";
            }
            overrideLon = geocoded.lon();
            overrideLat = geocoded.lat();
            log.debug("saveAreaResearch resolved override location to lon={}, lat={}", overrideLon, overrideLat);
        } else if (!userHasHomeAddress()) {
            log.warn("saveAreaResearch rejected for user {}: no home address on file", userId);
            return "Please set your home address in your profile before creating an area search.";
        }

        String taskName = (name != null && !name.isBlank())
            ? name : "Best listings within " + radiusKm + "km";
        AreaResearchPayload payload = new AreaResearchPayload(
            radiusKm, limit, minBedrooms, minRooms, minLivingAreaM2,
            minPrice, maxPrice, blankToNull(keywords), overrideLon, overrideLat);
        agentTaskService.createAreaResearch(userId, taskName, payload);
        log.info("Area research '{}' saved for user {}", taskName, userId);
        return "Area research '" + taskName + "' saved — I'll research and rank the best listings daily.";
    }

    private boolean hasOverride(String nearAddress, String nearCity) {
        return (nearAddress != null && !nearAddress.isBlank()) || (nearCity != null && !nearCity.isBlank());
    }

    private boolean userHasHomeAddress() {
        return userProfileRepository.findById(userId)
            .map(p -> p.getLongitude() != null && p.getLatitude() != null)
            .orElse(false);
    }

    private GeocodeResult geocodeOverride(String nearAddress, String nearCity) {
        if (nearAddress != null && !nearAddress.isBlank()) {
            return geocodingService.geocodeAddress(nearAddress, "", "").orElse(null);
        }
        if (nearCity != null && !nearCity.isBlank()) {
            return geocodingService.geocodeCity(nearCity).orElse(null);
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
