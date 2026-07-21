Feature: Listing price report
  As a registered user
  I want to view a price report for a property listing
  So I can understand how the asking price has changed over time

  Background:
    Given the user is authenticated

  Scenario: Report shows a price decrease as a negative percentage
    Given a listing with asking-price history of 400000 then 360000
    When the user requests the report for that listing
    Then the response status is 200
    And the report shows an initial price of 400000
    And the report shows a price change of -10.0 percent

  Scenario: Report shows a price increase as a positive percentage
    Given a listing with asking-price history of 300000 then 360000
    When the user requests the report for that listing
    Then the response status is 200
    And the report shows an initial price of 300000
    And the report shows a price change of 20.0 percent

  Scenario: Report is unavailable when the listing has no price history
    Given a listing with no price history exists
    When the user requests the report for that listing
    Then the response status is 404

  Scenario: Report is unavailable when the listing does not exist
    When the user requests a report for an unknown listing id
    Then the response status is 404

  Scenario: Unauthenticated user cannot request a report
    Given the user is not authenticated
    When the user requests a report for an unknown listing id
    Then the response status is 401
