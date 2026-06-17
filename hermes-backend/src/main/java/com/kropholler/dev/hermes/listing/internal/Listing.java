package com.kropholler.dev.hermes.listing.internal;

import com.kropholler.dev.hermes.listing.ListingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String fundaId;

    @Column(nullable = false)
    private String url;

    private String street;
    private String houseNumber;
    private String houseNumberAddition;
    private String zipCode;
    private String city;
    private String province;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer livingAreaM2;
    private Integer rooms;
    private Integer bedrooms;

    @Column(length = 10)
    private String energyLabel;

    private Integer plotAreaM2;

    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    private Instant lastUpdatedAt;

    private Instant deletedAt;

}
