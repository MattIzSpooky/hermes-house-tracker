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

public interface ScrapingSessionRepository extends JpaRepository<ScrapingSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScrapingSessionEntity s WHERE s.status = :status ORDER BY s.createdAt ASC LIMIT 1")
    Optional<ScrapingSessionEntity> findFirstPendingWithLock(ScrapingSessionStatus status);

    List<ScrapingSessionEntity> findByStatusAndStartedAtBefore(ScrapingSessionStatus status, Instant cutoff);
}
