package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.Device;
import com.atp.platform.exception.AppException;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.DeviceService;
import com.atp.platform.service.PythonExecutorClient;
import com.atp.platform.service.ScreenStreamTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScreenStreamController {

    private final DeviceService deviceService;
    private final ScreenStreamTokenService tokenService;
    private final PythonExecutorClient executorClient;
    private final AtpProperties properties;

    @PostMapping("/api/v1/devices/{id}/screen/start")
    public ApiResponse<Map<String, Object>> startStream(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 投屏", HttpStatus.BAD_REQUEST);
        }
        if (device.getStatus() == Device.DeviceStatus.offline) {
            throw new AppException("INVALID", "设备离线，无法投屏", HttpStatus.BAD_REQUEST);
        }
        ScreenStreamTokenService.StreamSession session = tokenService.createSession(
                id, device.getSerialNumber(), SecurityUtils.currentUserId());

        String executorHttp = executorClient.resolveBaseUrl(device);
        String executorWs = executorHttp
                .replace("https://", "wss://").replace("http://", "ws://");
        String tokenQ = "?token=" + session.token();
        String absoluteWs = executorWs + "/ws/screen/" + device.getSerialNumber() + tokenQ;
        String defaultExec = properties.getExecutor().getUrl()
                .replaceAll("/$", "");
        boolean localDefault = executorHttp.equalsIgnoreCase(defaultExec)
                || executorHttp.contains("localhost")
                || executorHttp.contains("127.0.0.1");
        // 本地默认执行器走 Vite 代理；远程执行器须浏览器直连绝对 WS
        String proxyWs = localDefault
                ? "/ws/executor/ws/screen/" + device.getSerialNumber() + tokenQ
                : absoluteWs;

        return ApiResponse.ok(Map.of(
                "ws_url", absoluteWs,
                "proxy_ws_url", proxyWs,
                "executor_url", executorHttp,
                "token", session.token(),
                "serial_number", device.getSerialNumber(),
                "device_id", id,
                "screen_width", device.getScreenWidth() != null ? device.getScreenWidth() : 1080,
                "screen_height", device.getScreenHeight() != null ? device.getScreenHeight() : 1920
        ));
    }

    @GetMapping("/api/v1/screen/verify")
    public ApiResponse<Map<String, Boolean>> verify(
            @RequestParam String token,
            @RequestParam String serial) {
        return ApiResponse.ok(Map.of("valid", tokenService.verify(token, serial)));
    }

    @PostMapping("/api/v1/devices/{id}/screen/tap")
    public ApiResponse<Void> tap(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        int x = requireInt(body, "x");
        int y = requireInt(body, "y");
        executorClient.tap(device, x, y);
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/devices/{id}/screen/swipe")
    public ApiResponse<Void> swipe(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        executorClient.swipe(device,
                requireInt(body, "x1"), requireInt(body, "y1"),
                requireInt(body, "x2"), requireInt(body, "y2"),
                intOrDefault(body, "duration_ms", 300));
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/devices/{id}/screen/input")
    public ApiResponse<Void> inputText(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (body == null || body.get("text") == null) {
            throw new AppException("INVALID", "缺少输入文本", HttpStatus.BAD_REQUEST);
        }
        Integer x = body.get("x") != null ? ((Number) body.get("x")).intValue() : null;
        Integer y = body.get("y") != null ? ((Number) body.get("y")).intValue() : null;
        executorClient.inputText(device, body.get("text").toString(), x, y);
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/devices/{id}/screen/key")
    public ApiResponse<Void> systemKey(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (body == null || body.get("key") == null) {
            throw new AppException("INVALID", "缺少按键 key", HttpStatus.BAD_REQUEST);
        }
        executorClient.pressSystemKey(device, body.get("key").toString());
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/devices/{id}/screen/inspect")
    public ApiResponse<Map<String, Object>> inspectPoint(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        int x = requireInt(body, "x");
        int y = requireInt(body, "y");
        boolean blocking = Boolean.parseBoolean(String.valueOf(body.getOrDefault("blocking", true)));
        Integer displayWidth = body.get("display_width") != null
                ? ((Number) body.get("display_width")).intValue() : device.getScreenWidth();
        Integer displayHeight = body.get("display_height") != null
                ? ((Number) body.get("display_height")).intValue() : device.getScreenHeight();
        Map<String, Object> inspect = executorClient.inspectPoint(
                device, x, y, displayWidth, displayHeight, blocking);
        return ApiResponse.ok(enrichInspectResult(inspect));
    }

    @PostMapping("/api/v1/devices/{id}/screen/switch-context")
    public ApiResponse<Map<String, Object>> switchContext(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "WebView 上下文切换当前仅支持 Android", HttpStatus.BAD_REQUEST);
        }
        String target = body != null && body.get("target") != null ? body.get("target").toString() : "auto";
        return ApiResponse.ok(executorClient.switchContext(device, target));
    }

    @PostMapping("/api/v1/devices/{id}/screen/warm-ui")
    public ApiResponse<Map<String, Object>> warmUiCache(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 控件识别", HttpStatus.BAD_REQUEST);
        }
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        boolean blocking = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("blocking", false)));
        Map<String, Object> warm = executorClient.warmUiCache(device, blocking);
        java.util.LinkedHashMap<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("ok", warm.getOrDefault("ok", true));
        resp.put("blocking", blocking);
        if (warm.get("page_context") != null) resp.put("page_context", warm.get("page_context"));
        if (warm.get("needs_context_switch") != null) resp.put("needs_context_switch", warm.get("needs_context_switch"));
        if (warm.get("app_profile") != null) resp.put("app_profile", warm.get("app_profile"));
        if (warm.get("reused_cache") != null) resp.put("reused_cache", warm.get("reused_cache"));
        if (warm.get("dump_source") != null) resp.put("dump_source", warm.get("dump_source"));
        if (warm.get("error") != null) resp.put("error", warm.get("error"));
        if (warm.get("message") != null) resp.put("message", warm.get("message"));
        return ApiResponse.ok(resp);
    }

    @PostMapping("/api/v1/devices/{id}/screen/ui-hierarchy")
    public ApiResponse<Map<String, Object>> uiHierarchy(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 控件树", HttpStatus.BAD_REQUEST);
        }
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        boolean force = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", false)));
        return ApiResponse.ok(executorClient.uiHierarchy(device, force));
    }

    @PostMapping("/api/v1/devices/{id}/screen/inspect-bounds")
    public ApiResponse<Map<String, Object>> inspectBounds(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 控件识别", HttpStatus.BAD_REQUEST);
        }
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        if (body == null || body.get("bounds") == null || body.get("bounds").toString().isBlank()) {
            throw new AppException("INVALID", "缺少 bounds", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> inspect = executorClient.inspectByBounds(device, body.get("bounds").toString());
        return ApiResponse.ok(enrichInspectResult(inspect));
    }

    @PostMapping("/api/v1/devices/{id}/screen/prepare-ui")
    public ApiResponse<Map<String, Object>> prepareUi(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 控件识别", HttpStatus.BAD_REQUEST);
        }
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(executorClient.prepareUi(device));
    }

    @PostMapping("/api/v1/devices/{id}/screen/validate-locator")
    public ApiResponse<Map<String, Object>> validateLocator(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body) {
        Device device = deviceService.getById(id);
        if (device.getPlatform() != Device.Platform.android) {
            throw new AppException("INVALID", "当前仅支持 Android 控件校验", HttpStatus.BAD_REQUEST);
        }
        if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> locators = body.get("locators") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chain = body.get("locator_chain") instanceof List<?> list
                ? (List<Map<String, Object>>) list : null;
        Map<String, Object> result = executorClient.validateLocatorOnScreen(device, locators, chain);
        return ApiResponse.ok(result);
    }

    private static Map<String, Object> enrichInspectResult(Map<String, Object> inspect) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>(inspect);
        boolean valid = Boolean.TRUE.equals(inspect.get("valid"));
        if (!valid) {
            Object locators = inspect.get("locators");
            if (locators instanceof Map<?, ?> map && !map.isEmpty()) valid = true;
            String lv = String.valueOf(inspect.getOrDefault("locator_value", ""));
            if (!lv.isBlank()) valid = true;
            String en = String.valueOf(inspect.getOrDefault("element_name", ""));
            if (!en.isBlank()) valid = true;
            String dn = String.valueOf(inspect.getOrDefault("display_name", ""));
            if (!dn.isBlank() && !"未知控件".equals(dn)) valid = true;
        }
        result.put("valid", valid);
        return result;
    }

    private static int requireInt(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            throw new AppException("INVALID", "缺少参数: " + key, HttpStatus.BAD_REQUEST);
        }
        return ((Number) body.get(key)).intValue();
    }

    private static int intOrDefault(Map<String, Object> body, String key, int defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        return ((Number) body.get(key)).intValue();
    }
}
