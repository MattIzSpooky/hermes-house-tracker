package com.kropholler.dev.hermes.profile;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserProfileRepositoryTest {

    @Autowired
    UserProfileRepository repository;

    @Autowired
    EntityManager em;

    @Test
    void savesAndReloadsAllFields() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setStreet("Dorpstraat");
        entity.setHouseNumber("10");
        entity.setHouseNumberAddition("A");
        entity.setZipCode("1234AB");
        entity.setCity("Utrecht");
        entity.setProvince("Utrecht");
        entity.setLatitude(52.09);
        entity.setLongitude(5.12);
        entity.setUpdatedAt(now);

        repository.saveAndFlush(entity);
        em.clear();

        Optional<UserProfileEntity> reloaded = repository.findById(userId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStreet()).isEqualTo("Dorpstraat");
        assertThat(reloaded.get().getHouseNumber()).isEqualTo("10");
        assertThat(reloaded.get().getHouseNumberAddition()).isEqualTo("A");
        assertThat(reloaded.get().getZipCode()).isEqualTo("1234AB");
        assertThat(reloaded.get().getCity()).isEqualTo("Utrecht");
        assertThat(reloaded.get().getProvince()).isEqualTo("Utrecht");
        assertThat(reloaded.get().getLatitude()).isEqualTo(52.09);
        assertThat(reloaded.get().getLongitude()).isEqualTo(5.12);
    }
}
