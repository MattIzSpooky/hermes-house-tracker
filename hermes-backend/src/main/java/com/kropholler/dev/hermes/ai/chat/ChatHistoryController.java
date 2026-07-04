package com.kropholler.dev.hermes.ai.chat;

import com.kropholler.dev.hermes.ai.chat.openapi.ChatHistoryApi;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatMessageResponse;
import com.kropholler.dev.hermes.ai.chat.openapi.ChatSessionSummaryResponse;
import com.kropholler.dev.hermes.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatHistoryController implements ChatHistoryApi {

    private final ChatHistoryService chatHistoryService;
    private final ChatHistoryApiMapper chatHistoryApiMapper;

    @Override
    public ResponseEntity<List<ChatSessionSummaryResponse>> getChatSessions() {
        List<ChatSessionSummaryResponse> responses = chatHistoryService.listSessions(CurrentUser.current().id())
            .stream().map(chatHistoryApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<List<ChatMessageResponse>> getChatSessionMessages(UUID sessionId) {
        List<ChatMessageResponse> responses = chatHistoryService.getMessages(CurrentUser.current().id(), sessionId)
            .stream().map(chatHistoryApiMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<Void> deleteChatSession(UUID sessionId) {
        chatHistoryService.deleteSession(CurrentUser.current().id(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
