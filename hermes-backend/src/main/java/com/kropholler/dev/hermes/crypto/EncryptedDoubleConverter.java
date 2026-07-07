package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedDoubleConverter implements AttributeConverter<Double, String> {

    private final FieldEncryptor fieldEncryptor;

    public EncryptedDoubleConverter(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    // Spring AOT processing (spring-boot:process-aot) builds Hibernate's metamodel
    // without a live ApplicationContext, so it can't resolve the constructor-injected
    // FieldEncryptor and falls back to reflective no-arg instantiation just to inspect
    // this converter's generic type parameters — it never invokes convert methods on
    // the resulting instance.
    EncryptedDoubleConverter() {
        this.fieldEncryptor = null;
    }

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        return attribute == null ? null : fieldEncryptor.encrypt(attribute.toString());
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        String decrypted = fieldEncryptor.decrypt(dbData);
        return decrypted == null ? null : Double.valueOf(decrypted);
    }
}
