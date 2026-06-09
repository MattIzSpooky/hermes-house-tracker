package com.kropholler.dev.hermes;

import org.springframework.boot.SpringApplication;

public class TestHermesBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(HermesBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
