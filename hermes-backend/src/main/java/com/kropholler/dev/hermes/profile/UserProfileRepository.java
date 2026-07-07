package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying
    @Query("UPDATE UserProfileEntity u SET u.email = :email WHERE u.userId = :userId")
    int updateEmail(@Param("userId") UUID userId, @Param("email") String email);
}
