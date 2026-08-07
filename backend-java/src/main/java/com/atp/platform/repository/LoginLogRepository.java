package com.atp.platform.repository;

import com.atp.platform.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    List<LoginLog> findByUserIdAndLoginAtAfterOrderByLoginAtDesc(Long userId, LocalDateTime after);
}
