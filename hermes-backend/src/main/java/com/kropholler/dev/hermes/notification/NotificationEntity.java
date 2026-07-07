package com.kropholler.dev.hermes.notification;

import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter @Setter @NoArgsConstructor
class NotificationEntity implements EncryptionVersioned {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID taskId;

    @Column(nullable = false)
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String listingIds = "[]";

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant emailSentAt;
}
