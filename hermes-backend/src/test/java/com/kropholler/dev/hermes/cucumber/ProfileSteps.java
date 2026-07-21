package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class ProfileSteps {

    @Autowired MockMvc mockMvc;
    @Autowired GeocodingService geocodingService;
    @Autowired UserProfileRepository userProfileRepository;
    @Autowired ScenarioContext context;

    @Given("the user has a saved address in {string}")
    public void userHasSavedAddress(String city) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(context.getCurrentUserId());
        entity.setStreet("Teststraat");
        entity.setHouseNumber("1");
        entity.setCity(city);
        entity.setLatitude(52.3676);
        entity.setLongitude(4.9041);
        entity.setUpdatedAt(Instant.now());
        userProfileRepository.save(entity);
    }

    @Given("the address can be geocoded successfully")
    public void addressCanBeGeocodedSuccessfully() {
        when(geocodingService.geocodeAddress(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(new GeocodeResult(4.9041, 52.3676, null)));
    }

    @Given("the address cannot be geocoded")
    public void addressCannotBeGeocoded() {
        when(geocodingService.geocodeAddress(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
    }

    @When("the user retrieves their profile")
    public void userRetrievesProfile() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/profile"))));
    }

    @When("the user saves their address as street {string} number {string} in {string}")
    public void userSavesAddress(String street, String number, String city) throws Exception {
        String body = """
            {"street":"%s","houseNumber":"%s","city":"%s"}
            """.formatted(street, number, city);
        context.setLastResponse(mockMvc.perform(context.withAuth(
            put("/api/profile/address")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )));
    }

    @Then("the profile has no saved address")
    public void profileHasNoSavedAddress() throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.city").value((Object) null));
    }

    @Then("the profile city is {string}")
    public void profileCityIs(String expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.city").value(expected));
    }

    @Then("the profile latitude is not null")
    public void profileLatitudeIsNotNull() throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.latitude").isNotEmpty());
    }
}
