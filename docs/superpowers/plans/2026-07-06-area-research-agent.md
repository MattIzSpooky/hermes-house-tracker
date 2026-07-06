# Area Research Agent Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recurring `AREA_RESEARCH` agent task that finds and researches the best available listings within a configurable radius of the user's home address, ranked by an LLM, delivered as an in-app notification and a real per-user email — plus make the chat listing-search result count configurable/clamped everywhere and fix email delivery to go to the actual user instead of one hardcoded address.

**Architecture:** Extends the existing `AgentTask`/`AgentTaskHandler`/`AgentTaskExecutor` strategy-map loop (see `docs/superpowers/specs/2026-06-19-agentic-ai-design.md`) with a fourth task type. Deterministic DB search finds candidates (guaranteed count/radius/criteria); an LLM with a narrowed tool set researches and ranks only those candidates. Email routing is fixed by capturing the JWT's `email` claim and syncing it onto the user's profile via a servlet filter, so background jobs (no JWT available) can still look it up.

**Tech Stack:** Spring Boot 4.1 / Spring AI 2.0 (`ChatClient`), Spring Data JPA (native queries), Flyway, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Result limit: `null`/non-positive → default `5`; otherwise clamp to `Math.min(limit, 15)`. Applies to `ListingService.findForChat`, the new `findNearLocation`, and `ListingSearchTool`.
- `AREA_RESEARCH` task schedule is fixed daily at 8am: `"0 0 8 * * *"` (same as `WATCH`).
- Center resolution: no runtime geocoding calls, ever. Home-address path reads `UserProfileEntity.latitude/longitude` fresh each run; override path is geocoded once at task-creation time and frozen into the payload.
- Creation-time validation in `SaveAreaResearchTool` must reject immediately (no task created) when: (a) no override given and the user has no home address on file, or (b) an override is given but fails to geocode.
- The new handler's LLM tool set is exactly `GetListingSummaryTool`, `GetPriceHistoryTool`, `CompareListingsTool` — never `ListingSearchTool` or `GetFavouriteListingsTool`.
- `NotificationContent.listingIds` for this task type must be the deterministic candidate IDs from the DB search, not anything scraped from LLM tool-call side effects.
- Every new Java file/package follows existing conventions exactly (Lombok `@RequiredArgsConstructor`/`@Slf4j`, package-private classes where siblings are package-private, existing import ordering).

---

### Task 1: Configurable `limit` in `ListingRepository`'s chat-search queries

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingRepository.java`

**Interfaces:**
- Produces: `searchForChat(Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String province, String city, String keywords, Integer minPrice, Integer maxPrice, boolean sortDesc, int limit)` and `searchForChatNearLocation(Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String province, String keywords, Integer minPrice, Integer maxPrice, double lon, double lat, int radiusMeters, int limit)` — both now require a trailing `int limit` argument, and both native queries use `LIMIT :limit` instead of a literal `LIMIT 5`.

There is no dedicated unit test file for these native queries (they're only exercised indirectly, through `ListingService`, using a mocked repository — see Task 2). This task is a pure signature/SQL change with no test of its own; Task 2's tests are what verify the behavior end-to-end through the service layer.

- [ ] **Step 1: Add the `limit` parameter and `LIMIT :limit` to `searchForChatNearLocation`**

In `ListingRepository.java`, change:

```java
            ORDER BY ST_Distance(
                l.location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            ) ASC
            LIMIT 5
            """,
            nativeQuery = true)
    List<ListingEntity> searchForChatNearLocation(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") int radiusMeters
    );
```

to:

```java
            ORDER BY ST_Distance(
                l.location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            ) ASC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<ListingEntity> searchForChatNearLocation(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit
    );
```

- [ ] **Step 2: Add the `limit` parameter and `LIMIT :limit` to `searchForChat`**

Change:

```java
            ORDER BY CASE WHEN :sortDesc = true THEN -latest_price.price ELSE latest_price.price END ASC NULLS LAST
            LIMIT 5
            """, nativeQuery = true)
    List<ListingEntity> searchForChat(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("city") String city,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("sortDesc") boolean sortDesc
    );
```

to:

```java
            ORDER BY CASE WHEN :sortDesc = true THEN -latest_price.price ELSE latest_price.price END ASC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<ListingEntity> searchForChat(
            @Param("minBedrooms") Integer minBedrooms,
            @Param("minRooms") Integer minRooms,
            @Param("minLivingAreaM2") Integer minLivingAreaM2,
            @Param("province") String province,
            @Param("city") String city,
            @Param("keywords") String keywords,
            @Param("minPrice") Integer minPrice,
            @Param("maxPrice") Integer maxPrice,
            @Param("sortDesc") boolean sortDesc,
            @Param("limit") int limit
    );
```

- [ ] **Step 3: Compile to confirm the signature change is syntactically valid (callers will be broken until Task 2 — that's expected)**

Run: `cd hermes-backend && ./mvnw -q compile`
Expected: FAIL — `ListingService.java` no longer matches the new repository signatures. Confirm the errors are exactly about `searchForChat`/`searchForChatNearLocation` call sites in `ListingService.java` (nothing else).

- [ ] **Step 4: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/data/ListingRepository.java
git commit -m "feat(listing): parameterize LIMIT in chat-search queries"
```

---

### Task 2: Thread `limit` through `ListingService`, add `findNearLocation`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceFindForChatTest.java`

**Interfaces:**
- Consumes: `ListingRepository.searchForChat(..., int limit)` / `searchForChatNearLocation(..., int limit)` from Task 1.
- Produces:
  - `ListingService.findForChat(Integer minPrice, Integer maxPrice, Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String province, String city, String keywords, boolean sortByPriceDesc, String nearAddress, String nearCity, Integer radiusKm, Integer limit)` — one new trailing `Integer limit` parameter.
  - `ListingService.findNearLocation(double lon, double lat, Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, String province, String keywords, Integer minPrice, Integer maxPrice, int radiusMeters, Integer limit)` — new public method, no geocoding, used directly by `AreaResearchTaskHandler` in Task 9.
  - Private `clampLimit(Integer limit)` helper: `null` or `<= 0` → `5`; otherwise `Math.min(limit, 15)`.

- [ ] **Step 1: Update existing `ListingServiceFindForChatTest` call sites to pass the new trailing `null` argument**

Every existing call to `service.findForChat(...)` in this file currently passes 12 arguments ending in `radiusKm`. Add a 13th argument, `null`, to every one of them (12 call sites — 9 plain calls with a trailing `null`/`5`/`null` for radiusKm, plus the radius-path tests). For example, change:

```java
        List<ListingDto> result = service.findForChat(200_000, null, null, null, null, null, null, null, false, null, null, null);
```

to:

```java
        List<ListingDto> result = service.findForChat(200_000, null, null, null, null, null, null, null, false, null, null, null, null);
```

Apply the same trailing `, null` to all of:
- `findForChat_minPrice_excludesListingsBelowMinPrice`
- `findForChat_maxPrice_excludesListingsAboveMaxPrice`
- `findForChat_nullCurrentPrice_excludedWhenMinPriceSet`
- `findForChat_nullCurrentPrice_excludedWhenMaxPriceSet`
- `findForChat_noPriceBounds_allListingsPassThrough`
- `findForChat_noPriceBounds_nullPriceListingsPassThrough`
- `findForChat_withNearAddressAndRadius_geocodesAndCallsNearLocationSearch` (the `service.findForChat(...)` call)
- `findForChat_withNearCityAndRadius_geocodesCityAndCallsNearLocationSearch`
- `findForChat_withNearAddressAndRadius_geocodingFails_fallsBackToRegularSearch`
- `findForChat_nearAddressBlank_resolvesFromNearCity`
- `findForChat_radiusKmNull_nearAddressIgnored_usesRegularSearch`
- `findForChat_radiusKmSetButBothNearAddressAndNearCityNull_usesRegularSearch`
- `findForChat_blankNearAddressAndNullNearCity_resolveLatLonReturnsNull_usesRegularSearch`
- `findForChat_blankNearAddressAndBlankNearCity_resolveLatLonReturnsNull_usesRegularSearch`

Also update the `stubSearchForChat` helper and both `searchForChatNearLocation` stubs to add a trailing `anyInt()`/`eq(5)` matcher (matching the clamped default when the test passes `null` for limit):

```java
    private void stubSearchForChat(List<ListingEntity> results) {
        when(listingRepository.searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), any(Boolean.class), anyInt()))
                .thenReturn(results);
    }
```

And in `findForChat_withNearAddressAndRadius_geocodesAndCallsNearLocationSearch` / `findForChat_withNearCityAndRadius_geocodesCityAndCallsNearLocationSearch`, change:

```java
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000))).thenReturn(List.of());
```

to:

```java
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000), anyInt())).thenReturn(List.of());
```

(same for the `10_000` variant), and their matching `verify(...)` calls get the same trailing `anyInt()`. Add `import static org.mockito.ArgumentMatchers.anyInt;` if not already present (it already is, from the existing `anyInt()` usage in `findForChat_withNearAddressAndRadius_geocodingFails_fallsBackToRegularSearch`).

- [ ] **Step 2: Run the test file to confirm it fails to compile against the still-unmodified `ListingService`**

Run: `cd hermes-backend && ./mvnw -q test-compile`
Expected: FAIL — `ListingServiceFindForChatTest` calls `findForChat` with 13 args, but `ListingService.findForChat` still only accepts 12.

- [ ] **Step 3: Implement the `limit` threading and `clampLimit` helper in `ListingService`**

Replace the `findForChat` method and the two private helpers it calls, in `ListingService.java`:

```java
    @Transactional(readOnly = true)
    public List<ListingDto> findForChat(Integer minPrice, Integer maxPrice,
                                        Integer minBedrooms, Integer minRooms,
                                        Integer minLivingAreaM2, String province,
                                        String city, String keywords,
                                        boolean sortByPriceDesc,
                                        String nearAddress, String nearCity, Integer radiusKm,
                                        Integer limit) {
        if (radiusKm != null && (nearAddress != null || nearCity != null)) {
            GeocodeResult latLon = resolveLatLon(nearAddress, nearCity);
            if (latLon != null) {
                return searchNearLocationInternal(latLon.lon(), latLon.lat(),
                        minBedrooms, minRooms, minLivingAreaM2, province, keywords,
                        minPrice, maxPrice, radiusKm * 1000, limit);
            }
        }
        List<ListingEntity> results = listingRepository.searchForChat(minBedrooms, minRooms, minLivingAreaM2,
                        province, city, keywords, minPrice, maxPrice, sortByPriceDesc, clampLimit(limit));
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
        return results.stream().map(l -> toDto(l, geoById)).toList();
    }

    /**
     * Radius search using coordinates that are already known (e.g. from a saved, geocoded
     * user profile) — skips the string-based geocoding {@link #resolveLatLon} performs.
     */
    @Transactional(readOnly = true)
    public List<ListingDto> findNearLocation(double lon, double lat,
                                              Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
                                              String province, String keywords,
                                              Integer minPrice, Integer maxPrice,
                                              int radiusMeters, Integer limit) {
        return searchNearLocationInternal(lon, lat, minBedrooms, minRooms, minLivingAreaM2,
                province, keywords, minPrice, maxPrice, radiusMeters, limit);
    }

    private List<ListingDto> searchNearLocationInternal(double lon, double lat,
                                                          Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2,
                                                          String province, String keywords,
                                                          Integer minPrice, Integer maxPrice,
                                                          int radiusMeters, Integer limit) {
        List<ListingEntity> results = listingRepository.searchForChatNearLocation(
                        minBedrooms, minRooms, minLivingAreaM2,
                        province, keywords, minPrice, maxPrice,
                        lon, lat, radiusMeters, clampLimit(limit));
        Map<UUID, ListingGeoProjection> geoById = fetchGeoSafely(idsOf(results));
        return results.stream().map(l -> toDto(l, geoById)).toList();
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return 5;
        return Math.min(limit, 15);
    }
```

- [ ] **Step 4: Run the test file to confirm it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=ListingServiceFindForChatTest`
Expected: PASS (all tests green).

- [ ] **Step 5: Add clamp-behavior tests**

Append to `ListingServiceFindForChatTest.java`:

```java
    // ── limit clamping ────────────────────────────────────────────────────────

    @Test
    void findForChat_nullLimit_defaultsToFive() {
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, null);

        verify(listingRepository).searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), eq(5));
    }

    @Test
    void findForChat_limitAboveFifteen_clampedToFifteen() {
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, 20);

        verify(listingRepository).searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), eq(15));
    }

    @Test
    void findForChat_limitWithinRange_passedThroughUnchanged() {
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, 10);

        verify(listingRepository).searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), eq(10));
    }

    @Test
    void findForChat_zeroOrNegativeLimit_clampedToFive() {
        stubSearchForChat(List.of());

        service.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, 0);

        verify(listingRepository).searchForChat(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), eq(5));
    }

    // ── findNearLocation ──────────────────────────────────────────────────────

    @Test
    void findNearLocation_callsRepositoryDirectlyWithoutGeocoding() {
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000), eq(5))).thenReturn(List.of());

        service.findNearLocation(4.9041, 52.3676, null, null, null, null, null, null, null, 5_000, null);

        verifyNoInteractions(geocodingService);
        verify(listingRepository).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000), eq(5));
    }

    @Test
    void findNearLocation_limitAboveFifteen_clampedToFifteen() {
        when(listingRepository.searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                anyDouble(), anyDouble(), anyInt(), eq(15))).thenReturn(List.of());

        service.findNearLocation(4.9041, 52.3676, null, null, null, null, null, null, null, 5_000, 30);

        verify(listingRepository).searchForChatNearLocation(
                any(), any(), any(), any(), any(), any(), any(),
                eq(4.9041), eq(52.3676), eq(5_000), eq(15));
    }
```

- [ ] **Step 6: Run the full test file again**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=ListingServiceFindForChatTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/listing/ListingService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/listing/ListingServiceFindForChatTest.java
git commit -m "feat(listing): add configurable/clamped limit and findNearLocation"
```

---

### Task 3: Thread `limit` through `ListingSearchTool` and fix `WatchTaskHandler`'s call site

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/tool/ListingSearchTool.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/WatchTaskHandler.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/ListingSearchToolTest.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/WatchTaskHandlerTest.java`

**Interfaces:**
- Consumes: `ListingService.findForChat(..., Integer limit)` from Task 2.
- Produces: `ListingSearchTool.searchListings(..., Integer limit)` — the chat-facing tool now accepts an optional `limit` parameter as its 13th argument.

- [ ] **Step 1: Update `WatchTaskHandlerTest`'s stub to add a 13th `any()` matcher**

In `WatchTaskHandlerTest.java`, every occurrence of:

```java
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any())).thenReturn(List.of(old));
```

(and its variants with `newListing`, `noDate`) gets a 13th `any()`:

```java
        when(listingService.findForChat(any(), any(), any(), any(), any(), any(), any(), any(),
            any(Boolean.class), any(), any(), any(), any())).thenReturn(List.of(old));
```

Apply this to all matching stub calls in the file (there are 6: `returnsEmptyWhenNoNewListings`, `returnsNotificationWhenNewListingFound`, `handle_lastRunAtNull_usesCreatedAt`, `handle_firstSeenAtNull_noRecentPriceChange_returnsEmpty`, `handle_priceHistoryWithOldAndNullTimestamps_notCountedAsChange`, `handle_priceChangedListing_returnsNotification`, `handle_mixedNewAndPriceChanged_returnsNotification`, `handle_newListingWithNullCurrentPrice_formatsWithoutPrice` — 8 occurrences total).

- [ ] **Step 2: Update `ListingSearchToolTest`'s stubs and tool method calls**

Every `when(listingService.findForChat(...))` call gets a trailing `, null` (13th arg), and every `tool.searchListings(...)` call gets a trailing `, null` (13th arg). For example:

```java
        when(listingService.findForChat(null, 500000, 3, null, null, null, "Amsterdam", null, false, null, null, null))
                .thenReturn(List.of(listing));
        ...
        List<ChatListingCard> result = tool.searchListings("Amsterdam", null, null, 500000, 3, null, null, null, "asc", null, null, null);
```

becomes:

```java
        when(listingService.findForChat(null, 500000, 3, null, null, null, "Amsterdam", null, false, null, null, null, null))
                .thenReturn(List.of(listing));
        ...
        List<ChatListingCard> result = tool.searchListings("Amsterdam", null, null, 500000, 3, null, null, null, "asc", null, null, null, null);
```

Apply the same trailing `, null` to all 6 test methods in the file.

- [ ] **Step 3: Run both test files to confirm they fail to compile (the production code doesn't accept the new arg yet)**

Run: `cd hermes-backend && ./mvnw -q test-compile`
Expected: FAIL — too many arguments to `findForChat`/`searchListings`.

- [ ] **Step 4: Add the `limit` parameter to `ListingSearchTool.searchListings`**

In `ListingSearchTool.java`, change the method signature and body:

```java
    public List<ChatListingCard> searchListings(
            @ToolParam(required = false, description = "City to filter by, omit if not specified") String city,
            @ToolParam(required = false, description = "Province to filter by, omit if not specified") String province,
            @ToolParam(required = false, description = "Minimum asking price in euros, omit if no minimum") Integer minPrice,
            @ToolParam(required = false, description = "Maximum asking price in euros, omit if no maximum") Integer maxPrice,
            @ToolParam(required = false, description = "Minimum number of bedrooms, omit if no minimum") Integer minBedrooms,
            @ToolParam(required = false, description = "Minimum total number of rooms, omit if no minimum") Integer minRooms,
            @ToolParam(required = false, description = "Minimum living area in square metres, omit if no minimum") Integer minLivingAreaM2,
            @ToolParam(required = false, description = "Free-text keywords to search in property descriptions, omit if not specified") String keywords,
            @ToolParam(required = false, description = "Price sort: use 'desc' for most expensive first, 'asc' or omit for cheapest first or no preference") String priceSort,
            @ToolParam(required = false, description = "Address to search near, format: 'houseNumber, street, city'. Use when user asks about listings near a specific address.") String nearAddress,
            @ToolParam(required = false, description = "City name to search near. Use when user asks about listings near a city.") String nearCity,
            @ToolParam(required = false, description = "Search radius in kilometres. Required when nearAddress or nearCity is set.") Integer radiusKm,
            @ToolParam(required = false, description = "Number of listings to return, default 5, max 15") Integer limit
    ) {
        boolean sortDesc = "desc".equalsIgnoreCase(priceSort);
        log.info("searchListings called: city={}, province={}, minBedrooms={}, minPrice={}, maxPrice={}, priceSort={}, nearAddress={}, nearCity={}, radiusKm={}, limit={}",
                city, province, minBedrooms, minPrice, maxPrice, priceSort, nearAddress, nearCity, radiusKm, limit);
        callCounter.increment();
        List<ChatListingCard> cards = listingService.findForChat(
                minPrice, maxPrice,
                minBedrooms, minRooms, minLivingAreaM2,
                blankToNull(province), blankToNull(city), blankToNull(keywords),
                sortDesc,
                blankToNull(nearAddress), blankToNull(nearCity), radiusKm, limit
        ).stream().map(mapper::toChatListingCard).toList();
        log.info("searchListings returned {} results", cards.size());
        resultHolder.set(cards);
        return cards;
    }
```

Also update the `@Tool` description just above the method to mention the new parameter:

```java
    @Tool(description = "Search for property listings matching the user's criteria. "
            + "ALWAYS call this tool before describing any listings — never invent property details. "
            + "Use priceSort='desc' for 'most expensive'/'highest price'/'luxury'; use priceSort='asc' or omit for 'cheapest'/'lowest price' or no sort preference. "
            + "For radius searches: set nearAddress (format: 'houseNumber, street, city') or nearCity and radiusKm. "
            + "Use limit to control how many results come back (default 5, max 15) — e.g. set limit=10 if the user asks for 10 listings.")
```

- [ ] **Step 5: Update `WatchTaskHandler`'s call site to pass `null` for the new parameter**

In `WatchTaskHandler.java`, change:

```java
        List<ListingDto> matches = listingService.findForChat(
            payload.minPrice(), payload.maxPrice(),
            payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
            payload.province(), payload.city(), payload.keywords(),
            false, null, payload.nearCity(), payload.radiusKm()
        );
```

to:

```java
        List<ListingDto> matches = listingService.findForChat(
            payload.minPrice(), payload.maxPrice(),
            payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
            payload.province(), payload.city(), payload.keywords(),
            false, null, payload.nearCity(), payload.radiusKm(), null
        );
```

- [ ] **Step 6: Run all three affected test files**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=ListingSearchToolTest,WatchTaskHandlerTest`
Expected: PASS.

- [ ] **Step 7: Add a limit-passthrough test to `ListingSearchToolTest`**

Append:

```java
    @Test
    void searchListings_limitParam_passedThroughToService() {
        when(listingService.findForChat(null, null, null, null, null, null, null, null, false, null, null, null, 10))
                .thenReturn(List.of());

        AtomicReference<List<ChatListingCard>> holder = new AtomicReference<>(List.of());
        ListingSearchTool tool = new ListingSearchTool(listingService, mapper, holder, new SimpleMeterRegistry());

        tool.searchListings(null, null, null, null, null, null, null, null, null, null, null, null, 10);

        // verified implicitly by the stub matching; an unstubbed call would return null and NPE on .stream()
    }
```

- [ ] **Step 8: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS (all tests green, including the previously-updated ones).

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/tool/ListingSearchTool.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/WatchTaskHandler.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/tool/ListingSearchToolTest.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/WatchTaskHandlerTest.java
git commit -m "feat(ai): expose configurable result limit on the chat search tool"
```

---

### Task 4: Capture the JWT `email` claim on `CurrentUser`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java`

**Interfaces:**
- Produces: `CurrentUser(UUID id, String username, String email, Set<String> roles)` — `email` is a new record component, positioned after `username`. `CurrentUser.from(Jwt)` extracts it via `jwt.getClaimAsString("email")` (may be `null` if the claim is absent). No existing call site uses the positional constructor or depends on component order (verified: all usages go through `.id()`/`.username()`/`.current()`/`.from()`), so this is a safe additive change.

- [ ] **Step 1: Write the failing test**

Append to `CurrentUserTest.java`:

```java
    @Test
    void from_extractsEmailClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .claim("email", "testuser@hermes.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.email()).isEqualTo("testuser@hermes.local");
    }

    @Test
    void from_missingEmailClaim_returnsNull() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        CurrentUser currentUser = CurrentUser.from(jwt);

        assertThat(currentUser.email()).isNull();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=CurrentUserTest`
Expected: FAIL — `CurrentUser` has no `email()` accessor yet (compile error).

- [ ] **Step 3: Add the `email` field**

In `CurrentUser.java`, change:

```java
public record CurrentUser(UUID id, String username, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        return new CurrentUser(id, username, extractRoles(jwt));
    }
```

to:

```java
public record CurrentUser(UUID id, String username, String email, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        return new CurrentUser(id, username, email, extractRoles(jwt));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=CurrentUserTest`
Expected: PASS.

- [ ] **Step 5: Run the full backend test suite to confirm nothing else broke**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/security/CurrentUser.java hermes-backend/src/test/java/com/kropholler/dev/hermes/security/CurrentUserTest.java
git commit -m "feat(security): capture email claim on CurrentUser"
```

---

### Task 5: Add `email` column to `UserProfileEntity`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java`
- Create: `hermes-backend/src/main/resources/db/migration/V14__add_email_to_user_profiles.sql`

**Interfaces:**
- Produces: `UserProfileEntity.getEmail()`/`setEmail(String)` (via existing `@Getter @Setter` on the class), backed by a new nullable `email VARCHAR(255)` column.

This is a schema-only change with no new business logic, so there's no isolated unit test — it's exercised by Task 6's filter test and any existing Flyway-validating integration test that boots the full schema (e.g. `ListingRepositoryRadiusTest` runs `spring.jpa.hibernate.ddl-auto=validate` against a real Postgres via Testcontainers, which will catch any entity/schema mismatch).

- [ ] **Step 1: Add the migration**

Create `hermes-backend/src/main/resources/db/migration/V14__add_email_to_user_profiles.sql`:

```sql
ALTER TABLE user_profiles ADD COLUMN email VARCHAR(255);
```

- [ ] **Step 2: Add the `email` field to the entity**

In `UserProfileEntity.java`, add the field after `province`:

```java
    private String street;
    private String houseNumber;
    private String houseNumberAddition;
    private String zipCode;
    private String city;
    private String province;
    private String email;

    private Double latitude;
    private Double longitude;
```

- [ ] **Step 3: Run the Flyway-validating integration test to confirm the entity mapping matches the new schema**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=ListingRepositoryRadiusTest`
Expected: PASS (this test's `spring.jpa.hibernate.ddl-auto=validate` setting will fail loudly if the entity and migration disagree).

- [ ] **Step 4: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/profile/UserProfileEntity.java hermes-backend/src/main/resources/db/migration/V14__add_email_to_user_profiles.sql
git commit -m "feat(profile): add email column to user_profiles"
```

---

### Task 6: `UserProfileSyncFilter` — sync JWT email onto the profile on every authenticated request

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/UserProfileSyncFilter.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/UserProfileSyncFilterTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java`

**Interfaces:**
- Consumes: `CurrentUser.from(Jwt)` (Task 4), `UserProfileRepository` (existing), `UserProfileEntity.getEmail()/setEmail()` (Task 5).
- Produces: `UserProfileSyncFilter` — a `@Component` implementing `OncePerRequestFilter`, registered via `http.addFilterAfter(userProfileSyncFilter, BearerTokenAuthenticationFilter.class)` in `SecurityConfig.filterChain(...)`.

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/config/UserProfileSyncFilterTest.java`:

```java
package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileSyncFilterTest {

    @Mock UserProfileRepository userProfileRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    private UserProfileSyncFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserProfileSyncFilter(userProfileRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwtWithEmail(UUID subject, String email) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("email", email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }

    @Test
    void doFilterInternal_newProfile_createsRowWithEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "user@hermes.local")));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEmail()).isEqualTo("user@hermes.local");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_existingProfileWithChangedEmail_updatesEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "new@hermes.local")));
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setEmail("old@hermes.local");
        existing.setUpdatedAt(Instant.now().minusSeconds(3600));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@hermes.local");
    }

    @Test
    void doFilterInternal_existingProfileWithSameEmail_doesNotSave() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtWithEmail(userId, "same@hermes.local")));
        UserProfileEntity existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setEmail("same@hermes.local");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).save(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noAuthentication_skipsSyncAndContinuesChain() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).save(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_jwtWithoutEmailClaim_skipsSync() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt jwtNoEmail = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtNoEmail));

        filter.doFilterInternal(request, response, filterChain);

        verify(userProfileRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }
}
```

Add `import org.junit.jupiter.api.BeforeEach;` alongside the other JUnit imports at the top of the file.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileSyncFilterTest`
Expected: FAIL — `UserProfileSyncFilter` class doesn't exist yet (compile error).

- [ ] **Step 3: Implement `UserProfileSyncFilter`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/config/UserProfileSyncFilter.java`:

```java
package com.kropholler.dev.hermes.config;

import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import com.kropholler.dev.hermes.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Keeps {@code user_profiles.email} in sync with the JWT's {@code email} claim.
 * Runs on every authenticated request so that background jobs (which have no JWT
 * available) can still look up a user's email via their profile.
 */
@Component
@RequiredArgsConstructor
class UserProfileSyncFilter extends OncePerRequestFilter {

    private final UserProfileRepository userProfileRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            syncEmail(CurrentUser.from(jwt));
        }
        filterChain.doFilter(request, response);
    }

    private void syncEmail(CurrentUser user) {
        if (user.email() == null) return;
        UserProfileEntity entity = userProfileRepository.findById(user.id()).orElseGet(() -> {
            UserProfileEntity e = new UserProfileEntity();
            e.setUserId(user.id());
            e.setUpdatedAt(Instant.now());
            return e;
        });
        if (user.email().equals(entity.getEmail())) return;
        entity.setEmail(user.email());
        userProfileRepository.save(entity);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=UserProfileSyncFilterTest`
Expected: PASS.

- [ ] **Step 5: Register the filter in `SecurityConfig`**

In `SecurityConfig.java`, add the import:

```java
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
```

Change the `filterChain` bean method signature and body from:

```java
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/ws/chat/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }
```

to:

```java
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler,
            UserProfileSyncFilter userProfileSyncFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/ws/chat/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling.accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .addFilterAfter(userProfileSyncFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
```

- [ ] **Step 6: Update `SecurityConfigTest` to supply the new filter dependency**

`SecurityConfigTest` uses `@WebMvcTest(FavoriteController.class)` with `@Import(SecurityConfig.class)`, so `UserProfileSyncFilter` needs to be resolvable as a bean in that slice test too. Add a `@MockitoBean` for it:

```java
    @MockitoBean UserProfileSyncFilter userProfileSyncFilter;
```

Add this alongside the other `@MockitoBean` fields (`jwtDecoder`, `favoriteService`, `favoriteApiMapper`). Since `UserProfileSyncFilter` is package-private and `SecurityConfigTest` is in the same package (`com.kropholler.dev.hermes.config`), no import is needed.

Note: a plain Mockito mock of `OncePerRequestFilter` won't actually invoke `filterChain.doFilter(...)` by default, which would make requests hang in the slice test. Use `@MockitoBean(answer = Answers.CALLS_REAL_METHODS)`... actually simpler: since `doFilterInternal` is `protected` and the mock is never told to call through, Mockito's default `RETURNS_DEFAULTS` on a `void` method is a no-op, and `OncePerRequestFilter.doFilter(...)` (the public entry point Spring Security actually calls) is a real, un-mocked method on the real class hierarchy *unless* Mockito fully mocks it too. To keep this simple and correct, mock only what's needed by having Mockito produce a working no-op filter: add `doAnswer` stubbing in each test that hits the filter chain isn't necessary here because `@MockitoBean` mocks the entire object, including `doFilter`. Add this stub once, in a `@BeforeEach`:

```java
    @org.junit.jupiter.api.BeforeEach
    void passThroughFilter() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest req = invocation.getArgument(0);
            jakarta.servlet.ServletResponse res = invocation.getArgument(1);
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(userProfileSyncFilter).doFilter(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
```

- [ ] **Step 7: Run `SecurityConfigTest` to confirm existing tests still pass**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SecurityConfigTest`
Expected: PASS.

- [ ] **Step 8: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/config/UserProfileSyncFilter.java hermes-backend/src/test/java/com/kropholler/dev/hermes/config/UserProfileSyncFilterTest.java hermes-backend/src/main/java/com/kropholler/dev/hermes/config/SecurityConfig.java hermes-backend/src/test/java/com/kropholler/dev/hermes/config/SecurityConfigTest.java
git commit -m "feat(security): sync JWT email onto user profile via a servlet filter"
```

---

### Task 7: Route notification emails to the actual user

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/EmailNotificationSender.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/EmailNotificationSenderTest.java`

**Interfaces:**
- Consumes: `UserProfileRepository.findById(UUID)` (existing), `UserProfileEntity.getEmail()` (Task 5).
- Produces: `EmailNotificationSender` now sends to `dto.userId()`'s profile email when present, falling back to the existing `hermes.notifications.to-email` config value otherwise. Public method signature (`sendAsync(NotificationDto)`) is unchanged.

- [ ] **Step 1: Write the failing test**

Add to `EmailNotificationSenderTest.java` (new imports: `com.kropholler.dev.hermes.profile.UserProfileEntity`, `com.kropholler.dev.hermes.profile.UserProfileRepository`, and add `@Mock UserProfileRepository userProfileRepository;` alongside the existing mocks):

```java
    @Mock UserProfileRepository userProfileRepository;

    @Test
    void sendAsync_userHasProfileEmail_sendsToProfileEmail() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        setEmails("from@hermes.nl", "fallback@hermes.nl");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setEmail("actualuser@hermes.local");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(notificationRepository.findById(id)).thenReturn(Optional.of(new NotificationEntity()));

        sender.sendAsync(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("actualuser@hermes.local");
    }

    @Test
    void sendAsync_userHasNoProfileEmail_fallsBackToConfigValue() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(id, null, userId,
            "Price alert", "Dropped 10%", List.of(), false, null, null);
        setEmails("from@hermes.nl", "fallback@hermes.nl");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(new NotificationEntity()));

        sender.sendAsync(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("fallback@hermes.nl");
    }
```

The three pre-existing tests each need a `when(userProfileRepository.findById(any())).thenReturn(Optional.empty());` stub added (the lookup now happens before every send, including the one in the exception-swallowing test, since it runs before `mailSender.send` is called). Replace all three with:

```java
    @Test
    void sendAsync_sendsMailWithCorrectFields() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("from@hermes.nl");
        assertThat(msg.getTo()).containsExactly("to@user.nl");
        assertThat(msg.getSubject()).isEqualTo("[Hermes] Price alert");
        assertThat(msg.getText()).isEqualTo("Dropped 10%");
    }

    @Test
    void sendAsync_updatesEmailSentAt() {
        UUID id = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(entity));

        sender.sendAsync(dto(id));

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailSentAt()).isNotNull();
    }

    @Test
    void sendAsync_swallowsMailException() {
        UUID id = UUID.randomUUID();
        setEmails("from@hermes.nl", "to@user.nl");
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.sendAsync(dto(id));

        verify(notificationRepository, never()).save(any());
    }
```

These three replace the file's existing versions of the same three test methods verbatim (same method names, same assertions — only the added `userProfileRepository` stub line is new).

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=EmailNotificationSenderTest`
Expected: FAIL — `EmailNotificationSender` has no `UserProfileRepository` field for `@InjectMocks` to wire, and `sendAsync` still always uses the config `toEmail`.

- [ ] **Step 3: Implement the per-user lookup with fallback**

In `EmailNotificationSender.java`, add the import and field, and change `sendAsync`:

```java
package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
class EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${hermes.notifications.from-email}")
    private String fromEmail;

    @Value("${hermes.notifications.to-email}")
    private String toEmail;

    @Async
    public void sendAsync(NotificationDto dto) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(resolveRecipient(dto.userId()));
            msg.setSubject("[Hermes] " + dto.title());
            msg.setText(dto.body());
            mailSender.send(msg);
            notificationRepository.findById(dto.id()).ifPresent(n -> {
                n.setEmailSentAt(Instant.now());
                notificationRepository.save(n);
            });
        } catch (Exception e) {
            log.error("Failed to send notification email for {}", dto.id(), e);
        }
    }

    private String resolveRecipient(java.util.UUID userId) {
        return userProfileRepository.findById(userId)
            .map(com.kropholler.dev.hermes.profile.UserProfileEntity::getEmail)
            .filter(email -> email != null && !email.isBlank())
            .orElse(toEmail);
    }
}
```

(Using fully-qualified `java.util.UUID` and `UserProfileEntity` inline in `resolveRecipient` to avoid adding two more import lines — replace with proper top-of-file imports `java.util.UUID` and `com.kropholler.dev.hermes.profile.UserProfileEntity` instead, matching the rest of the codebase's style of always importing rather than fully-qualifying inline.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=EmailNotificationSenderTest`
Expected: PASS.

- [ ] **Step 5: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/notification/EmailNotificationSender.java hermes-backend/src/test/java/com/kropholler/dev/hermes/notification/EmailNotificationSenderTest.java
git commit -m "fix(notification): send emails to the actual user instead of one hardcoded address"
```

---

### Task 8: `AREA_RESEARCH` task type, payload, and `AgentTaskService.createAreaResearch`

**Files:**
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskType.java`
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/json/AreaResearchPayload.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java`

**Interfaces:**
- Produces:
  - `AgentTaskType.AREA_RESEARCH` enum constant.
  - `AreaResearchPayload(Integer radiusKm, Integer limit, Integer minBedrooms, Integer minRooms, Integer minLivingAreaM2, Integer minPrice, Integer maxPrice, String keywords, Double overrideLon, Double overrideLat)` record.
  - `AgentTaskService.createAreaResearch(UUID userId, String name, AreaResearchPayload payload)` — persists with `schedule = "0 0 8 * * *"`, same pattern as `createWatch`.

- [ ] **Step 1: Write the failing test**

Append to `AgentTaskServiceTest.java` (add import `com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;` alongside the existing `WatchPayload` import):

```java
    @Test
    void createAreaResearch_persistsTaskWithDailySchedule() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AreaResearchPayload payload = new AreaResearchPayload(15, 10, 3, null, 80, null, 500000, null, null, null);
        service.createAreaResearch(UUID.randomUUID(), "Best nearby homes", payload);

        ArgumentCaptor<AgentTaskEntity> captor = ArgumentCaptor.forClass(AgentTaskEntity.class);
        verify(repo).save(captor.capture());
        AgentTaskEntity saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(AgentTaskType.AREA_RESEARCH);
        assertThat(saved.getSchedule()).isEqualTo("0 0 8 * * *");
        assertThat(saved.getNextRunAt()).isAfter(Instant.now().minusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(AgentTaskStatus.ACTIVE);
        assertThat(saved.getName()).isEqualTo("Best nearby homes");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AgentTaskServiceTest`
Expected: FAIL — `AgentTaskType.AREA_RESEARCH`, `AreaResearchPayload`, and `AgentTaskService.createAreaResearch` don't exist yet (compile error).

- [ ] **Step 3: Add the `AREA_RESEARCH` enum constant**

In `AgentTaskType.java`, change:

```java
public enum AgentTaskType { WATCH, RESEARCH, DIGEST }
```

to:

```java
public enum AgentTaskType { WATCH, RESEARCH, DIGEST, AREA_RESEARCH }
```

- [ ] **Step 4: Create `AreaResearchPayload`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/json/AreaResearchPayload.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task.handler.json;

public record AreaResearchPayload(
    Integer radiusKm,
    Integer limit,
    Integer minBedrooms,
    Integer minRooms,
    Integer minLivingAreaM2,
    Integer minPrice,
    Integer maxPrice,
    String keywords,
    Double overrideLon,
    Double overrideLat
) {}
```

- [ ] **Step 5: Add `createAreaResearch` to `AgentTaskService`**

In `AgentTaskService.java`, add the import `com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;` alongside the existing payload imports, and add this method after `createDigest`:

```java
    @Transactional
    public AgentTaskDto createAreaResearch(UUID userId, String name, AreaResearchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setUserId(userId);
        task.setName(name);
        task.setPayload(serialize(payload));
        task.setSchedule("0 0 8 * * *");
        task.setNextRunAt(computeNext("0 0 8 * * *"));
        return toDto(agentTaskRepository.save(task));
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AgentTaskServiceTest`
Expected: PASS.

- [ ] **Step 7: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskType.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/json/AreaResearchPayload.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskService.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/AgentTaskServiceTest.java
git commit -m "feat(agent-task): add AREA_RESEARCH task type and creation method"
```

---

### Task 9: `AreaResearchTaskHandler`

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandler.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandlerTest.java`

**Interfaces:**
- Consumes: `AgentTaskHandler` interface (existing), `ListingService.findNearLocation(...)` (Task 2), `AreaResearchPayload` (Task 8), `UserProfileRepository`/`UserProfileEntity.getLongitude()/getLatitude()` (existing), `GetListingSummaryTool`/`GetPriceHistoryTool`/`CompareListingsTool` (existing, unchanged).
- Produces: `AreaResearchTaskHandler` registered as a `@Component` implementing `AgentTaskHandler` with `getType() == AgentTaskType.AREA_RESEARCH` — picked up automatically by `AgentTaskExecutor`'s `List<AgentTaskHandler>` injection, no wiring changes needed there.

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandlerTest.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.ListingStatus;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaResearchTaskHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock ListingService listingService;
    @Mock ChatListingCardMapper chatListingCardMapper;
    @Mock ListingSummaryService listingSummaryService;
    @Mock UserProfileRepository userProfileRepository;

    JsonMapper objectMapper = new JsonMapper();
    AreaResearchTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AreaResearchTaskHandler(chatClient, listingService, chatListingCardMapper,
            listingSummaryService, new SimpleMeterRegistry(), objectMapper, userProfileRepository);
    }

    private AgentTaskEntity task(UUID userId, AreaResearchPayload payload) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setStatus(AgentTaskStatus.ACTIVE);
        task.setUserId(userId);
        task.setName("Best nearby homes");
        task.setPayload(objectMapper.writeValueAsString(payload));
        task.setNextRunAt(Instant.now());
        return task;
    }

    private ListingDto listing(UUID id) {
        return new ListingDto(id, "fundaId", "http://example.com", "Herenstraat", "10", null,
            "3500AA", "Utrecht", "Utrecht", Instant.now(), Instant.now(),
            350000, ListingStatus.FOR_SALE, null, 120, 5, 3, null, null, null);
    }

    private void stubAiNarrative(String narrative) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any(Object[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(narrative);
    }

    @Test
    void handle_homeAddress_usesProfileCoordinatesAndReturnsNotification() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(15, 5, 3, null, 80, null, 500000, null, null, null);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        UUID listingId = UUID.randomUUID();
        when(listingService.findNearLocation(4.9041, 52.3676, 3, null, 80, null, null, null, 500000, 15_000, 5))
            .thenReturn(List.of(listing(listingId)));
        stubAiNarrative("1. Herenstraat 10 is the best match because it fits the budget and size.");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isPresent();
        assertThat(result.get().title()).contains("1 best listings within 15km");
        assertThat(result.get().body()).contains("Herenstraat 10");
        assertThat(result.get().listingIds()).containsExactly(listingId);
    }

    @Test
    void handle_overrideCoordinates_usedInsteadOfProfile() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative("Good options near the overridden location.");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isPresent();
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void handle_noOverrideAndNoProfileCoordinates_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, null, null);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_profileExistsButCoordinatesNull_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, null, null);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_noCandidatesFound_returnsEmptyWithoutCallingAi() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of());

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void handle_aiReturnsBlank_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative("   ");

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_aiReturnsNull_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AreaResearchPayload payload = new AreaResearchPayload(10, 5, null, null, null, null, null, null, 5.1214, 52.0907);
        when(listingService.findNearLocation(5.1214, 52.0907, null, null, null, null, null, null, null, 10_000, 5))
            .thenReturn(List.of(listing(UUID.randomUUID())));
        stubAiNarrative(null);

        Optional<NotificationContent> result = handler.handle(task(userId, payload));

        assertThat(result).isEmpty();
    }

    @Test
    void handle_invalidPayload_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        AgentTaskEntity task = new AgentTaskEntity();
        task.setId(UUID.randomUUID());
        task.setType(AgentTaskType.AREA_RESEARCH);
        task.setUserId(userId);
        task.setName("Broken");
        task.setPayload("{not valid json");
        task.setNextRunAt(Instant.now());

        Optional<NotificationContent> result = handler.handle(task);

        assertThat(result).isEmpty();
        verifyNoInteractions(userProfileRepository, chatClient);
    }

    @Test
    void getType_returnsAreaResearch() {
        assertThat(handler.getType()).isEqualTo(AgentTaskType.AREA_RESEARCH);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test-compile`
Expected: FAIL — `AreaResearchTaskHandler` doesn't exist yet (compile error).

- [ ] **Step 3: Implement `AreaResearchTaskHandler`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandler.java`:

```java
package com.kropholler.dev.hermes.ai.agent.task.handler;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskEntity;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.ai.chat.ChatListingCardMapper;
import com.kropholler.dev.hermes.ai.tool.CompareListingsTool;
import com.kropholler.dev.hermes.ai.tool.GetListingSummaryTool;
import com.kropholler.dev.hermes.ai.tool.GetPriceHistoryTool;
import com.kropholler.dev.hermes.listing.ListingDto;
import com.kropholler.dev.hermes.listing.ListingService;
import com.kropholler.dev.hermes.listing.summary.ListingSummaryService;
import com.kropholler.dev.hermes.notification.NotificationContent;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
class AreaResearchTaskHandler implements AgentTaskHandler {

    private final ChatClient chatClient;
    private final ListingService listingService;
    private final ChatListingCardMapper chatListingCardMapper;
    private final ListingSummaryService listingSummaryService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final UserProfileRepository userProfileRepository;

    public AreaResearchTaskHandler(@Qualifier("chatClient") ChatClient chatClient,
                                    ListingService listingService,
                                    ChatListingCardMapper chatListingCardMapper,
                                    ListingSummaryService listingSummaryService,
                                    MeterRegistry meterRegistry,
                                    ObjectMapper objectMapper,
                                    UserProfileRepository userProfileRepository) {
        this.chatClient = chatClient;
        this.listingService = listingService;
        this.chatListingCardMapper = chatListingCardMapper;
        this.listingSummaryService = listingSummaryService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public AgentTaskType getType() { return AgentTaskType.AREA_RESEARCH; }

    @Override
    public Optional<NotificationContent> handle(AgentTaskEntity task) {
        AreaResearchPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), AreaResearchPayload.class);
        } catch (JacksonException e) {
            log.error("Failed to deserialize AreaResearchPayload for task {}", task.getId(), e);
            return Optional.empty();
        }

        double[] center = resolveCenter(task.getUserId(), payload);
        if (center == null) {
            log.warn("No center coordinates available for area research task {}; skipping this run", task.getId());
            return Optional.empty();
        }

        List<ListingDto> candidates = listingService.findNearLocation(
                center[0], center[1],
                payload.minBedrooms(), payload.minRooms(), payload.minLivingAreaM2(),
                null, payload.keywords(), payload.minPrice(), payload.maxPrice(),
                payload.radiusKm() * 1000, payload.limit());

        if (candidates.isEmpty()) {
            log.info("Area research task {}: no candidates found within radius", task.getId());
            return Optional.empty();
        }

        var summaryTool = new GetListingSummaryTool(listingService, listingSummaryService, meterRegistry);
        var historyTool = new GetPriceHistoryTool(listingService, meterRegistry);
        var compareTool = new CompareListingsTool(listingService, chatListingCardMapper,
                new AtomicReference<>(List.of()), meterRegistry);

        String result = chatClient.prompt()
                .user(buildPrompt(payload, candidates))
                .tools(summaryTool, historyTool, compareTool)
                .call()
                .content();

        if (result == null || result.isBlank()) return Optional.empty();

        List<UUID> listingIds = candidates.stream().map(ListingDto::id).toList();
        return Optional.of(new NotificationContent(
                "%d best listings within %dkm".formatted(candidates.size(), payload.radiusKm()),
                result, listingIds));
    }

    private double[] resolveCenter(UUID userId, AreaResearchPayload payload) {
        if (payload.overrideLon() != null && payload.overrideLat() != null) {
            return new double[] { payload.overrideLon(), payload.overrideLat() };
        }
        return userProfileRepository.findById(userId)
                .filter(p -> p.getLongitude() != null && p.getLatitude() != null)
                .map(p -> new double[] { p.getLongitude(), p.getLatitude() })
                .orElse(null);
    }

    private String buildPrompt(AreaResearchPayload payload, List<ListingDto> candidates) {
        String candidateList = candidates.stream()
                .map(c -> "- %s %s, %s: €%s, %s bedrooms, %s m²".formatted(
                        c.street(), c.houseNumber(), c.city(),
                        c.currentPrice() != null ? String.format("%,d", c.currentPrice()).replace(",", ".") : "unknown",
                        c.bedrooms() != null ? c.bedrooms() : "unknown",
                        c.livingAreaM2() != null ? c.livingAreaM2() : "unknown"))
                .collect(Collectors.joining("\n"));

        return """
            Research and rank the following %d property listings, which are already the closest
            matches within %d km of the target location satisfying the requested criteria
            (minimum bedrooms: %s, minimum living area: %s m², price range: %s-%s):

            %s

            Use getListingSummary, getPriceHistory, and compareListings to research these specific
            properties. Do not search for other properties. Write a ranked write-up explaining why
            each one is a good match, considering price, size, bedrooms, and overall value.
            """.formatted(
                candidates.size(), payload.radiusKm(),
                payload.minBedrooms() != null ? payload.minBedrooms() : "any",
                payload.minLivingAreaM2() != null ? payload.minLivingAreaM2() : "any",
                payload.minPrice() != null ? payload.minPrice() : "any",
                payload.maxPrice() != null ? payload.maxPrice() : "any",
                candidateList);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AreaResearchTaskHandlerTest`
Expected: PASS.

- [ ] **Step 5: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS. (`AgentTaskExecutor`'s constructor takes `List<AgentTaskHandler>` and builds its dispatch map from `AgentTaskHandler::getType`, so the new `@Component` is picked up automatically — no changes needed there or in any test that mocks `AgentTaskExecutor`'s handler list, since none exist; `AgentTaskExecutor` itself is only unit-tested with hand-built handler lists, not a full Spring context.)

- [ ] **Step 6: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandler.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandlerTest.java
git commit -m "feat(agent-task): implement AreaResearchTaskHandler"
```

---

### Task 10: `SaveAreaResearchTool` chat tool with creation-time validation

**Files:**
- Create: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`
- Create: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`
- Modify: `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java`
- Modify: `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProviderTest.java`

**Interfaces:**
- Consumes: `AgentTaskService.createAreaResearch(...)` (Task 8), `TaskTool` base class (existing), `UserProfileRepository`/`UserProfileEntity` (existing/Task 5), `GeocodingService.geocodeAddress(String, String, String)`/`.findOrFetchCity(String)` (existing).
- Produces: `SaveAreaResearchTool` — a chat-facing `@Tool` method `saveAreaResearch(...)`, registered in `AgentChatToolProvider.provideTools(UUID)`.

- [ ] **Step 1: Write the failing test**

Create `hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java`:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskDto;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskStatus;
import com.kropholler.dev.hermes.ai.agent.task.AgentTaskType;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.listing.city.CityEntity;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveAreaResearchToolTest {

    @Mock AgentTaskService agentTaskService;
    @Mock UserProfileRepository userProfileRepository;
    @Mock GeocodingService geocodingService;

    private SaveAreaResearchTool tool(UUID userId) {
        return new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService);
    }

    @Test
    void noOverrideAndProfileHasAddress_createsTask() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "Best listings within 15km", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 15, 10, 3, null, 80, null, 500000, null, null, null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().radiusKm()).isEqualTo(15);
        assertThat(cap.getValue().limit()).isEqualTo(10);
        assertThat(cap.getValue().overrideLon()).isNull();
        assertThat(cap.getValue().overrideLat()).isNull();
        assertThat(result).contains("saved");
        verifyNoInteractions(geocodingService);
    }

    @Test
    void noOverrideAndNoProfileAddress_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        String result = tool(userId).saveAreaResearch(null, 15, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("set your home address");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void profileExistsButCoordinatesNull_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        String result = tool(userId).saveAreaResearch(null, 15, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("set your home address");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void nearAddressOverride_geocodesAndFreezesCoordinates() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("10, Kerkstraat, Utrecht", "", ""))
            .thenReturn(Optional.of(new GeocodeResult(5.1214, 52.0907, null)));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "name", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            "10, Kerkstraat, Utrecht", null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().overrideLon()).isEqualTo(5.1214);
        assertThat(cap.getValue().overrideLat()).isEqualTo(52.0907);
        assertThat(result).contains("saved");
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void nearCityOverride_geocodesAndFreezesCoordinates() {
        UUID userId = UUID.randomUUID();
        CityEntity city = new CityEntity();
        city.setLongitude(4.4777);
        city.setLatitude(51.9244);
        when(geocodingService.findOrFetchCity("Rotterdam")).thenReturn(Optional.of(city));
        AgentTaskDto dto = new AgentTaskDto(UUID.randomUUID(), AgentTaskType.AREA_RESEARCH,
            AgentTaskStatus.ACTIVE, userId, "name", "0 0 8 * * *", null, Instant.now(), Instant.now());
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(dto);

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            null, "Rotterdam");

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), anyString(), cap.capture());
        assertThat(cap.getValue().overrideLon()).isEqualTo(4.4777);
        assertThat(cap.getValue().overrideLat()).isEqualTo(51.9244);
        assertThat(result).contains("saved");
    }

    @Test
    void overrideGeocodingFails_rejectsWithoutCreatingTask() {
        UUID userId = UUID.randomUUID();
        when(geocodingService.geocodeAddress("Nowhere Street", "", "")).thenReturn(Optional.empty());

        String result = tool(userId).saveAreaResearch(null, 10, null, null, null, null, null, null, null,
            "Nowhere Street", null);

        assertThat(result).contains("could not find", "Could not find");
        verify(agentTaskService, never()).createAreaResearch(any(), anyString(), any());
    }

    @Test
    void blankName_buildsDefaultNameFromRadius() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(null);

        tool(userId).saveAreaResearch("  ", 12, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentTaskService).createAreaResearch(eq(userId), nameCaptor.capture(), any());
        assertThat(nameCaptor.getValue()).contains("12");
    }

    @Test
    void blankKeywords_treatedAsNullInPayload() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setLongitude(4.9041);
        profile.setLatitude(52.3676);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(agentTaskService.createAreaResearch(any(), anyString(), any())).thenReturn(null);

        tool(userId).saveAreaResearch("My search", 12, null, null, null, null, null, null, "  ", null, null);

        ArgumentCaptor<AreaResearchPayload> cap = ArgumentCaptor.forClass(AreaResearchPayload.class);
        verify(agentTaskService).createAreaResearch(eq(userId), eq("My search"), cap.capture());
        assertThat(cap.getValue().keywords()).isNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hermes-backend && ./mvnw -q test-compile`
Expected: FAIL — `SaveAreaResearchTool` doesn't exist yet (compile error).

- [ ] **Step 3: Implement `SaveAreaResearchTool`**

Create `hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java`:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.agent.task.handler.json.AreaResearchPayload;
import com.kropholler.dev.hermes.listing.geocoding.GeocodeResult;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileEntity;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

class SaveAreaResearchTool extends TaskTool {

    private final UserProfileRepository userProfileRepository;
    private final GeocodingService geocodingService;

    protected SaveAreaResearchTool(UUID userId, AgentTaskService agentTaskService,
                                    UserProfileRepository userProfileRepository,
                                    GeocodingService geocodingService) {
        super(userId, agentTaskService);
        this.userProfileRepository = userProfileRepository;
        this.geocodingService = geocodingService;
    }

    @Tool(description = "Set up a recurring daily search that finds and researches the best available "
        + "listings within a radius of your home address (or another address/city), ranked by an AI "
        + "reviewing price, size, bedrooms, and value. Call this when the user wants ongoing curated "
        + "recommendations near a location, e.g. 'find me the best 10 houses within 15km of my address "
        + "every day'. Use limit to control how many listings to rank (default 5, max 15).")
    public String saveAreaResearch(
        @ToolParam(required = false, description = "Friendly name for this search, e.g. 'Best homes near me'") String name,
        @ToolParam(description = "Search radius in kilometres from the target location") Integer radiusKm,
        @ToolParam(required = false, description = "Number of listings to rank, default 5, max 15") Integer limit,
        @ToolParam(required = false, description = "Minimum number of bedrooms") Integer minBedrooms,
        @ToolParam(required = false, description = "Minimum total rooms") Integer minRooms,
        @ToolParam(required = false, description = "Minimum living area in square metres") Integer minLivingAreaM2,
        @ToolParam(required = false, description = "Minimum asking price in euros") Integer minPrice,
        @ToolParam(required = false, description = "Maximum asking price in euros") Integer maxPrice,
        @ToolParam(required = false, description = "Keywords to search in descriptions") String keywords,
        @ToolParam(required = false, description = "Address to search near instead of the user's home address, format: 'houseNumber, street, city'") String nearAddress,
        @ToolParam(required = false, description = "City to search near instead of the user's home address") String nearCity
    ) {
        Double overrideLon = null;
        Double overrideLat = null;

        if (hasOverride(nearAddress, nearCity)) {
            GeocodeResult geocoded = geocodeOverride(nearAddress, nearCity);
            if (geocoded == null) {
                return "Could not find that location — please try a different address or city.";
            }
            overrideLon = geocoded.lon();
            overrideLat = geocoded.lat();
        } else if (!userHasHomeAddress()) {
            return "Please set your home address in your profile before creating an area search.";
        }

        String taskName = (name != null && !name.isBlank()) ? name : "Best listings within " + radiusKm + "km";
        AreaResearchPayload payload = new AreaResearchPayload(
            radiusKm, limit, minBedrooms, minRooms, minLivingAreaM2, minPrice, maxPrice,
            blankToNull(keywords), overrideLon, overrideLat);
        agentTaskService.createAreaResearch(userId, taskName, payload);
        return "Area research '" + taskName + "' saved — I'll research and rank the best listings daily.";
    }

    private boolean hasOverride(String nearAddress, String nearCity) {
        return (nearAddress != null && !nearAddress.isBlank()) || (nearCity != null && !nearCity.isBlank());
    }

    private boolean userHasHomeAddress() {
        return userProfileRepository.findById(userId)
            .map(p -> p.getLongitude() != null && p.getLatitude() != null)
            .orElse(false);
    }

    private GeocodeResult geocodeOverride(String nearAddress, String nearCity) {
        if (nearAddress != null && !nearAddress.isBlank()) {
            return geocodingService.geocodeAddress(nearAddress, "", "").orElse(null);
        }
        if (nearCity != null && !nearCity.isBlank()) {
            return geocodingService.findOrFetchCity(nearCity)
                .map(c -> new GeocodeResult(c.getLongitude(), c.getLatitude(), null))
                .orElse(null);
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=SaveAreaResearchToolTest`
Expected: PASS.

- [ ] **Step 5: Register the tool in `AgentChatToolProvider`**

In `AgentChatToolProvider.java`, add the two new dependencies and register the tool:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.ai.ChatToolProvider;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentChatToolProvider implements ChatToolProvider {

    private final AgentTaskService agentTaskService;
    private final UserProfileRepository userProfileRepository;
    private final GeocodingService geocodingService;

    @Override
    public List<Object> provideTools(UUID userId) {
        return List.of(
            new SaveWatchTool(userId, agentTaskService),
            new TriggerResearchTool(userId, agentTaskService),
            new TriggerDigestTool(userId, agentTaskService),
            new ListWatchesTool(userId, agentTaskService),
            new SaveAreaResearchTool(userId, agentTaskService, userProfileRepository, geocodingService)
        );
    }
}
```

- [ ] **Step 6: Update `AgentChatToolProviderTest` for the new dependencies and expected tool list**

In `AgentChatToolProviderTest.java`, add the two new mocks and update the expected count/types:

```java
package com.kropholler.dev.hermes.ai.agent.tool;

import com.kropholler.dev.hermes.ai.agent.task.AgentTaskService;
import com.kropholler.dev.hermes.listing.geocoding.GeocodingService;
import com.kropholler.dev.hermes.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AgentChatToolProviderTest {

    @Mock AgentTaskService agentTaskService;
    @Mock UserProfileRepository userProfileRepository;
    @Mock GeocodingService geocodingService;
    @InjectMocks AgentChatToolProvider provider;

    @Test
    void provideTools_returnsListOfFiveTools() {
        List<Object> tools = provider.provideTools(UUID.randomUUID());

        assertThat(tools).hasSize(5);
        assertThat(tools).hasExactlyElementsOfTypes(
            SaveWatchTool.class,
            TriggerResearchTool.class,
            TriggerDigestTool.class,
            ListWatchesTool.class,
            SaveAreaResearchTool.class
        );
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd hermes-backend && ./mvnw -q test -Dtest=AgentChatToolProviderTest`
Expected: PASS.

- [ ] **Step 8: Run the full backend test suite**

Run: `cd hermes-backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchTool.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/SaveAreaResearchToolTest.java hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProvider.java hermes-backend/src/test/java/com/kropholler/dev/hermes/ai/agent/tool/AgentChatToolProviderTest.java
git commit -m "feat(ai): register SaveAreaResearchTool for chat-triggered area research"
```

---

### Task 11: Full-suite verification and design doc cross-check

**Files:** None (verification only).

- [ ] **Step 1: Run the complete backend build and test suite**

Run: `cd hermes-backend && ./mvnw -q clean verify`
Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 2: Manually cross-check against the spec's Global Constraints**

Confirm each of the following against the actual code (not just re-reading the plan):
- `ListingSearchTool`, `ListingService.findForChat`, and `findNearLocation` all clamp `null`/non-positive → 5, else `min(limit, 15)`.
- `AreaResearchPayload`-created tasks have `schedule = "0 0 8 * * *"`.
- `AreaResearchTaskHandler` never calls `GeocodingService` (grep for it in that file — it shouldn't appear at all).
- `AreaResearchTaskHandler`'s tool set passed to `chatClient.prompt().tools(...)` is exactly `summaryTool, historyTool, compareTool` — no search or favourites tool.
- `NotificationContent.listingIds` in `AreaResearchTaskHandler.handle` comes from `candidates.stream().map(ListingDto::id)`, not from any `AtomicReference` populated by a tool call.

Run: `grep -n "GeocodingService\|ListingSearchTool\|GetFavouriteListingsTool" hermes-backend/src/main/java/com/kropholler/dev/hermes/ai/agent/task/handler/AreaResearchTaskHandler.java`
Expected: no matches.

- [ ] **Step 3: Confirm no frontend changes are needed**

The frontend has no hardcoded switch on `AgentTaskType` values (confirmed during design research — task lists render generically), and `AgentTaskResponse.type` in the OpenAPI schema is a plain `string`, so the new `AREA_RESEARCH` value flows through without any codegen or frontend changes. No action needed, just a final sanity check:

Run: `grep -rn "AREA_RESEARCH\|WATCH\|RESEARCH\|DIGEST" hermes-frontend/src --include=*.ts`
Expected: no matches (confirms the frontend genuinely doesn't branch on task type).
