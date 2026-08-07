package com.atp.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCiConfigRequest {
    @NotBlank
    private String webhookToken;
    private Boolean enabled;
    private String defaultPlatform;
    private String defaultScriptType;
    private String defaultScriptContent;
    private String defaultAppPackage;
    private Integer defaultTimeoutSeconds;
    private Boolean autoSubmit;
    private String ipWhitelist;
    private String branchAllow;
    private String branchDeny;
    private Integer maxConcurrentTasks;
    private Integer receiveRetryCount;
    private Integer receiveTimeoutSeconds;
    private Long defaultSuiteId;
    private Long defaultBaselineId;
    private Long defaultEnvId;
}
