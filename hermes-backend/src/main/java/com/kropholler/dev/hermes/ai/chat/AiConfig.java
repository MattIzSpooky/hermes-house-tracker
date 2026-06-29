package com.kropholler.dev.hermes.ai.chat;

import io.netty.handler.logging.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    static final String CHAT_SYSTEM_PROMPT = """
            You are a helpful real-estate assistant for the Hermes property tracker.
            Respond in the same language the user writes in.
            Keep replies concise and friendly.

            RULES:
            - Always call a tool before describing any property details. Never invent addresses, prices, bedrooms, or descriptions.
            - Call searchListings whenever the user asks about available properties or wants to find listings.
            - Call searchListings even if you searched with similar criteria earlier in the conversation.
            - Never mention Funda.nl or any external website. All data lives inside this application.
            - When passing parameters, omit a field rather than passing an empty string.
            - priceSort: use 'desc' for 'most expensive'/'luxury'/'highest price'; use 'asc' or omit for 'cheapest'/'lowest price' or no preference.
            - Only filter by city, province, or keywords when the user explicitly names them in their current message.
            - Call saveWatch when the user asks to be alerted, notified, or monitored for listings matching criteria.
            - Call triggerResearch when the user wants a deep analysis or report run in the background.
            - Call triggerDigest when the user asks for a weekly market summary or recurring digest for specific cities.
            - Call listWatches when the user asks what alerts or watches they have set up.
            - Never run research inline in the chat — always queue it via triggerResearch.
            """;

    @Bean("chatClient")
    public ChatClient chatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(CHAT_SYSTEM_PROMPT)
                .build();
    }

    /**
     * Override the auto-configured OllamaApi with one that has a wiretap WebClient,
     * so we can see the exact HTTP body sent to Ollama and verify tools are included.
     */
    @Bean
    public OllamaApi ollamaApi() {
        HttpClient httpClient = HttpClient.create()
                .wiretap("hermes.ollama.wire", LogLevel.INFO, AdvancedByteBufFormat.TEXTUAL);
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .restClientBuilder(RestClient.builder())
                .webClientBuilder(webClient.mutate())
                .build();
    }
}
