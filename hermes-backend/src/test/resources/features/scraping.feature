Feature: Scraping session management
  As an admin
  I want to create and monitor scraping sessions
  So that I can trigger and track Funda data imports

  Background:
    Given the user is authenticated

  Scenario: Admin creates a scraping session
    Given the user has admin privileges
    When the user creates a scraping session for city "Amsterdam"
    Then the request succeeds and creates a new resource
    And the response contains a scraping session id
    And the scraping session status is "PENDING"
    And the scraping session type is "SEARCH"

  Scenario: Admin retrieves an existing scraping session
    Given the user has admin privileges
    And a scraping session exists for "Rotterdam"
    When the user retrieves the scraping session
    Then the request succeeds
    And the scraping session status is "PENDING"

  Scenario: Admin retrieves a non-existent scraping session
    Given the user has admin privileges
    When the user retrieves a scraping session with an unknown id
    Then the request fails because the resource cannot be found

  Scenario: Non-admin user cannot create a scraping session
    When the user creates a scraping session for city "Amsterdam"
    Then the request is rejected because the user is not allowed to do this

  Scenario: Non-admin user cannot retrieve a scraping session
    Given a scraping session exists for "Utrecht"
    When the user retrieves the scraping session
    Then the request is rejected because the user is not allowed to do this

  Scenario: Unauthenticated user cannot create a scraping session
    Given the user is not authenticated
    When the user creates a scraping session for city "Amsterdam"
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot retrieve a scraping session
    Given a scraping session exists for "Den Haag"
    And the user is not authenticated
    When the user retrieves the scraping session
    Then the request is rejected because the user is not signed in
