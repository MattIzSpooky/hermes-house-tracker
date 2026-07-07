package com.kropholler.dev.hermes.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

// Registered via @EnableConfigurationProperties on HermesBackendApplication, not
// @Component: a plain @Component-scanned @ConfigurationProperties record doesn't get
// Spring's constructor-binding treatment, so a full application-context component scan
// (unlike @DataJpaTest's restricted scan, which never triggered this) tries ordinary
// autowiring on `currentVersion` and fails with "no bean of type 'int'".
@ConfigurationProperties(prefix = "hermes.encryption")
public record EncryptionProperties(Map<Integer, String> keys, Map<Integer, String> salts, int currentVersion) {
}
