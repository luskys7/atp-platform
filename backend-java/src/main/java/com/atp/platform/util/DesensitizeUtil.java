package com.atp.platform.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesensitizeUtil {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern BANK = Pattern.compile("(?<!\\d)(\\d{16,19})(?!\\d)");
    private static final Pattern ORDER = Pattern.compile("(?i)(ORD|ORDER|SN|NO)[-_]?(\\d{6,})");

    private DesensitizeUtil() {}

    public static String desensitize(String text) {
        if (text == null || text.isBlank()) return text;
        String out = text;
        out = replaceAll(out, PHONE, m -> m.group(1).substring(0, 3) + "****" + m.group(1).substring(7));
        out = replaceAll(out, ID_CARD, m -> {
            String s = m.group(1);
            return s.substring(0, 4) + "**********" + s.substring(s.length() - 4);
        });
        out = replaceAll(out, EMAIL, m -> {
            String user = m.group(1);
            String prefix = user.length() > 2 ? user.substring(0, 2) : user;
            return prefix + "***@" + m.group(2);
        });
        out = replaceAll(out, BANK, m -> {
            String s = m.group(1);
            return s.substring(0, 4) + " **** **** " + s.substring(s.length() - 4);
        });
        out = replaceAll(out, ORDER, m -> maskOrder(m.group()));
        return out;
    }

    public static boolean containsSensitive(String text) {
        if (text == null || text.isBlank()) return false;
        return PHONE.matcher(text).find()
                || ID_CARD.matcher(text).find()
                || EMAIL.matcher(text).find()
                || BANK.matcher(text).find()
                || ORDER.matcher(text).find();
    }

    private static String maskOrder(String raw) {
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (Character.isDigit(chars[i]) && i > 4 && i < chars.length - 3) {
                chars[i] = '*';
            }
        }
        return new String(chars);
    }

    private static String replaceAll(String input, Pattern pattern, Replacer replacer) {
        Matcher m = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.replace(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @FunctionalInterface
    private interface Replacer {
        String replace(Matcher m);
    }
}
