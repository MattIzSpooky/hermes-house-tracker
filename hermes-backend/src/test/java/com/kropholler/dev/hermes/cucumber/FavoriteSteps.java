package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.favorites.FavoriteEntity;
import com.kropholler.dev.hermes.favorites.FavoriteRepository;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class FavoriteSteps {

    @Autowired MockMvc mockMvc;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired ScenarioContext context;

    private UUID listingId;

    @Given("a listing id to work with")
    public void aListingIdToWorkWith() {
        listingId = createListing().getId();
        context.setListingId(listingId);
    }

    @Given("the listing is already in the user's favourites")
    public void listingIsAlreadyInFavourites() {
        listingId = createListing().getId();
        context.setListingId(listingId);
        FavoriteEntity entity = new FavoriteEntity();
        entity.setUserId(context.getCurrentUserId());
        entity.setListingId(listingId);
        favoriteRepository.save(entity);
    }

    @When("the user adds the listing to their favourites")
    public void userAddsFavourite() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(put("/api/favorites/{id}", listingId))));
    }

    @When("the user removes the listing from their favourites")
    public void userRemovesFavourite() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(delete("/api/favorites/{id}", listingId))));
    }

    @When("the user retrieves their favourites")
    public void userRetrievesFavourites() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/favorites"))));
    }

    @Then("the user has exactly {int} favourite(s)")
    public void userHasExactlyNFavourites(int expected) throws Exception {
        assertFavourites(mockMvc.perform(context.withAuth(get("/api/favorites"))), expected);
    }

    @Then("the response body contains {int} favourite(s)")
    public void responseBodyContainsNFavourites(int expected) throws Exception {
        assertFavourites(context.getLastResponse(), expected);
    }

    private void assertFavourites(ResultActions result, int expected) throws Exception {
        result.andExpect(jsonPath("$.length()").value(expected));
        if (expected > 0) {
            result.andExpect(jsonPath("$[0].listingId").value(listingId.toString()))
                .andExpect(jsonPath("$[0].savedAt").exists());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ListingEntity createListing() {
        ListingEntity e = new ListingEntity();
        e.setFundaId("funda-" + UUID.randomUUID());
        e.setUrl("https://funda.nl/test");
        e.setStreet("Teststraat");
        e.setHouseNumber("1");
        e.setCity("Amsterdam");
        e.setProvince("Noord-Holland");
        e.setStatus(ListingStatus.FOR_SALE);
        e.setFirstSeenAt(Instant.now());
        e.setLastSeenAt(Instant.now());
        return listingRepository.save(e);
    }
}
