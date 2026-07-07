package com.kropholler.dev.hermes.crypto;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyVersionListener {

    private final FieldEncryptor fieldEncryptor;

    public EncryptionKeyVersionListener(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @PrePersist
    @PreUpdate
    public void stampCurrentVersion(Object entity) {
        if (entity instanceof EncryptionVersioned versioned) {
            versioned.setEncryptionKeyVersion(fieldEncryptor.getCurrentVersion());
        }
    }
}
