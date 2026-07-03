package com.kropholler.dev.hermes.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record CurrentUser(UUID id, String username, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        return new CurrentUser(id, username, extractRoles(jwt));
    }

    public static CurrentUser current() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return from(jwt);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return Set.of();
        }
        return roles.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
    }
}
