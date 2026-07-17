package com.atp.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceRegisterRequest {
    @NotBlank
    private String serialNumber;
    private String name;
    @NotBlank
    private String platform;
    private String osVersion;
    private String model;
    private String agentHost;
    private Integer agentPort;
    /** 设备所在执行器地址，如 http://192.168.1.20:9002 */
    private String executorUrl;
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer wdaPort;
    private Integer adbPort;
    private Integer batteryLevel;
}
