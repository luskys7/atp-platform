package com.atp.platform.repository;

import com.atp.platform.entity.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {
    List<UserApiKey> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserApiKey> findByIdAndUserId(Long id, Long userId);

    Optional<UserApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
