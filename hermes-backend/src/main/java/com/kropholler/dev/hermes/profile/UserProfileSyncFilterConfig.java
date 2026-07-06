package com.kropholler.dev.hermes.profile;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link UserProfileSyncFilter} as a bean explicitly, rather than via
 * {@code @Component}, so that plain classpath component scanning (including
 * {@code @WebMvcTest}'s web-layer auto-detection, which would otherwise sweep up any
 * {@code @Component} implementing {@code Filter} regardless of package) never
 * auto-includes it. {@code SecurityConfig} picks it up only via the qualifier below.
 */
@Configuration
class UserProfileSyncFilterConfig {

    @Bean
    @Qualifier("userProfileSyncFilter")
    UserProfileSyncFilter userProfileSyncFilter(UserProfileRepository userProfileRepository) {
        return new UserProfileSyncFilter(userProfileRepository);
    }
}
