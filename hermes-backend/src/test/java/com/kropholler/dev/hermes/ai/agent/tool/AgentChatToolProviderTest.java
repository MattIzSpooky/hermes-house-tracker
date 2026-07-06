package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AgentChatToolProviderTest {

    @Mock AgentTaskService agentTaskService;
    @Mock UserProfileRepository userProfileRepository;
    @Mock GeocodingService geocodingService;
    @InjectMocks AgentChatToolProvider provider;

    @Test
    void provideTools_returnsListOfFiveTools() {
        List<Object> tools = provider.provideTools(UUID.randomUUID());

        assertThat(tools).hasSize(5);
        assertThat(tools).hasExactlyElementsOfTypes(
            SaveWatchTool.class,
            TriggerResearchTool.class,
            TriggerDigestTool.class,
            ListWatchesTool.class,
            SaveAreaResearchTool.class
        );
    }
}
