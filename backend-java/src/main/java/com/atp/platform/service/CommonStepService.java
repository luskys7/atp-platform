package com.atp.platform.service;

import com.atp.platform.entity.CommonStep;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.CommonStepRepository;
import com.atp.platform.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommonStepService {

    private final CommonStepRepository repository;
    private final TestCaseRepository caseRepository;
    private final RecycleBinService recycleBinService;

    public List<CommonStep> list() {
        return repository.findByDeletedAtIsNullOrderByNameAsc();
    }

    public CommonStep get(Long id) {
        CommonStep s = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "公共步骤不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() != null) {
            throw new AppException("NOT_FOUND", "公共步骤已删除", HttpStatus.NOT_FOUND);
        }
        return s;
    }

    @Transactional
    public CommonStep create(Map<String, Object> body, Long userId) {
        String name = body.get("name").toString();
        if (repository.findByNameAndDeletedAtIsNull(name).isPresent()) {
            throw new AppException("DUPLICATE", "步骤名称已存在", HttpStatus.BAD_REQUEST);
        }
        CommonStep s = mapToStep(new CommonStep(), body);
        s.setCreatedBy(userId);
        return repository.save(s);
    }

    @Transactional
    public CommonStep update(Long id, Map<String, Object> body) {
        CommonStep s = get(id);
        mapToStep(s, body);
        return repository.save(s);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        CommonStep s = get(id);
        Map<String, Object> deps = checkDependencies(id);
        if ((long) deps.get("case_refs") > 0) {
            throw new AppException("DEPENDENCY", "存在 " + deps.get("case_refs") + " 个用例引用，无法删除", HttpStatus.BAD_REQUEST);
        }
        s.setDeletedAt(LocalDateTime.now());
        repository.save(s);
        recycleBinService.add("common_step", id, s.getName(), s, userId);
    }

    @Transactional
    public Map<String, Object> transferSteps(List<Long> stepIds, Long ownerId) {
        if (ownerId == null) {
            throw new AppException("INVALID", "请指定新负责人", HttpStatus.BAD_REQUEST);
        }
        int count = 0;
        for (Long id : stepIds) {
            CommonStep s = get(id);
            s.setCreatedBy(ownerId);
            repository.save(s);
            count++;
        }
        return Map.of("transferred", count, "owner_id", ownerId);
    }

    public Map<String, Object> checkDependencies(Long id) {
        CommonStep s = get(id);
        String ref = "\"common_step\":\"" + s.getName() + "\"";
        long caseRefs = caseRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null && c.getStepsContent() != null && c.getStepsContent().contains(ref))
                .count();
        return Map.of("case_refs", caseRefs, "step_name", s.getName());
    }

    private CommonStep mapToStep(CommonStep s, Map<String, Object> body) {
        if (body.containsKey("name")) s.setName(body.get("name").toString());
        if (body.containsKey("description")) s.setDescription(str(body.get("description")));
        if (body.containsKey("steps_content")) s.setStepsContent(str(body.get("steps_content")));
        if (body.containsKey("input_params")) s.setInputParams(str(body.get("input_params")));
        if (body.containsKey("output_params")) s.setOutputParams(str(body.get("output_params")));
        if (body.containsKey("status")) s.setStatus(CommonStep.StepStatus.valueOf(str(body.get("status"))));
        return s;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
