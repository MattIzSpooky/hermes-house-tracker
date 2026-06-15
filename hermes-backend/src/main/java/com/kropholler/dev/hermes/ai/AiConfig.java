package com.kropholler.dev.hermes.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    static final String CHAT_SYSTEM_PROMPT = """
            You are a helpful real-estate assistant for the Hermes property tracker.
            When the user expresses a preference for a property (location, size, price, features, etc.),
            always call the searchListings tool to find matching listings.
            You may ask clarifying questions before searching if the intent is unclear.
            Respond in the same language the user writes in.
            Keep replies concise and friendly.
            """;

    @Bean("chatClient")
    public ChatClient chatChatClient(OllamaApi ollamaApi) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model("llama3.2:3b")
                .build();
        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .build();
        return ChatClient.builder(model)
                .defaultSystem(CHAT_SYSTEM_PROMPT)
                .build();
    }
}
