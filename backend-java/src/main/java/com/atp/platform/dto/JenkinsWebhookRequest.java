package com.atp.platform.dto;

import lombok.Data;

import java.util.Map;

@Data
public class JenkinsWebhookRequest {
    private String jobName;
    private String buildNumber;
    private String callbackUrl;
    /** 可选：覆盖默认任务配置 */
    private Map<String, Object> task;
}
