package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionVersioned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@EntityListeners(EncryptionKeyVersionListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserProfileEntity implements EncryptionVersioned {

    @Id
    private UUID userId;

    @Convert(converter = EncryptedStringConverter.class)
    private String street;
    @Convert(converter = EncryptedStringConverter.class)
    private String houseNumber;
    @Convert(converter = EncryptedStringConverter.class)
    private String houseNumberAddition;
    @Convert(converter = EncryptedStringConverter.class)
    private String zipCode;
    @Convert(converter = EncryptedStringConverter.class)
    private String city;
    @Convert(converter = EncryptedStringConverter.class)
    private String province;
    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    @Convert(converter = EncryptedDoubleConverter.class)
    private Double latitude;
    @Convert(converter = EncryptedDoubleConverter.class)
    private Double longitude;

    @Column(name = "encryption_key_version", nullable = false)
    private Integer encryptionKeyVersion = 1;

    private Instant updatedAt;
}
