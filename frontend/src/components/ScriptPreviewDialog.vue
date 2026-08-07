<template>
  <el-dialog
    :model-value="modelValue"
    width="1100px"
    top="4vh"
    class="script-preview-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <!-- 模块1：顶部标题栏 -->
    <template #header>
      <div class="spd-header">
        <h3 class="spd-title">生成的执行脚本</h3>
        <div class="spd-actions">
          <el-button size="small" type="primary" plain @click="copyAll">
            📋 复制全部脚本
          </el-button>
          <el-button size="small" plain @click="downloadPy">
            💾 下载 .py 脚本文件
          </el-button>
          <el-button size="small" text @click="close">
            ✖ 关闭弹窗
          </el-button>
        </div>
      </div>
    </template>

    <div class="spd-layout">
      <div class="spd-body" v-if="code">
        <!-- 模块2：左侧目录 -->
        <aside class="spd-toc">
          <div class="spd-toc-title">脚本目录</div>
          <el-scrollbar class="spd-toc-scroll">
            <div
              v-for="sec in outline"
              :key="sec.id"
              class="toc-item"
              :class="{ active: activeSectionId === sec.id }"
              @click="jumpToSection(sec)"
            >
              <span class="toc-bar">▌</span>
              <div class="toc-text">
                <div class="toc-label">{{ sec.label }}</div>
                <div v-if="sec.children?.length" class="toc-children">
                  <template v-for="ch in sec.children" :key="ch.id">
                    <div
                      class="toc-child"
                      :class="{ active: activeSectionId === ch.id }"
                      @click.stop="jumpToSection(ch)"
                    >
                      └─ {{ ch.label }}
                    </div>
                    <div
                      v-for="sub in (ch.children || [])"
                      :key="sub.id"
                      class="toc-child toc-child-nested"
                      :class="{ active: activeSectionId === sub.id }"
                      @click.stop="jumpToSection(sub)"
                    >
                      └─ {{ sub.label }}
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </aside>

        <!-- 右侧：编辑器 + 释义 -->
        <section class="spd-main">
          <!-- 模块3：代码预览 -->
          <div
            ref="editorRef"
            class="spd-editor"
            @contextmenu.prevent="onContextMenu"
          >
            <div
              v-for="line in renderedLines"
              :key="line.no"
              :id="'spd-line-' + line.no"
              class="code-line"
              :class="{
                'is-step': line.isStep,
                'is-highlight': line.no >= highlightRange[0] && line.no <= highlightRange[1]
              }"
              @click="onLineClick(line.no)"
            >
              <span class="ln">{{ line.no }}</span>
              <span class="code" v-html="line.html" />
            </div>
          </div>

          <!-- 模块4：释义面板 -->
          <div class="spd-explain">
            <div class="explain-title">{{ explain.title }}</div>
            <div class="explain-body" v-html="explain.html" />
          </div>
        </section>
      </div>
      <el-empty v-else description="暂无脚本内容" :image-size="72" />

      <!-- 模块5：底部折叠说明 -->
      <el-collapse v-model="helpOpen" class="spd-help">
        <el-collapse-item name="help">
          <template #title>
            <span>📖 脚本运行机制说明</span>
          </template>
          <div class="help-content">
            <h4>脚本执行整体流程</h4>
            <p>① 加载依赖库 → ② 读取平台下发环境参数 → ③ 初始化变量替换工具 → ④ 依次逐条执行录制步骤 → ⑤ 步骤失败自动匹配断言策略（白名单跳过 / 黑名单终止任务）</p>
            <h4>变量模板规则</h4>
            <p>脚本中所有 <code v-pre>{{变量名称}}</code> 在任务启动时自动被全局变量、造数模板变量替换。</p>
            <h4>跨系统控件兼容说明</h4>
            <p>定位规则携带 <code>@Android||</code> / <code>@iOS||</code> 前缀，执行引擎自动根据当前设备系统过滤可用定位策略。</p>
            <h4>步骤埋点作用</h4>
            <p><code>emit_step_begin</code> / <code>emit_step_end</code> 用于记录步骤耗时、成功失败状态，最终生成测试报告。</p>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="spd-ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <button type="button" @click="copySelection">复制选中代码</button>
      </div>
    </Teleport>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  code: { type: String, default: '' },
  fileName: { type: String, default: 'atp_case_script.py' }
})
const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const activeSectionId = ref('imports')
const highlightRange = ref([0, 0])
const helpOpen = ref([])
const ctxMenu = ref({ visible: false, x: 0, y: 0 })

const ENV_TIPS = {
  ATP_DEVICE_SERIAL: '测试设备序列号',
  ATP_APP_PACKAGE: '被测 App 包名',
  ATP_HUMAN_DELAY: '是否开启拟人化延迟（1=开启）',
  ATP_VARS_JSON: '全局变量 JSON 字符串，用于 {{变量名}} 模板替换',
  ATP_CALIBRATION_JSON: '控件坐标校准参数（偏移/缩放）',
  ATP_ASSERT_POLICY_JSON: '断言黑白名单策略',
  ATP_PLATFORM: '当前执行平台 android / ios'
}

const PY_KEYWORDS = new Set([
  'import', 'from', 'as', 'def', 'class', 'return', 'if', 'elif', 'else', 'for', 'while',
  'try', 'except', 'finally', 'with', 'raise', 'pass', 'break', 'continue', 'in', 'not',
  'and', 'or', 'is', 'None', 'True', 'False', 'lambda', 'yield', 'global', 'nonlocal', 'assert', 'print'
])

function close() {
  emit('update:modelValue', false)
}

function copyAll() {
  if (!props.code) return
  navigator.clipboard.writeText(props.code).then(() => {
    ElMessage.success('已复制全部脚本')
  }).catch(() => ElMessage.error('复制失败'))
}

function downloadPy() {
  if (!props.code) return
  const blob = new Blob([props.code], { type: 'text/x-python;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = props.fileName || 'atp_case_script.py'
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已开始下载 .py 文件')
}

function copySelection() {
  const text = window.getSelection()?.toString() || ''
  ctxMenu.value.visible = false
  if (!text.trim()) {
    ElMessage.warning('请先选中要复制的代码')
    return
  }
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制选中代码'))
    .catch(() => ElMessage.error('复制失败'))
}

function onContextMenu(e) {
  const text = window.getSelection()?.toString() || ''
  if (!text.trim()) return
  ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY }
}

function hideCtx() {
  ctxMenu.value.visible = false
}

onMounted(() => {
  document.addEventListener('click', hideCtx)
})
onUnmounted(() => {
  document.removeEventListener('click', hideCtx)
})

const outline = computed(() => parseOutline(props.code || ''))

const lineSectionMap = computed(() => {
  const map = new Map()
  const walk = (nodes) => {
    for (const n of nodes) {
      for (let i = n.start; i <= n.end; i++) map.set(i, n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(outline.value)
  return map
})

const explain = computed(() => buildExplain(activeSectionId.value, outline.value, props.code || ''))

const renderedLines = computed(() => {
  const lines = (props.code || '').split('\n')
  const stepRanges = []
  for (const sec of outline.value) {
    if (sec.id === 'steps' && sec.children) {
      for (const ch of sec.children) stepRanges.push([ch.start, ch.end])
    }
  }
  const isStepLine = (no) => stepRanges.some(([a, b]) => no >= a && no <= b)
  return lines.map((raw, idx) => {
    const no = idx + 1
    return {
      no,
      raw,
      isStep: isStepLine(no),
      html: highlightPythonLine(raw)
    }
  })
})

function parseOutline(code) {
  if (!code.trim()) return []
  const lines = code.split('\n')
  const n = lines.length

  let firstEnv = -1
  let firstDef = -1
  let generated = -1
  let bootstrap = -1
  const stepStarts = []

  lines.forEach((line, i) => {
    const t = line.trim()
    if (firstEnv < 0 && /^(serial|app_package|HUMAN_DELAY|_VARS|_CAL|_ASSERT_POLICY)\s*=/.test(t)) {
      firstEnv = i
    }
    if (firstDef < 0 && /^def\s+\w+/.test(t)) firstDef = i
    if (generated < 0 && t.includes('Generated from TestFlow')) generated = i
    if (bootstrap < 0 && t.startsWith('# === Bootstrap')) bootstrap = i
    const sm = t.match(/^# Step\s+(\d+)\s*:\s*(.*)$/i)
    if (sm) {
      stepStarts.push({
        index: Number(sm[1]),
        title: sm[2].trim() || `步骤${sm[1]}`,
        line: i
      })
    }
  })

  if (firstEnv < 0) firstEnv = Math.min(firstDef > 0 ? firstDef : n, n)
  if (firstDef < 0) firstDef = generated > 0 ? generated : n
  const toolsEnd = generated >= 0 ? generated - 1 : (bootstrap >= 0 ? bootstrap - 1 : n - 1)
  const stepsRootStart = bootstrap >= 0 ? bootstrap : (generated >= 0 ? generated : firstDef)

  const sections = [
    {
      id: 'imports',
      label: '【1. 基础依赖导入】',
      level: 0,
      start: 1,
      end: Math.max(1, firstEnv),
      kind: 'imports',
      children: []
    },
    {
      id: 'env',
      label: '【2. 运行环境初始化】',
      level: 0,
      start: firstEnv + 1,
      end: Math.max(firstEnv + 1, firstDef),
      kind: 'env',
      children: [
        {
          id: 'env-device',
          label: '设备序列号、应用包名',
          level: 1,
          start: firstEnv + 1,
          end: Math.max(firstEnv + 1, Math.min(firstEnv + 3, firstDef)),
          kind: 'env-device'
        },
        {
          id: 'env-vars',
          label: '全局变量、校准参数、断言策略加载',
          level: 1,
          start: Math.min(firstEnv + 4, firstDef) + 1,
          end: Math.max(firstEnv + 1, firstDef),
          kind: 'env-vars'
        }
      ]
    },
    {
      id: 'tools',
      label: '【3. 内置工具函数】',
      level: 0,
      start: firstDef + 1,
      end: Math.max(firstDef + 1, toolsEnd + 1),
      kind: 'tools',
      children: buildToolChildren(lines, firstDef, toolsEnd)
    },
    {
      id: 'steps',
      label: '【4. 录制自动化步骤（业务流程）】',
      level: 0,
      start: stepsRootStart + 1,
      end: n,
      kind: 'steps',
      children: []
    }
  ]

  sections.forEach((s) => {
    if (s.end < s.start) s.end = s.start
    s.children?.forEach((c) => {
      if (c.end < c.start) c.end = c.start
      if (c.start < s.start) c.start = s.start
      if (c.end > s.end) c.end = s.end
    })
  })

  const stepChildren = []
  for (let i = 0; i < stepStarts.length; i++) {
    const cur = stepStarts[i]
    const next = stepStarts[i + 1]
    const endLine = next ? next.line : n
    const label = enrichStepLabel(lines, cur.line, endLine, cur.index, cur.title)
    stepChildren.push({
      id: `step-${cur.index}`,
      label: `步骤${cur.index}：${label}`,
      level: 1,
      start: cur.line + 1,
      end: endLine,
      kind: 'step',
      stepIndex: cur.index,
      stepType: cur.title.split(/\s+/)[0] || cur.title
    })
  }
  sections[3].children = stepChildren
  return sections
}

function buildToolChildren(lines, firstDef, toolsEnd) {
  const children = []
  for (let i = firstDef; i <= toolsEnd && i < lines.length; i++) {
    const m = lines[i].match(/^def\s+(\w+)\s*\(/)
    if (!m) continue
    let end = toolsEnd
    for (let j = i + 1; j <= toolsEnd; j++) {
      if (/^def\s+\w+\s*\(/.test(lines[j])) {
        end = j - 1
        break
      }
    }
    const name = m[1]
    let label = `工具函数 ${name}`
    if (name === '_subst_tpl') label = '变量模板替换函数 _subst_tpl'
    else if (name === 'set_relative_time') label = '相对时间设置 set_relative_time'
    else if (name.startsWith('_guard') || name.includes('assert')) label = `断言策略函数 ${name}`
    children.push({
      id: `fn-${name}`,
      label,
      level: 1,
      start: i + 1,
      end: end + 1,
      kind: 'fn',
      fnName: name
    })
  }

  const subst = children.find(c => c.fnName === '_subst_tpl')
  let emitLine = lines.findIndex(l => /emit_step_begin|step_marker_helper/.test(l))
  if (emitLine < 0) emitLine = 0

  const result = []
  if (subst) {
    result.push(subst)
  }
  result.push({
    id: 'fn-emit',
    label: '步骤埋点上报函数',
    level: 1,
    start: emitLine + 1,
    end: emitLine + 1,
    kind: 'emit'
  })

  const prefer = ['set_var', 'set_relative_time', 'tap_recorded', '_guard_assert', 'human_pause']
  const preferred = children.filter(c => prefer.includes(c.fnName))
  const rest = children.filter(c => c.fnName !== '_subst_tpl' && !prefer.includes(c.fnName))
  result.push(...preferred)
  if (rest.length) {
    result.push({
      id: 'fn-others',
      label: `其他内置函数（${rest.length}）`,
      level: 1,
      start: rest[0].start,
      end: rest[rest.length - 1].end,
      kind: 'fn-group',
      children: rest
    })
  }
  return result.length ? result : children.slice(0, 12)
}

function enrichStepLabel(lines, startIdx, endIdx, index, title) {
  const slice = lines.slice(startIdx, endIdx).join('\n')
  const m = slice.match(/emit_step_begin\([^,]+,\s*[^,]+,\s*["']([^"']+)["']/)
  if (m?.[1]) {
    const name = m[1]
    if (/click|tap/i.test(title)) return `点击【${name}】控件`
    if (/input/i.test(title)) return `输入文本【${name}】`
    if (/assert_toast/i.test(title)) return '断言Toast提示'
    if (/assert/i.test(title)) return `断言【${name}】`
    return `${title}【${name}】`
  }
  if (/assert_toast/i.test(title)) return '断言Toast提示'
  if (/click/i.test(title)) return '点击控件'
  if (/input/i.test(title)) return '输入文本'
  if (/set_relative_time/i.test(title)) return '设置相对时间'
  return title || `步骤${index}`
}

function highlightPythonLine(line) {
  if (!line) return '&nbsp;'
  const commentIdx = findCommentIndex(line)
  let codePart = commentIdx >= 0 ? line.slice(0, commentIdx) : line
  let commentPart = commentIdx >= 0 ? line.slice(commentIdx) : ''

  let html = escapeHtml(codePart)
  html = html.replace(/(['"])(?:\\.|(?!\1).)*\1/g, (m) => {
    let inner = m
    for (const [k, tip] of Object.entries(ENV_TIPS)) {
      if (inner.includes(k)) {
        inner = inner.split(k).join(`<span class="tok-env" title="${escapeAttr(tip)}">${k}</span>`)
      }
    }
    return `<span class="tok-str">${inner}</span>`
  })
  html = html.replace(/\b([A-Za-z_][A-Za-z0-9_]*)\b/g, (m, w, offset, full) => {
    if (isInsideTag(full, offset)) return m
    if (PY_KEYWORDS.has(w)) return `<span class="tok-kw">${w}</span>`
    if (w.startsWith('ATP_') && ENV_TIPS[w]) {
      return `<span class="tok-env" title="${escapeAttr(ENV_TIPS[w])}">${w}</span>`
    }
    return m
  })
  html = html.replace(/\b(\d+(?:\.\d+)?)\b/g, (m, _d, offset, full) => {
    if (isInsideTag(full, offset)) return m
    return `<span class="tok-num">${m}</span>`
  })

  if (commentPart) {
    html += `<span class="tok-comment">${escapeHtml(commentPart)}</span>`
  }
  return html || '&nbsp;'
}

function findCommentIndex(line) {
  let inStr = null
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inStr) {
      if (c === '\\') { i++; continue }
      if (c === inStr) inStr = null
      continue
    }
    if (c === '"' || c === "'") { inStr = c; continue }
    if (c === '#') return i
  }
  return -1
}

function isInsideTag(html, offset) {
  const before = html.slice(0, offset)
  const open = (before.match(/</g) || []).length
  const close = (before.match(/>/g) || []).length
  return open > close
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, '&quot;')
}

function findSectionById(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const hit = findSectionById(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

function jumpToSection(sec) {
  if (!sec) return
  activeSectionId.value = sec.id
  highlightRange.value = [sec.start, sec.end]
  nextTick(() => {
    const el = document.getElementById(`spd-line-${sec.start}`)
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function onLineClick(no) {
  const sec = lineSectionMap.value.get(no)
  if (sec) {
    activeSectionId.value = sec.id
    highlightRange.value = [sec.start, sec.end]
  }
}

function buildExplain(sectionId, nodes, code) {
  const sec = findSectionById(nodes, sectionId) || nodes[0]
  if (!sec) {
    return { title: '【说明】', html: '<p>请选择左侧目录或代码区域查看释义。</p>' }
  }
  if (sec.kind === 'imports') {
    return {
      title: '【模块说明】基础依赖导入',
      html: '<p>导入操作系统、正则、子进程、时间等标准库，以及平台控件定位（<code>atp_controls</code>）与步骤埋点（<code>emit_step_begin/end</code>）能力，为后续自动化执行提供基础依赖。</p>'
    }
  }
  if (sec.kind === 'env' || sec.kind === 'env-device' || sec.kind === 'env-vars') {
    return {
      title: '【模块说明】运行环境初始化',
      html: `
        <p>从执行环境读取平台传入参数：设备序列号、被测 App 包名、全局变量集合、控件校准参数、断言黑白名单策略，为后续自动化执行提供运行上下文。</p>
        <p><strong>环境变量说明：</strong></p>
        <ul>
          <li><code>ATP_DEVICE_SERIAL</code>：测试设备序列号</li>
          <li><code>ATP_APP_PACKAGE</code>：被测应用包名</li>
          <li><code>ATP_VARS_JSON</code>：全局变量 JSON 字符串，用于模板变量替换</li>
          <li><code>ATP_CALIBRATION_JSON</code>：坐标校准（偏移/缩放）</li>
          <li><code>ATP_ASSERT_POLICY_JSON</code>：断言白名单/黑名单策略</li>
        </ul>`
    }
  }
  if (sec.kind === 'emit') {
    return {
      title: '【函数说明】步骤埋点上报',
      html: '<p><code>emit_step_begin</code> / <code>emit_step_end</code> 记录步骤起止、耗时与成功/失败状态，最终汇总到测试报告。</p>'
    }
  }
  if (sec.kind === 'fn' && sec.fnName === '_subst_tpl') {
    return {
      title: '【函数说明】_subst_tpl(s)',
      html: '<p>变量模板替换方法：自动扫描脚本内模板占位符，从全局变量集合中匹配并替换真实数值，实现参数化执行。</p>'
    }
  }
  if (sec.kind === 'fn' || sec.kind === 'fn-group' || sec.kind === 'tools') {
    const name = sec.fnName || ''
    const map = {
      set_var: '将运行时变量写入 _VARS，并打印 ATP_VAR_OUT 供平台收集。',
      set_relative_time: '将时间选择器滚到「当前时间 + N 分钟」，并写入 TIME_HM 等变量。',
      tap_recorded: '按定位链/坐标点击录制控件，失败时回退文本或 id 匹配。',
      human_pause: '步骤间拟人化等待，可受 WAIT_MULTIPLIER 与 HUMAN_DELAY 控制。',
      _guard_assert: '按断言策略执行：黑名单跳过、白名单软断言、其余失败抛错。'
    }
    return {
      title: `【函数说明】${name || '内置工具函数'}`,
      html: `<p>${map[name] || '平台内置辅助函数，供业务流程步骤调用；包含定位点击、断言守卫、变量读写等能力。'}</p>
             <p>步骤埋点函数 <code>emit_step_begin</code> / <code>emit_step_end</code> 用于上报步骤起止，支撑报告统计。</p>`
    }
  }
  if (sec.kind === 'step') {
    const block = code.split('\n').slice(sec.start - 1, sec.end).join('\n')
    const type = sec.stepType || 'unknown'
    const nameMatch = block.match(/emit_step_begin\([^,]+,\s*[^,]+,\s*["']([^"']+)["']/)
    const target = nameMatch?.[1] || ''
    const locator = extractLocatorHint(block)
    const typeLabel = {
      click: '控件点击',
      tap_xy: '坐标点击',
      input: '文本输入',
      swipe: '滑动',
      assert_text: '文本断言',
      assert_toast: 'Toast 断言',
      assert_exists: '控件存在断言',
      wait: '等待',
      launch: '启动应用',
      set_relative_time: '设置相对时间',
      tap_ocr: 'OCR 点击',
      dismiss_popup: '关闭弹窗',
      check_anomaly: '页面异常检测'
    }[type] || type
    return {
      title: '【步骤释义】',
      html: `
        <p><strong>操作类型：</strong>${escapeHtml(typeLabel)}</p>
        <p><strong>目标控件：</strong>${escapeHtml(target || '（见代码）')}</p>
        <p><strong>定位规则：</strong><code>${escapeHtml(locator || '坐标 / 定位链，详见代码')}</code></p>
        <p><strong>执行行为：</strong>按脚本逻辑执行该业务动作；执行前后通过 <code>emit_step_begin/end</code> 上报埋点，用于测试报告统计。</p>
        <pre class="explain-code">${escapeHtml(block.trim().slice(0, 600))}</pre>`
    }
  }
  if (sec.kind === 'steps') {
    return {
      title: '【模块说明】录制自动化步骤',
      html: '<p>按可视化用例顺序生成的业务流程代码。每一步包含埋点上报与失败重试/断言策略处理。点击子步骤可查看单步释义。</p>'
    }
  }
  return { title: '【说明】', html: `<p>${escapeHtml(sec.label)}</p>` }
}

function extractLocatorHint(block) {
  const m1 = block.match(/tap_recorded\("(\{.*?\})"/)
  if (m1) {
    try {
      const locs = JSON.parse(m1[1].replace(/\\"/g, '"'))
      if (locs.content_desc) return `@Android||content-desc=${locs.content_desc}`
      if (locs.id || locs.resource_id) return `@Android||id=${locs.id || locs.resource_id}`
      if (locs.text) return `@Android||text=${locs.text}`
    } catch { /* ignore */ }
  }
  const xy = block.match(/tap_xy\((\d+)\s*,\s*(\d+)\)/)
  if (xy) return `坐标 (${xy[1]}, ${xy[2]})`
  return ''
}

watch(() => props.modelValue, (v) => {
  if (v) {
    activeSectionId.value = outline.value[0]?.id || 'imports'
    const first = outline.value[0]
    if (first) highlightRange.value = [first.start, first.end]
    nextTick(() => {
      document.getElementById('spd-line-1')?.scrollIntoView({ block: 'start' })
    })
  }
})

watch(() => props.code, () => {
  if (props.modelValue && outline.value[0]) {
    activeSectionId.value = outline.value[0].id
    highlightRange.value = [outline.value[0].start, outline.value[0].end]
  }
})
</script>

<style scoped>
.spd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding-right: 4px;
}
.spd-title {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}
.spd-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.spd-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 180px);
  max-height: 720px;
  min-height: 420px;
  overflow: hidden;
}
.spd-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 12px;
  overflow: hidden;
}
.spd-toc {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.spd-toc-title {
  flex-shrink: 0;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.spd-toc-scroll {
  flex: 1;
  min-height: 0;
  height: 100%;
  padding: 6px 0;
}
.spd-toc-scroll :deep(.el-scrollbar__wrap) {
  max-height: 100%;
}
.toc-item {
  display: flex;
  gap: 6px;
  padding: 8px 10px;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-regular);
}
.toc-item:hover,
.toc-item.active {
  background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
  color: var(--el-color-primary);
}
.toc-bar {
  color: var(--el-color-primary);
  flex-shrink: 0;
}
.toc-label {
  font-weight: 600;
}
.toc-children {
  margin-top: 4px;
}
.toc-child {
  padding: 3px 0 3px 2px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.toc-child-nested {
  padding-left: 14px;
  font-size: 11px;
}
.toc-child:hover,
.toc-child.active {
  color: var(--el-color-primary);
}
.spd-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.spd-editor {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-radius: 8px;
  background: #0d1117;
  color: #e6edf3;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  border: 1px solid #30363d;
  user-select: text;
}
.code-line {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 0;
  padding: 0 10px 0 0;
}
.code-line:hover {
  background: rgba(110, 118, 129, 0.12);
}
.code-line.is-step {
  background: rgba(56, 139, 253, 0.08);
}
.code-line.is-highlight {
  background: rgba(210, 153, 34, 0.18);
  box-shadow: inset 3px 0 0 #d29922;
}
.ln {
  text-align: right;
  padding: 0 10px 0 8px;
  color: #6e7681;
  user-select: none;
}
.code {
  white-space: pre;
  overflow-wrap: normal;
}
.spd-editor :deep(.tok-kw) { color: #ff7b72; }
.spd-editor :deep(.tok-str) { color: #a5d6ff; }
.spd-editor :deep(.tok-num) { color: #79c0ff; }
.spd-editor :deep(.tok-comment) { color: #8b949e; font-style: italic; }
.spd-editor :deep(.tok-env) {
  color: #ffa657;
  border-bottom: 1px dashed rgba(255, 166, 87, 0.55);
  cursor: help;
}
.spd-explain {
  flex-shrink: 0;
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-color-primary) 8%, #eef6ff);
  border: 1px solid color-mix(in srgb, var(--el-color-primary) 22%, #cfe3ff);
  padding: 10px 12px;
  max-height: 160px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
}
.explain-title {
  font-weight: 650;
  margin-bottom: 6px;
}
.explain-body :deep(ul) {
  margin: 6px 0 0;
  padding-left: 18px;
}
.explain-body :deep(p) {
  margin: 0 0 6px;
}
.explain-body :deep(.explain-code) {
  margin: 8px 0 0;
  padding: 8px;
  background: rgba(15, 23, 42, 0.06);
  border-radius: 6px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}
.spd-help {
  flex-shrink: 0;
  margin: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  position: relative;
  z-index: 2;
  overflow: hidden;
}
.spd-help :deep(.el-collapse-item__header) {
  height: 40px;
  line-height: 40px;
  padding: 0 12px;
  background: var(--el-bg-color);
}
.spd-help :deep(.el-collapse-item__wrap) {
  background: var(--el-bg-color);
  max-height: 180px;
  overflow: auto;
}
.spd-help :deep(.el-collapse-item__content) {
  padding: 0 12px 12px;
}
.help-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
.help-content h4 {
  margin: 10px 0 4px;
  font-size: 13px;
}
.help-content p {
  margin: 0 0 6px;
}
.spd-ctx-menu {
  position: fixed;
  z-index: 4000;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  padding: 4px;
  min-width: 140px;
}
.spd-ctx-menu button {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
}
.spd-ctx-menu button:hover {
  background: var(--el-fill-color-light);
}
@media (max-width: 900px) {
  .spd-layout {
    height: auto;
    max-height: none;
    overflow: visible;
  }
  .spd-body {
    grid-template-columns: 1fr;
    overflow: visible;
  }
  .spd-toc {
    max-height: 200px;
  }
  .spd-editor {
    min-height: 240px;
    max-height: 360px;
  }
}
</style>

<style>
.script-preview-dialog .el-dialog__header {
  margin-right: 0;
  padding-bottom: 8px;
}
.script-preview-dialog .el-dialog__body {
  padding-top: 8px;
  overflow: hidden;
}
</style>
