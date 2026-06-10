package com.kropholler.dev.hermes.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class EventPublicationSchemaMigrator implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // Spring Modulith creates event_publication with VARCHAR(255) by default.
        // ddl-auto=update never alters existing column types, so we do it here.
        // The try-catch handles H2 (tests) and cases where columns are already TEXT.
        for (String column : new String[]{"serialized_event", "event_type", "listener_id"}) {
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE event_publication ALTER COLUMN " + column + " TYPE TEXT");
                log.debug("Widened event_publication.{} to TEXT", column);
            } catch (Exception e) {
                log.trace("Could not widen event_publication.{}: {}", column, e.getMessage());
            }
        }
    }
}
