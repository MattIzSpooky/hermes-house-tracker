package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "listing_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ListingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID listingId;

    @Column(nullable = false)
    private Instant scrapedAt = Instant.now();

    private Integer askingPrice;
    private Integer livingAreaM2;
    private Integer rooms;
    private String energyLabel;
    private LocalDate listedOnFundaSince;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;
}
