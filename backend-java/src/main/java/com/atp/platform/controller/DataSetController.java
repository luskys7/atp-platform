package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.DataSet;
import com.atp.platform.entity.DataSetRow;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.DataSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DataSetController {

    private final DataSetService service;

    @GetMapping
    public ApiResponse<List<DataSet>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> detail = service.getDetail(id);
        @SuppressWarnings("unchecked")
        List<DataSetRow> rows = (List<DataSetRow>) detail.get("rows");
        rows.forEach(r -> r.setRowDataJson(service.maskSensitive(r.getRowDataJson())));
        return ApiResponse.ok(detail);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<DataSet> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<DataSet> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/acquire")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<DataSetRow> acquire(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("task_id").toString());
        return ApiResponse.ok(service.acquireRow(id, taskId));
    }

    @PostMapping("/rows/{rowId}/release")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> release(@PathVariable Long rowId) {
        service.releaseRow(rowId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/import")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> importCsv(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.importCsv(id, body.get("csv_content")));
    }
}
