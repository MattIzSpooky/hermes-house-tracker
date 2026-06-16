package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ResultFrame;
import com.kropholler.dev.hermes.ai.internal.TokenFrame;
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
                        // Completed JSON tool-call object — discard it silently
                        leadingBuffer.setLength(0);
                        jsonDepth = 0;
                        leadingResolved = true;
                    }
                    // Otherwise keep buffering until the object closes or we know it's not JSON
                    continue;
                }

                fullResponse.append(token);
                messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
            }

            // If something was buffered and never identified as a tool call JSON,
            // flush it now (e.g. a response that starts with '{' but isn't a tool call)
            if (!leadingResolved && !leadingBuffer.isEmpty()) {
                String remaining = leadingBuffer.toString().stripLeading();
                if (!remaining.isEmpty()) {
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

        } catch (Exception e) {
            log.error("Error handling chat for session {}", request.sessionId(), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));
        }
    }
}
