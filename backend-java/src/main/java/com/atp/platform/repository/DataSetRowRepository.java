package com.atp.platform.repository;

import com.atp.platform.entity.DataSetRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataSetRowRepository extends JpaRepository<DataSetRow, Long> {
    List<DataSetRow> findByDatasetIdOrderByIdAsc(Long datasetId);
    Optional<DataSetRow> findFirstByDatasetIdAndLockStatusOrderByIdAsc(Long datasetId, DataSetRow.LockStatus lockStatus);
    List<DataSetRow> findByLockedByTaskId(Long lockedByTaskId);
}
