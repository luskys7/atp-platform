package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.TestCase;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class OfflinePackageService {

    private final AtpProperties properties;
    private final VisualScriptGenerator visualScriptGenerator;
    private final ControlResolverService controlResolverService;
    private final TestCaseRepository caseRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> buildForTask(TestTask task) {
        String script = resolveScript(task.getScriptType(), task.getScriptContent());
        String controlsJson = controlResolverService.buildControlsJson(task);
        return writePackage("task", task.getId(), task.getName(), task.getPlatform().name(),
                task.getAppPackage(), script, controlsJson);
    }

    public Map<String, Object> buildForCase(Long caseId) {
        TestCase c = caseRepository.findById(caseId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
        if (c.getDeletedAt() != null) {
            throw new AppException("NOT_FOUND", "用例已删除", HttpStatus.NOT_FOUND);
        }
        String script = resolveScript(c.getScriptType(), c.getStepsContent());
        TestTask pseudo = new TestTask();
        pseudo.setId(caseId);
        pseudo.setAppPackage(c.getAppPackage());
        String controlsJson = controlResolverService.buildControlsJson(pseudo);
        return writePackage("case", caseId, c.getName(), c.getPlatform().name(),
                c.getAppPackage(), script, controlsJson);
    }

    public byte[] readPackage(String filename) {
        String safe = sanitizeFilename(filename);
        Path file = packageDir().resolve(safe);
        if (!Files.exists(file)) {
            throw new AppException("NOT_FOUND", "离线包不存在", HttpStatus.NOT_FOUND);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AppException("IO", "读取离线包失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String resolveScript(String scriptType, String content) {
        if ("visual".equals(scriptType)) {
            return visualScriptGenerator.generate(content);
        }
        return content != null ? content : "print('empty script')";
    }

    private Map<String, Object> writePackage(String kind, Long id, String name, String platform,
                                               String appPackage, String script, String controlsJson) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = kind + "-" + id + "-" + ts + ".zip";
        Path dir = packageDir();
        try {
            Files.createDirectories(dir);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("kind", kind);
            manifest.put("resource_id", id);
            manifest.put("name", name);
            manifest.put("platform", platform);
            manifest.put("app_package", appPackage);
            manifest.put("generated_at", LocalDateTime.now().toString());
            manifest.put("executor_url", properties.getExecutor().getUrl());
            manifest.put("readme", "离线执行：解压后 python run_offline.py --serial <设备号>");

            byte[] zipBytes = buildZip(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                    script,
                    controlsJson
            );
            Path target = dir.resolve(filename);
            Files.write(target, zipBytes);
            return Map.of(
                    "filename", filename,
                    "size_bytes", zipBytes.length,
                    "download_url", "/api/v1/offline-packages/" + filename + "/download"
            );
        } catch (IOException e) {
            throw new AppException("IO", "生成离线包失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private byte[] buildZip(String manifest, String script, String controlsJson) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            putEntry(zos, "manifest.json", manifest);
            putEntry(zos, "controls.json", controlsJson);
            putEntry(zos, "test_script.py", script);
            putEntry(zos, "run_offline.py", offlineRunner());
            putEntry(zos, "requirements.txt", "fastapi\nuvicorn\npydantic\n");
            putEntry(zos, "README.md", readmeText());
        }
        return bos.toByteArray();
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private Path packageDir() {
        return Paths.get("./data/offline-packages").toAbsolutePath().normalize();
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new AppException("INVALID", "非法文件名", HttpStatus.BAD_REQUEST);
        }
        if (!filename.endsWith(".zip")) {
            throw new AppException("INVALID", "仅支持 zip 离线包", HttpStatus.BAD_REQUEST);
        }
        return filename;
    }

    private static String offlineRunner() {
        return """
                #!/usr/bin/env python3
                \"\"\"TestFlow 离线执行入口（无平台调度）\"\"\"
                import argparse
                import json
                import os
                import subprocess
                import sys
                
                ROOT = os.path.dirname(os.path.abspath(__file__))
                
                
                def main():
                    parser = argparse.ArgumentParser(description="TestFlow offline runner")
                    parser.add_argument("--serial", required=True, help="adb device serial")
                    parser.add_argument("--executor-url", default=os.environ.get("ATP_EXECUTOR_URL", "http://127.0.0.1:9002"))
                    args = parser.parse_args()
                
                    manifest = json.load(open(os.path.join(ROOT, "manifest.json"), encoding="utf-8"))
                    controls = open(os.path.join(ROOT, "controls.json"), encoding="utf-8").read()
                    script = open(os.path.join(ROOT, "test_script.py"), encoding="utf-8").read()
                
                    payload = {
                        "task_id": manifest.get("resource_id", 0),
                        "execution_id": 0,
                        "serial_number": args.serial,
                        "platform": manifest.get("platform", "android"),
                        "app_package": manifest.get("app_package", ""),
                        "script_content": script,
                        "controls_json": controls,
                        "auto_grant_permissions": True,
                        "execution_isolation": "case",
                    }
                    import urllib.request
                    body = json.dumps(payload).encode()
                    req = urllib.request.Request(
                        args.executor_url.rstrip("/") + "/api/v1/execute",
                        data=body,
                        headers={"Content-Type": "application/json"},
                        method="POST",
                    )
                    with urllib.request.urlopen(req, timeout=3600) as resp:
                        result = json.loads(resp.read().decode())
                    print(json.dumps(result, ensure_ascii=False, indent=2))
                    sys.exit(0 if result.get("success") else 1)
                
                
                if __name__ == "__main__":
                    main()
                """;
    }

    private static String readmeText() {
        return """
                # TestFlow 离线执行包
                
                1. 解压到测试机或内网隔离环境
                2. 确保 adb 可用且设备已连接
                3. 启动 Python 执行器（可选，默认 http://127.0.0.1:9002）
                4. 运行：`python run_offline.py --serial <设备序列号>`
                
                包内文件：
                - manifest.json  元数据
                - controls.json  控件映射
                - test_script.py 自动化脚本
                """;
    }
}
