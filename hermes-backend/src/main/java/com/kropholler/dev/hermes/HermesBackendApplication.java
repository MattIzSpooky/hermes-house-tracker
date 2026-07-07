package com.kropholler.dev.hermes;

import com.kropholler.dev.hermes.crypto.EncryptionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EncryptionProperties.class)
public class HermesBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesBackendApplication.class, args);
    }

}
