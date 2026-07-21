package com.kropholler.dev.hermes.cucumber;

import io.cucumber.spring.ScenarioScope;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@Component
@ScenarioScope
public class ScenarioContext {

    private UUID currentUserId = UUID.randomUUID();
    private boolean authenticated = true;
    private boolean admin = false;
    private UUID listingId;
    private UUID notificationId;
    private ResultActions lastResponse;

    public UUID getCurrentUserId() { return currentUserId; }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }

    public ResultActions getLastResponse() { return lastResponse; }
    public void setLastResponse(ResultActions lastResponse) { this.lastResponse = lastResponse; }

    public MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder req) {
        if (!authenticated) return req;
        if (admin) {
            return req.with(jwt()
                .jwt(b -> b.subject(currentUserId.toString())
                    .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        }
        return req.with(jwt().jwt(b -> b.subject(currentUserId.toString())));
    }
}
