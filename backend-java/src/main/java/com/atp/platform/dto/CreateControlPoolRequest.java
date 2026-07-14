package com.atp.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateControlPoolRequest {
    @NotBlank
    private String appPackage;
    private String pageName;
    @NotBlank
    private String elementName;
    private String platform;
    @NotBlank
    private String locatorType;
    @NotBlank
    private String locatorValue;
    private String versionTag;
    private String envTag;
    private Long teamId;
    private Boolean isCore;
    private String controlTag;
    private java.util.Map<String, Object> waitRule;
    private String displayName;
    private String widgetType;
    private String riskLevel;
    private java.util.List<String> riskTags;
    private java.util.List<String> riskReasons;
    private java.util.Map<String, Object> locators;
    private java.util.List<java.util.Map<String, Object>> locatorChain;
    private java.util.Map<String, Object> validateResult;
    private String validatedAt;
    private Integer tapX;
    private Integer tapY;
}
