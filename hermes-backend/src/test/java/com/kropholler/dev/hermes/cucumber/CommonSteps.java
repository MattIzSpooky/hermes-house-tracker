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

    @Given("the user is not authenticated")
    public void theUserIsNotAuthenticated() {
        context.setAuthenticated(false);
    }

    @Given("the user has admin privileges")
    public void theUserHasAdminPrivileges() {
        context.setAdmin(true);
    }

    @Then("the request succeeds")
    public void theRequestSucceeds() throws Exception {
        context.getLastResponse().andExpect(status().isOk());
    }

    @Then("the request succeeds and creates a new resource")
    public void theRequestSucceedsAndCreatesANewResource() throws Exception {
        context.getLastResponse().andExpect(status().isCreated());
    }

    @Then("the request is accepted for processing")
    public void theRequestIsAcceptedForProcessing() throws Exception {
        context.getLastResponse().andExpect(status().isAccepted());
    }

    @Then("the request succeeds with no content")
    public void theRequestSucceedsWithNoContent() throws Exception {
        context.getLastResponse().andExpect(status().isNoContent());
    }

    @Then("the request is rejected because the user is not signed in")
    public void theRequestIsRejectedBecauseTheUserIsNotSignedIn() throws Exception {
        context.getLastResponse().andExpect(status().isUnauthorized());
    }

    @Then("the request is rejected because the user is not allowed to do this")
    public void theRequestIsRejectedBecauseTheUserIsNotAllowedToDoThis() throws Exception {
        context.getLastResponse().andExpect(status().isForbidden());
    }

    @Then("the request fails because the resource cannot be found")
    public void theRequestFailsBecauseTheResourceCannotBeFound() throws Exception {
        context.getLastResponse().andExpect(status().isNotFound());
    }

    @Then("the request is rejected because the input is invalid")
    public void theRequestIsRejectedBecauseTheInputIsInvalid() throws Exception {
        context.getLastResponse().andExpect(status().isUnprocessableEntity());
    }
}
