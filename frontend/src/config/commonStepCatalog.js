/**
 * 公共步骤轻量化目录：6 大类 + 动作 → 可执行 steps 映射 + 模板库
 * meta 写入 steps_content，执行侧只消费 steps 数组。
 */

export const UI_MODE_KEY = 'atp.commonStep.uiMode'
export const GUIDE_SEEN_KEY = 'atp.commonStep.guideSeen'

export const CATEGORIES = [
  { key: 'atomic', label: '基础原子步骤', hint: '点击、输入、等待等高频操作' },
  { key: 'flow', label: '逻辑流程步骤', hint: '分支、循环、异常处理' },
  { key: 'device', label: '设备硬件步骤', hint: '配网、机器人指令、系统操作' },
  { key: 'assert', label: '断言校验步骤', hint: '元素/文本/设备状态校验' },
  { key: 'data', label: '数据与参数步骤', hint: '变量、读写数据' },
  { key: 'advanced', label: '高级自定义步骤', hint: '脚本、接口、第三方（默认折叠）', foldDefault: true }
]

export const PLATFORMS = [
  { key: 'android', label: '安卓' },
  { key: 'ios', label: 'iOS' },
  { key: 'any', label: '通用' }
]

/** 机器人指令预设 → robot_send_command.command */
export const ROBOT_COMMANDS = [
  { value: 'power_on', label: '机器人开机' },
  { value: 'dock', label: '回充' },
  { value: 'start_clean', label: '开始清扫' },
  { value: 'stop_clean', label: '停止清扫' },
  { value: 'clean_mode_auto', label: '清扫模式-自动' },
  { value: 'clean_mode_spot', label: '清扫模式-局部' },
  { value: 'suction_low', label: '吸力-低' },
  { value: 'suction_mid', label: '吸力-中' },
  { value: 'suction_high', label: '吸力-高' },
  { value: 'schedule_sync', label: '定时任务下发' },
  { value: 'bind_device', label: '设备配网/绑定' },
  { value: 'unbind_device', label: '设备解绑' },
  { value: 'bt_connect', label: '蓝牙连接' },
  { value: 'bt_disconnect', label: '蓝牙断开' },
  { value: 'wifi_switch', label: 'WiFi 切换' }
]

function baseMeta(form) {
  return {
    category: form.category || 'atomic',
    platform: form.platform || 'any',
    action_key: form.action_key || '',
    ui_mode: form.ui_mode || 'simple',
    template_id: form.template_id || ''
  }
}

function locatorFields(form) {
  const out = {}
  if (form.locator_type) out.locator_type = form.locator_type
  if (form.locator_value) out.locator_value = form.locator_value
  if (form.element_name) out.element_name = form.element_name
  if (form.display_name) out.display_name = form.display_name
  return out
}

/**
 * @typedef {{ key: string, label: string, category: string, hazardous?: boolean, fields: string[], defaults?: object, toSteps: Function, inferFromStep?: Function }} ActionDef
 */

/** @type {ActionDef[]} */
export const ACTIONS = [
  // —— 基础原子 ——
  {
    key: 'click',
    label: '点击控件',
    category: 'atomic',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 3, locator_type: 'id', locator_value: '', element_name: '' },
    toSteps: (f) => [{
      type: 'click', enabled: true, timeout: f.timeout ?? 3, ...locatorFields(f),
      element_name: f.element_name || '点击目标'
    }]
  },
  {
    key: 'long_press',
    label: '长按',
    category: 'atomic',
    fields: ['locator', 'timeout', 'duration_ms'],
    defaults: { timeout: 3, duration_ms: 800, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'long_press', enabled: true, timeout: f.timeout ?? 3,
      duration_ms: f.duration_ms ?? 800, ...locatorFields(f)
    }]
  },
  {
    key: 'swipe',
    label: '滑动',
    category: 'atomic',
    fields: ['swipe_coords', 'timeout'],
    defaults: { timeout: 3, x1: 500, y1: 1400, x2: 500, y2: 400 },
    toSteps: (f) => [{
      type: 'swipe', enabled: true, timeout: f.timeout ?? 3,
      x1: Number(f.x1) || 500, y1: Number(f.y1) || 1400,
      x2: Number(f.x2) || 500, y2: Number(f.y2) || 400
    }]
  },
  {
    key: 'input',
    label: '输入文本',
    category: 'atomic',
    fields: ['locator', 'text', 'timeout'],
    defaults: { timeout: 3, text: '', locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'input', enabled: true, timeout: f.timeout ?? 3,
      text: f.text || '', ...locatorFields(f)
    }]
  },
  {
    key: 'clear_input',
    label: '清除输入',
    category: 'atomic',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 3, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'clear_input', enabled: true, timeout: f.timeout ?? 3, ...locatorFields(f)
    }]
  },
  {
    key: 'wait',
    label: '固定延时',
    category: 'atomic',
    fields: ['seconds'],
    defaults: { seconds: 2 },
    toSteps: (f) => [{ type: 'wait', enabled: true, seconds: Number(f.seconds) || 2 }]
  },
  {
    key: 'wait_appear',
    label: '等待控件出现',
    category: 'atomic',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 10, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'wait', enabled: true, wait_mode: 'appear', timeout: f.timeout ?? 10, ...locatorFields(f)
    }]
  },
  {
    key: 'wait_gone',
    label: '等待控件消失',
    category: 'atomic',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 10, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'wait', enabled: true, wait_mode: 'wait_gone', timeout: f.timeout ?? 10, ...locatorFields(f)
    }]
  },
  {
    key: 'back',
    label: '返回上一页',
    category: 'atomic',
    fields: [],
    defaults: {},
    toSteps: () => [{ type: 'press_key', enabled: true, key: 'back' }]
  },
  {
    key: 'home',
    label: '回到首页',
    category: 'atomic',
    fields: [],
    defaults: {},
    toSteps: () => [{ type: 'press_key', enabled: true, key: 'home' }]
  },
  {
    key: 'dismiss_popup',
    label: '关闭弹窗',
    category: 'atomic',
    fields: ['timeout'],
    defaults: { timeout: 3 },
    toSteps: (f) => [{ type: 'dismiss_popup', enabled: true, optional: true, timeout: f.timeout ?? 3 }]
  },

  // —— 逻辑流程 ——
  {
    key: 'branch_if',
    label: 'if 条件分支',
    category: 'flow',
    fields: ['condition', 'branch_true', 'branch_false'],
    defaults: { condition: '控件存在', branch_true: '成立分支', branch_false: '否则分支' },
    toSteps: (f) => [{
      type: 'branch', enabled: true,
      condition: f.condition || '控件存在',
      branch_true: f.branch_true || '成立分支',
      branch_false: f.branch_false || '否则分支'
    }]
  },
  {
    key: 'loop_for',
    label: 'for 循环',
    category: 'flow',
    fields: ['loop_count', 'loop_body'],
    defaults: { loop_count: 3, loop_body: '循环体' },
    toSteps: (f) => [{
      type: 'loop', enabled: true,
      loop_count: Number(f.loop_count) || 3,
      loop_body: f.loop_body || '循环体'
    }]
  },
  {
    key: 'loop_while',
    label: 'while 循环',
    category: 'flow',
    fields: ['condition', 'loop_count', 'loop_body'],
    defaults: { condition: '条件成立', loop_count: 10, loop_body: '循环体' },
    toSteps: (f) => [{
      type: 'loop', enabled: true, loop_mode: 'while',
      condition: f.condition || '条件成立',
      loop_count: Number(f.loop_count) || 10,
      loop_body: f.loop_body || '循环体'
    }]
  },
  {
    key: 'try_catch',
    label: 'try/catch 异常兜底',
    category: 'flow',
    fields: ['try_body', 'catch_body'],
    defaults: { try_body: '主流程', catch_body: '兜底流程' },
    toSteps: (f) => [{
      type: 'branch', enabled: true, branch_mode: 'try_catch',
      condition: '执行成功',
      branch_true: f.try_body || '主流程',
      branch_false: f.catch_body || '兜底流程'
    }]
  },
  {
    key: 'ignore_error',
    label: '忽略异常继续',
    category: 'flow',
    fields: ['note'],
    defaults: { note: '后续步骤继续执行' },
    toSteps: (f) => [{
      type: 'wait', enabled: true, seconds: 0.1, optional: true, ignore_error: true,
      name: f.note || '忽略异常继续'
    }]
  },

  // —— 设备硬件 ——
  {
    key: 'robot_cmd',
    label: '机器人指令',
    category: 'device',
    fields: ['robot_command', 'wait_after'],
    defaults: { robot_command: 'dock', wait_after: 2 },
    toSteps: (f) => {
      const steps = [{
        type: 'robot_send_command', enabled: true,
        command: f.robot_command || 'dock'
      }]
      const w = Number(f.wait_after)
      if (w > 0) steps.push({ type: 'wait', enabled: true, seconds: w })
      return steps
    }
  },
  {
    key: 'app_restart',
    label: 'App 重启',
    category: 'device',
    fields: ['app_package', 'wait_after'],
    defaults: { app_package: '', wait_after: 2 },
    toSteps: (f) => [
      { type: 'force_stop', enabled: true, app_package: f.app_package || undefined },
      { type: 'launch', enabled: true, app_package: f.app_package || 'com.example.app' },
      ...(Number(f.wait_after) > 0 ? [{ type: 'wait', enabled: true, seconds: Number(f.wait_after) }] : [])
    ]
  },
  {
    key: 'screenshot',
    label: '截屏',
    category: 'device',
    fields: ['save_path'],
    defaults: { save_path: '' },
    toSteps: (f) => [{ type: 'screenshot', enabled: true, save_path: f.save_path || '' }]
  },
  {
    key: 'grant_permission',
    label: '权限授予',
    category: 'device',
    fields: ['shell_cmd'],
    defaults: { shell_cmd: 'pm grant ${package} android.permission.CAMERA' },
    toSteps: (f) => [{ type: 'shell', enabled: true, command: f.shell_cmd || 'echo grant' }],
    hazardous: true
  },
  {
    key: 'shell_device',
    label: '系统 Shell',
    category: 'device',
    fields: ['shell_cmd'],
    defaults: { shell_cmd: 'echo ok' },
    toSteps: (f) => [{ type: 'shell', enabled: true, command: f.shell_cmd || 'echo ok' }],
    hazardous: true
  },

  // —— 断言 ——
  {
    key: 'assert_exists',
    label: '元素存在',
    category: 'assert',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 5, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'assert_exists', enabled: true, timeout: f.timeout ?? 5, ...locatorFields(f)
    }]
  },
  {
    key: 'assert_not_exists',
    label: '元素不存在',
    category: 'assert',
    fields: ['locator', 'timeout'],
    defaults: { timeout: 5, locator_type: 'id', locator_value: '' },
    toSteps: (f) => [{
      type: 'assert_not_exists', enabled: true, timeout: f.timeout ?? 5, ...locatorFields(f)
    }]
  },
  {
    key: 'assert_text',
    label: '文本匹配',
    category: 'assert',
    fields: ['locator', 'expected', 'timeout'],
    defaults: { timeout: 5, expected: '', locator_type: 'text', locator_value: '' },
    toSteps: (f) => [{
      type: 'assert_text', enabled: true, timeout: f.timeout ?? 5,
      expected: f.expected || '', ...locatorFields(f)
    }]
  },
  {
    key: 'assert_compare',
    label: '数据比较断言',
    category: 'assert',
    fields: ['expected', 'actual', 'compare_op'],
    defaults: { expected: '', actual: '', compare_op: 'equals' },
    toSteps: (f) => [{
      type: 'assert_compare', enabled: true,
      op: f.compare_op || 'equals',
      expected: f.expected || '',
      actual: f.actual || ''
    }]
  },
  {
    key: 'robot_log_assert',
    label: '设备状态/日志断言',
    category: 'assert',
    fields: ['expected', 'timeout'],
    defaults: { expected: '', timeout: 10 },
    toSteps: (f) => [{
      type: 'robot_log_assert', enabled: true,
      expected: f.expected || '',
      timeout: f.timeout ?? 10
    }]
  },

  // —— 数据 ——
  {
    key: 'set_var',
    label: '定义/赋值变量',
    category: 'data',
    fields: ['var_name', 'var_value'],
    defaults: { var_name: 'MY_VAR', var_value: '' },
    toSteps: (f) => [{
      type: 'set_var', enabled: true,
      name: f.var_name || 'MY_VAR',
      value: f.var_value ?? ''
    }]
  },
  {
    key: 'read_file',
    label: '读取本地文件',
    category: 'data',
    fields: ['file_path', 'var_name'],
    defaults: { file_path: '', var_name: 'FILE_CONTENT' },
    toSteps: (f) => [{
      type: 'custom_script', enabled: true, language: 'python',
      script: [
        'from pathlib import Path',
        `p = Path(${JSON.stringify(f.file_path || '')})`,
        'text = p.read_text(encoding="utf-8") if p.exists() else ""',
        `set_var(${JSON.stringify(f.var_name || 'FILE_CONTENT')}, text)`,
        'print("read_file bytes", len(text))'
      ].join('\n')
    }],
    hazardous: true
  },
  {
    key: 'write_log',
    label: '日志写入变量',
    category: 'data',
    fields: ['var_name', 'var_value'],
    defaults: { var_name: 'RUN_LOG', var_value: 'ok' },
    toSteps: (f) => [{
      type: 'set_var', enabled: true,
      name: f.var_name || 'RUN_LOG',
      value: f.var_value ?? 'ok'
    }]
  },

  // —— 高级 ——
  {
    key: 'python_script',
    label: 'Python 脚本',
    category: 'advanced',
    fields: ['script'],
    defaults: { script: 'print("hello")' },
    hazardous: true,
    toSteps: (f) => [{
      type: 'custom_script', enabled: true, language: 'python',
      script: f.script || 'print("hello")'
    }]
  },
  {
    key: 'js_script',
    label: 'JS 脚本',
    category: 'advanced',
    fields: ['script'],
    defaults: { script: 'console.log("hello")' },
    hazardous: true,
    toSteps: (f) => [{
      type: 'custom_script', enabled: true, language: 'javascript',
      script: f.script || 'console.log("hello")'
    }]
  },
  {
    key: 'shell_cmd',
    label: 'Shell 命令',
    category: 'advanced',
    fields: ['shell_cmd'],
    defaults: { shell_cmd: 'echo ok' },
    hazardous: true,
    toSteps: (f) => [{ type: 'shell', enabled: true, command: f.shell_cmd || 'echo ok' }]
  },
  {
    key: 'http_request',
    label: 'HTTP 请求',
    category: 'advanced',
    fields: ['http_method', 'http_url', 'http_body'],
    defaults: { http_method: 'GET', http_url: 'https://example.com', http_body: '' },
    hazardous: true,
    toSteps: (f) => [{
      type: 'custom_script', enabled: true, language: 'python',
      script: [
        'import urllib.request',
        `url = ${JSON.stringify(f.http_url || 'https://example.com')}`,
        `method = ${JSON.stringify((f.http_method || 'GET').toUpperCase())}`,
        `body = ${JSON.stringify(f.http_body || '')}`,
        'data = body.encode() if body and method != "GET" else None',
        'req = urllib.request.Request(url, data=data, method=method)',
        'with urllib.request.urlopen(req, timeout=30) as resp:',
        '    text = resp.read().decode("utf-8", errors="ignore")',
        '    print("http_status", resp.status)',
        '    set_var("HTTP_BODY", text[:4000])'
      ].join('\n')
    }]
  },
  {
    key: 'testbrain_hook',
    label: '对接 TestBrain',
    category: 'advanced',
    fields: ['script'],
    defaults: { script: 'print("testbrain hook placeholder")' },
    hazardous: true,
    toSteps: (f) => [{
      type: 'custom_script', enabled: true, language: 'python',
      script: f.script || 'print("testbrain hook placeholder")'
    }]
  }
]

export function actionsByCategory(category) {
  return ACTIONS.filter(a => a.category === category)
}

export function getAction(key) {
  return ACTIONS.find(a => a.key === key) || null
}

export function categoryLabel(key) {
  return CATEGORIES.find(c => c.key === key)?.label || key || '—'
}

export function platformLabel(key) {
  return PLATFORMS.find(p => p.key === key)?.label || key || '—'
}

/** 从单步反推 action_key */
export function inferActionKey(step) {
  if (!step || !step.type) return ''
  const t = step.type
  if (t === 'click') return 'click'
  if (t === 'long_press') return 'long_press'
  if (t === 'swipe') return 'swipe'
  if (t === 'input') return 'input'
  if (t === 'clear_input') return 'clear_input'
  if (t === 'dismiss_popup') return 'dismiss_popup'
  if (t === 'press_key') {
    if (step.key === 'home') return 'home'
    if (step.key === 'back') return 'back'
    return 'back'
  }
  if (t === 'wait') {
    if (step.wait_mode === 'appear') return 'wait_appear'
    if (step.wait_mode === 'wait_gone' || step.wait_mode === 'disappear') return 'wait_gone'
    if (step.ignore_error) return 'ignore_error'
    return 'wait'
  }
  if (t === 'branch') {
    if (step.branch_mode === 'try_catch') return 'try_catch'
    return 'branch_if'
  }
  if (t === 'loop') return step.loop_mode === 'while' ? 'loop_while' : 'loop_for'
  if (t === 'robot_send_command') return 'robot_cmd'
  if (t === 'screenshot') return 'screenshot'
  if (t === 'shell') return 'shell_cmd'
  if (t === 'assert_exists') return 'assert_exists'
  if (t === 'assert_not_exists') return 'assert_not_exists'
  if (t === 'assert_text') return 'assert_text'
  if (t === 'assert_compare') return 'assert_compare'
  if (t === 'robot_log_assert') return 'robot_log_assert'
  if (t === 'set_var') return 'set_var'
  if (t === 'custom_script') {
    if (step.language === 'javascript') return 'js_script'
    if ((step.script || '').includes('urllib.request')) return 'http_request'
    if ((step.script || '').includes('testbrain')) return 'testbrain_hook'
    if ((step.script || '').includes('Path(')) return 'read_file'
    return 'python_script'
  }
  if (t === 'force_stop' || t === 'launch') return 'app_restart'
  return ''
}

export function blankSimpleForm() {
  return {
    name: '',
    description: '',
    category: 'atomic',
    platform: 'android',
    action_key: 'click',
    ui_mode: 'simple',
    template_id: '',
    timeout: 3,
    seconds: 2,
    duration_ms: 800,
    text: '',
    locator_type: 'id',
    locator_value: '',
    element_name: '',
    display_name: '',
    x1: 500, y1: 1400, x2: 500, y2: 400,
    condition: '控件存在',
    branch_true: '成立分支',
    branch_false: '否则分支',
    loop_count: 3,
    loop_body: '循环体',
    try_body: '主流程',
    catch_body: '兜底流程',
    note: '',
    robot_command: 'dock',
    wait_after: 2,
    app_package: '',
    save_path: '',
    shell_cmd: 'echo ok',
    expected: '',
    actual: '',
    compare_op: 'equals',
    var_name: 'MY_VAR',
    var_value: '',
    file_path: '',
    script: 'print("hello")',
    http_method: 'GET',
    http_url: 'https://example.com',
    http_body: '',
    remark: ''
  }
}

export function actionToSteps(form) {
  const action = getAction(form.action_key)
  if (!action) return []
  return action.toSteps(form).map(s => ({ ...s, enabled: s.enabled !== false }))
}

export function buildStepsContent(form, steps) {
  const meta = baseMeta(form)
  return JSON.stringify({ steps: steps || actionToSteps(form), meta }, null, 2)
}

export function parseStepsContentFull(text) {
  try {
    const obj = JSON.parse(text || '{"steps":[]}')
    if (Array.isArray(obj)) {
      return { ok: true, steps: obj, meta: {} }
    }
    if (!obj || typeof obj !== 'object') {
      return { ok: false, error: '脚本必须是 JSON 对象', steps: [], meta: {} }
    }
    const steps = Array.isArray(obj.steps) ? obj.steps : []
    const meta = obj.meta && typeof obj.meta === 'object' ? obj.meta : {}
    return { ok: true, steps, meta, error: '' }
  } catch (e) {
    return { ok: false, error: e.message || 'JSON 解析失败', steps: [], meta: {} }
  }
}

export function extractMetaFromRow(row) {
  const parsed = parseStepsContentFull(row?.steps_content || '{}')
  if (!parsed.ok) return { category: '', platform: '', action_key: '' }
  const meta = { ...parsed.meta }
  if (!meta.action_key && parsed.steps[0]) {
    meta.action_key = inferActionKey(parsed.steps[0])
  }
  if (!meta.category && meta.action_key) {
    meta.category = getAction(meta.action_key)?.category || ''
  }
  return meta
}

export function formFromRow(row) {
  const form = blankSimpleForm()
  if (!row) return form
  form.name = row.name || ''
  form.description = row.description || ''
  const parsed = parseStepsContentFull(row.steps_content || '{}')
  const meta = parsed.meta || {}
  form.category = meta.category || 'atomic'
  form.platform = meta.platform || 'any'
  form.action_key = meta.action_key || inferActionKey(parsed.steps[0]) || 'click'
  form.ui_mode = meta.ui_mode || 'simple'
  form.template_id = meta.template_id || ''
  const s = parsed.steps[0] || {}
  if (s.timeout != null) form.timeout = s.timeout
  if (s.seconds != null) form.seconds = s.seconds
  if (s.duration_ms != null) form.duration_ms = s.duration_ms
  if (s.text != null) form.text = s.text
  if (s.locator_type) form.locator_type = s.locator_type
  if (s.locator_value) form.locator_value = s.locator_value
  if (s.element_name) form.element_name = s.element_name
  if (s.display_name) form.display_name = s.display_name
  if (s.x1 != null) form.x1 = s.x1
  if (s.y1 != null) form.y1 = s.y1
  if (s.x2 != null) form.x2 = s.x2
  if (s.y2 != null) form.y2 = s.y2
  if (s.condition) form.condition = s.condition
  if (s.branch_true) form.branch_true = s.branch_true
  if (s.branch_false) form.branch_false = s.branch_false
  if (s.loop_count != null) form.loop_count = s.loop_count
  if (s.loop_body) form.loop_body = s.loop_body
  if (s.command) form.robot_command = s.command
  if (s.app_package) form.app_package = s.app_package
  if (s.save_path) form.save_path = s.save_path
  if (s.command && s.type === 'shell') form.shell_cmd = s.command
  if (s.expected) form.expected = s.expected
  if (s.actual) form.actual = s.actual
  if (s.op) form.compare_op = s.op
  if (s.type === 'set_var') {
    form.var_name = s.name || form.var_name
    form.var_value = s.value ?? ''
  }
  if (s.script) form.script = s.script
  if (s.language === 'javascript') form.action_key = 'js_script'
  return form
}

export function suggestTemplatesByName(name) {
  const n = (name || '').toLowerCase()
  const hits = []
  for (const t of TEMPLATES) {
    if (t.keywords.some(k => n.includes(k.toLowerCase()) || (name || '').includes(k))) {
      hits.push(t)
    }
  }
  return hits.slice(0, 5)
}

export function uniqueName(base, existingNames) {
  const set = new Set((existingNames || []).map(s => String(s)))
  let name = (base || '未命名步骤').trim() || '未命名步骤'
  if (!set.has(name)) return name
  for (let i = 2; i < 200; i++) {
    const cand = `${name}_${i}`
    if (!set.has(cand)) return cand
  }
  return `${name}_${Date.now()}`
}

export function isHazardousAction(actionKey) {
  return !!getAction(actionKey)?.hazardous
}

export function autoDescription(form) {
  const cat = categoryLabel(form.category)
  const act = getAction(form.action_key)?.label || form.action_key
  const plat = platformLabel(form.platform)
  return `${cat} · ${act} · ${plat}${form.remark ? ` · ${form.remark}` : ''}`
}

/** 专业模式调色板（扩展） */
export const PRO_PALETTE = CATEGORIES.map(cat => ({
  key: cat.key,
  label: cat.label,
  items: actionsByCategory(cat.key).map(a => ({
    type: a.toSteps(a.defaults || { ...blankSimpleForm(), action_key: a.key, ...a.defaults })[0]?.type || a.key,
    label: a.label,
    defaults: (() => {
      const f = { ...blankSimpleForm(), action_key: a.key, ...a.defaults }
      const steps = a.toSteps(f)
      const first = steps[0] || { type: 'wait', seconds: 1 }
      const { type, enabled, ...rest } = first
      return rest
    })(),
    action_key: a.key
  }))
}))

export const TEMPLATES = [
  {
    id: 'dismiss_popup',
    name: '关闭广告弹窗',
    keywords: ['弹窗', '广告', '关闭'],
    category: 'atomic',
    platform: 'any',
    action_key: 'dismiss_popup',
    patch: { timeout: 3 }
  },
  {
    id: 'page_wait',
    name: '页面等待',
    keywords: ['等待', '加载'],
    category: 'atomic',
    platform: 'any',
    action_key: 'wait',
    patch: { seconds: 3 }
  },
  {
    id: 'device_connect',
    name: '设备连接',
    keywords: ['连接', '蓝牙', '配网'],
    category: 'device',
    platform: 'android',
    action_key: 'robot_cmd',
    patch: { robot_command: 'bt_connect', wait_after: 3 }
  },
  {
    id: 'bind_net',
    name: '设备配网',
    keywords: ['配网', '绑定'],
    category: 'device',
    platform: 'android',
    action_key: 'robot_cmd',
    patch: { robot_command: 'bind_device', wait_after: 5 }
  },
  {
    id: 'dock',
    name: '机器人回充',
    keywords: ['回充', '充电'],
    category: 'device',
    platform: 'android',
    action_key: 'robot_cmd',
    patch: { robot_command: 'dock', wait_after: 2 }
  },
  {
    id: 'login_init',
    name: '登录初始化',
    keywords: ['登录', '账号'],
    category: 'atomic',
    platform: 'android',
    action_key: 'click',
    multiSteps: [
      { type: 'launch', app_package: 'com.example.app', enabled: true },
      { type: 'dismiss_popup', enabled: true, optional: true },
      { type: 'input', locator_type: 'id', locator_value: 'username', text: '${username}', enabled: true },
      { type: 'input', locator_type: 'id', locator_value: 'password', text: '${password}', enabled: true },
      { type: 'click', locator_type: 'id', locator_value: 'login_btn', element_name: '登录', enabled: true }
    ]
  },
  {
    id: 'assert_home',
    name: '断言首页可见',
    keywords: ['断言', '首页'],
    category: 'assert',
    platform: 'any',
    action_key: 'assert_exists',
    patch: { locator_type: 'id', locator_value: 'home', timeout: 8 }
  }
]

export function applyTemplate(templateId, form) {
  const tpl = TEMPLATES.find(t => t.id === templateId)
  if (!tpl) return form
  const next = { ...form }
  next.category = tpl.category
  next.platform = tpl.platform
  next.action_key = tpl.action_key
  next.template_id = tpl.id
  if (!next.name?.trim()) next.name = tpl.name
  Object.assign(next, tpl.patch || {})
  next._multiSteps = tpl.multiSteps || null
  return next
}

export function resolveStepsForSave(form) {
  if (form._multiSteps?.length) {
    return form._multiSteps.map(s => ({ ...s, enabled: s.enabled !== false }))
  }
  return actionToSteps(form)
}
