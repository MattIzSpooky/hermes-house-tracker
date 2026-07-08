package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.agent.tool.json.SaveAreaResearchToolParams;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

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
    public String saveAreaResearch(SaveAreaResearchToolParams params) {
        log.info("saveAreaResearch called: userId={}, radiusKm={}, limit={}, nearAddress={}, nearCity={}",
            userId, params.radiusKm(), params.limit(), params.nearAddress(), params.nearCity());
        if (!hasEmail()) {
            log.warn("saveAreaResearch rejected for user {}: no email on file", userId);
            return "Please make sure your account has an email address before setting up notifications.";
        }

        Double overrideLon = null;
        Double overrideLat = null;

        if (hasOverride(params.nearAddress(), params.nearCity())) {
            GeocodeResult geocoded = geocodeOverride(params.nearAddress(), params.nearCity());
            if (geocoded == null) {
                log.warn("saveAreaResearch could not geocode nearAddress={}, nearCity={} for user {}",
                    params.nearAddress(), params.nearCity(), userId);
                return "Could not find that location — we could not find a match for that address or city, please try again.";
            }
            overrideLon = geocoded.lon();
            overrideLat = geocoded.lat();
            log.debug("saveAreaResearch resolved override location to lon={}, lat={}", overrideLon, overrideLat);
        } else if (!userHasHomeAddress()) {
            log.warn("saveAreaResearch rejected for user {}: no home address on file", userId);
            return "Please set your home address in your profile before creating an area search.";
        }

        String taskName = (params.name() != null && !params.name().isBlank())
            ? params.name() : "Best listings within " + params.radiusKm() + "km";
        AreaResearchPayload payload = new AreaResearchPayload(
            params.radiusKm(), params.limit(), params.minBedrooms(), params.minRooms(), params.minLivingAreaM2(),
            params.minPrice(), params.maxPrice(), blankToNull(params.keywords()), overrideLon, overrideLat);
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
