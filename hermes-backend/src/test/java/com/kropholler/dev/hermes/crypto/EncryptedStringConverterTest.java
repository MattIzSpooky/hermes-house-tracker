package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(new EncryptionProperties(
        Map.of(1, "test-encryption-key-v1"), Map.of(1, "abcd1234abcd1234"), 1));
    private final EncryptedStringConverter converter = new EncryptedStringConverter(fieldEncryptor);

    @Test
    void roundTrip_returnsOriginalValue() {
        String ciphertext = converter.convertToDatabaseColumn("52 Dorpstraat");

        assertThat(ciphertext).isNotEqualTo("52 Dorpstraat");
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("52 Dorpstraat");
    }

    @Test
    void nullAttribute_convertsToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullColumn_convertsToNullAttribute() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
