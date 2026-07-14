package com.atp.platform.repository;

import com.atp.platform.entity.DeviceWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceWhitelistRepository extends JpaRepository<DeviceWhitelist, Long> {
    Optional<DeviceWhitelist> findBySerialNumber(String serialNumber);
}
