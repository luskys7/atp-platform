package com.atp.platform.service;

import com.atp.platform.entity.DataSet;
import com.atp.platform.entity.DataSetRow;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.DataSetRepository;
import com.atp.platform.repository.DataSetRowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DataSetService {

    private final DataSetRepository datasetRepository;
    private final DataSetRowRepository rowRepository;
    private final RecycleBinService recycleBinService;
    private final ObjectMapper objectMapper;

    public List<DataSet> list() {
        return datasetRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc();
    }

    public Map<String, Object> getDetail(Long id) {
        DataSet ds = get(id);
        List<DataSetRow> rows = rowRepository.findByDatasetIdOrderByIdAsc(id);
        return Map.of("dataset", ds, "rows", rows);
    }

    public DataSet get(Long id) {
        DataSet ds = datasetRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "数据集不存在", HttpStatus.NOT_FOUND));
        if (ds.getDeletedAt() != null) {
            throw new AppException("NOT_FOUND", "数据集已删除", HttpStatus.NOT_FOUND);
        }
        return ds;
    }

    @Transactional
    public DataSet create(Map<String, Object> body) {
        DataSet ds = mapDataset(new DataSet(), body);
        ds = datasetRepository.save(ds);
        saveRows(ds.getId(), body.get("rows"));
        return ds;
    }

    @Transactional
    public DataSet update(Long id, Map<String, Object> body) {
        DataSet ds = get(id);
        mapDataset(ds, body);
        ds = datasetRepository.save(ds);
        if (body.containsKey("rows")) {
            rowRepository.findByDatasetIdOrderByIdAsc(id).forEach(r -> rowRepository.delete(r));
            saveRows(id, body.get("rows"));
        }
        return ds;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        DataSet ds = get(id);
        ds.setDeletedAt(LocalDateTime.now());
        datasetRepository.save(ds);
        recycleBinService.add("data_set", id, ds.getName(), ds, userId);
    }

    @Transactional
    public DataSetRow acquireRow(Long datasetId, Long taskId) {
        DataSetRow row = rowRepository.findFirstByDatasetIdAndLockStatusOrderByIdAsc(datasetId, DataSetRow.LockStatus.idle)
                .orElseThrow(() -> new AppException("NO_DATA", "无空闲数据行", HttpStatus.BAD_REQUEST));
        row.setLockStatus(DataSetRow.LockStatus.busy);
        row.setLockedByTaskId(taskId);
        return rowRepository.save(row);
    }

    @Transactional
    public void releaseRow(Long rowId) {
        rowRepository.findById(rowId).ifPresent(row -> {
            row.setLockStatus(DataSetRow.LockStatus.idle);
            row.setLockedByTaskId(null);
            rowRepository.save(row);
        });
    }

    /** CSV/TSV 导入：首行为表头，后续每行转为 JSON 对象 */
    @Transactional
    public Map<String, Object> importCsv(Long datasetId, String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new AppException("INVALID", "CSV 内容为空", HttpStatus.BAD_REQUEST);
        }
        get(datasetId);
        String[] lines = csvContent.strip().split("\\r?\\n");
        if (lines.length < 2) {
            throw new AppException("INVALID", "至少需要表头行和一行数据", HttpStatus.BAD_REQUEST);
        }
        char delim = lines[0].contains("\t") && !lines[0].contains(",") ? '\t' : ',';
        String[] headers = parseCsvLine(lines[0], delim);
        List<String> columns = new ArrayList<>();
        for (String h : headers) {
            columns.add(h.trim());
        }
        int imported = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] values = parseCsvLine(lines[i], delim);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < columns.size(); j++) {
                rowMap.put(columns.get(j), j < values.length ? values[j].trim() : "");
            }
            try {
                DataSetRow row = new DataSetRow();
                row.setDatasetId(datasetId);
                row.setRowDataJson(objectMapper.writeValueAsString(rowMap));
                rowRepository.save(row);
                imported++;
            } catch (Exception e) {
                throw new AppException("INVALID", "第 " + (i + 1) + " 行解析失败", HttpStatus.BAD_REQUEST);
            }
        }
        DataSet ds = get(datasetId);
        ds.setColumnsJson("[\"" + String.join("\",\"", columns) + "\"]");
        datasetRepository.save(ds);
        return Map.of("imported", imported, "columns", columns);
    }

    private String[] parseCsvLine(String line, char delim) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == delim && !inQuote) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    /** 脱敏展示 */
    public String maskSensitive(String json) {
        if (json == null) return null;
        return json.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"******\"")
                .replaceAll("\"phone\"\\s*:\\s*\"(\\d{3})\\d{4}(\\d{4})\"", "\"phone\":\"$1****$2\"");
    }

    private void saveRows(Long datasetId, Object rowsObj) {
        if (!(rowsObj instanceof List<?> list)) return;
        for (Object o : list) {
            DataSetRow row = new DataSetRow();
            row.setDatasetId(datasetId);
            row.setRowDataJson(o instanceof String ? (String) o : o.toString());
            rowRepository.save(row);
        }
    }

    private DataSet mapDataset(DataSet ds, Map<String, Object> body) {
        if (body.containsKey("name")) ds.setName(body.get("name").toString());
        if (body.containsKey("env_id") && body.get("env_id") != null) {
            ds.setEnvId(Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("description")) ds.setDescription(str(body.get("description")));
        if (body.containsKey("columns_json")) ds.setColumnsJson(str(body.get("columns_json")));
        return ds;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
