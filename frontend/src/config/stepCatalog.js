/**
 * 可视化用例编辑器 — 三级步骤目录 + 快捷操作
 * leaf: { id, label, type, extras?, needsLocator?, fields? }
 */

function leaf(id, label, type, opts = {}) {
  return { id, label, type, isLeaf: true, ...opts }
}

/** 可后台调整的高频快捷指令 id 列表 */
export const DEFAULT_QUICK_ACTION_IDS = [
  'ctrl.android.click',
  'ctrl.android.input',
  'ctrl.webview.clear',
  'ctrl.coord.swipe',
  'flow.wait',
  'verify.contains',
  'image.screenshot',
  'flow.common',
  'script.custom'
]

export const STEP_CATALOG = [
  {
    id: 'device',
    label: '设备操作',
    children: [
      {
        id: 'device.screen',
        label: '屏幕交互',
        children: [
          leaf('device.screen.lock', '锁定屏幕', 'lock_screen'),
          leaf('device.screen.unlock', '解锁屏幕', 'wake_screen'),
          leaf('device.screen.rotate_left', '左转屏幕', 'rotate_screen', { extras: { direction: 'left' } }),
          leaf('device.screen.rotate_right', '右转屏幕', 'rotate_screen', { extras: { direction: 'right' } }),
          leaf('device.screen.auto_rotate_off', '关闭自动旋转', 'set_auto_rotate', { extras: { enabled: false } }),
          leaf('device.screen.swipe_center', '从屏幕中央滑动距离', 'swipe_from_center', {
            fields: ['distance', 'direction', 'duration_ms']
          })
        ]
      },
      {
        id: 'device.special',
        label: '特殊交互',
        children: [
          leaf('device.special.key', '系统按键', 'press_key', { fields: ['key'] }),
          leaf('device.special.key_custom', '系统按键（自定义）', 'press_key', {
            fields: ['key_code'],
            extras: { key: 'custom' }
          })
        ]
      }
    ]
  },
  {
    id: 'app',
    label: '应用操作',
    children: [
      {
        id: 'app.basic',
        label: '应用基础',
        children: [
          leaf('app.basic.launch', '打开应用', 'launch', { fields: ['app_package'] }),
          leaf('app.basic.stop', '终止应用', 'force_stop'),
          leaf('app.basic.install', '安装应用', 'install_apk', { fields: ['app_package'] }),
          leaf('app.basic.uninstall', '卸载应用', 'uninstall_app', { fields: ['app_package'] }),
          leaf('app.basic.clear_cache', '清空 App 内存缓存', 'clear_cache', { extras: { mode: 'memory' } })
        ]
      }
    ]
  },
  {
    id: 'ctrl',
    label: '控件元素操作',
    children: [
      {
        id: 'ctrl.android',
        label: '安卓原生控件',
        children: [
          leaf('ctrl.android.find_strategy', '设置查找控件策略', 'set_find_strategy', {
            fields: ['strategy', 'timeout'],
            extras: { platform: 'android' }
          }),
          leaf('ctrl.android.switch_window', '切换窗口模式', 'switch_context', { fields: ['mode'] }),
          leaf('ctrl.android.exists', '判断控件元素是否存在', 'assert_exists', { needsLocator: true }),
          leaf('ctrl.android.count', '判断控件元素存在个数', 'assert_element_count', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected_count']
          }),
          leaf('ctrl.android.click', '点击控件元素', 'click', { needsLocator: true }),
          leaf('ctrl.android.input', '输入文本', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text']
          }),
          leaf('ctrl.android.input_actions', '输入文本 (Actions)', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text'],
            extras: { input_mode: 'actions' }
          }),
          leaf('ctrl.android.clear', '清空输入框', 'clear_input', {
            needsLocator: true,
            extras: { platform: 'android' }
          }),
          leaf('ctrl.android.drag', '拖拽控件元素', 'drag_element', { needsLocator: true, fields: ['element_name', 'x2', 'y2'] }),
          leaf('ctrl.android.scroll_to', '滚动到控件元素', 'scroll_to_element', { needsLocator: true }),
          leaf('ctrl.android.long_press', '长按控件元素', 'long_press', { needsLocator: true }),
          leaf('ctrl.android.verify_attr', '验证控件属性', 'assert_attribute', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'attr_name', 'expected']
          }),
          leaf('ctrl.android.get_text', '获取文本', 'get_text', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'var_name']
          }),
          leaf('ctrl.android.assert_text', '断言文本', 'assert_text', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected']
          }),
          leaf('ctrl.android.log_info', '日志输出控件信息', 'log_element', { needsLocator: true })
        ]
      },
      {
        id: 'ctrl.ios',
        label: 'iOS 控件',
        children: [
          leaf('ctrl.ios.find_strategy', '设置查找控件策略', 'set_find_strategy', {
            fields: ['strategy', 'timeout'],
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.exists', '判断控件元素是否存在', 'assert_exists', {
            needsLocator: true,
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.count', '判断控件元素存在个数', 'assert_element_count', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected_count'],
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.click', '点击控件元素', 'click', {
            needsLocator: true,
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.input', '输入文本', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text'],
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.clear', '清空输入框', 'clear_input', {
            needsLocator: true,
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.long_press', '长按控件元素', 'long_press', {
            needsLocator: true,
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.scroll_to', '滚动到控件元素', 'scroll_to_element', {
            needsLocator: true,
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.assert_text', '断言文本', 'assert_text', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected'],
            extras: { platform: 'ios' }
          }),
          leaf('ctrl.ios.get_text', '获取文本', 'get_text', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'var_name'],
            extras: { platform: 'ios' }
          })
        ]
      },
      {
        id: 'ctrl.coord',
        label: '坐标控件',
        children: [
          leaf('ctrl.coord.tap', '点击坐标', 'tap_xy', {
            fields: ['element_name'],
            needsCoords: 'tap'
          }),
          leaf('ctrl.coord.long_press', '长按坐标', 'long_press', {
            fields: ['element_name', 'duration_ms'],
            needsCoords: 'tap'
          }),
          leaf('ctrl.coord.swipe', '滑动拖拽', 'swipe', {
            fields: ['swipe_start_name', 'swipe_end_name', 'duration_ms'],
            needsCoords: 'swipe'
          })
        ]
      },
      {
        id: 'ctrl.webview',
        label: 'WebView 控件',
        children: [
          leaf('ctrl.webview.find_strategy', '设置查找控件策略', 'set_find_strategy', {
            fields: ['strategy', 'timeout'],
            extras: { platform: 'webview' }
          }),
          leaf('ctrl.webview.switch', '切换 WebView', 'switch_context', { extras: { mode: 'webview' } }),
          leaf('ctrl.webview.handle', '切换 Handle', 'switch_handle', { fields: ['handle'] }),
          leaf('ctrl.webview.exists', '判断控件元素是否存在', 'assert_exists', {
            needsLocator: true,
            extras: { context: 'webview' }
          }),
          leaf('ctrl.webview.count', '判断控件元素存在个数', 'assert_element_count', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected_count'],
            extras: { context: 'webview' }
          }),
          leaf('ctrl.webview.click', '点击控件元素', 'click', {
            needsLocator: true,
            extras: { context: 'webview' }
          }),
          leaf('ctrl.webview.scroll_top', '滚动控件至顶部可见', 'scroll_to_element', {
            needsLocator: true,
            extras: { align: 'top', context: 'webview' }
          }),
          leaf('ctrl.webview.input', '输入文本', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text'],
            extras: { context: 'webview' }
          }),
          leaf('ctrl.webview.input_actions', '输入文本 (Actions)', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text'],
            extras: { input_mode: 'actions', context: 'webview' }
          }),
          leaf('ctrl.webview.clear', '清空输入框', 'clear_input', { needsLocator: true, extras: { context: 'webview' } })
        ]
      },
      {
        id: 'ctrl.poco',
        label: 'POCO 控件',
        children: [
          leaf('ctrl.poco.find_strategy', '设置查找控件策略', 'set_find_strategy', {
            fields: ['strategy', 'timeout'],
            extras: { platform: 'poco' }
          }),
          leaf('ctrl.poco.exists', '判断控件元素是否存在', 'assert_exists', {
            needsLocator: true,
            extras: { context: 'poco' }
          }),
          leaf('ctrl.poco.count', '判断控件元素存在个数', 'assert_element_count', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'expected_count'],
            extras: { context: 'poco' }
          }),
          leaf('ctrl.poco.click', '点击控件元素', 'click', { needsLocator: true, extras: { context: 'poco' } }),
          leaf('ctrl.poco.input', '输入文本', 'input', {
            needsLocator: true,
            fields: ['element_name', 'locator_type', 'locator_value', 'text'],
            extras: { context: 'poco' }
          }),
          leaf('ctrl.poco.drag', '拖拽控件元素', 'drag_element', {
            needsLocator: true,
            fields: ['element_name', 'x2', 'y2'],
            extras: { context: 'poco' }
          })
        ]
      }
    ]
  },
  {
    id: 'verify',
    label: '验证操作',
    children: [
      {
        id: 'verify.custom',
        label: '自定义断言',
        children: [
          leaf('verify.equals', '断言验证（相等）', 'assert_compare', {
            fields: ['actual', 'expected'],
            extras: { op: 'eq' }
          }),
          leaf('verify.not_equals', '断言验证（不相等）', 'assert_compare', {
            fields: ['actual', 'expected'],
            extras: { op: 'ne' }
          }),
          leaf('verify.contains', '断言验证（包含）', 'assert_compare', {
            fields: ['actual', 'expected'],
            extras: { op: 'contains' }
          }),
          leaf('verify.not_contains', '断言验证（不包含）', 'assert_compare', {
            fields: ['actual', 'expected'],
            extras: { op: 'not_contains' }
          })
        ]
      }
    ]
  },
  {
    id: 'image',
    label: '图像操作',
    children: [
      {
        id: 'image.capture',
        label: '屏幕捕获',
        children: [
          leaf('image.screenshot', '获取截图', 'screenshot', {
            fields: ['element_name', 'save_path'],
            extras: {}
          })
        ]
      }
    ]
  },
  {
    id: 'flow',
    label: '流程控制',
    children: [
      {
        id: 'flow.reuse',
        label: '流程复用',
        children: [
          leaf('flow.common', '公共步骤', 'invoke_common', { fields: ['common_step', 'input_params_json'] })
        ]
      },
      {
        id: 'flow.schedule',
        label: '流程调度',
        children: [
          leaf('flow.wait', '强制等待', 'wait', {
            fields: ['seconds'],
            extras: { wait_mode: 'fixed' }
          }),
          leaf('flow.if', 'if 判断', 'branch', {
            fields: ['condition_kind', 'var_name', 'condition', 'element_name', 'locator_type', 'locator_value', 'expected', 'timeout'],
            extras: { condition: '控件存在', condition_kind: 'exists', timeout: 5 }
          }),
          leaf('flow.else_if', 'else if', 'else_if', {
            fields: ['condition_kind', 'var_name', 'condition', 'element_name', 'locator_type', 'locator_value', 'expected', 'timeout'],
            extras: { condition: '控件存在', condition_kind: 'exists', timeout: 5 }
          }),
          leaf('flow.else', 'else 否则', 'else', {
            extras: { remark: '否则分支' }
          }),
          leaf('flow.end_if', '结束分支', 'end_block', {
            extras: { block_type: 'branch', remark: '结束分支' }
          })
        ]
      }
    ]
  },
  {
    id: 'script',
    label: '脚本拓展',
    children: [
      {
        id: 'script.ext',
        label: '拓展能力',
        children: [
          leaf('script.custom', '自定义脚本', 'custom_script', {
            fields: ['script_lang', 'script_code', 'script_timeout', 'element_name']
          }),
          leaf('script.monkey', '随机事件', 'random_event', { fields: ['event_count', 'throttle_ms'] })
        ]
      }
    ]
  },
  {
    id: 'runtime',
    label: '运行设置',
    children: [
      {
        id: 'runtime.global',
        label: '全局配置',
        children: [
          leaf('runtime.step_interval', '步骤间隔设置', 'set_step_interval', { fields: ['interval_ms'] }),
          leaf('runtime.touch_mode', '触控模式设置', 'set_touch_mode', { fields: ['touch_mode'] })
        ]
      }
    ]
  },
  {
    id: 'robot',
    label: '机器人操作',
    children: [
      {
        id: 'robot.real',
        label: '真实机器人',
        children: [
          leaf('robot.firmware', '固件升级', 'robot_firmware_upgrade', { fields: ['firmware_path'] }),
          leaf('robot.log_assert', '日志断言', 'robot_log_assert', { fields: ['expected', 'timeout'] }),
          leaf('robot.command', '发送命令', 'robot_send_command', { fields: ['command'] })
        ]
      }
    ]
  }
]

const LEAF_INDEX = (() => {
  const map = new Map()
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.isLeaf) map.set(n.id, n)
      if (n.children) walk(n.children)
    }
  }
  walk(STEP_CATALOG)
  return map
})()

export function getCatalogLeaf(id) {
  return LEAF_INDEX.get(id) || null
}

export function findCatalogPath(leafId) {
  const path = []
  const dfs = (nodes, trail) => {
    for (const n of nodes || []) {
      const next = [...trail, n.id]
      if (n.id === leafId) {
        path.push(...next)
        return true
      }
      if (n.children && dfs(n.children, next)) return true
    }
    return false
  }
  dfs(STEP_CATALOG, [])
  return path
}

export function getQuickActions(ids = DEFAULT_QUICK_ACTION_IDS) {
  return ids
    .map(id => {
      const leaf = getCatalogLeaf(id)
      if (!leaf) return null
      return { id: leaf.id, label: leaf.label, type: leaf.type, primary: ['ctrl.android.click', 'ctrl.android.input', 'verify.contains'].includes(id) }
    })
    .filter(Boolean)
}

/** el-tree 数据 */
export function toElTreeData(catalog = STEP_CATALOG) {
  const mapNode = (n) => ({
    id: n.id,
    label: n.label,
    type: n.type,
    isLeaf: !!n.isLeaf,
    disabled: false,
    children: n.children?.map(mapNode)
  })
  return catalog.map(mapNode)
}

export const COMMON_FORM_FIELDS = ['remark', 'logic_process', 'on_fail']

/** 步骤失败处理策略（单步可覆盖用例默认策略） */
export const ON_FAIL_OPTIONS = [
  { label: '失败终止', value: 'fail' },
  { label: '失败继续', value: 'skip' },
  { label: '中断', value: 'interrupt' },
  { label: '异常', value: 'exception' },
  { label: '忽略', value: 'ignore' }
]

export const ON_FAIL_LABELS = Object.fromEntries(
  ON_FAIL_OPTIONS.map(o => [o.value, o.label])
)

/** 步骤逻辑处理：分支 / 循环控制 */
export const LOGIC_PROCESS_OPTIONS = [
  { label: '无', value: 'none' },
  { label: 'if', value: 'if' },
  { label: 'else if', value: 'else_if' },
  { label: 'else', value: 'else' },
  { label: 'while', value: 'while' }
]

/** 兼容旧值 restart_app → fail */
export function normalizeOnFail(value) {
  if (!value || value === 'restart_app') return 'fail'
  return ON_FAIL_LABELS[value] ? value : 'fail'
}

/** el-cascader 选项（仅叶子可选） */
export function toCascaderOptions(catalog = STEP_CATALOG) {
  const mapNode = (n) => {
    const node = { value: n.id, label: n.label }
    if (n.children?.length) {
      node.children = n.children.map(mapNode)
    } else if (n.isLeaf) {
      node.leaf = true
    }
    return node
  }
  return catalog.map(mapNode)
}

export const FIELD_META = {
  remark: { label: '描述信息', kind: 'textarea', placeholder: '' },
  logic_process: {
    label: '逻辑处理',
    kind: 'select',
    options: LOGIC_PROCESS_OPTIONS,
    tip: '生成真实 if/else if/else/while 控制块（脚本可执行），非仅选项'
  },
  on_fail: {
    label: '异常处理',
    kind: 'select',
    options: ON_FAIL_OPTIONS
  },
  element_name: { label: '控件', kind: 'text', placeholder: '从控件库选择' },
  locator_type: {
    label: '定位方式',
    kind: 'select',
    options: [
      { label: '资源 ID', value: 'id' },
      { label: 'XPath', value: 'xpath' },
      { label: '文本描述', value: 'accessibility' },
      { label: '文本内容', value: 'text' },
      { label: 'className', value: 'class' },
      { label: '坐标定位', value: 'bounds' }
    ]
  },
  locator_value: { label: '定位表达式', kind: 'textarea', placeholder: '从控件库选择后自动回填' },
  swipe_start_name: { label: '起点控件', kind: 'text', placeholder: '从控件库选择起点' },
  swipe_end_name: { label: '终点控件', kind: 'text', placeholder: '从控件库选择终点' },
  text: { label: '输入文本', kind: 'text', placeholder: '支持 ${变量名}' },
  expected: { label: '期望值', kind: 'text' },
  actual: { label: '实际值来源', kind: 'text', placeholder: '文本或 ${变量名}' },
  seconds: { label: '等待时长(秒)', kind: 'number', min: 1, max: 300 },
  timeout: { label: '超时(秒)', kind: 'number', min: 1, max: 120 },
  wait_timeout: { label: '等待控件出现(秒)', kind: 'number', min: 0, max: 60 },
  app_package: { label: '应用包名/路径', kind: 'text' },
  key: {
    label: '系统按键',
    kind: 'select',
    options: [
      { label: '返回', value: 'back' },
      { label: '主页', value: 'home' },
      { label: '多任务', value: 'recent' },
      { label: '电源', value: 'power' },
      { label: '音量+', value: 'volume_up' },
      { label: '音量-', value: 'volume_down' }
    ]
  },
  key_code: { label: '自定义键值', kind: 'text', placeholder: '例：KEYCODE_ENTER / 66' },
  x: { label: 'X', kind: 'number', min: 0, max: 4000 },
  y: { label: 'Y', kind: 'number', min: 0, max: 4000 },
  x1: { label: '起点 X', kind: 'number' },
  y1: { label: '起点 Y', kind: 'number' },
  x2: { label: '终点 X', kind: 'number' },
  y2: { label: '终点 Y', kind: 'number' },
  duration_ms: { label: '时长(ms)', kind: 'number', min: 50, max: 5000 },
  distance: { label: '滑动距离(px)', kind: 'number', min: 10, max: 2000 },
  direction: {
    label: '方向',
    kind: 'select',
    options: [
      { label: '上', value: 'up' },
      { label: '下', value: 'down' },
      { label: '左', value: 'left' },
      { label: '右', value: 'right' }
    ]
  },
  mode: {
    label: '模式',
    kind: 'select',
    options: [
      { label: '自动', value: 'auto' },
      { label: 'Native', value: 'native' },
      { label: 'WebView', value: 'webview' }
    ]
  },
  strategy: {
    label: '查找策略',
    kind: 'select',
    options: [
      { label: '默认', value: 'default' },
      { label: '优先 ID', value: 'id_first' },
      { label: '优先文本', value: 'text_first' },
      { label: 'AI 辅助', value: 'ai' }
    ]
  },
  expected_count: { label: '期望个数', kind: 'number', min: 0, max: 100 },
  attr_name: { label: '属性名', kind: 'text', placeholder: 'text / content-desc / enabled' },
  var_name: { label: '存入变量', kind: 'text', placeholder: '例：TITLE' },
  common_step: { label: '公共步骤', kind: 'common_step' },
  input_params_json: { label: '入参 JSON', kind: 'textarea', placeholder: '{"username":"test1"}' },
  script_lang: {
    label: '脚本语言',
    kind: 'select',
    options: [
      { label: 'Python', value: 'python' },
      { label: 'Java', value: 'java' }
    ]
  },
  script_code: { label: '脚本代码', kind: 'code' },
  script_timeout: { label: '超时(秒)', kind: 'number', min: 5, max: 600 },
  save_path: { label: '保存路径', kind: 'text', placeholder: '可选，默认任务目录' },
  handle: { label: 'Handle', kind: 'text' },
  event_count: { label: '随机事件次数', kind: 'number', min: 1, max: 500 },
  throttle_ms: { label: '事件间隔(ms)', kind: 'number', min: 50, max: 5000 },
  interval_ms: { label: '步骤间隔(ms)', kind: 'number', min: 0, max: 10000 },
  touch_mode: {
    label: '触控模式',
    kind: 'select',
    options: [
      { label: '默认', value: 'default' },
      { label: '真人模拟', value: 'human' },
      { label: '高速', value: 'fast' }
    ]
  },
  condition: { label: '条件说明', kind: 'text', placeholder: '自定义条件文案' },
  condition_kind: {
    label: '判断类型',
    kind: 'select',
    options: [
      { label: '控件存在', value: 'exists' },
      { label: '控件不存在', value: 'not_exists' },
      { label: '文本包含', value: 'text_contains' },
      { label: '变量等于', value: 'var_equals' },
      { label: '变量不等于', value: 'var_not_equals' },
      { label: '自定义条件', value: 'custom' }
    ]
  },
  firmware_path: { label: '固件路径', kind: 'text' },
  command: { label: '命令内容', kind: 'textarea' }
}

export function resolveLeafFields(leaf) {
  if (!leaf) return [...COMMON_FORM_FIELDS]
  if (Array.isArray(leaf.fields) && leaf.fields.length) {
    return [...leaf.fields, ...COMMON_FORM_FIELDS]
  }
  const fields = []
  if (leaf.needsLocator) {
    fields.push('element_name', 'locator_type', 'locator_value', 'wait_timeout')
  }
  if (leaf.type === 'wait') fields.push('seconds')
  if (leaf.type === 'input') fields.push('text')
  if (leaf.type === 'assert_text') fields.push('expected')
  if (leaf.type === 'assert_compare') fields.push('actual', 'expected')
  if (leaf.type === 'assert_element_count') fields.push('expected_count')
  return [...fields, ...COMMON_FORM_FIELDS]
}
