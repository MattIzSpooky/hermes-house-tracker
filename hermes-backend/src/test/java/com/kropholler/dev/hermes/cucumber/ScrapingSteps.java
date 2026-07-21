package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.scraping.ScrapingQueueService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class ScrapingSteps {

    @Autowired MockMvc mockMvc;
    @Autowired ScenarioContext context;
    @Autowired ScrapingQueueService scrapingQueueService;

    private UUID sessionId;

    @Given("a scraping session exists for {string}")
    public void aScrapingSessionExistsFor(String city) {
        sessionId = scrapingQueueService.enqueueSearch(city, null, null, null, null, 1).id();
    }

    @When("the user creates a scraping session for city {string}")
    public void userCreatesScrapingSession(String city) throws Exception {
        String body = """
            {"city":"%s","pageLimit":1}
            """.formatted(city);
        context.setLastResponse(mockMvc.perform(context.withAuth(
            post("/api/scraping-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )));
    }

    @When("the user retrieves the scraping session")
    public void userRetrievesScrapingSession() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(
            get("/api/scraping-sessions/{id}", sessionId)
        )));
    }

    @When("the user retrieves a scraping session with an unknown id")
    public void userRetrievesUnknownScrapingSession() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(
            get("/api/scraping-sessions/{id}", UUID.randomUUID())
        )));
    }

    @Then("the scraping session status is {string}")
    public void scrapingSessionStatusIs(String expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.status").value(expected));
    }

    @Then("the scraping session type is {string}")
    public void scrapingSessionTypeIs(String expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.type").value(expected));
    }

    @Then("the response contains a scraping session id")
    public void responseContainsScrapingSessionId() throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.id").isNotEmpty());
    }
}
