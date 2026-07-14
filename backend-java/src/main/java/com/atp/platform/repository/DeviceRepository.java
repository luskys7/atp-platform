package com.atp.platform.repository;

import com.atp.platform.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findBySerialNumber(String serialNumber);

    @Query("SELECT d FROM Device d WHERE (:platform IS NULL OR d.platform = :platform) " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:teamId IS NULL OR d.teamId = :teamId)")
    Page<Device> findByFilters(@Param("platform") Device.Platform platform,
                               @Param("status") Device.DeviceStatus status,
                               @Param("teamId") Long teamId,
                               Pageable pageable);

    @Query("SELECT d FROM Device d WHERE d.status = 'online' AND d.isWhitelisted = true " +
           "AND (:platform IS NULL OR d.platform = :platform) " +
           "AND (:teamId IS NULL OR d.teamId = :teamId)")
    List<Device> findAvailable(@Param("platform") Device.Platform platform,
                               @Param("teamId") Long teamId,
                               Pageable pageable);

    @Query("SELECT d FROM Device d WHERE d.id IN :ids AND d.status = 'online' AND d.isWhitelisted = true " +
           "AND (:teamId IS NULL OR d.teamId = :teamId)")
    List<Device> findAvailableByIds(@Param("ids") List<Long> ids, @Param("teamId") Long teamId);

    long countByStatus(Device.DeviceStatus status);

    List<Device> findByLockedByTaskId(Long lockedByTaskId);
}
