package com.kropholler.dev.hermes.ai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AiConfigTest {

    @Mock OllamaChatModel ollamaChatModel;

    private final AiConfig config = new AiConfig();

    @Test
    void chatClient_returnsNonNull() {
        ChatClient client = config.chatClient(ollamaChatModel);
        assertThat(client).isNotNull();
    }

    @Test
    void ollamaApi_returnsNonNull() {
        ReflectionTestUtils.setField(config, "ollamaBaseUrl", "http://localhost:11434");
        OllamaApi api = config.ollamaApi();
        assertThat(api).isNotNull();
    }
}
