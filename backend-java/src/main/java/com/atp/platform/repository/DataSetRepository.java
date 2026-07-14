package com.atp.platform.repository;

import com.atp.platform.entity.DataSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSetRepository extends JpaRepository<DataSet, Long> {
    List<DataSet> findByDeletedAtIsNullOrderByUpdatedAtDesc();
}
