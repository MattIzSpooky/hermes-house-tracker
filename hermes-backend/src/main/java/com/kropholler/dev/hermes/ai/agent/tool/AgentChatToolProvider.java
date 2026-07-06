package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.ChatToolProvider;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentChatToolProvider implements ChatToolProvider {

    private final AgentTaskService agentTaskService;
    private final UserProfileRepository userProfileRepository;
    private final GeocodingService geocodingService;

    @Override
    public List<Object> provideTools(UUID userId, String email) {
        return List.of(
            new SaveWatchTool(userId, agentTaskService, email),
            new TriggerResearchTool(userId, agentTaskService, email),
            new TriggerDigestTool(userId, agentTaskService, email),
            new ListWatchesTool(userId, agentTaskService, email),
            new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService, email)
        );
    }
}
