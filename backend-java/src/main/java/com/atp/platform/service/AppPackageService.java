package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.AppPackage;
import com.atp.platform.entity.Device;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AppPackageRepository;
import com.atp.platform.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppPackageService {

    private final AppPackageRepository repository;
    private final DeviceRepository deviceRepository;
    private final PythonExecutorClient executorClient;
    private final AtpProperties properties;

    public List<AppPackage> list() {
        return repository.findByStatusOrderByUpdatedAtDesc(AppPackage.PackageStatus.active);
    }

    public AppPackage get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "安装包不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public AppPackage upload(MultipartFile file, String name, String packageName, String versionName,
                             String branch, String platform, String channel, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException("INVALID", "请选择 APK/IPA 文件", HttpStatus.BAD_REQUEST);
        }
        validatePackageFile(file, platform);
        Path dir = Path.of(properties.getStorage().getAppsPath());
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "app.apk";
        String storedName = System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = dir.resolve(storedName);
        String md5;
        try (InputStream in = file.getInputStream()) {
            md5 = computeMd5(in);
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        AppPackage pkg = new AppPackage();
        pkg.setName(name != null && !name.isBlank() ? name : original);
        pkg.setPackageName(packageName);
        pkg.setVersionName(versionName);
        pkg.setPlatform(AppPackage.Platform.valueOf(platform != null ? platform : "android"));
        pkg.setFileName(original);
        pkg.setFilePath(target.toAbsolutePath().toString());
        pkg.setFileSize(file.getSize());
        pkg.setMd5Hash(md5);
        pkg.setBranch(branch);
        if (channel != null && !channel.isBlank()) {
            pkg.setPackageChannel(AppPackage.PackageChannel.valueOf(channel));
        }
        pkg.setUploadedBy(userId);
        return repository.save(pkg);
    }

    /** 自动化执行/批量安装前校验渠道是否允许 */
    public void assertAllowedForAutomation(Long packageId) {
        if (packageId == null) return;
        AppPackage pkg = get(packageId);
        List<String> blocked = properties.getGovernance().getBlockedPackageChannels();
        if (blocked == null || blocked.isEmpty()) return;
        String ch = pkg.getPackageChannel() != null ? pkg.getPackageChannel().name() : "test";
        if (blocked.contains(ch)) {
            throw new AppException("PACKAGE_BLOCKED",
                    "渠道「" + ch + "」安装包禁止用于自动化测试（已拦截线上/正式包风险）",
                    HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void delete(Long id) {
        AppPackage pkg = get(id);
        pkg.setStatus(AppPackage.PackageStatus.deprecated);
        repository.save(pkg);
        try {
            Files.deleteIfExists(Path.of(pkg.getFilePath()));
        } catch (IOException ignored) {
        }
    }

    public Map<String, Object> batchInstall(Long packageId, List<Long> deviceIds) {
        assertAllowedForAutomation(packageId);
        AppPackage pkg = get(packageId);
        if (!Files.exists(Path.of(pkg.getFilePath()))) {
            throw new AppException("NOT_FOUND", "安装包文件不存在", HttpStatus.NOT_FOUND);
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0, failed = 0;
        for (Long deviceId : deviceIds) {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "设备不存在: " + deviceId, HttpStatus.NOT_FOUND));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_id", deviceId);
            row.put("serial_number", device.getSerialNumber());
            try {
                if (device.getPlatform() != Device.Platform.android) {
                    row.put("success", false);
                    row.put("message", "当前仅支持 Android 批量安装");
                    failed++;
                } else {
                    executorClient.installApp(device.getSerialNumber(), device.getPlatform().name(), pkg.getFilePath());
                    row.put("success", true);
                    row.put("message", "安装指令已下发");
                    success++;
                }
            } catch (Exception e) {
                row.put("success", false);
                row.put("message", e.getMessage());
                failed++;
            }
            results.add(row);
        }
        return Map.of("package_id", packageId, "success_count", success, "failed_count", failed, "results", results);
    }

    public String resolveFilePath(Long packageId) {
        if (packageId == null) return null;
        return get(packageId).getFilePath();
    }

    private String computeMd5(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (DigestInputStream dis = new DigestInputStream(in, md)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) { /* drain */ }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("MD5 计算失败", e);
        }
    }

    private void validatePackageFile(MultipartFile file, String platform) throws IOException {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String pf = platform != null ? platform : "android";
        if ("android".equals(pf) && !original.endsWith(".apk")) {
            throw new AppException("INVALID", "Android 安装包须为 .apk 格式", HttpStatus.BAD_REQUEST);
        }
        if ("ios".equals(pf) && !original.endsWith(".ipa")) {
            throw new AppException("INVALID", "iOS 安装包须为 .ipa 格式", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() < 1024) {
            throw new AppException("INVALID", "安装包文件过小，可能已损坏", HttpStatus.BAD_REQUEST);
        }
        byte[] magic = file.getInputStream().readNBytes(2);
        if (magic.length < 2 || magic[0] != 'P' || magic[1] != 'K') {
            throw new AppException("INVALID", "安装包不是有效的 ZIP/APK 格式", HttpStatus.BAD_REQUEST);
        }
    }
}
