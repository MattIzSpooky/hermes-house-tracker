package com.kropholler.dev.hermes.cucumber;

import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.data.ListingEntity;
import com.kropholler.dev.hermes.listing.data.ListingRepository;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryEntity;
import com.kropholler.dev.hermes.listing.pricehistory.PriceHistoryEntryRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class ListingReportSteps {

    @Autowired MockMvc mockMvc;
    @Autowired ListingRepository listingRepository;
    @Autowired PriceHistoryEntryRepository priceHistoryRepository;
    @Autowired ScenarioContext context;

    @Given("a listing with asking-price history of {int} then {int}")
    public void listingWithPriceHistory(int initialPrice, int currentPrice) {
        ListingEntity listing = saveListing();
        savePriceEntry(listing.getId(), initialPrice, Instant.now().minusSeconds(3600));
        savePriceEntry(listing.getId(), currentPrice, Instant.now());
        context.setListingId(listing.getId());
    }

    @Given("a listing with no price history exists")
    public void listingWithNoPriceHistory() {
        context.setListingId(saveListing().getId());
    }

    @When("the user requests the report for that listing")
    public void userRequestsReport() throws Exception {
        context.setLastResponse(mockMvc.perform(
            context.withAuth(get("/api/listings/{id}/report", context.getListingId()))
        ));
    }

    @When("the user requests a report for an unknown listing id")
    public void userRequestsReportForUnknownId() throws Exception {
        context.setLastResponse(mockMvc.perform(
            context.withAuth(get("/api/listings/{id}/report", UUID.randomUUID()))
        ));
    }

    @Then("the report shows an initial price of {int}")
    public void reportShowsInitialPrice(int expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.initialPrice").value(expected));
    }

    @Then("the report shows a price change of {double} percent")
    public void reportShowsPriceChange(double expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.priceChangePct").value(expected));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ListingEntity saveListing() {
        ListingEntity e = new ListingEntity();
        e.setFundaId("funda-" + UUID.randomUUID());
        e.setUrl("https://funda.nl/test");
        e.setStreet("Teststraat");
        e.setHouseNumber("1");
        e.setCity("Amsterdam");
        e.setProvince("Noord-Holland");
        e.setStatus(ListingStatus.FOR_SALE);
        e.setFirstSeenAt(Instant.now().minusSeconds(7200));
        e.setLastSeenAt(Instant.now());
        return listingRepository.save(e);
    }

    private void savePriceEntry(UUID listingId, int price, Instant timestamp) {
        PriceHistoryEntryEntity entry = new PriceHistoryEntryEntity();
        entry.setListingId(listingId);
        entry.setPrice(price);
        entry.setStatus("asking_price");
        entry.setSource("funda");
        entry.setDate(LocalDate.now());
        entry.setTimestamp(timestamp);
        priceHistoryRepository.save(entry);
    }
}
