package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

@Slf4j
class SaveWatchTool extends TaskTool {

    protected SaveWatchTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }

    @Tool(description = "Save a listing watch that runs daily and sends a notification when new properties matching your criteria appear. "
        + "Call this when the user asks to be alerted, notified, or monitored for listings. "
        + "Use the same criteria you would pass to searchListings.")
    public String saveWatch(
        @ToolParam(required = false, description = "Friendly name for this watch, e.g. 'Utrecht 3-bed under 400k'") String name,
        @ToolParam(required = false, description = "City to filter by") String city,
        @ToolParam(required = false, description = "Province to filter by") String province,
        @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
        @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
        @ToolParam(required = false, description = "City to search near") String nearCity,
        @ToolParam(required = false, description = "Radius in km when nearCity is set") Integer radiusKm
    ) {
        log.info("saveWatch called: userId={}, city={}, province={}, minPrice={}, maxPrice={}, minBedrooms={}, nearCity={}, radiusKm={}",
            userId, city, province, minPrice, maxPrice, minBedrooms, nearCity, radiusKm);
        if (!hasEmail()) {
            log.warn("saveWatch rejected for user {}: no email on file", userId);
            return "Please make sure your account has an email address before setting up notifications.";
        }
        String watchName = (name != null && !name.isBlank()) ? name : buildName(city, minBedrooms, maxPrice);
        WatchPayload payload = new WatchPayload(
            blankToNull(city), blankToNull(province), minPrice, maxPrice,
            minBedrooms, minRooms, minLivingAreaM2, blankToNull(keywords),
            blankToNull(nearCity), radiusKm
        );
        agentTaskService.createWatch(userId, watchName, payload);
        log.info("Watch '{}' saved for user {}", watchName, userId);
        return "Watch '" + watchName + "' saved — I'll alert you daily when matching listings appear.";
    }

    private static String buildName(String city, Integer minBedrooms, Integer maxPrice) {
        StringBuilder sb = new StringBuilder();
        if (city != null) sb.append(city).append(" ");
        if (minBedrooms != null) sb.append(minBedrooms).append("-bed ");
        if (maxPrice != null) sb.append("under €").append(String.format("%,d", maxPrice).replace(",", "."));
        return sb.toString().isBlank() ? "New watch" : sb.toString().strip();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
