package com.kropholler.dev.hermes.ai;

import com.kropholler.dev.hermes.ai.internal.ResultFrame;
import com.kropholler.dev.hermes.ai.internal.TokenFrame;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/chat")
    public void handleMessage(@Valid ChatMessageRequest request) {
        aiChatService.saveUserMessage(request.sessionId(), request.message());

        AiChatService.StreamHandle handle = aiChatService.startStream(request.sessionId(), request.message());
        String destination = "/topic/chat/" + request.sessionId();
        StringBuilder fullResponse = new StringBuilder();

        try {
            for (String token : handle.tokens().toIterable()) {
                fullResponse.append(token);
                messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
            }
        } catch (Exception e) {
            log.error("Error streaming chat for session {}", request.sessionId(), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));
            return;
        }

        aiChatService.saveAssistantMessage(request.sessionId(), fullResponse.toString());

        List<ChatListingCard> listings = handle.resultHolder().get();
        messaging.convertAndSend(destination, new ResultFrame("RESULT", listings));
    }
}
