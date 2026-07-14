package com.atp.platform.repository;

import com.atp.platform.entity.AppPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppPackageRepository extends JpaRepository<AppPackage, Long> {
    List<AppPackage> findByStatusOrderByUpdatedAtDesc(AppPackage.PackageStatus status);
}
