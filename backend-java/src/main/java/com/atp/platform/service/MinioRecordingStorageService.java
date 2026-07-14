package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class MinioRecordingStorageService implements RecordingStorageService {

    private final MinioClient minioClient;
    private final AtpProperties properties;

    @Override
    public String getPlaybackUrl(String objectPath) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(properties.getMinio().getBucket())
                        .object(objectPath)
                        .expiry(1, TimeUnit.HOURS)
                        .build());
    }
}
