package com.atp.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTaskRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String platform;
    @NotBlank
    private String scriptType;
    @NotBlank
    private String scriptContent;
    private String appPackage;
    private String appPath;
    private Byte priority;
    private Integer parallelCount;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Boolean enableRecording;
    private java.util.List<Long> deviceIds;
    private Boolean useAccountPool;
    private Long envId;
}
