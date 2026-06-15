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

        try {
            AiChatService.StreamHandle handle = aiChatService.startStream(request.sessionId(), request.message());

            for (String token : handle.tokens().toIterable()) {
                fullResponse.append(token);
                messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
            }

            aiChatService.saveUserMessage(request.sessionId(), request.message());
            aiChatService.saveAssistantMessage(request.sessionId(), fullResponse.toString());
            messaging.convertAndSend(destination, new ResultFrame("RESULT", handle.resultHolder().get()));

        } catch (Exception e) {
            log.error("Error handling chat for session {}", request.sessionId(), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));
        }
    }
}
