package com.kropholler.dev.hermes.ai;

import java.util.List;
import java.util.UUID;

/**
 * Extension point that allows other modules (e.g. agent) to contribute
 * per-request chat tools to the AI chat pipeline without creating a
 * circular dependency between the {@code ai} and {@code agent} modules.
 *
 * <p>Implementations are discovered via Spring's dependency injection
 * ({@code List<ChatToolProvider>}) and called in {@code AiChatService.startStream}.
 */
public interface ChatToolProvider {

    /**
     * Return tool instances scoped to the given user.
     * Called once per chat request — implementations may create new instances each time.
     *
     * @param userId the authenticated user's UUID for this chat session
     * @return a list of Spring AI tool objects (annotated with {@code @Tool})
     */
    List<Object> provideTools(UUID userId);
}
