package com.kropholler.dev.hermes.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncryptionKeyVersionListenerTest {

    @Mock FieldEncryptor fieldEncryptor;
    @InjectMocks EncryptionKeyVersionListener listener;

    static class Versioned implements EncryptionVersioned {
        private Integer version;

        @Override
        public void setEncryptionKeyVersion(Integer version) {
            this.version = version;
        }

        Integer getVersion() {
            return version;
        }
    }

    @Test
    void stampCurrentVersion_setsCurrentVersionOnVersionedEntity() {
        when(fieldEncryptor.getCurrentVersion()).thenReturn(2);
        Versioned entity = new Versioned();

        listener.stampCurrentVersion(entity);

        assertThat(entity.getVersion()).isEqualTo(2);
    }

    @Test
    void stampCurrentVersion_ignoresNonVersionedEntity() {
        listener.stampCurrentVersion(new Object());
        // No exception, and no interaction with fieldEncryptor needed to reach this point.
    }
}
