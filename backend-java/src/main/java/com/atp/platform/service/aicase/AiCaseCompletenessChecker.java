package com.atp.platform.service.aicase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标准用例字段完整性软检查（非 TestBrain 深度评审）。
 */
final class AiCaseCompletenessChecker {

    private AiCaseCompletenessChecker() {}

    @SuppressWarnings("unchecked")
    static List<String> check(Map<String, Object> draft) {
        List<String> issues = new ArrayList<>();
        String name = str(draft.get("name"));
        if (name.isBlank()) {
            issues.add("缺少用例标题");
        } else if (!name.matches("(?i)^tc-p[0-3]：.+")) {
            issues.add("标题未符合 tc-p0~p3：场景描述 规范");
        }
        if (str(draft.get("preconditions")).isBlank()) {
            issues.add("缺少前置条件");
        }
        if (str(draft.get("expected_result")).isBlank()) {
            issues.add("缺少总体预期结果");
        }
        Object stepsObj = draft.get("standard_steps");
        List<Map<String, String>> steps = stepsObj instanceof List<?> list
                ? (List<Map<String, String>>) list
                : List.of();
        if (steps.isEmpty()) {
            issues.add("缺少测试步骤");
        } else {
            int emptyStep = 0;
            int emptyExpected = 0;
            int vague = 0;
            for (Map<String, String> s : steps) {
                String step = s == null ? "" : str(s.get("step"));
                String expected = s == null ? "" : str(s.get("expected"));
                if (step.isBlank()) emptyStep++;
                if (expected.isBlank()) emptyExpected++;
                if (isVague(expected)) vague++;
            }
            if (emptyStep > 0) issues.add("存在 " + emptyStep + " 条空操作步骤");
            if (emptyExpected > 0) issues.add("存在 " + emptyExpected + " 条空步骤预期");
            if (vague > 0) issues.add("存在 " + vague + " 条预期过于空泛（如仅写「成功/正常」）");
            Object pri = draft.get("priority");
            int p = pri instanceof Number n ? n.intValue() : 2;
            if (p == 0 && steps.size() < 4) {
                issues.add("P0 冒烟步骤偏少（建议 ≥4 步覆盖主路径）");
            }
        }
        if (str(draft.get("module_name")).isBlank() || "未分类".equals(str(draft.get("module_name")))) {
            issues.add("模块未明确（仍为未分类）");
        }
        return issues;
    }

    private static boolean isVague(String expected) {
        if (expected == null) return true;
        String e = expected.replaceAll("\\s+", "");
        return e.equals("成功") || e.equals("正常") || e.equals("符合预期")
                || e.equals("操作成功") || e.equals("通过") || e.equals("ok") || e.equals("OK");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
