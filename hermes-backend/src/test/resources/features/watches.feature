Feature: Managing property watches
  As a registered user
  I want to create and manage watches on property search criteria
  So I am notified when new or price-changed listings match my interests

  Background:
    Given the user is authenticated

  Scenario: User lists their active watches
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user retrieves their watches
    Then the request succeeds
    And the response contains 1 watch

  Scenario: User with no watches gets an empty list
    When the user retrieves their watches
    Then the request succeeds
    And the response contains 0 watches

  Scenario: Listing watches only returns the current user's watches
    Given the user has an active watch named "My watch"
    And another user has an active watch
    When the user retrieves their watches
    Then the request succeeds
    And the response contains 1 watch

  Scenario: User deletes one of their watches
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user deletes the watch
    Then the request succeeds with no content
    And the user has 0 watches

  Scenario: User cannot delete another user's watch
    Given another user has an active watch
    When the current user tries to delete it
    Then the request is rejected because the user is not allowed to do this

  Scenario: Deleting a non-existent watch fails
    When the user tries to delete an unknown watch id
    Then the request fails because the resource cannot be found

  Scenario: Admin can trigger immediate execution of their own watch
    Given the user has admin privileges
    And the user has an active watch named "Utrecht search"
    When the user triggers the watch
    Then the request is accepted for processing

  Scenario: Admin cannot trigger another user's watch
    Given the user has admin privileges
    And another user has an active watch
    When the user triggers the watch
    Then the request is rejected because the user is not allowed to do this

  Scenario: Non-admin cannot trigger watch execution
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user triggers the watch
    Then the request is rejected because the user is not allowed to do this

  Scenario: Unauthenticated user cannot list watches
    Given the user is not authenticated
    When the user retrieves their watches
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot delete a watch
    Given the user is not authenticated
    When the user tries to delete an unknown watch id
    Then the request is rejected because the user is not signed in

  Scenario: Unauthenticated user cannot trigger a watch
    Given the user is not authenticated
    When the user tries to trigger an unknown watch
    Then the request is rejected because the user is not signed in
