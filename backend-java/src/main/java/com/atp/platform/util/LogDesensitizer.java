package com.atp.platform.util;

import java.util.regex.Pattern;

public final class LogDesensitizer {

    private static final Pattern PHONE = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]{1,3})[\\w.+-]*@([\\w.-]+\\.[\\w.-]+)");
    private static final Pattern PASSWORD_KV = Pattern.compile(
            "(password|passwd|pwd|token|secret|api_key)\\s*[:=]\\s*[^\\s,&}\"']+", Pattern.CASE_INSENSITIVE);

    private LogDesensitizer() {
    }

    public static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = PHONE.matcher(text).replaceAll("$1****$2");
        result = EMAIL.matcher(result).replaceAll("$1***@$2");
        result = PASSWORD_KV.matcher(result).replaceAll("$1=***");
        return result;
    }
}
