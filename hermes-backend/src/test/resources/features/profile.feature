Feature: User profile address management
  As a registered user
  I want to save and retrieve my home address
  So the application can suggest nearby listings

  Background:
    Given the user is authenticated

  Scenario: Profile is empty before any address is saved
    When the user retrieves their profile
    Then the request succeeds
    And the profile has no saved address

  Scenario: User can retrieve a previously saved address
    Given the user has a saved address in "Amsterdam"
    When the user retrieves their profile
    Then the request succeeds
    And the profile city is "Amsterdam"

  Scenario: User can save a new address
    Given the address can be geocoded successfully
    When the user saves their address as street "Teststraat" number "1" in "Amsterdam"
    Then the request succeeds
    And the profile city is "Amsterdam"

  Scenario: Saving a new address includes geocoded coordinates
    Given the address can be geocoded successfully
    When the user saves their address as street "Teststraat" number "1" in "Amsterdam"
    Then the request succeeds
    And the profile latitude is not null

  Scenario: Saving a new address replaces an existing one
    Given the user has a saved address in "Amsterdam"
    And the address can be geocoded successfully
    When the user saves their address as street "Dorpstraat" number "5" in "Rotterdam"
    Then the request succeeds
    And the profile city is "Rotterdam"

  Scenario: An address that cannot be geocoded is rejected
    Given the address cannot be geocoded
    When the user saves their address as street "Nep" number "0" in "Nergens"
    Then the request is rejected because the input is invalid

  Scenario: Unauthenticated user cannot retrieve their profile
    Given the user is not authenticated
    When the user retrieves their profile
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot update their address
    Given the user is not authenticated
    When the user saves their address as street "Teststraat" number "1" in "Amsterdam"
    Then the request is rejected because the user is not signed in
