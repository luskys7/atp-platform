package com.atp.platform.repository;

import com.atp.platform.entity.FunctionTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FunctionTagRepository extends JpaRepository<FunctionTag, Long> {
    List<FunctionTag> findByTeamIdOrderByTagTypeAscTagNameAscContentNameAsc(Long teamId);

    List<FunctionTag> findAllByOrderByTagTypeAscTagNameAscContentNameAsc();

    List<FunctionTag> findByTeamIdAndStatusOrderByTagTypeAscTagNameAscContentNameAsc(Long teamId, Byte status);

    List<FunctionTag> findByStatusOrderByTagTypeAscTagNameAscContentNameAsc(Byte status);

    List<FunctionTag> findByTagTypeAndTagNameAndContentNameAndTeamId(
            String tagType, String tagName, String contentName, Long teamId);

    List<FunctionTag> findByContentNameAndTeamIdOrderByIdAsc(String contentName, Long teamId);

    List<FunctionTag> findByTagNameAndTeamIdOrderByIdAsc(String tagName, Long teamId);

    boolean existsByTagTypeAndTagNameAndContentNameAndTeamId(
            String tagType, String tagName, String contentName, Long teamId);

    boolean existsByTagTypeAndTagNameAndContentNameAndTeamIdAndIdNot(
            String tagType, String tagName, String contentName, Long teamId, Long id);
}
