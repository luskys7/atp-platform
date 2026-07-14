package com.atp.platform.repository;

import com.atp.platform.entity.TestAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TestAccountRepository extends JpaRepository<TestAccount, Long> {
    List<TestAccount> findByStatusOrderByUpdatedAtDesc(TestAccount.AccountStatus status);
    List<TestAccount> findAllByOrderByUpdatedAtDesc();
    Optional<TestAccount> findFirstByStatusAndLockedByTaskIdIsNullOrderByIdAsc(TestAccount.AccountStatus status);

    Optional<TestAccount> findByLockedByTaskId(Long lockedByTaskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM TestAccount a WHERE a.status = com.atp.platform.entity.TestAccount$AccountStatus.active "
            + "AND a.lockedByTaskId IS NULL "
            + "AND (:envId IS NULL OR a.envId IS NULL OR a.envId = :envId) "
            + "ORDER BY a.id ASC")
    List<TestAccount> findAvailableForUpdate(@Param("envId") Long envId);
}
