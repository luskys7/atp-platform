package com.atp.platform.service;

import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecycleBinService {

    private static final int RETAIN_DAYS = 90;

    private final RecycleBinItemRepository repository;
    private final TestCaseRepository caseRepository;
    private final TestSuiteRepository suiteRepository;
    private final CommonStepRepository commonStepRepository;
    private final DataSetRepository dataSetRepository;
    private final ObjectMapper objectMapper;

    public void add(String resourceType, Long resourceId, String resourceName, Object snapshot, Long deletedBy) {
        try {
            RecycleBinItem item = new RecycleBinItem();
            item.setResourceType(resourceType);
            item.setResourceId(resourceId);
            item.setResourceName(resourceName);
            item.setSnapshotJson(snapshot != null ? objectMapper.writeValueAsString(snapshot) : null);
            item.setDeletedBy(deletedBy);
            item.setDeletedAt(LocalDateTime.now());
            item.setExpireAt(LocalDateTime.now().plusDays(RETAIN_DAYS));
            repository.save(item);
        } catch (Exception ignored) {
            RecycleBinItem item = new RecycleBinItem();
            item.setResourceType(resourceType);
            item.setResourceId(resourceId);
            item.setResourceName(resourceName);
            item.setDeletedBy(deletedBy);
            item.setDeletedAt(LocalDateTime.now());
            item.setExpireAt(LocalDateTime.now().plusDays(RETAIN_DAYS));
            repository.save(item);
        }
    }

    public List<RecycleBinItem> list() {
        return repository.findByExpireAtAfterOrderByDeletedAtDesc(LocalDateTime.now());
    }

    @Transactional
    public RecycleBinItem restore(Long id) {
        RecycleBinItem item = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "回收站记录不存在", HttpStatus.NOT_FOUND));
        if (item.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new AppException("EXPIRED", "已超过 90 天保留期，无法还原", HttpStatus.BAD_REQUEST);
        }
        switch (item.getResourceType()) {
            case "test_case" -> restoreTestCase(item);
            case "test_suite" -> restoreTestSuite(item);
            case "common_step" -> restoreCommonStep(item);
            case "data_set" -> restoreDataSet(item);
            default -> throw new AppException("UNSUPPORTED", "不支持还原该资源类型: " + item.getResourceType(), HttpStatus.BAD_REQUEST);
        }
        repository.delete(item);
        return item;
    }

    @Transactional
    public Map<String, Object> batchRestore(List<Long> ids) {
        int restored = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                restore(id);
                restored++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        return Map.of("restored", restored, "errors", errors);
    }

    public void purge(Long id) {
        repository.deleteById(id);
    }

    private void restoreTestCase(RecycleBinItem item) {
        TestCase c = caseRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "用例实体不存在", HttpStatus.NOT_FOUND));
        if (c.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "用例未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        c.setDeletedAt(null);
        caseRepository.save(c);
    }

    private void restoreTestSuite(RecycleBinItem item) {
        TestSuite s = suiteRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "套件实体不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "套件未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        s.setDeletedAt(null);
        suiteRepository.save(s);
    }

    private void restoreCommonStep(RecycleBinItem item) {
        CommonStep s = commonStepRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "公共步骤实体不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "公共步骤未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        commonStepRepository.findByNameAndDeletedAtIsNull(s.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(s.getId())) {
                throw new AppException("DUPLICATE", "同名公共步骤已存在，请先处理冲突", HttpStatus.BAD_REQUEST);
            }
        });
        s.setDeletedAt(null);
        commonStepRepository.save(s);
    }

    private void restoreDataSet(RecycleBinItem item) {
        DataSet ds = dataSetRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "数据集实体不存在", HttpStatus.NOT_FOUND));
        if (ds.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "数据集未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        ds.setDeletedAt(null);
        dataSetRepository.save(ds);
    }
}
