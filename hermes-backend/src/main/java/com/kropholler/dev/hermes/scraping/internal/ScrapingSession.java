package com.kropholler.dev.hermes.scraping.internal;

import com.kropholler.dev.hermes.scraping.ScrapingSessionStatus;
import com.kropholler.dev.hermes.scraping.ScrapingSessionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scraping_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ScrapingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrapingSessionStatus status = ScrapingSessionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScrapingSessionType type;

    private String city;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minArea;
    private Integer maxArea;

    @Column(nullable = false)
    private Integer pageLimit;

    @Column(nullable = false)
    private String fundaUrl;

    private String targetListingUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant completedAt;
}
