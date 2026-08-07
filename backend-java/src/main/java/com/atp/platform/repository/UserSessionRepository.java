package com.atp.platform.repository;

import com.atp.platform.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUserIdAndActiveTrueOrderByLoginAtDesc(Long userId);

    Optional<UserSession> findByJtiAndActiveTrue(String jti);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.active = false, s.revokedAt = CURRENT_TIMESTAMP where s.userId = :userId and s.active = true")
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.active = false, s.revokedAt = CURRENT_TIMESTAMP where s.userId = :userId and s.active = true and s.jti <> :keepJti")
    int revokeOthers(@Param("userId") Long userId, @Param("keepJti") String keepJti);
}
