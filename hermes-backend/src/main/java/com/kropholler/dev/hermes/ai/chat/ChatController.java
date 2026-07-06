package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.profile.UserProfileService;
import com.kropholler.dev.hermes.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;
    private final UserProfileService userProfileService;
    private final SimpMessagingTemplate messaging;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/chat")
    public void handleMessage(ChatMessageRequest request, @Header("simpUser") Principal principal) {
        if (request == null || request.sessionId() == null || request.message() == null || request.message().isBlank()) {
            log.warn("Received invalid chat request: {}", request);
            return;
        }

        CurrentUser currentUser = CurrentUser.from((Jwt) ((JwtAuthenticationToken) principal).getPrincipal());
        UUID userId = currentUser.id();
        String email = currentUser.email();

        try {
            userProfileService.syncEmail(userId, email);
        } catch (Exception e) {
            log.warn("Failed to sync email onto profile for user {}; continuing chat request", userId, e);
        }

        String destination = "/topic/chat/" + request.sessionId();
        Timer.Sample requestTimer = Timer.start(meterRegistry);
        Timer.Sample ttftTimer = Timer.start(meterRegistry);
        long startNanos = System.nanoTime();
        String outcome = "success";

        log.info("AI chat request: session={}, messageLength={}", request.sessionId(), request.message().length());

        try {
            AiChatService.StreamHandle handle = aiChatService.startStream(
                    request.sessionId(), userId, email, request.message());

            String response = streamTokens(handle, destination, request.sessionId(), ttftTimer, startNanos);

            aiChatService.saveUserMessage(request.sessionId(), userId, request.message());
            if (!response.isBlank()) {
                aiChatService.saveAssistantMessage(request.sessionId(), userId, response);
            }
            messaging.convertAndSend(destination, new ResultFrame("RESULT", handle.resultHolder().get()));

            log.info("AI chat completed: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos));

        } catch (Exception e) {
            outcome = "error";
            log.error("AI chat error: session={}, duration={}ms", request.sessionId(), elapsedMs(startNanos), e);
            messaging.convertAndSend(destination, new TokenFrame("ERROR", "Something went wrong. Please try again."));

        } finally {
            requestTimer.stop(Timer.builder("hermes.ai.chat.duration")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            meterRegistry.counter("hermes.ai.chat.requests", "outcome", outcome).increment();
        }
    }

    private String streamTokens(AiChatService.StreamHandle handle, String destination,
                                UUID sessionId, Timer.Sample ttftTimer, long startNanos) {
        StringBuilder response = new StringBuilder();
        AtomicBoolean firstToken = new AtomicBoolean(true);

        handle.tokens()
                .doOnNext(token -> {
                    if (firstToken.compareAndSet(true, false)) {
                        ttftTimer.stop(Timer.builder("hermes.ai.chat.time_to_first_token")
                                .description("Time from request to first text token")
                                .register(meterRegistry));
                        log.info("AI chat first token: session={}, ttft={}ms", sessionId, elapsedMs(startNanos));
                    }
                    response.append(token);
                    messaging.convertAndSend(destination, new TokenFrame("TOKEN", token));
                })
                .blockLast();

        return response.toString().strip();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
