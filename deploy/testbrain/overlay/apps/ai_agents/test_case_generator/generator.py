from typing import Dict, Any, List, Optional
import json
# from langchain_core.messages import SystemMessage, HumanMessage
from apps.llm.base import BaseLLMService
from apps.knowledge.service import KnowledgeService
from .prompts import TestCaseGeneratorPrompt
from apps.utils.logger_manager import get_logger
import re

_METHOD_NOISE = re.compile(
    r"(基于|采用|使用|运用|按照|依据|通过)?"
    r"(等价类划分法?|等价类|边界值分析(?:法)?|边界值|判定表(?:法)?|决策表|"
    r"因果图(?:法)?|正交(?:分析)?法?|正交表|场景法|场景测试|"
    r"正负向|正向用例|负向用例|用例设计方法|测试设计方法|设计方法)"
    r"([：:\-—–\|/·•]\s*|[）\)】]\s*|\s*[（\(\[【])?",
    re.IGNORECASE,
)
_GENERIC_PREFIX = re.compile(
    r"^(验证|测试|检查|确认|校验)(一下|下|是否|能否|可以)?[:：\s]*"
)


class TestCaseGeneratorAgent:
    """测试用例生成Agent"""
    
    def __init__(self, llm_service: BaseLLMService, knowledge_service: KnowledgeService, case_design_methods: List[str], case_categories: List[str], case_count: int = 10):
        self.llm_service = llm_service
        self.case_design_methods = case_design_methods
        self.case_categories = case_categories
        self.case_count = case_count
        self.knowledge_service = knowledge_service
        self.prompt = TestCaseGeneratorPrompt()
        self.logger = get_logger(self.__class__.__name__)  # 添加logger
    

    async def async_generate(self, input_text: str, input_type: str = "requirement") -> List[Dict[str, Any]]:
        """异步方式生成测试用例"""
        self.logger.info(f"开始生成测试用例-异步方式,进入生成测试用例的TestCaseGeneratorAgent")
        # 确定输入类型描述
        input_type_desc = "需求描述" if input_type == "requirement" else "代码片段"
        
        # 获取知识上下文
        knowledge_context = self._get_knowledge_context(input_text)
        self.logger.info(f"获取到知识库上下文: \n{'='*50}\n{knowledge_context}\n{'='*50}")
        
        # 处理设计方法和测试类型
        case_design_methods = ",".join(self.case_design_methods) if self.case_design_methods else ""
        case_categories = ",".join(self.case_categories) if self.case_categories else ""
        
        # 使用新的 format_messages 方法获取消息列表
        messages = self.prompt.format_messages(
            requirements=input_text,
            case_design_methods=case_design_methods,
            case_categories=case_categories,
            case_count=self.case_count,
            knowledge_context=knowledge_context
        )
        self.logger.info(f"构建后大模型提示词+用户需求消息: \n{'='*50}\n{messages}\n{'='*50}")
        
        # 调用LLM服务
        try:
            response = await self.llm_service.ainvoke(messages)
            result = response.content
            self.logger.info(f"LLM原始响应: \n{'='*50}\n{result}\n{'='*50}")
            
            # 尝试提取JSON部分
            json_str = self._extract_json_from_response(result)
            if not json_str:
                raise ValueError("无法从响应中提取有效的JSON数据")
                
            # 尝试解析JSON
            test_cases = json.loads(json_str)
            self.logger.info(f"_validate_test_cases处理前的用例个数: {len(test_cases)}")
            
            valid_test_cases = self._validate_test_cases(test_cases)
            if len(valid_test_cases) > self.case_count:
                self.logger.warning(f"LLM 超量生成：期望 {self.case_count} 条，但拿到 {len(valid_test_cases)} 条，自动裁剪。")
                valid_test_cases = valid_test_cases[: self.case_count]
            valid_test_cases = await self._arewrite_titles(valid_test_cases)
            return valid_test_cases
            
        except Exception as e:
            raise ValueError(f"无法解析生成的测试用例: {str(e)}\n原始响应: {result}")


    
    def generate(self, input_text: str, input_type: str = "requirement") -> List[Dict[str, Any]]:
        """同步方式生成测试用例"""
        self.logger.info(f"开始生成测试用例-同步方式,进入生成测试用例的TestCaseGeneratorAgent")
        # 确定输入类型描述
        input_type_desc = "需求描述" if input_type == "requirement" else "代码片段"
        
        # 获取知识上下文
        knowledge_context = self._get_knowledge_context(input_text)
        self.logger.info(f"获取到知识库上下文: \n{'='*50}\n{knowledge_context}\n{'='*50}")
        
        # 处理设计方法和测试类型
        case_design_methods = ",".join(self.case_design_methods) if self.case_design_methods else ""
        case_categories = ",".join(self.case_categories) if self.case_categories else ""
        
        # 使用新的 format_messages 方法获取消息列表
        messages = self.prompt.format_messages(
            requirements=input_text,
            case_design_methods=case_design_methods,
            case_categories=case_categories,
            case_count=self.case_count,
            knowledge_context=knowledge_context
        )
        self.logger.info(f"构建后大模型提示词+用户需求消息: \n{'='*50}\n{messages}\n{'='*50}")
        
        # 调用LLM服务
        try:
            response = self.llm_service.invoke(messages)
            result = response.content
            self.logger.info(f"LLM原始响应: \n{'='*50}\n{result}\n{'='*50}")
            
            # 尝试提取JSON部分
            json_str = self._extract_json_from_response(result)
            if not json_str:
                raise ValueError("无法从响应中提取有效的JSON数据")
                
            # 尝试解析JSON
            test_cases = json.loads(json_str)
            self.logger.info(f"_validate_test_cases处理前的用例个数: {len(test_cases)}")
            
            valid_test_cases = self._validate_test_cases(test_cases)
            valid_test_cases = self._rewrite_titles(valid_test_cases)
            return valid_test_cases
            
        except Exception as e:
            raise ValueError(f"无法解析生成的测试用例: {str(e)}\n原始响应: {result}")
    
    def _get_knowledge_context(self, input_text: str) -> str:
        """获取相关知识上下文（支持强制注入已选文档）。"""
        forced = getattr(self, "_forced_knowledge", None)
        if forced:
            return forced
        try:
            knowledge = self.knowledge_service.search_relevant_knowledge(input_text)
            if knowledge:
                return f"{knowledge}"
        except Exception as e:
            self.logger.warning(f"获取知识上下文失败: {str(e)}")
        return ""
    
    def _validate_test_cases(self, test_cases: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """验证并修复测试用例格式
        
        Args:
            test_cases: 原始测试用例列表
            
        Returns:
            验证并修复后的测试用例列表
        """  
        valid_test_cases = []
        required_fields = {"description", "test_steps", "expected_results"}
        
        for i, test_case in enumerate(test_cases):
            try:
                # 如果不是字典格式，跳过这个测试用例
                if not isinstance(test_case, dict):
                    self.logger.warning(f"测试用例 #{i+1} 不是字典格式，已跳过")
                    continue
                
                # 检查必要字段是否存在
                missing_fields = required_fields - set(test_case.keys())
                if missing_fields:
                    self.logger.warning(f"测试用例 #{i+1} 缺少必要字段: {missing_fields}，已跳过")
                    continue
                
                # 验证并修复字段格式
                # 1. description必须是字符串
                if not isinstance(test_case['description'], str):
                    self.logger.warning(f"测试用例 #{i+1} 的description不是字符串格式，已跳过")
                    continue
                
                # 2. test_steps必须是列表
                if not isinstance(test_case['test_steps'], list):
                    self.logger.warning(f"测试用例 #{i+1} 的test_steps格式无法修复，已跳过")
                    continue
                
                # 3. expected_results必须是列表
                if not isinstance(test_case['expected_results'], list):
                    self.logger.warning(f"测试用例 #{i+1} 的expected_results格式无法修复，已跳过")
                    continue
                
                # 确保所有字段都不为空
                if not test_case['description'].strip():
                    self.logger.warning(f"测试用例 #{i+1} 的description为空，已跳过")
                    continue
                
                if not test_case['test_steps']:
                    self.logger.warning(f"测试用例 #{i+1} 的test_steps为空，已跳过")
                    continue
                
                if not test_case['expected_results']:
                    self.logger.warning(f"测试用例 #{i+1} 的expected_results为空，已跳过")
                    continue

                test_case['description'] = self._normalize_case_title(test_case['description'])
                if not test_case['description']:
                    self.logger.warning(f"测试用例 #{i+1} 标题规范化后为空，已跳过")
                    continue

                # 解析/补齐 priority，并统一标题为 tc-px：在…得到…
                pri = self._resolve_priority(test_case, i, len(test_cases))
                test_case['priority'] = pri
                test_case['description'] = self._format_tc_title(test_case['description'], pri)

                # 前置条件（可选；缺省为「无」）
                pre = test_case.get('preconditions', test_case.get('precondition', '无'))
                if isinstance(pre, list):
                    pre = '；'.join(str(x).strip() for x in pre if str(x).strip()) or '无'
                else:
                    pre = str(pre or '').strip() or '无'
                test_case['preconditions'] = pre

                valid_test_cases.append(test_case)
                
            except Exception as e:
                self.logger.warning(f"处理测试用例 #{i+1} 时出错: {str(e)}，已跳过")
                continue
        
        if not valid_test_cases:
            raise ValueError("没有找到任何合法的测试用例")
        
        self.logger.info(f"共处理 {len(test_cases)} 个测试用例，"
                        f"其中 {len(valid_test_cases)} 个合法")
        
        return valid_test_cases

    def _normalize_case_title(self, description: str) -> str:
        """去掉设计方法字样与旧前缀，清理空泛前缀。"""
        title = (description or "").strip()
        # 先去掉已有 tc-pN 前缀，后面再统一加回
        title = re.sub(r"(?i)^tc-p\d+\s*[:：]\s*", "", title).strip()
        title = re.sub(
            r"[（(【\[][^）)】\]]*(等价类|边界值|判定表|因果图|正交|场景法|设计方法)[^）)】\]]*[）)】\]]",
            "",
            title,
        )
        for _ in range(4):
            nxt = _METHOD_NOISE.sub("", title).strip(" -—–|:：、，,/")
            if nxt == title:
                break
            title = nxt
        title = _GENERIC_PREFIX.sub("", title).strip(" -—–|:：、，,/")
        title = re.sub(r"\s+", "", title)
        title = self._heuristic_action_title(title)
        return title

    def _heuristic_action_title(self, title: str) -> str:
        """把常见的「因…时…显示」叙述句改成「页面+操作+结果」。"""
        if not title:
            return title
        action_verbs = (
            "打开", "进入", "点击", "输入", "选择", "切换", "查看", "下拉",
            "返回", "提交", "保存", "删除", "新增", "编辑", "勾选", "取消",
            "确认", "关闭", "长按", "滑动",
        )
        head = title[:18]
        if any(v in head for v in action_verbs) and not re.search(r"因.+?时(?!间).{0,10}(显示|展示)", title):
            return title

        # 建图任务因低电量…结束时详情页显示XXX
        m = re.match(
            r"^(?P<sub>.+?)因(?P<reason>.+?)时(?!间)(?P<page>.+?页)(?P<rest>显示|展示|支持点击|支持)(?P<tail>.+)$",
            title,
        )
        if m:
            return (
                f"{m.group('page')}打开因{m.group('reason')}的{m.group('sub')}"
                f"查看{m.group('tail')}"
            )

        # XX页显示YYY时(?!间)ZZZ
        m = re.match(r"^(?P<page>.+?页)(?:中)?(?:显示|展示)(?P<body>.+)$", title)
        if m:
            body = m.group("body")
            m2 = re.match(r"^(?P<cond>.+?)时(?!间)(?P<result>.+)$", body)
            if m2:
                return (
                    f"{m.group('page')}打开{m2.group('cond')}的任务"
                    f"查看{m2.group('result')}"
                )
            return f"{m.group('page')}查看{body}"

        # XX列表页中…任务显示…
        m = re.match(
            r"^(?P<page>.+?页)中(?P<body>.+?)(显示|展示)(?P<tail>.+)$",
            title,
        )
        if m:
            return f"{m.group('page')}查看{m.group('body')}{m.group('tail')}"

        return title

    _TITLE_REWRITE_SYSTEM = (
        "你是测试用例标题改写器。\n"
        "只输出 JSON 字符串数组，例如：[\"tc-p1：在登录页输入正确账号密码点击登录得到进入首页\"]。\n"
        "禁止输出对象，禁止使用 new/old/description 等字段名。\n"
        "每条必须是纯文本：tc-px：在地方操作得到结果（x=0/1/2/3，全角冒号：）。\n"
        "结合步骤与预期及给定 priority 改写；无设计方法名。\n"
        "数组长度与输入一致，顺序一致。"
    )

    def _extract_title_text(self, item: Any) -> str:
        """从改写结果中取出纯标题（兼容模型误返回对象）。"""
        if item is None:
            return ""
        if isinstance(item, str):
            text = item.strip()
        elif isinstance(item, dict):
            for key in ("new", "description", "title", "rewritten", "text", "value"):
                if item.get(key):
                    text = str(item.get(key)).strip()
                    break
            else:
                # 取第一个非空字符串值
                text = ""
                for v in item.values():
                    if isinstance(v, str) and v.strip():
                        text = v.strip()
                        break
                if not text:
                    text = str(item)
        else:
            text = str(item).strip()
        # 清理误拼进标题的 dict 字面量
        m = re.search(r"['\"]new['\"]\s*:\s*['\"](.+?)['\"]\s*\}?\s*$", text)
        if m:
            text = m.group(1)
        text = re.sub(r"^在?\s*\{['\"]new['\"]\s*:\s*['\"]?", "在", text)
        text = re.sub(r"['\"]\s*\}\s*$", "", text)
        return text.strip().strip("'\"")

    def _rewrite_titles(self, test_cases: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """强制二次改写为「在…得到…」模板（始终执行）。"""
        if not test_cases:
            return test_cases
        payload = [
            {
                "old": tc.get("description", ""),
                "priority": tc.get("priority", 1),
                "steps": tc.get("test_steps", []),
                "expected": tc.get("expected_results", []),
            }
            for tc in test_cases
        ]
        try:
            from langchain_core.messages import SystemMessage, HumanMessage

            messages = [
                SystemMessage(content=self._TITLE_REWRITE_SYSTEM),
                HumanMessage(
                    content="请按模板改写下列用例标题，只返回字符串数组：\n"
                    + json.dumps(payload, ensure_ascii=False)
                ),
            ]
            response = self.llm_service.invoke(messages)
            raw = (response.content or "").strip()
            if raw.startswith("```"):
                raw = re.sub(r"^```(?:json)?\s*", "", raw)
                raw = re.sub(r"\s*```$", "", raw)
            m = re.search(r"\[[\s\S]*\]", raw)
            rewritten = json.loads(m.group(0) if m else raw)
            if not isinstance(rewritten, list) or len(rewritten) != len(test_cases):
                self.logger.warning("标题改写返回长度不匹配，改用本地模板修补")
                for i, tc in enumerate(test_cases):
                    pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                    tc["description"] = self._format_tc_title(tc.get("description", ""), pri)
                return test_cases
            for i, tc in enumerate(test_cases):
                pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                new_t = self._format_tc_title(self._extract_title_text(rewritten[i]), pri)
                if new_t:
                    tc["description"] = new_t
                    tc["priority"] = pri
            return test_cases
        except Exception as e:
            self.logger.warning(f"标题二次改写失败，改用本地模板修补: {e}")
            for i, tc in enumerate(test_cases):
                pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                tc["description"] = self._format_tc_title(tc.get("description", ""), pri)
            return test_cases

    async def _arewrite_titles(self, test_cases: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        if not test_cases:
            return test_cases
        payload = [
            {
                "old": tc.get("description", ""),
                "priority": tc.get("priority", 1),
                "steps": tc.get("test_steps", []),
                "expected": tc.get("expected_results", []),
            }
            for tc in test_cases
        ]
        try:
            from langchain_core.messages import SystemMessage, HumanMessage

            messages = [
                SystemMessage(content=self._TITLE_REWRITE_SYSTEM),
                HumanMessage(
                    content="请按模板改写下列用例标题，只返回字符串数组：\n"
                    + json.dumps(payload, ensure_ascii=False)
                ),
            ]
            response = await self.llm_service.ainvoke(messages)
            raw = (response.content or "").strip()
            if raw.startswith("```"):
                raw = re.sub(r"^```(?:json)?\s*", "", raw)
                raw = re.sub(r"\s*```$", "", raw)
            m = re.search(r"\[[\s\S]*\]", raw)
            rewritten = json.loads(m.group(0) if m else raw)
            if not isinstance(rewritten, list) or len(rewritten) != len(test_cases):
                self.logger.warning("标题改写返回长度不匹配，改用本地模板修补")
                for i, tc in enumerate(test_cases):
                    pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                    tc["description"] = self._format_tc_title(tc.get("description", ""), pri)
                return test_cases
            for i, tc in enumerate(test_cases):
                pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                new_t = self._format_tc_title(self._extract_title_text(rewritten[i]), pri)
                if new_t:
                    tc["description"] = new_t
                    tc["priority"] = pri
            return test_cases
        except Exception as e:
            self.logger.warning(f"标题二次改写失败，改用本地模板修补: {e}")
            for i, tc in enumerate(test_cases):
                pri = int(tc.get("priority", self._default_priority(i, len(test_cases))))
                tc["description"] = self._format_tc_title(tc.get("description", ""), pri)
            return test_cases

    def _default_priority(self, index: int, total: int) -> int:
        cats = ",".join(self.case_categories or [])
        want_smoke = "冒烟" in cats
        if want_smoke and index == 0:
            return 0
        # 其余在 1~3 间轮转
        return 1 + (index % 3)

    def _resolve_priority(self, test_case: Dict[str, Any], index: int, total: int) -> int:
        raw = test_case.get("priority", None)
        title = str(test_case.get("description") or "")
        m = re.match(r"(?i)^tc-p(\d+)\s*[:：]", title)
        if m:
            try:
                return max(0, min(3, int(m.group(1))))
            except Exception:
                pass
        if raw is not None:
            try:
                return max(0, min(3, int(raw)))
            except Exception:
                pass
        return self._default_priority(index, total)

    def _format_tc_title(self, title: str, priority: int) -> str:
        """统一为 tc-px：在…得到…"""
        body = self._extract_title_text(title)
        body = re.sub(r"(?i)^tc-p\d+\s*[:：]\s*", "", body).strip()
        body = self._force_zai_dedao_title(body)
        p = max(0, min(3, int(priority)))
        if not body:
            return f"tc-p{p}：在相关页面完成操作得到符合预期的结果"
        return f"tc-p{p}：{body}"

    def _force_zai_dedao_title(self, title: str) -> str:
        """强制落成「在…得到…」；无法拆分时尽量补全关键字。"""
        title = self._normalize_case_title(title)
        if not title:
            return title
        # 已符合
        if re.match(r"^在.+得到.+$", title) and title.count("得到") == 1:
            return title
        # 去掉重复的在
        title = re.sub(r"^在+", "在", title)
        if not title.startswith("在"):
            title = "在" + title
        if "得到" not in title:
            # 优先：动作动词之后视为结果
            inserted = False
            for verb in ("查看", "点击", "打开", "选择", "输入", "进入"):
                idx = title.rfind(verb)
                if idx > 1 and idx + len(verb) < len(title):
                    title = title[: idx + len(verb)] + "得到" + title[idx + len(verb) :]
                    inserted = True
                    break
            if not inserted:
                m = re.search(
                    r"(置灰|展示|显示|跳转至|跳转|弹出|提示|出现|不可点击|可点击)",
                    title,
                )
                if m and m.start() > 4:
                    title = title[: m.start()] + "得到" + title[m.start() :]
                else:
                    title = title + "得到符合预期的页面结果"
        # 多个「得到」时只保留第一个
        parts = title.split("得到")
        if len(parts) > 2:
            title = parts[0] + "得到" + "".join(parts[1:])
        # 清理「得到得到」
        title = title.replace("得到得到", "得到")
        if not re.match(r"^在.+得到.+$", title):
            return title if title.startswith("在") else "在" + title
        return title

    def _looks_like_action_title(self, title: str) -> bool:
        if not title:
            return False
        if not re.match(r"(?i)^tc-p[0-3]：在.+得到.+$", title):
            return False
        if title.count("得到") != 1:
            return False
        banned = ("等价类", "边界值", "判定表", "因果图", "正交", "场景法", "验证是否", "测试是否")
        return not any(b in title for b in banned)
            
    def _extract_json_from_response(self, response: str) -> str:
        """从响应中提取JSON部分并进行基础修复
        
        Args:
            response: 原始响应字符串
            
        Returns:
            修复后的JSON字符串
        """
        # 使用正则表达式提取JSON字符串
        result = ""
        right_format_pattern = r'^\[([\s\S]*)\]$'
        match = re.search(right_format_pattern, response)
        if match:
            result = match.group(0)  # 使用group(0)返回完整匹配，包含方括号
        else:
            #从字符串中找到最后一个出现},的位置，然后取},前面的内容,并补全]和json结束标记```
            last_comma_index = response.rfind('},')
            if last_comma_index != -1:
                result = response[:last_comma_index+1] + ']'
        # self.logger.info(f"_extract_json_from_response函数处理结果: \n{'='*50}\n{result}\n{'='*50}")    
        return result
        

            
