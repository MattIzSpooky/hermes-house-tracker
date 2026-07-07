package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.crypto.EncryptedDoubleConverter;
import com.kropholler.dev.hermes.crypto.EncryptedStringConverter;
import com.kropholler.dev.hermes.crypto.EncryptionKeyVersionListener;
import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import com.kropholler.dev.hermes.crypto.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnableConfigurationProperties(EncryptionProperties.class)
@Import({FieldEncryptor.class, EncryptedStringConverter.class, EncryptedDoubleConverter.class, EncryptionKeyVersionListener.class, UserProfileReencryptionTask.class})
@TestPropertySource(properties = "hermes.encryption.current-version=2")
class UserProfileReencryptionTaskTest {

    @Autowired UserProfileRepository userProfileRepository;
    @Autowired UserProfileReencryptionTask task;
    @Autowired EntityManager em;

    @Test
    void reencryptBatch_migratesStaleRowsToCurrentVersionAndReturnsCountProcessed() {
        UUID userId = UUID.randomUUID();
        String legacyStreet = legacyEncrypt("Dorpstraat");
        String legacyHouseNumber = legacyEncrypt("12");
        String legacyHouseNumberAddition = legacyEncrypt("A");
        String legacyZipCode = legacyEncrypt("1234AB");
        String legacyCity = legacyEncrypt("Utrecht");
        String legacyProvince = legacyEncrypt("Utrecht");
        String legacyEmail = legacyEncrypt("user@hermes.local");
        String legacyLatitude = legacyEncrypt("52.09");
        String legacyLongitude = legacyEncrypt("5.12");

        em.createNativeQuery("""
                INSERT INTO user_profiles (user_id, street, house_number, house_number_addition, zip_code, city,
                    province, email, latitude, longitude, encryption_key_version, updated_at)
                VALUES (:userId, :street, :houseNumber, :houseNumberAddition, :zipCode, :city, :province, :email,
                    :latitude, :longitude, 1, now())
                """)
            .setParameter("userId", userId)
            .setParameter("street", legacyStreet)
            .setParameter("houseNumber", legacyHouseNumber)
            .setParameter("houseNumberAddition", legacyHouseNumberAddition)
            .setParameter("zipCode", legacyZipCode)
            .setParameter("city", legacyCity)
            .setParameter("province", legacyProvince)
            .setParameter("email", legacyEmail)
            .setParameter("latitude", legacyLatitude)
            .setParameter("longitude", legacyLongitude)
            .executeUpdate();
        em.clear();

        int processed = task.reencryptBatch();
        em.clear();

        assertThat(processed).isEqualTo(1);
        UserProfileEntity reloaded = userProfileRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.getHouseNumber()).isEqualTo("12");
        assertThat(reloaded.getHouseNumberAddition()).isEqualTo("A");
        assertThat(reloaded.getZipCode()).isEqualTo("1234AB");
        assertThat(reloaded.getCity()).isEqualTo("Utrecht");
        assertThat(reloaded.getProvince()).isEqualTo("Utrecht");
        assertThat(reloaded.getEmail()).isEqualTo("user@hermes.local");
        assertThat(reloaded.getLatitude()).isEqualTo(52.09);
        assertThat(reloaded.getLongitude()).isEqualTo(5.12);
        assertThat(reloaded.getEncryptionKeyVersion()).isEqualTo(2);

        Object rawEmail = em.createNativeQuery("SELECT email FROM user_profiles WHERE user_id = :id")
            .setParameter("id", userId)
            .getSingleResult();
        assertThat(rawEmail.toString()).startsWith("2:");

        Object rawLatitude = em.createNativeQuery("SELECT latitude FROM user_profiles WHERE user_id = :id")
            .setParameter("id", userId)
            .getSingleResult();
        assertThat(rawLatitude.toString()).startsWith("2:");

        assertThat(task.reencryptBatch()).isEqualTo(0);
    }

    private static String legacyEncrypt(String plaintext) {
        return "1:" + Encryptors.text("test-encryption-key-v1", "abcd1234abcd1234").encrypt(plaintext);
    }
}
