package com.atp.platform.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

@Data
public class JenkinsWebhookRequest {
    private String jobName;

    @JsonAlias({"build_num", "buildNumber"})
    private String buildNumber;

    private String branch;

    @JsonAlias({"download_url", "package_url", "app_url"})
    private String downloadUrl;

    private String callbackUrl;

    /** 可选：覆盖默认任务配置 */
    private Map<String, Object> task;
}
