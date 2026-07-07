package com.kropholler.dev.hermes.crypto;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FieldEncryptor {

    private static final String VERSION_SEPARATOR = ":";

    private final Map<Integer, TextEncryptor> encryptorsByVersion;
    private final int currentVersion;

    public FieldEncryptor(EncryptionProperties properties) {
        this.currentVersion = properties.currentVersion();
        this.encryptorsByVersion = properties.keys().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> Encryptors.text(e.getValue(), properties.salts().get(e.getKey()))));
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        String ciphertext = requireEncryptor(currentVersion).encrypt(plaintext);
        return currentVersion + VERSION_SEPARATOR + ciphertext;
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        int separatorIndex = stored.indexOf(VERSION_SEPARATOR);
        int version = Integer.parseInt(stored.substring(0, separatorIndex));
        String ciphertext = stored.substring(separatorIndex + 1);
        return requireEncryptor(version).decrypt(ciphertext);
    }

    private TextEncryptor requireEncryptor(int version) {
        TextEncryptor encryptor = encryptorsByVersion.get(version);
        if (encryptor == null) {
            throw new IllegalStateException("No encryption key configured for version " + version);
        }
        return encryptor;
    }
}
