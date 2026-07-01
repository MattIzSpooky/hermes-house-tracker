package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
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
    @InjectMocks AgentChatToolProvider provider;

    @Test
    void provideTools_returnsListOfFourTools() {
        List<Object> tools = provider.provideTools(UUID.randomUUID());

        assertThat(tools).hasSize(4);
        assertThat(tools).hasExactlyElementsOfTypes(
            SaveWatchTool.class,
            TriggerResearchTool.class,
            TriggerDigestTool.class,
            ListWatchesTool.class
        );
    }
}
