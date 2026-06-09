package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.RawListing;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FundaScraperService {

    private static final String BASE_URL = "https://www.funda.nl";
    private static final Pattern AREA_PATTERN = Pattern.compile("(\\d+)\\s*m²");
    private static final Pattern ROOMS_PATTERN = Pattern.compile("(\\d+)\\s*kamers?");
    private static final Pattern ID_PATTERN = Pattern.compile("-(\\d+)-");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^(.+?)\\s+(\\d+)\\s*(\\S+)?$");
    private static final DateTimeFormatter DUTCH_DATE =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"));

    private Playwright playwright;
    private Browser browser;

    // Package-private no-arg constructor for unit tests (no browser needed for parsing tests)
    FundaScraperService() {}

    @PostConstruct
    void init() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(List.of(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-blink-features=AutomationControlled",
                "--no-first-run",
                "--no-default-browser-check"
            )));
        log.info("Playwright browser initialized");
    }

    @PreDestroy
    void destroy() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    synchronized List<RawListing> scrapeSearchPage(String url, String city) {
        log.info("Scraping Funda search page: {}", url);
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("nl-NL")
                .setTimezoneId("Europe/Amsterdam"))) {

            context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            Page page = context.newPage();
            page.setExtraHTTPHeaders(Map.of(
                "Accept-Language", "nl-NL,nl;q=0.9,en-US;q=0.8,en;q=0.7",
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            ));

            page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(30_000));

            try {
                page.waitForSelector(".object-list-item", new Page.WaitForSelectorOptions().setTimeout(10_000));
            } catch (Exception e) {
                log.warn("Listing selector not found on {}, may be bot challenge or empty results", url);
            }

            String html = page.content();
            log.info("Received HTML ({} bytes)", html.length());
            return parseListings(html, city);
        }
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
        log.info("Parsed {} listings from page", results.size());
        return results;
    }

    private String extractId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String[] parseAddress(String text) {
        Matcher m = ADDRESS_PATTERN.matcher(text.trim());
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2), m.group(3)};
        }
        return new String[]{text, "", null};
    }

    private Integer parsePrice(String text) {
        String digits = text.replace(".", "").replaceAll("\\D", "");
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
