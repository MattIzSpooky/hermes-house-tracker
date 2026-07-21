package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.cucumber.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ScenarioScope
public class NotificationSteps {

    @Autowired MockMvc mockMvc;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ScenarioContext context;

    @Given("the user has an unread notification")
    public void userHasUnreadNotification() {
        context.setNotificationId(saveNotification(context.getCurrentUserId(), false).getId());
    }

    @Given("the user has {int} unread notifications")
    public void userHasNUnreadNotifications(int count) {
        for (int i = 0; i < count; i++) {
            saveNotification(context.getCurrentUserId(), false);
        }
    }

    @Given("another user has a notification")
    public void anotherUserHasNotification() {
        UUID anotherUser = UUID.randomUUID();
        context.setNotificationId(saveNotification(anotherUser, false).getId());
    }

    @When("the user marks the notification as read")
    public void userMarksNotificationAsRead() throws Exception {
        context.setLastResponse(mockMvc.perform(
            patch("/api/notifications/{id}/read", context.getNotificationId())
                .with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())))
        ));
    }

    @When("the current user tries to mark it as read")
    public void currentUserTriesToMarkItAsRead() throws Exception {
        context.setLastResponse(mockMvc.perform(
            patch("/api/notifications/{id}/read", context.getNotificationId())
                .with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())))
        ));
    }

    @When("the user tries to mark an unknown notification as read")
    public void userTriesToMarkUnknownNotificationAsRead() throws Exception {
        context.setLastResponse(mockMvc.perform(
            patch("/api/notifications/{id}/read", UUID.randomUUID())
                .with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())))
        ));
    }

    @When("the user requests their unread count")
    public void userRequestsUnreadCount() throws Exception {
        context.setLastResponse(mockMvc.perform(
            get("/api/notifications/unread-count")
                .with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())))
        ));
    }

    @Then("the user now has {int} unread notifications")
    public void userNowHasNUnreadNotificationsAssertion(int expected) throws Exception {
        mockMvc.perform(
            get("/api/notifications/unread-count")
                .with(jwt().jwt(b -> b.subject(context.getCurrentUserId().toString())))
        ).andExpect(jsonPath("$.count").value(expected));
    }

    @Then("the unread count is {int}")
    public void unreadCountIs(int expected) throws Exception {
        context.getLastResponse().andExpect(jsonPath("$.count").value(expected));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NotificationEntity saveNotification(UUID userId, boolean read) {
        NotificationEntity e = new NotificationEntity();
        e.setUserId(userId);
        e.setTitle("Test notification");
        e.setBody("Test body");
        e.setListingIds("[]");
        e.setRead(read);
        return notificationRepository.save(e);
    }
}
