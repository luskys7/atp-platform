package com.atp.platform.repository;

import com.atp.platform.entity.RecordingResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecordingResourceRepository extends JpaRepository<RecordingResource, Long>,
        JpaSpecificationExecutor<RecordingResource> {

    List<RecordingResource> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    Page<RecordingResource> findByTaskId(Long taskId, Pageable pageable);

    Page<RecordingResource> findByDeviceId(Long deviceId, Pageable pageable);

    void deleteByTaskId(Long taskId);

    @Query("""
            SELECT r FROM RecordingResource r WHERE
            (:taskId IS NULL OR r.taskId = :taskId) AND
            (:deviceId IS NULL OR r.deviceId = :deviceId) AND
            (:moduleName IS NULL OR :moduleName = '' OR LOWER(r.moduleName) LIKE LOWER(CONCAT('%', :moduleName, '%'))) AND
            (:versionLabel IS NULL OR :versionLabel = '' OR LOWER(r.versionLabel) LIKE LOWER(CONCAT('%', :versionLabel, '%'))) AND
            (:keyword IS NULL OR :keyword = '' OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(r.operatorLabel) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(r.moduleName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<RecordingResource> search(
            @Param("taskId") Long taskId,
            @Param("deviceId") Long deviceId,
            @Param("moduleName") String moduleName,
            @Param("versionLabel") String versionLabel,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT DISTINCT r.moduleName FROM RecordingResource r WHERE r.moduleName IS NOT NULL AND r.moduleName <> '' ORDER BY r.moduleName")
    List<String> distinctModules();

    @Query("SELECT DISTINCT r.versionLabel FROM RecordingResource r WHERE r.versionLabel IS NOT NULL AND r.versionLabel <> '' ORDER BY r.versionLabel")
    List<String> distinctVersions();

    @Query("SELECT DISTINCT r.operatorLabel FROM RecordingResource r WHERE r.operatorLabel IS NOT NULL AND r.operatorLabel <> '' ORDER BY r.operatorLabel")
    List<String> distinctOperators();

    @Query("SELECT DISTINCT r.deviceId FROM RecordingResource r WHERE r.deviceId IS NOT NULL ORDER BY r.deviceId")
    List<Long> distinctDeviceIds();
}
