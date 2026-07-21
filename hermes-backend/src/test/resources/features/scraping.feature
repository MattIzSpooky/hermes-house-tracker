Feature: Scraping session management
  As an admin
  I want to create and monitor scraping sessions
  So that I can trigger and track Funda data imports

  Background:
    Given the user is authenticated

  Scenario: Admin creates a scraping session
    Given the user has admin privileges
    When the user creates a scraping session for city "Amsterdam"
    Then the response status is 201
    And the response contains a scraping session id
    And the scraping session status is "PENDING"
    And the scraping session type is "SEARCH"

  Scenario: Admin retrieves an existing scraping session
    Given the user has admin privileges
    And a scraping session exists for "Rotterdam"
    When the user retrieves the scraping session
    Then the response status is 200
    And the scraping session status is "PENDING"

  Scenario: Admin retrieves a non-existent scraping session
    Given the user has admin privileges
    When the user retrieves a scraping session with an unknown id
    Then the response status is 404

  Scenario: Non-admin user cannot create a scraping session
    When the user creates a scraping session for city "Amsterdam"
    Then the response status is 403

  Scenario: Non-admin user cannot retrieve a scraping session
    Given a scraping session exists for "Utrecht"
    When the user retrieves the scraping session
    Then the response status is 403

  Scenario: Unauthenticated user cannot create a scraping session
    Given the user is not authenticated
    When the user creates a scraping session for city "Amsterdam"
    Then the response status is 401

  Scenario: Unauthenticated user cannot retrieve a scraping session
    Given a scraping session exists for "Den Haag"
    And the user is not authenticated
    When the user retrieves the scraping session
    Then the response status is 401
