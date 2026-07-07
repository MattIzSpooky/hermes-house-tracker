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
                e -> Encryptors.text(e.getValue(), requireValidSalt(e.getKey(), properties.salts().get(e.getKey())))));
    }

    private static String requireValidSalt(int version, String salt) {
        if (salt == null) {
            throw new IllegalStateException(
                "hermes.encryption.salts." + version + " is not configured, but hermes.encryption.keys." + version + " is");
        }
        if (!salt.matches("[0-9a-fA-F]+")) {
            throw new IllegalStateException(
                "hermes.encryption.salts." + version + " must be a valid hex string (only 0-9a-f characters), got: " + salt);
        }
        return salt;
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
        if (separatorIndex <= 0) {
            throw new IllegalArgumentException("Stored value is not a valid encrypted field (missing key-version prefix): " + stored);
        }
        int version;
        try {
            version = Integer.parseInt(stored.substring(0, separatorIndex));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stored value has a malformed key-version prefix: " + stored, e);
        }
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
