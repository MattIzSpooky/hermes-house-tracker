package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ScrapingPoller {

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingWorker worker;

    @Scheduled(fixedDelay = 5_000)
    public void pollQueue() {
        sessionRepository.findFirstPendingWithLock(ScrapingSessionStatus.PENDING)
            .ifPresent(worker::process);
    }
}
