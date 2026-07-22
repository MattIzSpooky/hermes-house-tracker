Feature: Searching for and retrieving property listings
  As a registered user
  I want to search and filter property listings
  So I can find properties that match my criteria

  Background:
    Given the user is authenticated

  Scenario: Searching with no filters returns all listings
    Given 3 listings exist in the database
    When the user searches for listings with no filters
    Then the request succeeds
    And the response contains 3 listings

  Scenario: Search returns an empty page when no listings exist
    When the user searches for listings with no filters
    Then the request succeeds
    And the response contains 0 listings

  Scenario: Filtering by city returns only matching listings
    Given a listing in "Amsterdam" and a listing in "Rotterdam" exist
    When the user searches for listings in "Amsterdam"
    Then the request succeeds
    And the response contains 1 listing

  Scenario: Filtering by minimum bedrooms narrows the results
    Given a listing with 2 bedrooms and a listing with 4 bedrooms exist
    When the user searches for listings with at least 3 bedrooms
    Then the request succeeds
    And the response contains 1 listing

  Scenario: Combining city and bedroom filters narrows the results
    Given a listing with 4 bedrooms in "Amsterdam" exists
    And a listing with 2 bedrooms in "Amsterdam" exists
    And a listing with 4 bedrooms in "Rotterdam" exists
    When the user searches for listings in "Amsterdam" with at least 3 bedrooms
    Then the request succeeds
    And the response contains 1 listing

  Scenario: Radius search returns only listings within range
    Given a listing in Amsterdam at coordinates 4.9041 52.3676
    And a listing in Groningen at coordinates 6.5665 53.2194
    And the city Amsterdam is known at coordinates 4.9041 52.3676
    When the user searches within 50 km of city "Amsterdam"
    Then the request succeeds
    And the response contains 1 listing

  Scenario: Radius search returns no results when no listings are nearby
    Given the city Amsterdam is known at coordinates 4.9041 52.3676
    When the user searches within 5 km of city "Amsterdam"
    Then the request succeeds
    And the response contains 0 listings

  Scenario: Retrieving a listing by id returns the details
    Given a listing in "Utrecht" exists
    When the user retrieves that listing by id
    Then the request succeeds

  Scenario: Retrieving a non-existent listing fails
    When the user retrieves a listing with an unknown id
    Then the request fails because the resource cannot be found

  Scenario: Unauthenticated user cannot search listings
    Given the user is not authenticated
    When the user searches for listings with no filters
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot retrieve a listing by id
    Given the user is not authenticated
    When the user retrieves a listing with an unknown id
    Then the request is rejected because the user is not signed in
