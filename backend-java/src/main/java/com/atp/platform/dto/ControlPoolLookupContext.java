package com.atp.platform.dto;

/** 执行/解析时的四维匹配上下文（M3） */
public record ControlPoolLookupContext(
        Long teamId,
        String platform,
        String versionTag,
        String envTag
) {
    public static ControlPoolLookupContext empty() {
        return new ControlPoolLookupContext(null, "both", "*", "*");
    }
}
