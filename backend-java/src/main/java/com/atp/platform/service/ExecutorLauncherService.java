package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ExecutorLauncherService {

    private static final String DEFAULT_FILENAME = "TestFlow-Executor.exe";

    private final AtpProperties properties;

    public Map<String, Object> info() {
        Path file = resolveLauncherFile();
        Map<String, Object> data = new LinkedHashMap<>();
        if (file != null && Files.isRegularFile(file)) {
            data.put("available", true);
            data.put("filename", file.getFileName().toString());
            try {
                data.put("size", Files.size(file));
            } catch (IOException e) {
                data.put("size", 0L);
            }
            data.put("message", "启动器已就绪");
        } else {
            data.put("available", false);
            data.put("filename", DEFAULT_FILENAME);
            data.put("size", 0L);
            data.put("message", "启动器尚未就绪，请将 " + DEFAULT_FILENAME
                    + " 放到 " + downloadsDir().toAbsolutePath().normalize() + " 后重试");
        }
        return data;
    }

    public Path requireLauncherFile() {
        Path file = resolveLauncherFile();
        if (file == null || !Files.isRegularFile(file)) {
            throw new AppException("NOT_FOUND",
                    "启动器文件不存在，请联系管理员将 " + DEFAULT_FILENAME
                            + " 放到 " + downloadsDir().toAbsolutePath().normalize(),
                    HttpStatus.NOT_FOUND);
        }
        return file;
    }

    private Path downloadsDir() {
        return Path.of(properties.getDownloads().getDir()).toAbsolutePath().normalize();
    }

    /** 优先固定文件名，否则取目录下最新 .exe */
    private Path resolveLauncherFile() {
        Path dir = downloadsDir();
        Path preferred = dir.resolve(DEFAULT_FILENAME);
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".exe"))
                    .max((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
