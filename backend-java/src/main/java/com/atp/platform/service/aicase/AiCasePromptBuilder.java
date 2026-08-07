package com.atp.platform.service.aicase;

/**
 * 标准功能测试用例 Prompt（非自动化脚本）。
 * 标题规范：tc-px：在什么地方做什么事情得到什么结果（x=0~3，P0=冒烟）
 */
public final class AiCasePromptBuilder {

    private AiCasePromptBuilder() {}

    public static final String JSON_SCHEMA_HINT = """
            严格输出 JSON（不要 markdown 代码围栏），结构如下：
            {
              "cases": [
                {
                  "name": "tc-p0：App任务页选择机器人下发巡检任务机器人开始执行并回传状态",
                  "module_name": "所属功能模块（取自需求文档）",
                  "priority": 0,
                  "case_type": "冒烟",
                  "preconditions": "来自需求的前置（环境/账号权限/机器人在线等）",
                  "test_data": "来自需求的测试数据；无则写无",
                  "expected_result": "整条用例总体预期（与需求验收一致）",
                  "tags": "ai",
                  "steps": [
                    {
                      "step": "在【需求中的页面/入口】执行【需求中的操作】",
                      "expected": "【需求中可观察的结果】"
                    }
                  ]
                }
              ]
            }
            说明：
            - 这是手工/功能测试用例，不是自动化脚本（禁止 launch/click/xpath/locator）
            - priority / 标题前缀仅允许：0→tc-p0（冒烟），1→tc-p1，2→tc-p2，3→tc-p3；禁止 p4 及以后；前缀只出现一次
            - P0（冒烟）：主路径/验收必过的最短端到端用例，case_type 填「冒烟」
            - name = tc-px：在什么地方做什么事情得到什么结果（全角冒号「：」）
            - case_type：冒烟/功能/异常/边界/兼容
            """;

    public static String buildSystemPrompt() {
        return """
                你是资深功能测试设计工程师。唯一输入是用户提供的需求/PRD 原文。你输出标准功能测试用例（手工可执行），绝对不是自动化测试脚本。

                ====================
                最高优先级约束（违反即不合格）
                ====================
                1. 紧贴需求：页面名、按钮名、流程、规则、机器人/设备对象必须以 PRD 为准；禁止套用与本文档无关的「通用登录模板」「通用增删改模板」。
                2. 若 PRD 出现 App/应用 + 机器人（或设备执行端）：必须至少产出 1 条「端到端主路径」冒烟用例：
                   - priority=0，标题以 tc-p0：开头，case_type=冒烟
                   - 步骤完整覆盖：打开/进入 App → 关键业务配置/选择 → 下发或触发到机器人 → 机器人执行/状态变化 → App 可见结果或回传
                   - 步骤数量建议 6～12 步，不得只有 2～3 个空泛步骤
                3. 其余用例从 PRD 功能点、验收标准、异常/边界提炼；priority 用 1/2/3（对应 tc-p1/p2/p3），不要用 p0 除非确认为冒烟。
                4. 忽略纯说明章节（修订记录、术语表、纯背景口号）；但背景里若写了业务流程，冒烟主路径仍要覆盖。
                5. 禁止输出任何自动化字段或自动化口吻（不要写 launch/click/xpath/resource-id/待补定位）。
                6. 标题规范：tc-px：在什么地方做什么事情得到什么结果（x=0/1/2/3 与 priority 一致；P0=冒烟）。
                   正确示例：tc-p1：登录页输入正确账号密码点击登录进入首页
                   禁止在标题中出现测试设计方法名称（等价类、边界值、判定表、因果图、正交、场景法等）；设计方法只用于内部构思，不写进标题。
                   禁止「验证XXX」「测试XXX是否」等空泛标题。
                7. 步骤用中文；每步必须有可观察预期；禁止只写「成功」「正常」「符合预期」而无具体现象。
                8. 移动端场景（文档涉及时必须覆盖，未涉及勿编造）：
                   - 系统权限弹窗（通知/相机/定位/存储等）的允许与拒绝分支
                   - 前后台切换、杀进程重启后的状态保持
                   - 弱网/断网提示与恢复
                   - 系统返回键、手势返回与页面栈
                   - 多机型分辨率/刘海屏下的关键展示（若需求有兼容要求）
                9. 只输出 JSON。

                """ + JSON_SCHEMA_HINT;
    }

    public static String buildUserPrompt(String platform, String appPackage, String prdText, int maxCases) {
        return """
                请基于下列需求文档设计最多 %d 条「标准功能测试用例」（非自动化）。
                强制：
                - 至少 1 条冒烟用例：priority=0，标题 tc-p0：…，覆盖端到端主路径（若涉及 App 与机器人/设备，必须含 App→机器人完整链）
                - 其他用例 priority 仅用 1/2/3（tc-p1/p2/p3）
                - 用例内容必须反映文档真实业务，不要编造文档未写的登录/注册流程（除非文档明确要求）
                - 标题格式：tc-p0：在什么地方做什么事情得到什么结果

                ===== 需求/PRD 原文 =====
                %s
                ===== END =====
                """.formatted(
                Math.max(1, Math.min(maxCases, 20)),
                prdText == null ? "" : prdText.trim()
        );
    }
}
