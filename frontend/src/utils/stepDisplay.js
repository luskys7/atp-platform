const LOCATOR_TYPE_LABELS = {
  id: 'ID 定位',
  resource_id: 'ID 定位',
  xpath: 'xpath',
  xpath_desc: 'xpath',
  xpath_desc_contains: 'xpath',
  xpath_text: 'xpath',
  absolute_xpath: '绝对 xpath',
  text: '文本定位',
  content_desc: '文案定位',
  bounds: '坐标定位',
  accessibility_id: '无障碍 ID',
  nspredicate: 'NSPredicate',
  relative_xpath: '相对 xpath',
  parent_index: '父容器下标',
  anchor_adjacent: '锚点定位',
  region_locator: '区域定位',
  uiselector: 'UiSelector',
  screen_ratio: '屏幕比例',
  ocr: 'OCR 定位',
  class_name: '类名定位'
}

const LOCATOR_PRIORITY = [
  'id', 'resource_id', 'uiselector', 'content_desc', 'xpath_desc', 'xpath_desc_contains', 'text', 'xpath_text',
  'relative_xpath', 'parent_index', 'anchor_adjacent', 'region_locator',
  'class_name', 'absolute_xpath', 'bounds', 'screen_ratio', 'ocr'
]

export function pickPrimaryLocator(step) {
  if (!step) return null
  if (step.locator_type && step.locator_value) {
    return { type: step.locator_type, value: String(step.locator_value) }
  }
  const locs = step.locators
  if (locs && typeof locs === 'object') {
    for (const key of LOCATOR_PRIORITY) {
      if (locs[key]) return { type: key, value: String(locs[key]) }
    }
    const keys = Object.keys(locs)
    if (keys.length) return { type: keys[0], value: String(locs[keys[0]]) }
  }
  return null
}

export function formatLocatorType(type) {
  return LOCATOR_TYPE_LABELS[type] || type || '定位'
}

export function truncateText(text, max = 56) {
  const s = String(text || '')
  if (s.length <= max) return s
  return `${s.slice(0, max - 3)}...`
}

/** 步骤列表主行：业务描述 / 元素名 / 坐标等 */
export function formatStepTarget(step) {
  if (!step) return ''
  // 用户在编辑器填写的「控件名」优先于录制识别的 display_name
  if (step.element_name) return step.element_name
  if (step.display_name) return step.display_name
  if (step.type === 'click' || step.type === 'select' || step.type === 'hover') {
    return ''
  }
  if (step.type === 'tap_xy' || step.type === 'long_press') {
    if (step.x != null && step.y != null) return `(${step.x}, ${step.y})`
  }
  if (step.type === 'tap_ocr' || step.type === 'assert_ocr') return step.expected || ''
  if (step.type === 'input' || step.type === 'rich_text') return step.text || ''
  if (step.type === 'set_relative_time') {
    const mins = step.offset_minutes ?? 5
    return `当前时间+${mins}分钟 → TIME_HM`
  }
  if (step.type === 'custom_script') {
    const lang = (step.script_lang || step.language || 'python').toUpperCase()
    return step.element_name || `${lang} 脚本`
  }
  if (step.type === 'swipe') {
    return `(${step.x1 ?? step.x}, ${step.y1 ?? step.y}) → (${step.x2}, ${step.y2})`
  }
  if (step.type === 'press_key' || step.type === 'assert_key') return step.key || 'back'
  if (step.type === 'assert_text') return step.expected || step.element_name || ''
  if (step.type === 'wait') return `${step.seconds || 0}s`
  if (step.type === 'launch') return step.app_package || ''
  return step.expected || ''
}

/** 步骤列表副行：定位方式摘要 */
export function formatStepLocator(step) {
  if (!step) return ''
  const loc = pickPrimaryLocator(step)
  if (loc) {
    return `${formatLocatorType(loc.type)} · ${truncateText(loc.value)}`
  }
  if (step.type === 'tap_xy' && step.x != null && step.y != null) {
    return step.needs_manual_fix ? `坐标 (${step.x}, ${step.y}) · 需人工补定位` : `坐标 (${step.x}, ${step.y})`
  }
  if (step.needs_manual_fix) return '需人工补定位'
  if (step.type === 'tap_ocr') return 'OCR 文本定位'
  if (step.locator_valid === false && (step.type === 'click' || step.type === 'tap_xy')) {
    return '定位未识别'
  }
  return ''
}

/** 审阅页：列出全部 locator 键值 */
export function formatAllLocators(step) {
  const loc = pickPrimaryLocator(step)
  const lines = []
  if (loc) {
    lines.push({ type: loc.type, value: loc.value, primary: true })
  }
  const locs = step?.locators
  if (locs && typeof locs === 'object') {
    Object.entries(locs).forEach(([key, val]) => {
      if (loc && key === loc.type && String(val) === loc.value) return
      lines.push({ type: key, value: String(val), primary: false })
    })
  }
  return lines
}
