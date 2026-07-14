package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalRecordingStorageService implements RecordingStorageService {

    private final AtpProperties properties;

    @Override
    public String getPlaybackUrl(String objectPath) {
        return "/api/v1/recordings/stream?path=" + objectPath;
    }

    public Path resolvePath(String objectPath) {
        Path base = Path.of(properties.getStorage().getLocalPath()).toAbsolutePath().normalize();
        Path file = base.resolve(objectPath).normalize();
        if (!file.startsWith(base)) {
            throw new SecurityException("非法路径");
        }
        return file;
    }

    public boolean exists(String objectPath) {
        return Files.exists(resolvePath(objectPath));
    }
}
