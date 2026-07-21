Feature: Managing property watches
  As a registered user
  I want to create and manage watches on property search criteria
  So I am notified when new or price-changed listings match my interests

  Background:
    Given the user is authenticated

  Scenario: User lists their active watches
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user retrieves their watches
    Then the response status is 200
    And the response contains 1 watch

  Scenario: User with no watches gets an empty list
    When the user retrieves their watches
    Then the response status is 200
    And the response contains 0 watches

  Scenario: Listing watches only returns the current user's watches
    Given the user has an active watch named "My watch"
    And another user has an active watch
    When the user retrieves their watches
    Then the response status is 200
    And the response contains 1 watch

  Scenario: User deletes one of their watches
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user deletes the watch
    Then the response status is 204
    And the user has 0 watches

  Scenario: User cannot delete another user's watch
    Given another user has an active watch
    When the current user tries to delete it
    Then the response status is 403

  Scenario: Deleting a non-existent watch returns 404
    When the user tries to delete an unknown watch id
    Then the response status is 404

  Scenario: Admin can trigger immediate execution of their own watch
    Given the user has admin privileges
    And the user has an active watch named "Utrecht search"
    When the user triggers the watch
    Then the response status is 202

  Scenario: Admin cannot trigger another user's watch
    Given the user has admin privileges
    And another user has an active watch
    When the user triggers the watch
    Then the response status is 403

  Scenario: Non-admin cannot trigger watch execution
    Given the user has an active watch named "Amsterdam 3-bed"
    When the user triggers the watch
    Then the response status is 403

  Scenario: Unauthenticated user cannot list watches
    Given the user is not authenticated
    When the user retrieves their watches
    Then the response status is 401

  Scenario: Unauthenticated user cannot delete a watch
    Given the user is not authenticated
    When the user tries to delete an unknown watch id
    Then the response status is 401

  Scenario: Unauthenticated user cannot trigger a watch
    Given the user is not authenticated
    When the user tries to trigger an unknown watch
    Then the response status is 401
