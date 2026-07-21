package com.kropholler.dev.hermes.cucumber;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

@Component
@ScenarioScope
public class ScenarioContext {

    private UUID currentUserId = UUID.randomUUID();
    private UUID listingId;
    private UUID notificationId;
    private ResultActions lastResponse;

    public UUID getCurrentUserId() { return currentUserId; }

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }

    public ResultActions getLastResponse() { return lastResponse; }
    public void setLastResponse(ResultActions lastResponse) { this.lastResponse = lastResponse; }
}
