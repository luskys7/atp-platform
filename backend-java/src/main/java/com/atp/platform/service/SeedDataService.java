package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.repository.ControlPoolRepository;
import com.atp.platform.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

/**
 * 便携种子：空库时从 classpath:/seed/atp_portable_seed.zip 或 data/seed/ 导入控件与用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedDataService {

    private final AtpProperties properties;
    private final BackupService backupService;
    private final ControlPoolRepository controlPoolRepository;
    private final TestCaseRepository caseRepository;

    @Transactional
    public Map<String, Object> importIfEmpty() {
        if (!properties.getSeed().isImportOnEmpty()) {
            return Map.of("skipped", true, "reason", "disabled");
        }
        long controls = controlPoolRepository.count();
        long cases = caseRepository.count();
        if (controls > 0 || cases > 0) {
            log.info("跳过种子导入：已有控件={} 用例={}", controls, cases);
            return Map.of("skipped", true, "reason", "not_empty", "controls", controls, "cases", cases);
        }
        return importPortableSeed();
    }

    @Transactional
    public Map<String, Object> importPortableSeed() {
        Path zip = resolveSeedZip();
        if (zip == null || !Files.exists(zip)) {
            log.warn("未找到便携种子包 seed/atp_portable_seed.zip");
            return Map.of("ok", false, "reason", "seed_not_found");
        }
        // 放入备份目录后走统一还原（按 ID 合并）
        try {
            Path backups = Paths.get(properties.getStorage().getLocalPath()).getParent().resolve("backups");
            Files.createDirectories(backups);
            Path target = backups.resolve("atp_portable_seed.zip");
            Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            Map<String, Object> result = backupService.restore("atp_portable_seed.zip");
            log.info("便携种子已导入: {}", result);
            return result;
        } catch (Exception e) {
            log.error("便携种子导入失败", e);
            return Map.of("ok", false, "reason", e.getMessage());
        }
    }

    private Path resolveSeedZip() {
        // 1) 外部目录优先（换机时直接丢 zip）
        String external = properties.getSeed().getExternalZip();
        if (external != null && !external.isBlank()) {
            Path p = Paths.get(external);
            if (Files.exists(p)) return p;
        }
        Path dataSeed = Paths.get(properties.getStorage().getLocalPath()).getParent().resolve("seed").resolve("atp_portable_seed.zip");
        if (Files.exists(dataSeed)) return dataSeed;

        // 2) classpath
        try {
            Resource res = new ClassPathResource("seed/atp_portable_seed.zip");
            if (!res.exists()) return null;
            Path tmp = Files.createTempFile("atp_portable_seed_", ".zip");
            try (InputStream in = res.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (Exception e) {
            log.warn("读取 classpath 种子失败: {}", e.getMessage());
            return null;
        }
    }
}
