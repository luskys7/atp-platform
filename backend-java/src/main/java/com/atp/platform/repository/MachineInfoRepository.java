package com.atp.platform.repository;

import com.atp.platform.entity.MachineInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MachineInfoRepository extends JpaRepository<MachineInfo, Long> {
    List<MachineInfo> findByTeamIdOrderByIdAsc(Long teamId);

    List<MachineInfo> findAllByOrderByIdAsc();

    List<MachineInfo> findByMachineNameAndTeamId(String machineName, Long teamId);

    boolean existsByMachineNameAndTeamId(String machineName, Long teamId);

    boolean existsByMachineNameAndTeamIdAndIdNot(String machineName, Long teamId, Long id);
}
