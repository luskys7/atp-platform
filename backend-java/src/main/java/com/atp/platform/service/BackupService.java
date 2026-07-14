package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final AtpProperties properties;
    private final TestCaseRepository caseRepository;
    private final TestSuiteRepository suiteRepository;
    private final CommonStepRepository commonStepRepository;
    private final TestEnvironmentRepository environmentRepository;
    private final DataSetRepository dataSetRepository;
    private final SecureCredentialRepository credentialRepository;
    private final TestAccountRepository accountRepository;

    private final ObjectMapper backupMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public List<Map<String, Object>> list() {
        Path dir = backupDir();
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                    .map(this::toMeta)
                    .toList();
        } catch (IOException e) {
            throw new AppException("BACKUP", "读取备份列表失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> create(Long userId) {
        try {
            Files.createDirectories(backupDir());
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path zipPath = backupDir().resolve("atp_backup_" + ts + ".zip");

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("created_at", LocalDateTime.now().toString());
            manifest.put("created_by", userId);
            manifest.put("version", "1.0");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                writeEntry(zos, "manifest.json", manifest);
                writeEntry(zos, "test_cases.json", caseRepository.findAll());
                writeEntry(zos, "test_suites.json", suiteRepository.findAll());
                writeEntry(zos, "common_steps.json", commonStepRepository.findAll());
                writeEntry(zos, "environments.json", environmentRepository.findAll());
                writeEntry(zos, "data_sets.json", dataSetRepository.findAll());
                writeEntry(zos, "secure_credentials.json", credentialRepository.findAll());
                writeEntry(zos, "test_suites.json", suiteRepository.findAll());
                writeEntry(zos, "test_accounts.json", accountRepository.findAll());
            }
            return toMeta(zipPath);
        } catch (IOException e) {
            throw new AppException("BACKUP", "创建备份失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public Map<String, Object> restore(String filename) {
        Path zipPath = backupDir().resolve(filename);
        if (!Files.exists(zipPath) || !filename.endsWith(".zip")) {
            throw new AppException("NOT_FOUND", "备份文件不存在", HttpStatus.NOT_FOUND);
        }
        int restored = 0;
        Map<String, Integer> detail = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] data = zis.readAllBytes();
                int count = switch (entry.getName()) {
                    case "environments.json" -> restoreEnvironments(data);
                    case "common_steps.json" -> restoreCommonSteps(data);
                    case "test_cases.json" -> restoreCases(data);
                    case "test_suites.json" -> restoreSuites(data);
                    case "data_sets.json" -> restoreDataSets(data);
                    case "secure_credentials.json" -> restoreCredentials(data);
                    case "test_accounts.json" -> restoreAccounts(data);
                    default -> 0;
                };
                if (count > 0) {
                    detail.put(entry.getName(), count);
                    restored += count;
                }
                zis.closeEntry();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("restored", filename);
            result.put("count", restored);
            result.put("detail", detail);
            result.put("message", "已按 ID 合并还原备份内容");
            return result;
        } catch (IOException e) {
            throw new AppException("BACKUP", "还原失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void delete(String filename) {
        try {
            Files.deleteIfExists(backupDir().resolve(filename));
        } catch (IOException e) {
            throw new AppException("BACKUP", "删除备份失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 清理超出保留期的备份文件 */
    public int pruneOldBackups(int retainDays) {
        if (retainDays <= 0) return 0;
        Path dir = backupDir();
        if (!Files.exists(dir)) return 0;
        long cutoff = System.currentTimeMillis() - (long) retainDays * 86400_000L;
        int pruned = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(x -> x.getFileName().toString().endsWith(".zip")).toList()) {
                if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                    Files.deleteIfExists(p);
                    pruned++;
                }
            }
        } catch (IOException e) {
            throw new AppException("BACKUP", "清理旧备份失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return pruned;
    }

    public byte[] download(String filename) {
        try {
            Path p = backupDir().resolve(filename);
            if (!Files.exists(p)) {
                throw new AppException("NOT_FOUND", "备份不存在", HttpStatus.NOT_FOUND);
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new AppException("BACKUP", "下载失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private int restoreCases(byte[] data) throws IOException {
        List<TestCase> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, TestCase.class));
        for (TestCase c : list) {
            caseRepository.save(c);
        }
        return list.size();
    }

    private int restoreCommonSteps(byte[] data) throws IOException {
        List<CommonStep> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, CommonStep.class));
        for (CommonStep s : list) {
            commonStepRepository.save(s);
        }
        return list.size();
    }

    private int restoreEnvironments(byte[] data) throws IOException {
        List<TestEnvironment> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, TestEnvironment.class));
        for (TestEnvironment e : list) {
            environmentRepository.save(e);
        }
        return list.size();
    }

    private int restoreSuites(byte[] data) throws IOException {
        List<TestSuite> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, TestSuite.class));
        for (TestSuite s : list) {
            suiteRepository.save(s);
        }
        return list.size();
    }

    private int restoreDataSets(byte[] data) throws IOException {
        List<DataSet> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, DataSet.class));
        for (DataSet d : list) {
            dataSetRepository.save(d);
        }
        return list.size();
    }

    private int restoreCredentials(byte[] data) throws IOException {
        List<SecureCredential> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, SecureCredential.class));
        for (SecureCredential c : list) {
            credentialRepository.save(c);
        }
        return list.size();
    }

    private int restoreAccounts(byte[] data) throws IOException {
        List<TestAccount> list = backupMapper.readValue(data, backupMapper.getTypeFactory()
                .constructCollectionType(List.class, TestAccount.class));
        for (TestAccount a : list) {
            accountRepository.save(a);
        }
        return list.size();
    }

    private void writeEntry(ZipOutputStream zos, String name, Object data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(backupMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
        zos.closeEntry();
    }

    private Path backupDir() {
        return Paths.get(properties.getStorage().getLocalPath()).getParent().resolve("backups");
    }

    private Map<String, Object> toMeta(Path p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("filename", p.getFileName().toString());
        try {
            row.put("size", Files.size(p));
            row.put("created_at", Files.getLastModifiedTime(p).toInstant().toString());
        } catch (IOException ignored) {
        }
        return row;
    }
}
