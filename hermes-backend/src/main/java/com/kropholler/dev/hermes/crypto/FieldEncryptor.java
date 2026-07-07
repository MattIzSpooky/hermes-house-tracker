package com.kropholler.dev.hermes.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryptor {

    private final TextEncryptor encryptor;

    public FieldEncryptor(
            @Value("${hermes.encryption.key}") String key,
            @Value("${hermes.encryption.salt}") String salt) {
        this.encryptor = Encryptors.text(key, salt);
    }

    public String encrypt(String plaintext) {
        return plaintext == null ? null : encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return ciphertext == null ? null : encryptor.decrypt(ciphertext);
    }
}
