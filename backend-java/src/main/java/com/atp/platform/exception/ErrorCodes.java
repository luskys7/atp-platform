package com.atp.platform.exception;

import java.util.Map;

public final class ErrorCodes {

    public static final String E1001 = "E1001";
    public static final String E1002 = "E1002";
    public static final String E1003 = "E1003";
    public static final String E2001 = "E2001";
    public static final String E2002 = "E2002";
    public static final String E2003 = "E2003";
    public static final String E3001 = "E3001";
    public static final String E3002 = "E3002";
    public static final String E4001 = "E4001";
    public static final String E4002 = "E4002";
    public static final String E4003 = "E4003";

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry(E1001, "设备未录入白名单，禁止接入"),
            Map.entry(E1002, "设备分布式锁占用，调度失败"),
            Map.entry(E1003, "iOS WDA授信失效，连接中断"),
            Map.entry(E2001, "设备录屏权限抢占失败"),
            Map.entry(E2002, "视频分片上传超时"),
            Map.entry(E2003, "存储空间不足，禁止启动任务"),
            Map.entry(E3001, "任务队列溢出，拒绝新建任务"),
            Map.entry(E3002, "任务超时熔断，强制终止"),
            Map.entry(E4001, "控件池检索异常，自动降级原生定位"),
            Map.entry(E4002, "私有控件绑定失效，隔离校验拦截"),
            Map.entry(E4003, "控件脏数据，禁止入库")
    );

    private ErrorCodes() {}

    public static String message(String code) {
        return MESSAGES.getOrDefault(code, "未知错误");
    }
}
