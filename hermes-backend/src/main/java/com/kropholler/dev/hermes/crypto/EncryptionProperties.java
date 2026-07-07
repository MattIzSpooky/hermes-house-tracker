package com.kropholler.dev.hermes.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "hermes.encryption")
public record EncryptionProperties(Map<Integer, String> keys, Map<Integer, String> salts, int currentVersion) {
}
