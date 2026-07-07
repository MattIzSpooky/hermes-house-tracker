package com.kropholler.dev.hermes.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("reencrypt")
public class ReencryptionRunner implements CommandLineRunner {

    private final List<Reencryptable> reencryptables;

    public ReencryptionRunner(List<Reencryptable> reencryptables) {
        this.reencryptables = reencryptables;
    }

    @Override
    public void run(String... args) {
        for (Reencryptable reencryptable : reencryptables) {
            int processed;
            do {
                processed = reencryptable.reencryptBatch();
                log.info("Re-encrypted {} rows in {}", processed, reencryptable.tableName());
            } while (processed > 0);
        }
        log.info("Re-encryption complete for all tables.");
    }
}
