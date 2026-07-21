package com.kropholler.dev.hermes.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CommonSteps {

    @Autowired
    ScenarioContext context;

    @Given("the user is authenticated")
    public void theUserIsAuthenticated() {
        // currentUserId is already set in ScenarioContext
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) throws Exception {
        context.getLastResponse().andExpect(status().is(expected));
    }
}
