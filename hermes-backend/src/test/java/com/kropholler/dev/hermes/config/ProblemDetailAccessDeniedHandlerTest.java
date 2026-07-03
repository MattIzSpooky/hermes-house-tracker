package com.kropholler.dev.hermes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemDetailAccessDeniedHandler handler = new ProblemDetailAccessDeniedHandler(objectMapper);

    @Test
    void handle_writes403ProblemDetailJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access is denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
            .contains("\"status\":403")
            .contains("\"detail\":\"Access is denied\"");
    }
}
