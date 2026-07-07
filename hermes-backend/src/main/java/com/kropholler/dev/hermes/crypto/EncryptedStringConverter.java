package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor fieldEncryptor;

    public EncryptedStringConverter(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    // Spring AOT processing (spring-boot:process-aot) builds Hibernate's metamodel
    // without a live ApplicationContext, so it can't resolve the constructor-injected
    // FieldEncryptor and falls back to reflective no-arg instantiation just to inspect
    // this converter's generic type parameters — it never invokes convert methods on
    // the resulting instance.
    EncryptedStringConverter() {
        this.fieldEncryptor = null;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptor.decrypt(dbData);
    }
}
