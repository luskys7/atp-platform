package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiLocateService {

    private final AtpProperties properties;
    private final RestClient restClient = RestClient.create();

    public AiLocateResult locate(String serialNumber, String platform, String query, String appPackage) {
        try {
            AiLocateResponse resp = restClient.post()
                    .uri(properties.getExecutor().getUrl() + "/api/v1/ai/locate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serial_number", serialNumber,
                            "platform", platform,
                            "query", query,
                            "app_package", appPackage != null ? appPackage : ""
                    ))
                    .retrieve()
                    .body(AiLocateResponse.class);
            if (resp == null || !resp.isSuccess()) {
                throw new AppException("E4001", resp != null ? resp.getErrorMessage() : "AI 定位无响应",
                        HttpStatus.NOT_FOUND);
            }
            return new AiLocateResult(resp.getLocatorType(), resp.getLocatorValue(),
                    resp.getConfidence() != null ? resp.getConfidence() : 0.0, resp.getStrategy());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("E4001", "AI 定位调用失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record AiLocateResult(String locatorType, String locatorValue, double confidence, String strategy) {}

    @Data
    private static class AiLocateResponse {
        private boolean success;
        private String locatorType;
        private String locatorValue;
        private Double confidence;
        private String strategy;
        private String errorMessage;
    }
}
