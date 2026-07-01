package com.kropholler.dev.hermes.favourites;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favourites")
@Getter
@Setter
@NoArgsConstructor
public class FavouriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private UUID listingId;

    @Column(nullable = false)
    private Instant savedAt = Instant.now();
}
