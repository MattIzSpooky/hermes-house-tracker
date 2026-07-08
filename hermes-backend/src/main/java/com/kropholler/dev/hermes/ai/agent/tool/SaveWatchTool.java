package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import com.kropholler.dev.hermes.ai.agent.tool.json.SaveWatchToolParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.UUID;

@Slf4j
class SaveWatchTool extends TaskTool {

    protected SaveWatchTool(UUID userId, AgentTaskService agentTaskService, String email) {
        super(userId, agentTaskService, email);
    }

    @Tool(description = "Save a listing watch that runs daily and sends a notification when new properties matching your criteria appear. "
        + "Call this when the user asks to be alerted, notified, or monitored for listings. "
        + "Use the same criteria you would pass to searchListings.")
    public String saveWatch(SaveWatchToolParams params) {
        log.info("saveWatch called: userId={}, city={}, province={}, minPrice={}, maxPrice={}, minBedrooms={}, nearCity={}, radiusKm={}",
            userId, params.city(), params.province(), params.minPrice(), params.maxPrice(),
            params.minBedrooms(), params.nearCity(), params.radiusKm());
        if (!hasEmail()) {
            log.warn("saveWatch rejected for user {}: no email on file", userId);
            return "Please make sure your account has an email address before setting up notifications.";
        }
        String watchName = (params.name() != null && !params.name().isBlank())
            ? params.name() : buildName(params.city(), params.minBedrooms(), params.maxPrice());
        WatchPayload payload = new WatchPayload(
            blankToNull(params.city()), blankToNull(params.province()), params.minPrice(), params.maxPrice(),
            params.minBedrooms(), params.minRooms(), params.minLivingAreaM2(), blankToNull(params.keywords()),
            blankToNull(params.nearCity()), params.radiusKm()
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
