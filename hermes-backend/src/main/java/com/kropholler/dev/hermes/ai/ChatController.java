package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ResultFrame;
import com.kropholler.dev.hermes.ai.internal.TokenFrame;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;
    private final SimpMessagingTemplate messaging;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/chat")
    public void handleMessage(ChatMessageRequest request) {
        if (request == null || request.sessionId() == null || request.message() == null || request.message().isBlank()) {
            log.warn("Received invalid chat request: {}", request);
            return;
        }

        String destination = "/topic/chat/" + request.sessionId();
        StringBuilder fullResponse = new StringBuilder();

        // llama3.2 sometimes leaks the tool-call JSON as text tokens before Spring AI
        // intercepts the structured tool invocation. We buffer the leading characters
        // until we can determine whether they form a JSON tool-call object, and if so
        // we suppress them rather than forwarding to the client or saving to history.
        StringBuilder leadingBuffer = new StringBuilder();
        boolean leadingResolved = false;
        int jsonDepth = 0;

        long startNanos = System.nanoTime();
        Timer.Sample requestTimer = Timer.start(meterRegistry);
        Timer.Sample ttftTimer = Timer.start(meterRegistry);
        boolean firstTokenSeen = false;
        int chunkCount = 0;

        log.info("AI chat request: session={}, messageLength={}", request.sessionId(), request.message().length());

        try {
            AiChatService.StreamHandle handle = aiChatService.startStream(request.sessionId(), request.message());

            for (String token : handle.tokens().toIterable()) {
                if (!leadingResolved) {
                    leadingBuffer.append(token);
                    String buf = leadingBuffer.toString().stripLeading();

                    if (buf.isEmpty()) continue;

                    if (buf.charAt(0) != '{') {
                        // Definitely not a JSON tool call — flush and proceed normally
                        leadingResolved = true;
                        if (!firstTokenSeen) {
                            firstTokenSeen = true;
                            ttftTimer.stop(Timer.builder("hermes.ai.chat.time_to_first_token")
                                    .description("Time from request to first text token")
                                    .register(meterRegistry));
                            log.info("AI chat first token: session={}, ttft={}ms",
                                    request.sessionId(), (System.nanoTime() - startNanos) / 1_000_000);
                        }
                        chunkCount++;
                        fullResponse.append(buf);
                        messaging.convertAndSend(destination, new TokenFrame("TOKEN", buf));
                        continue;
                    }

                    // Count braces to detect when the top-level JSON object closes
                    for (char c : token.toCharArray()) {
                        if (c == '{') jsonDepth++;
                        else if (c == '}') jsonDepth--;
                    }

                    if (jsonDepth <= 0 && buf.contains("\"name\"")) {
                        // Completed JSON tool-call object — discard it, but flush any text
                        // that appeared after the closing brace in the same buffered chunk.
                        String afterJson = textAfterJson(buf);
                        leadingBuffer.setLength(0);
                        jsonDepth = 0;
                        leadingResolved = true;
                        if (!afterJson.isEmpty()) {
                            if (!firstTokenSeen) {
                                firstTokenSeen = true;
                                ttftTimer.stop(Timer.builder("hermes.ai.chat.time_to_first_token")
                                        .description("Time from request to first text token")
                                        .register(meterRegistry));
                            }
                            chunkCount++;
                            fullResponse.append(afterJson);
                            messaging.convertAndSend(destination, new TokenFrame("TOKEN", afterJson));
                        }
                    }
                    // Otherwise keep buffering until the object closes or we know it's not JSON
                    continue;
                }

                if (!firstTokenSeen) {
                    firstTokenSeen = true;
                    ttftTimer.stop(Timer.builder("hermes.ai.chat.time_to_first_token")
                            .description("Time from request to first text token")
                            .register(meterRegistry));
                    log.info("AI chat first token: session={}, ttft={}ms",
                            request.sessionId(), (System.nanoTime() - startNanos) / 1_000_000);
                }
                chunkCount++;
                fullResponse.append(token);
                messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
            }

            // If something was buffered and never identified as a tool call JSON,
            // flush it now (e.g. a response that starts with '{' but isn't a tool call)
            if (!leadingResolved && !leadingBuffer.isEmpty()) {
                String remaining = leadingBuffer.toString().stripLeading();
                if (!remaining.isEmpty()) {
                    chunkCount++;
                    fullResponse.append(remaining);
                    messaging.convertAndSend(destination, new TokenFrame("TOKEN", remaining));
                }
            }

            aiChatService.saveUserMessage(request.sessionId(), request.message());
            String text = fullResponse.toString().strip();
            if (!text.isEmpty()) {
                aiChatService.saveAssistantMessage(request.sessionId(), text);
            }
            messaging.convertAndSend(destination, new ResultFrame("RESULT", handle.resultHolder().get()));

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("AI chat completed: session={}, chunks={}, duration={}ms", request.sessionId(), chunkCount, durationMs);
            requestTimer.stop(Timer.builder("hermes.ai.chat.duration")
                    .description("Total AI chat streaming duration")
                    .tag("outcome", "success")
                    .register(meterRegistry));
            meterRegistry.counter("hermes.ai.chat.requests", "outcome", "success").increment();

        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("AI chat error: session={}, duration={}ms", request.sessionId(), durationMs, e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));
            requestTimer.stop(Timer.builder("hermes.ai.chat.duration")
                    .description("Total AI chat streaming duration")
                    .tag("outcome", "error")
                    .register(meterRegistry));
            meterRegistry.counter("hermes.ai.chat.requests", "outcome", "error").increment();
        }
    }

    /** Returns the text that appears after the first complete top-level JSON object in {@code s}. */
    private static String textAfterJson(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(i + 1).stripLeading();
            }
        }
        return "";
    }
}
