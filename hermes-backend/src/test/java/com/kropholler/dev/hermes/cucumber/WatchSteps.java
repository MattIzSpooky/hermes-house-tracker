package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.WatchPayload;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ScenarioScope
public class WatchSteps {

    @Autowired MockMvc mockMvc;
    @Autowired AgentTaskService agentTaskService;
    @Autowired ScenarioContext context;

    private UUID watchId;
    private boolean admin = false;

    @Given("the user has admin privileges")
    public void userHasAdminPrivileges() {
        admin = true;
    }

    @Given("the user has an active watch named {string}")
    public void userHasActiveWatch(String name) {
        WatchPayload payload = new WatchPayload(null, null, null, null, null, null, null, null, null, null);
        watchId = agentTaskService.createWatch(context.getCurrentUserId(), name, payload).id();
    }

    @Given("another user has an active watch")
    public void anotherUserHasActiveWatch() {
        WatchPayload payload = new WatchPayload(null, null, null, null, null, null, null, null, null, null);
        watchId = agentTaskService.createWatch(UUID.randomUUID(), "Other user's watch", payload).id();
    }

    @When("the user retrieves their watches")
    public void userRetrievesWatches() throws Exception {
        context.setLastResponse(mockMvc.perform(withAuth(get("/api/agent-tasks"))));
    }

    @When("the user deletes the watch")
    public void userDeletesWatch() throws Exception {
        context.setLastResponse(mockMvc.perform(withAuth(delete("/api/agent-tasks/{id}", watchId))));
    }

    @When("the current user tries to delete it")
    public void currentUserTriesToDeleteIt() throws Exception {
        context.setLastResponse(mockMvc.perform(withAuth(delete("/api/agent-tasks/{id}", watchId))));
    }

    @When("the user tries to delete an unknown watch id")
    public void userTriesToDeleteUnknownWatch() throws Exception {
        context.setLastResponse(mockMvc.perform(withAuth(delete("/api/agent-tasks/{id}", UUID.randomUUID()))));
    }

    @When("the user triggers the watch")
    public void userTriggersWatch() throws Exception {
        context.setLastResponse(mockMvc.perform(withAuth(post("/api/agent-tasks/{id}/run", watchId))));
    }

    @Then("the response contains {int} watch(es)")
    public void responseContainsNWatches(int expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.length()").value(expected));
    }

    @Then("the user has {int} watches")
    public void userHasNWatches(int expected) throws Exception {
        mockMvc.perform(withAuth(get("/api/agent-tasks")))
            .andExpect(jsonPath("$.length()").value(expected));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder req) {
        if (admin) {
            return req.with(jwt()
                .jwt(b -> b
                    .subject(context.getCurrentUserId().toString())
                    .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        }
        return req.with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())));
    }
}
