package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.openapi.ChatMessageResponse;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatSessionSummaryResponse;
import com.kropholler.dev.hermes.config.SecurityConfig;
import com.kropholler.dev.hermes.security.NoOpUserProfileSyncFilterTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatHistoryController.class)
@Import({SecurityConfig.class, NoOpUserProfileSyncFilterTestConfig.class})
class ChatHistoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ChatHistoryService chatHistoryService;
    @MockitoBean ChatHistoryApiMapper chatHistoryApiMapper;

    @Test
    void getChatSessions_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-06-01T08:00:00Z");
        when(chatHistoryService.listSessions(userId))
            .thenReturn(List.of(new ChatSessionSummaryDto(sessionId, "Hello there", lastMessageAt)));
        ChatSessionSummaryResponse response = new ChatSessionSummaryResponse();
        response.setSessionId(sessionId);
        response.setTitle("Hello there");
        response.setLastMessageAt(OffsetDateTime.ofInstant(lastMessageAt, ZoneOffset.UTC));
        when(chatHistoryApiMapper.toResponse(any(ChatSessionSummaryDto.class))).thenReturn(response);

        mockMvc.perform(get("/api/chat/sessions")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessionId").value(sessionId.toString()))
            .andExpect(jsonPath("$[0].title").value("Hello there"));
    }

    @Test
    void getChatSessionMessages_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(chatHistoryService.getMessages(userId, sessionId))
            .thenReturn(List.of(new ChatMessageDto("USER", "Hi", Instant.parse("2026-06-01T08:00:00Z"))));
        ChatMessageResponse response = new ChatMessageResponse();
        response.setRole("USER");
        response.setContent("Hi");
        response.setCreatedAt(OffsetDateTime.ofInstant(Instant.parse("2026-06-01T08:00:00Z"), ZoneOffset.UTC));
        when(chatHistoryApiMapper.toResponse(any(ChatMessageDto.class))).thenReturn(response);

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", sessionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").value("USER"))
            .andExpect(jsonPath("$[0].content").value("Hi"));
    }

    @Test
    void deleteChatSession_usesSubjectFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/chat/sessions/{sessionId}", sessionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(chatHistoryService).deleteSession(eq(userId), eq(sessionId));
    }
}
