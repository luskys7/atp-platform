package com.atp.platform.repository;

import com.atp.platform.entity.DataFactoryTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataFactoryTemplateRepository extends JpaRepository<DataFactoryTemplate, Long> {
    List<DataFactoryTemplate> findByEnabledTrueOrderByNameAsc();
    List<DataFactoryTemplate> findAllByOrderByNameAsc();
}
