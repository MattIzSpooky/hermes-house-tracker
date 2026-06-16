package com.kropholler.dev.hermes.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${hermes.ai.chat.model:llama3.2:3b}")
    private String chatModel;

    static final String CHAT_SYSTEM_PROMPT = """
            You are a helpful real-estate assistant for the Hermes property tracker.
            When the user expresses a preference for a property (location, size, price, features, etc.),
            always call the searchListings tool to find matching listings.
            You may ask clarifying questions before searching if the intent is unclear.
            Respond in the same language the user writes in.
            Keep replies concise and friendly.

            STRICT RULES — never break these:
            - ONLY describe properties that were returned by the searchListings tool. Never invent addresses, prices, bedroom counts, or any other property details.
            - Always call searchListings before answering any question about properties, even if you searched for similar criteria earlier in the conversation. Never skip the tool call based on a previous empty result.
            - Only set city, province, or keywords filters when the user explicitly mentions them in their current message. Do not infer location from earlier conversation turns.
            - If searchListings returns an empty list, tell the user no matching properties were found and suggest broadening or changing the search criteria.
            - When the user asks for a summary or description of a specific property: call getListingSummary with its street, houseNumber, and city. If you don't know the address yet, call searchListings first to find the property, then call getListingSummary with the address from the result.
            - Never mention Funda.nl, funda.nl, or any external website or URL. Do not reference a "url" field. All information lives inside this application.
            - When passing search parameters, omit a parameter entirely (leave it null) rather than passing an empty string.
            """;

    @Bean("chatClient")
    public ChatClient chatClient(OllamaApi ollamaApi) {
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(chatModel)
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
