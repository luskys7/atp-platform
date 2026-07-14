package com.atp.platform.service;

import com.atp.platform.entity.CaseFolder;
import com.atp.platform.entity.TestCase;
import com.atp.platform.repository.CaseFolderRepository;
import com.atp.platform.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CoverageReportService {

    private final TestCaseRepository caseRepository;
    private final CaseFolderRepository folderRepository;

    public Map<String, Object> summary() {
        long total = caseRepository.countByDeletedAtIsNull();
        long active = caseRepository.countByCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus.active);
        long draft = caseRepository.countByCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus.draft);
        long review = caseRepository.countByCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus.review);
        long deprecated = caseRepository.countByCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus.deprecated);
        double automationRate = total > 0 ? active * 100.0 / total : 0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total_cases", total);
        row.put("active_cases", active);
        row.put("draft_cases", draft);
        row.put("review_cases", review);
        row.put("deprecated_cases", deprecated);
        row.put("automation_rate", Math.round(automationRate * 10) / 10.0);
        row.put("by_folder", folderBreakdown());
        return row;
    }

    private List<Map<String, Object>> folderBreakdown() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, String> folderNames = new LinkedHashMap<>();
        folderNames.put(null, "未分类");
        for (CaseFolder f : folderRepository.findAll()) {
            folderNames.put(f.getId(), f.getName());
        }
        for (Map.Entry<Long, String> entry : folderNames.entrySet()) {
            Long folderId = entry.getKey();
            long folderTotal = folderId == null
                    ? caseRepository.countActiveByFolderIdIsNull()
                    : caseRepository.countActiveByFolderId(folderId);
            if (folderTotal == 0 && folderId != null) continue;
            long folderActive = folderId == null
                    ? caseRepository.countByFolderIdIsNullAndCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus.active)
                    : caseRepository.countByFolderIdAndCaseStatusAndDeletedAtIsNull(folderId, TestCase.CaseStatus.active);
            rows.add(Map.of(
                    "folder_id", folderId != null ? folderId : 0,
                    "folder_name", entry.getValue(),
                    "total_cases", folderTotal,
                    "active_cases", folderActive,
                    "automation_rate", folderTotal > 0 ? Math.round(folderActive * 1000.0 / folderTotal) / 10.0 : 0
            ));
        }
        return rows;
    }
}
