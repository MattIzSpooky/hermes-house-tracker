package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private final EncryptionProperties properties = new EncryptionProperties(
        Map.of(1, "old-key", 2, "new-key"),
        Map.of(1, "abcd1234abcd1234", 2, "1234abcd1234abcd"),
        2
    );
    private final FieldEncryptor encryptor = new FieldEncryptor(properties);

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).isNotEqualTo("hello@hermes.local");
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void encrypt_prefixesCiphertextWithCurrentVersion() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).startsWith("2:");
    }

    @Test
    void decrypt_supportsValueEncryptedUnderAnOlderVersion() {
        FieldEncryptor olderEncryptor = new FieldEncryptor(new EncryptionProperties(
            Map.of(1, "old-key"), Map.of(1, "abcd1234abcd1234"), 1));
        String oldCiphertext = olderEncryptor.encrypt("hello@hermes.local");

        assertThat(oldCiphertext).startsWith("1:");
        assertThat(encryptor.decrypt(oldCiphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void decrypt_unknownVersion_throws() {
        assertThatThrownBy(() -> encryptor.decrypt("99:deadbeef"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_missingVersionPrefix_throwsClearError() {
        assertThatThrownBy(() -> encryptor.decrypt("not-a-valid-stored-value"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing key-version prefix");
    }

    @Test
    void decrypt_malformedVersionPrefix_throwsClearError() {
        assertThatThrownBy(() -> encryptor.decrypt("abc:deadbeef"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("malformed key-version prefix");
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void constructor_saltNotValidHex_throwsClearError() {
        EncryptionProperties badProperties = new EncryptionProperties(
            Map.of(1, "some-key"), Map.of(1, "not-hex!!"), 1);

        assertThatThrownBy(() -> new FieldEncryptor(badProperties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hermes.encryption.salts.1")
            .hasMessageContaining("valid hex string");
    }

    @Test
    void constructor_saltMissingForConfiguredKey_throwsClearError() {
        EncryptionProperties badProperties = new EncryptionProperties(
            Map.of(1, "some-key"), Map.of(), 1);

        assertThatThrownBy(() -> new FieldEncryptor(badProperties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hermes.encryption.salts.1")
            .hasMessageContaining("not configured");
    }
}
