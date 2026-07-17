package com.atp.platform.repository;

import com.atp.platform.entity.ControlPool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ControlPoolRepository extends JpaRepository<ControlPool, Long> {

    Optional<ControlPool> findByPoolKey(String poolKey);

    Optional<ControlPool> findFirstByAppPackageAndElementNameAndStatusOrderByHitCountDesc(
            String appPackage, String elementName, String status);

    List<ControlPool> findByAppPackageAndElementNameAndStatus(
            String appPackage, String elementName, String status);

    Page<ControlPool> findByAppPackage(String appPackage, Pageable pageable);

    List<ControlPool> findByAppPackageAndStatus(String appPackage, String status);

    @Query("""
            SELECT p FROM ControlPool p WHERE
            (:appPackage IS NULL OR :appPackage = '' OR p.appPackage = :appPackage) AND
            (:pageName IS NULL OR :pageName = '' OR p.pageName = :pageName) AND
            (:platform IS NULL OR :platform = '' OR CAST(p.platform AS string) = :platform) AND
            (:versionTag IS NULL OR :versionTag = '' OR p.versionTag = :versionTag OR p.versionTag = '' OR p.versionTag IS NULL) AND
            (:envTag IS NULL OR :envTag = '' OR p.envTag = :envTag OR p.envTag = '' OR p.envTag IS NULL) AND
            (:teamId IS NULL OR p.teamId = :teamId OR p.teamId IS NULL) AND
            (:status IS NULL OR :status = '' OR p.status = :status)
            ORDER BY p.id DESC
            """)
    Page<ControlPool> search(
            @Param("appPackage") String appPackage,
            @Param("pageName") String pageName,
            @Param("platform") String platform,
            @Param("versionTag") String versionTag,
            @Param("envTag") String envTag,
            @Param("teamId") Long teamId,
            @Param("status") String status,
            Pageable pageable);
}
