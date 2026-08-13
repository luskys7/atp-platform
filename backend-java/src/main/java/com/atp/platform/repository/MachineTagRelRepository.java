package com.atp.platform.repository;

import com.atp.platform.entity.MachineTagRel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineTagRelRepository extends JpaRepository<MachineTagRel, Long> {
    List<MachineTagRel> findByMachineId(Long machineId);

    List<MachineTagRel> findByMachineIdAndIsSupport(Long machineId, Byte isSupport);

    Optional<MachineTagRel> findByMachineIdAndTagId(Long machineId, Long tagId);

    void deleteByMachineId(Long machineId);

    void deleteByMachineIdAndTagId(Long machineId, Long tagId);

    void deleteByTagId(Long tagId);
}
