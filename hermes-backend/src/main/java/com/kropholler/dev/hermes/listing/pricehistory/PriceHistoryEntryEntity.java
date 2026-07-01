package com.kropholler.dev.hermes.listing.pricehistory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_history_entries")
@Getter
@Setter
@NoArgsConstructor
public class PriceHistoryEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID listingId;

    private Integer price;

    private String status;

    private String source;

    private LocalDate date;

    private Instant timestamp;
}
