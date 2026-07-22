Feature: Managing favourite listings
  As a registered user
  I want to save and manage favourite property listings
  So I can quickly revisit the properties I am most interested in

  Background:
    Given the user is authenticated

  Scenario: User saves a listing to favourites
    Given a listing id to work with
    When the user adds the listing to their favourites
    Then the request succeeds with no content

  Scenario: Adding the same listing twice is idempotent
    Given a listing id to work with
    When the user adds the listing to their favourites
    And the user adds the listing to their favourites
    Then the request succeeds with no content
    And the user has exactly 1 favourite

  Scenario: User removes a listing from favourites
    Given the listing is already in the user's favourites
    When the user removes the listing from their favourites
    Then the request succeeds with no content
    And the user has exactly 0 favourites

  Scenario: Removing a listing that was never saved is safe
    Given a listing id to work with
    When the user removes the listing from their favourites
    Then the request succeeds with no content

  Scenario: User retrieves their saved favourites
    Given the listing is already in the user's favourites
    When the user retrieves their favourites
    Then the request succeeds
    And the response body contains 1 favourite

  Scenario: Favourites list is empty when nothing has been saved
    When the user retrieves their favourites
    Then the request succeeds
    And the response body contains 0 favourites

  Scenario: Unauthenticated user cannot view favourites
    Given the user is not authenticated
    When the user retrieves their favourites
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot add a favourite
    Given the user is not authenticated
    And a listing id to work with
    When the user adds the listing to their favourites
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot remove a favourite
    Given the user is not authenticated
    And a listing id to work with
    When the user removes the listing from their favourites
    Then the request is rejected because the user is not signed in
