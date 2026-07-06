package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO user_profiles (user_id, email, updated_at)
            VALUES (:userId, :email, now())
            ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email
            """, nativeQuery = true)
    void upsertEmail(@Param("userId") UUID userId, @Param("email") String email);
}
