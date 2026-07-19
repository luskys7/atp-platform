package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.AuditLog;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AuditLogRepository;
import com.atp.platform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AtpProperties properties;
    private final CryptoService cryptoService;
    private final BackupService backupService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Map<String, Object> list(int page, int pageSize, Map<String, String> filters) {
        Specification<AuditLog> spec = buildSpec(filters);
        Page<AuditLog> result = auditLogRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(pageSize, 1), 100)));
        return pageResult(result, page, pageSize);
    }

    public Map<String, Object> stats() {
        long total = auditLogRepository.count();
        long evidence = listArchives().size();
        long backups = 0;
        try {
            backups = backupService.list().size();
        } catch (Exception ignored) {
            backups = 0;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total", total);
        row.put("evidence_count", evidence);
        row.put("backup_count", backups);
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> archive(Long userId) {
        try {
            Path dir = archiveDir();
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "audit_evidence_" + ts + ".zip";
            Path zipPath = dir.resolve(filename);

            List<AuditLog> logs = auditLogRepository.findAll();
            List<Map<String, Object>> payload = logs.stream().map(this::toRowPlain).toList();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            String cipher = cryptoService.encrypt(json);
            String checksum = sha256(cipher);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("created_at", LocalDateTime.now().toString());
            manifest.put("created_by", userId);
            manifest.put("type", "audit_evidence");
            manifest.put("count", logs.size());
            manifest.put("checksum_sha256", checksum);
            manifest.put("version", "1.0");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                writeBytes(zos, "manifest.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
                writeBytes(zos, "audit_logs.enc", cipher.getBytes(StandardCharsets.UTF_8));
                writeBytes(zos, "checksum.sha256", checksum.getBytes(StandardCharsets.UTF_8));
            }
            Files.writeString(dir.resolve(filename + ".sha256"), checksum, StandardCharsets.UTF_8);

            Map<String, Object> meta = archiveMeta(zipPath, checksum);
            meta.put("count", logs.size());
            return meta;
        } catch (Exception e) {
            throw new AppException("AUDIT", "生成审计归档失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public List<Map<String, Object>> listArchives() {
        Path dir = archiveDir();
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                    .map(p -> {
                        String expected = "";
                        try {
                            Path side = Paths.get(p.toString() + ".sha256");
                            if (Files.exists(side)) expected = Files.readString(side).trim();
                        } catch (Exception ignored) { /* ignore */ }
                        return archiveMeta(p, expected);
                    })
                    .toList();
        } catch (IOException e) {
            throw new AppException("AUDIT", "读取审计归档失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Map<String, Object> verifyArchive(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new AppException("INVALID", "归档文件名无效", HttpStatus.BAD_REQUEST);
        }
        Path zipPath = archiveDir().resolve(filename);
        if (!Files.exists(zipPath)) {
            throw new AppException("NOT_FOUND", "归档文件不存在", HttpStatus.NOT_FOUND);
        }
        try {
            String side = "";
            Path sidePath = Paths.get(zipPath.toString() + ".sha256");
            if (Files.exists(sidePath)) side = Files.readString(sidePath).trim();

            byte[] encBytes = null;
            String manifestChecksum = "";
            try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    byte[] data = zis.readAllBytes();
                    if ("audit_logs.enc".equals(entry.getName())) {
                        encBytes = data;
                    } else if ("manifest.json".equals(entry.getName())) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> manifest = mapper.readValue(data, Map.class);
                        Object cs = manifest.get("checksum_sha256");
                        if (cs != null) manifestChecksum = cs.toString();
                    } else if ("checksum.sha256".equals(entry.getName())) {
                        if (side.isBlank()) side = new String(data, StandardCharsets.UTF_8).trim();
                    }
                }
            }
            if (encBytes == null) {
                throw new AppException("INVALID", "归档包缺少加密日志内容", HttpStatus.BAD_REQUEST);
            }
            String actual = sha256(new String(encBytes, StandardCharsets.UTF_8));
            String expected = !side.isBlank() ? side : manifestChecksum;
            boolean ok = !expected.isBlank() && expected.equalsIgnoreCase(actual);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", filename);
            result.put("valid", ok);
            result.put("expected_checksum", expected);
            result.put("actual_checksum", actual);
            result.put("message", ok ? "凭证校验通过，日志备份完整未篡改" : "凭证校验失败，文件可能已损坏或被篡改");
            return result;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("AUDIT", "凭证校验失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> pageResult(Page<AuditLog> result, int page, int pageSize) {
        List<Long> userIds = result.getContent().stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> list = result.getContent().stream()
                .map(log -> toRow(log, users.get(log.getUserId())))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", result.getTotalElements());
        out.put("page", page);
        out.put("page_size", pageSize);
        return out;
    }

    private Specification<AuditLog> buildSpec(Map<String, String> filters) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (filters == null) return cb.and(preds.toArray(new Predicate[0]));

            String action = trim(filters.get("action"));
            if (action != null) preds.add(cb.equal(root.get("action"), action));

            String resourceType = trim(filters.get("resource_type"));
            if (resourceType != null) preds.add(cb.equal(root.get("resourceType"), resourceType));

            String userId = trim(filters.get("user_id"));
            if (userId != null) {
                try {
                    preds.add(cb.equal(root.get("userId"), Long.valueOf(userId)));
                } catch (NumberFormatException ignored) { /* skip */ }
            }

            String ip = trim(filters.get("ip"));
            if (ip != null) preds.add(cb.like(cb.lower(root.get("ip")), "%" + ip.toLowerCase() + "%"));

            String from = trim(filters.get("from"));
            if (from != null) {
                LocalDateTime t = parseTime(from);
                if (t != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), t));
            }
            String to = trim(filters.get("to"));
            if (to != null) {
                LocalDateTime t = parseTime(to);
                if (t != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), t));
            }

            if (query != null) query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> toRow(AuditLog log, User u) {
        Map<String, Object> row = toRowPlain(log);
        row.put("username", u != null ? u.getUsername() : null);
        row.put("display_name", u != null ? u.getDisplayName() : null);
        return row;
    }

    private Map<String, Object> toRowPlain(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("user_id", log.getUserId());
        row.put("action", log.getAction());
        row.put("resource_type", log.getResourceType());
        row.put("resource_id", log.getResourceId());
        row.put("detail", log.getDetail());
        row.put("ip", log.getIp());
        row.put("created_at", log.getCreatedAt());
        return row;
    }

    private Path archiveDir() {
        Path base = Paths.get(properties.getStorage().getLocalPath()).toAbsolutePath().normalize();
        Path parent = base.getParent() != null ? base.getParent() : Paths.get(".").toAbsolutePath();
        return parent.resolve("audit-archives");
    }

    private Map<String, Object> archiveMeta(Path path, String checksum) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("filename", path.getFileName().toString());
        try {
            row.put("size", Files.size(path));
            row.put("created_at", Files.getLastModifiedTime(path).toString());
        } catch (IOException e) {
            row.put("size", 0);
        }
        row.put("checksum_sha256", checksum);
        return row;
    }

    private void writeBytes(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private String sha256(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDateTime parseTime(String raw) {
        try {
            String s = raw.replace(" ", "T");
            if (s.length() == 10) s = s + "T00:00:00";
            return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
