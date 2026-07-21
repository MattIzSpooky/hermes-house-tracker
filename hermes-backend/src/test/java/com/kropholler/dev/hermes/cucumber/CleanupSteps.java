package com.kropholler.dev.hermes.cucumber;

import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class CleanupSteps {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Before
    public void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM listing_summaries");
        jdbcTemplate.execute("DELETE FROM price_history_entries");
        jdbcTemplate.execute("DELETE FROM favorites");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM agent_tasks");
        jdbcTemplate.execute("DELETE FROM listings");
        jdbcTemplate.execute("DELETE FROM cities");
    }
}
