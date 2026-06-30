package com.kropholler.dev.hermes.scraping.schedule.session;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScrapingSessionRepository extends JpaRepository<ScrapingSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScrapingSession s WHERE s.status = :status ORDER BY s.createdAt ASC LIMIT 1")
    Optional<ScrapingSession> findFirstPendingWithLock(ScrapingSessionStatus status);

    List<ScrapingSession> findByStatusAndStartedAtBefore(ScrapingSessionStatus status, Instant cutoff);
}
