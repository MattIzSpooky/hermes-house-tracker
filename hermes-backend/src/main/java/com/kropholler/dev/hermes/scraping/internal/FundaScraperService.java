package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FundaScraperService {

    private static final String BASE_URL = "https://www.funda.nl";
    private static final Pattern PRICE_PATTERN = Pattern.compile("[\\d.]+");
    private static final Pattern AREA_PATTERN = Pattern.compile("(\\d+)\\s*m²");
    private static final Pattern ROOMS_PATTERN = Pattern.compile("(\\d+)\\s*kamers?");
    private static final Pattern ID_PATTERN = Pattern.compile("-(\\d+)-");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^(.+?)\\s+(\\d+)\\s*(\\S+)?$");
    private static final DateTimeFormatter DUTCH_DATE =
        DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("nl", "NL"));

    private final RestClient restClient;

    @Autowired
    FundaScraperService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    // Package-private no-arg constructor for unit tests (no HTTP calls needed for parsing)
    FundaScraperService() {
        this.restClient = null;
    }

    List<RawListing> scrapeSearchPage(String url, String city) {
        String html = restClient.get()
            .uri(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; HermesBot/1.0)")
            .retrieve()
            .body(String.class);
        return parseListings(html, city);
    }

    List<RawListing> parseListings(String html, String city) {
        Document doc = Jsoup.parse(html);
        List<RawListing> results = new ArrayList<>();

        for (Element item : doc.select(".object-list-item")) {
            String objectUrl = item.attr("data-object-url");
            String fundaId = extractId(objectUrl);
            if (fundaId == null) continue;

            String fullUrl = BASE_URL + objectUrl;
            String addressText = item.select(".object-address-street-number").text();
            String[] addressParts = parseAddress(addressText);
            String priceText = item.select(".object-price").text();
            String kenmerken = item.select(".object-kenmerken").text();
            String energyLabel = item.select(".object-energy-label").text().trim();
            String dateText = item.select(".object-detail-date").text();

            results.add(new RawListing(
                fundaId,
                fullUrl,
                addressParts[0],
                addressParts[1],
                addressParts[2],
                null,
                city,
                null,
                parsePrice(priceText),
                parseArea(kenmerken),
                parseRooms(kenmerken),
                energyLabel.isEmpty() ? null : energyLabel,
                parseDate(dateText),
                "FOR_SALE"
            ));
        }
        return results;
    }

    private String extractId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    // Returns [street, houseNumber, houseNumberAddition]
    private String[] parseAddress(String text) {
        Matcher m = ADDRESS_PATTERN.matcher(text.trim());
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2), m.group(3)};
        }
        return new String[]{text, "", null};
    }

    private Integer parsePrice(String text) {
        String digits = text.replace(".", "").replaceAll("[^\\d]", "");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseArea(String text) {
        Matcher m = AREA_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Integer parseRooms(String text) {
        Matcher m = ROOMS_PATTERN.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private LocalDate parseDate(String text) {
        try {
            String datePart = text.replaceAll(".*since\\s+", "");
            return LocalDate.parse(datePart.trim(), DUTCH_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
