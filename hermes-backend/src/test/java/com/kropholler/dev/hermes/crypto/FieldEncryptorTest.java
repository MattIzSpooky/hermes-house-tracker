package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldEncryptorTest {

    private final FieldEncryptor encryptor = new FieldEncryptor("test-encryption-key", "abcd1234abcd1234");

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String ciphertext = encryptor.encrypt("hello@hermes.local");

        assertThat(ciphertext).isNotEqualTo("hello@hermes.local");
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo("hello@hermes.local");
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        assertThat(encryptor.decrypt(null)).isNull();
    }
}
