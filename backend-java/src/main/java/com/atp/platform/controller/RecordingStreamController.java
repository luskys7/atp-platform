package com.atp.platform.controller;

import com.atp.platform.entity.RecordingResource;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.RecordingResourceRepository;
import com.atp.platform.service.LocalRecordingStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class RecordingStreamController {

    private final RecordingResourceRepository recordingRepository;
    private final LocalRecordingStorageService storageService;

    @GetMapping("/api/v1/recordings/{id}/stream")
    public ResponseEntity<Resource> streamById(@PathVariable Long id) {
        RecordingResource rec = recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
        return streamFile(rec.getFilePath());
    }

    @GetMapping("/api/v1/recordings/stream")
    public ResponseEntity<Resource> streamByPath(@RequestParam String path) {
        return streamFile(path);
    }

    @GetMapping("/api/v1/recordings/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id) {
        RecordingResource rec = recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
        if (rec.getThumbnailPath() == null || rec.getThumbnailPath().isBlank()) {
            throw new AppException("NOT_FOUND", "缩略图不存在", HttpStatus.NOT_FOUND);
        }
        Path file = storageService.resolvePath(rec.getThumbnailPath());
        if (!file.toFile().exists()) {
            throw new AppException("NOT_FOUND", "缩略图文件不存在", HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    private ResponseEntity<Resource> streamFile(String objectPath) {
        Path file = storageService.resolvePath(objectPath);
        if (!file.toFile().exists()) {
            throw new AppException("NOT_FOUND", "录屏文件不存在", HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(file);
        String name = file.getFileName().toString().toLowerCase();
        MediaType type = name.endsWith(".webm")
                ? MediaType.parseMediaType("video/webm")
                : MediaType.parseMediaType("video/mp4");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}
