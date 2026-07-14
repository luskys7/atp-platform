/** 控件定位辅助 — 与设计文档 §2/§3 对齐 */

export const LOCATOR_CHAIN_PRIORITY = [
  'id', 'resource_id', 'uiselector', 'content_desc', 'xpath_desc', 'text', 'xpath_text',
  'relative_xpath', 'parent_index', 'anchor_adjacent', 'region_locator',
  'class_name', 'bounds', 'screen_ratio', 'ocr'
]

export const IOS_CHAIN_PRIORITY = [
  'accessibility_id', 'id', 'nspredicate', 'content_desc', 'text',
  'class_name', 'bounds', 'screen_ratio', 'ocr'
]

const ANDROID_SCORE = {
  id: 100, resource_id: 100, uiselector: 88, content_desc: 78, xpath_desc: 72,
  text: 52, xpath_text: 48, relative_xpath: 46, parent_index: 44, anchor_adjacent: 42,
  region_locator: 40, class_name: 22, bounds: 18, screen_ratio: 12, ocr: 8
}

const IOS_SCORE = {
  accessibility_id: 100, id: 98, nspredicate: 86, content_desc: 72, text: 50,
  class_name: 24, bounds: 18, screen_ratio: 12, ocr: 8
}

export const RECOMMEND_REASON_LABELS = {
  id: '唯一 resourceId / identifier',
  resource_id: '唯一 resourceId',
  accessibility_id: 'iOS 自动化专属标识',
  uiselector: 'Android UiSelector 原生组合',
  nspredicate: 'iOS NSPredicate 原生组合',
  content_desc: '无障碍描述 content-desc / label',
  xpath_desc: '基于 content-desc 的相对 XPath',
  text: '静态固定文本',
  relative_xpath: '短相对 XPath 备选',
  parent_index: '父容器 + 下标',
  anchor_adjacent: '锚点邻位',
  region_locator: '区域限定',
  class_name: '控件类名（稳定性偏低）',
  screen_ratio: '屏幕比例兜底',
  ocr: 'OCR 文本兜底'
}

export const ELEMENT_NAME_PATTERN = /^[a-z][a-z0-9_]{2,47}$/

export const CONTROL_TAGS = [
  { value: 'static', label: '静态控件' },
  { value: 'dynamic', label: '动态控件' },
  { value: 'popup', label: '弹窗控件' },
  { value: 'high_risk', label: '高风险控件' }
]

export const WAIT_CONDITIONS = [
  { value: 'clickable', label: '可点击' },
  { value: 'visible', label: '可见' },
  { value: 'exists', label: '存在' }
]

const GENERIC_RIDS = new Set(['content', 'decor_content_parent', 'action_bar_root'])

export const RISK_LEVEL_LABELS = {
  low: '低风险',
  medium: '中风险',
  high: '高风险'
}

export const RISK_TAG_LABELS = {
  container: '容器节点',
  no_unique_attr: '无唯一属性',
  layout_only: '仅布局 class',
  dynamic_text: '动态文本',
  duplicate: '重复匹配',
  webview: 'WebView',
  not_interactive: '不可交互',
  ratio_fallback: '比例坐标兜底',
  parent_index: '父容器+下标',
  anchor_adjacent: '锚点邻位',
  region_locator: '区域限定'
}

export const ANCHOR_DIRECTIONS = [
  { value: 'right', label: '右侧' },
  { value: 'left', label: '左侧' },
  { value: 'down', label: '下方' },
  { value: 'up', label: '上方' }
]

export function isDynamicText(text) {
  const s = String(text || '').trim()
  if (!s) return true
  if (/^\d{4,}$/.test(s)) return true
  if (/验证码|captcha|code/i.test(s)) return true
  if (/^\d{1,2}:\d{2}$/.test(s)) return true
  return false
}

export function chainPriorityForPlatform(platform) {
  return platform === 'ios' ? IOS_CHAIN_PRIORITY : LOCATOR_CHAIN_PRIORITY
}

export function scoreLocatorType(type, value, platform = 'android') {
  if (!value || !String(value).trim()) return -999
  const val = String(value).trim()
  if ((type === 'id' || type === 'resource_id') && isGenericRid(val)) return -999
  if ((type === 'text' || type === 'ocr') && isDynamicText(val)) return 6
  const table = platform === 'ios' ? IOS_SCORE : ANDROID_SCORE
  return table[type] ?? 10
}

export function pickRecommendedIndex(chain, platform = 'android') {
  if (!chain?.length) return -1
  let best = 0
  let bestScore = -999
  chain.forEach((item, idx) => {
    if (item.enabled === false) return
    const score = item.recommend_score ?? scoreLocatorType(item.type, item.value, platform)
    if (score > bestScore) {
      bestScore = score
      best = idx
    }
  })
  return best
}

export function recommendReasonLabel(type, custom) {
  return custom || RECOMMEND_REASON_LABELS[type] || '按平台规范推荐'
}

export function isGenericRid(rid) {
  const short = String(rid || '').split(':id/').pop().split('/').pop().toLowerCase()
  return GENERIC_RIDS.has(short)
}

export function riskLevelLabel(level) {
  return RISK_LEVEL_LABELS[level] || level || '未知'
}

export function riskTagLabel(tag) {
  return RISK_TAG_LABELS[tag] || tag
}

export function validateElementName(name) {
  const n = String(name || '').trim()
  if (!ELEMENT_NAME_PATTERN.test(n)) {
    return '元素名须为小写 snake_case（3-48 字符，字母开头）'
  }
  return ''
}

export function controlTagLabel(tag) {
  return CONTROL_TAGS.find(t => t.value === tag)?.label || tag || '静态控件'
}

export function riskTagType(level) {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}

export function parseLocatorKv(value) {
  const out = {}
  String(value || '').split('|').forEach(part => {
    const p = part.trim()
    if (!p || !p.includes('=')) return
    const idx = p.indexOf('=')
    out[p.slice(0, idx).trim()] = p.slice(idx + 1).trim()
  })
  return out
}

export function formatLocatorKv(obj) {
  return Object.entries(obj || {})
    .filter(([, v]) => v != null && String(v) !== '')
    .map(([k, v]) => `${k}=${v}`)
    .join('|')
}

export function buildParentIndexValue(container, index) {
  if (!container?.trim() || index == null || index < 0) return ''
  return formatLocatorKv({ container: container.trim(), index: String(index) })
}

export function buildAnchorAdjacentValue(anchor, dir = 'right') {
  if (!anchor?.trim()) return ''
  return formatLocatorKv({ anchor: anchor.trim(), dir: dir || 'right' })
}

export function buildRegionLocatorValue(region, innerType, innerValue) {
  if (!region?.trim() || !innerValue?.trim()) return ''
  return formatLocatorKv({ region: region.trim(), type: innerType || 'content_desc', value: innerValue.trim() })
}

export function computeStabilityScore(pick) {
  if (!pick) return 0
  let score = 100
  if (pick.risk_level === 'high') score -= 45
  else if (pick.risk_level === 'medium') score -= 22
  const tagPenalty = {
    duplicate: 28,
    container: 20,
    no_unique_attr: 18,
    layout_only: 15,
    dynamic_text: 25,
    not_interactive: 20,
    ratio_fallback: 12,
    webview: 10
  }
  for (const tag of pick.risk_tags || []) {
    score -= tagPenalty[tag] || 8
  }
  if (pick.validate_result?.valid === false) score -= 15
  if (pick.validate_result?.valid === true) score += 5
  return Math.max(0, Math.min(100, score))
}

export function stabilityScoreLabel(score) {
  if (score >= 80) return '稳定'
  if (score >= 55) return '一般'
  return '偏弱'
}

export function stabilityScoreType(score) {
  if (score >= 80) return 'success'
  if (score >= 55) return 'warning'
  return 'danger'
}

export function isWeakPick(pick) {
  if (!pick) return false
  if (pick.risk_level === 'high') return true
  const rid = pick.locators?.resource_id || pick.locators?.id || pick.locator_value || pick.element_name || ''
  if (isGenericRid(rid)) return true
  if (pick.inspect_error === 'generic_container') return true
  if ((pick.risk_tags || []).includes('container')) return true
  return false
}

export function buildLocatorChainFromPick(pick) {
  const platform = pick?.platform || 'android'
  if (pick?.locator_chain?.length) {
    let recommendedIdx = pick.locator_chain.findIndex(i => i.recommended === true)
    if (recommendedIdx < 0) recommendedIdx = pickRecommendedIndex(pick.locator_chain, platform)
    if (recommendedIdx < 0) recommendedIdx = pick.locator_chain.findIndex(i => i.primary === true)
    if (recommendedIdx < 0) recommendedIdx = 0
    return pick.locator_chain.map((item, idx) => ({
      type: item.type || item.key,
      value: String(item.value || ''),
      enabled: item.enabled !== false,
      priority: item.priority ?? idx + 1,
      recommend_score: item.recommend_score ?? scoreLocatorType(item.type || item.key, item.value, platform),
      recommend_reason: item.recommend_reason || (idx === recommendedIdx ? recommendReasonLabel(item.type || item.key) : ''),
      recommended: idx === recommendedIdx,
      primary: item.primary === true || idx === recommendedIdx
    }))
  }
  const locs = pick?.locators || {}
  const chain = []
  const seen = new Set()
  for (const type of chainPriorityForPlatform(platform)) {
    const value = locs[type]
    if (!value) continue
    if ((type === 'resource_id' || type === 'id') && isGenericRid(String(value))) continue
    if ((type === 'text' || type === 'ocr') && isDynamicText(String(value))) continue
    const sig = `${type}:${value}`
    if (seen.has(sig)) continue
    seen.add(sig)
    chain.push({
      type,
      value: String(value),
      enabled: true,
      priority: chain.length + 1,
      recommend_score: scoreLocatorType(type, String(value), platform),
      recommended: false,
      primary: false
    })
  }
  const recIdx = pickRecommendedIndex(chain, platform)
  if (recIdx >= 0) {
    chain.forEach((item, idx) => {
      item.recommended = idx === recIdx
      item.primary = idx === recIdx
      if (idx === recIdx) item.recommend_reason = recommendReasonLabel(item.type)
    })
  }
  return chain
}

export function chainToLocators(chain) {
  const locators = {}
  ;(chain || []).forEach(item => {
    if (item?.enabled === false) return
    if (item?.type && item?.value) locators[item.type] = item.value
  })
  return locators
}

export function primaryFromChain(chain) {
  const enabled = (chain || []).filter(i => i.enabled !== false)
  const primary = enabled.find(i => i.primary) || enabled[0]
  if (!primary) return { locator_type: '', locator_value: '' }
  const lt = primary.type === 'resource_id' ? 'id'
    : ['xpath_desc', 'xpath_text', 'relative_xpath', 'parent_index', 'anchor_adjacent', 'region_locator'].includes(primary.type) ? 'xpath'
      : primary.type
  return { locator_type: lt, locator_value: primary.value }
}

export function mapLocatorTypeForPool(type) {
  const t = type === 'resource_id' ? 'id' : type
  if (['id', 'xpath', 'accessibility', 'ai', 'image'].includes(t)) return t
  if (['text', 'content_desc', 'xpath_desc', 'xpath_text', 'relative_xpath', 'class_name', 'bounds', 'screen_ratio', 'ocr',
    'parent_index', 'anchor_adjacent', 'region_locator'].includes(t)) {
    return 'xpath'
  }
  if (t === 'uiselector') return 'xpath'
  return 'xpath'
}
