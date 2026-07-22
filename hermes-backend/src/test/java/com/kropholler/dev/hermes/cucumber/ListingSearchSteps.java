package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.city.CityRepository;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class ListingSearchSteps {

    @Autowired MockMvc mockMvc;
    @Autowired ListingRepository listingRepository;
    @Autowired CityRepository cityRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ScenarioContext context;

    private String searchedCity;

    @Given("{int} listings exist in the database")
    public void nListingsExistInDatabase(int count) {
        for (int i = 0; i < count; i++) {
            save(listing("funda-" + UUID.randomUUID(), "Amsterdam"));
        }
    }

    @Given("a listing in {string} and a listing in {string} exist")
    public void aListingInCityAndAnotherInCity(String city1, String city2) {
        save(listing("funda-" + UUID.randomUUID(), city1));
        save(listing("funda-" + UUID.randomUUID(), city2));
    }

    @Given("a listing with {int} bedrooms and a listing with {int} bedrooms exist")
    public void listingsWithDifferentBedrooms(int beds1, int beds2) {
        ListingEntity e1 = listing("funda-" + UUID.randomUUID(), "Amsterdam");
        e1.setBedrooms(beds1);
        save(e1);
        ListingEntity e2 = listing("funda-" + UUID.randomUUID(), "Amsterdam");
        e2.setBedrooms(beds2);
        save(e2);
    }

    @Given("a listing with {int} bedrooms in {string} exists")
    public void listingWithBedroomsInCityExists(int bedrooms, String city) {
        ListingEntity e = listing("funda-" + UUID.randomUUID(), city);
        e.setBedrooms(bedrooms);
        save(e);
    }

    @Given("a listing in Amsterdam at coordinates {double} {double}")
    public void listingInAmsterdamAtCoordinates(double lon, double lat) {
        ListingEntity e = listing("funda-ams-" + UUID.randomUUID(), "Amsterdam");
        UUID id = save(e).getId();
        setLocation(id, lon, lat);
    }

    @Given("a listing in Groningen at coordinates {double} {double}")
    public void listingInGroningenAtCoordinates(double lon, double lat) {
        ListingEntity e = listing("funda-grn-" + UUID.randomUUID(), "Groningen");
        UUID id = save(e).getId();
        setLocation(id, lon, lat);
    }

    @Given("the city Amsterdam is known at coordinates {double} {double}")
    public void cityAmsterdamKnownAtCoordinates(double lon, double lat) {
        seedCity("Amsterdam", lon, lat);
    }

    @Given("a listing in {string} exists")
    public void aListingInCityExists(String city) {
        ListingEntity e = listing("funda-" + UUID.randomUUID(), city);
        context.setListingId(save(e).getId());
    }

    @When("the user searches for listings with no filters")
    public void userSearchesWithNoFilters() throws Exception {
        searchedCity = null;
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/listings"))));
    }

    @When("the user searches for listings in {string}")
    public void userSearchesForListingsInCity(String city) throws Exception {
        searchedCity = city;
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/listings").param("city", city))));
    }

    @When("the user searches for listings with at least {int} bedrooms")
    public void userSearchesWithMinBedrooms(int minBedrooms) throws Exception {
        searchedCity = null;
        context.setLastResponse(mockMvc.perform(context.withAuth(
            get("/api/listings").param("minBedrooms", String.valueOf(minBedrooms))
        )));
    }

    @When("the user searches for listings in {string} with at least {int} bedrooms")
    public void userSearchesInCityWithMinBedrooms(String city, int minBedrooms) throws Exception {
        searchedCity = city;
        context.setLastResponse(mockMvc.perform(context.withAuth(
            get("/api/listings").param("city", city).param("minBedrooms", String.valueOf(minBedrooms))
        )));
    }

    @When("the user searches within {int} km of city {string}")
    public void userSearchesWithinKmOfCity(int radiusKm, String city) throws Exception {
        searchedCity = city;
        context.setLastResponse(mockMvc.perform(context.withAuth(
            get("/api/listings").param("city", city).param("radiusKm", String.valueOf(radiusKm))
        )));
    }

    @When("the user retrieves that listing by id")
    public void userRetrievesThatListingById() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/listings/{id}", context.getListingId()))));
    }

    @When("the user retrieves a listing with an unknown id")
    public void userRetrievesUnknownListing() throws Exception {
        context.setLastResponse(mockMvc.perform(context.withAuth(get("/api/listings/{id}", UUID.randomUUID()))));
    }

    @Then("the response contains {int} listing(s)")
    public void responseContainsNListings(int expected) throws Exception {
        ResultActions result = context.getLastResponse()
            .andExpect(jsonPath("$.totalElements").value(expected))
            .andExpect(jsonPath("$.content.length()").value(expected));
        if (searchedCity != null) {
            for (int i = 0; i < expected; i++) {
                result.andExpect(jsonPath("$.content[%d].city".formatted(i)).value(searchedCity));
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ListingEntity listing(String fundaId, String city) {
        ListingEntity e = new ListingEntity();
        e.setFundaId(fundaId);
        e.setUrl("https://funda.nl/" + fundaId);
        e.setCity(city);
        e.setStreet("Teststraat");
        e.setHouseNumber("1");
        e.setProvince("Test");
        e.setStatus(ListingStatus.FOR_SALE);
        e.setFirstSeenAt(Instant.now());
        e.setLastSeenAt(Instant.now());
        return e;
    }

    private ListingEntity save(ListingEntity e) {
        return listingRepository.save(e);
    }

    private void setLocation(UUID id, double lon, double lat) {
        jdbcTemplate.update(
            "UPDATE listings SET location = ST_SetSRID(ST_MakePoint(?, ?), 4326) WHERE id = ?",
            lon, lat, id
        );
    }

    private void seedCity(String name, double lon, double lat) {
        CityEntity city = new CityEntity();
        city.setName(name);
        city.setLongitude(lon);
        city.setLatitude(lat);
        cityRepository.save(city);
    }
}
