Feature: Notification lifecycle
  As a registered user
  I want to receive and manage notifications about my watched properties
  So I stay informed about updates that are relevant to me

  Background:
    Given the user is authenticated

  Scenario: User marks their own notification as read
    Given the user has an unread notification
    When the user marks the notification as read
    Then the response status is 204
    And the user now has 0 unread notifications

  Scenario: User cannot mark another user's notification as read
    Given another user has a notification
    When the current user tries to mark it as read
    Then the response status is 403

  Scenario: Marking a non-existent notification returns 404
    When the user tries to mark an unknown notification as read
    Then the response status is 404

  Scenario: User can see their unread notification count
    Given the user has 2 unread notifications
    When the user requests their unread count
    Then the response status is 200
    And the unread count is 2
