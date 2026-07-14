package com.atp.platform.repository;

import com.atp.platform.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByStatusOrderByNameAsc(Byte status);
    Optional<Team> findByCode(String code);
}
