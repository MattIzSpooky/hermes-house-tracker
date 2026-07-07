package com.kropholler.dev.hermes.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    @Modifying
    @Query("UPDATE UserProfileEntity u SET u.email = :email WHERE u.userId = :userId")
    int updateEmail(@Param("userId") UUID userId, @Param("email") String email);

    List<UserProfileEntity> findByEncryptionKeyVersionLessThan(int version, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserProfileEntity u SET
                u.street = :street, u.houseNumber = :houseNumber, u.houseNumberAddition = :houseNumberAddition,
                u.zipCode = :zipCode, u.city = :city, u.province = :province, u.email = :email,
                u.latitude = :latitude, u.longitude = :longitude, u.encryptionKeyVersion = :version
            WHERE u.userId = :userId
            """)
    void reencrypt(@Param("userId") UUID userId, @Param("street") String street, @Param("houseNumber") String houseNumber,
            @Param("houseNumberAddition") String houseNumberAddition, @Param("zipCode") String zipCode,
            @Param("city") String city, @Param("province") String province, @Param("email") String email,
            @Param("latitude") Double latitude, @Param("longitude") Double longitude, @Param("version") int version);
}
