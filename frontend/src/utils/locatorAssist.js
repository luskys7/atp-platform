/** 控件定位辅助 — 与设计文档 §2/§3 对齐 */

export const LOCATOR_CHAIN_PRIORITY = [
  'id', 'resource_id', 'uiselector', 'content_desc', 'xpath_desc', 'xpath_desc_contains', 'text', 'xpath_text',
  'relative_xpath', 'parent_index', 'anchor_adjacent', 'region_locator',
  'class_name', 'absolute_xpath', 'bounds', 'screen_ratio', 'ocr'
]

export const IOS_CHAIN_PRIORITY = [
  'accessibility_id', 'id', 'nspredicate', 'content_desc', 'text',
  'class_name', 'bounds', 'screen_ratio', 'ocr'
]

const ANDROID_SCORE = {
  id: 100, resource_id: 100, uiselector: 88, content_desc: 78, xpath_desc: 72, xpath_desc_contains: 68,
  text: 52, xpath_text: 48, relative_xpath: 46, parent_index: 44, anchor_adjacent: 42,
  region_locator: 40, class_name: 22, bounds: 18, absolute_xpath: 16, screen_ratio: 12, ocr: 8
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
  content_desc: '文案定位（content-desc）',
  xpath_desc: 'xpath（基于 content-desc）',
  xpath_desc_contains: 'xpath（contains）',
  text: '文本定位',
  relative_xpath: '相对 xpath',
  absolute_xpath: '绝对 xpath',
  parent_index: '父容器下标',
  anchor_adjacent: '锚点定位',
  region_locator: '区域定位',
  class_name: '类名定位',
  screen_ratio: '屏幕比例兜底',
  ocr: 'OCR 定位'
}

export const ELEMENT_NAME_PATTERN = /^([\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9_-]{0,47}|[a-z][a-z0-9_]{2,47})$/

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
  anchor_adjacent: '锚点定位',
  region_locator: '区域定位'
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

function isShortOptionLabel(val) {
  const s = String(val || '').trim()
  if (!s) return false
  if (/^\d{1,2}$/.test(s)) return true
  return s.length <= 6 && /^[\u4e00-\u9fffA-Za-z0-9]+$/.test(s)
}

function isLongMarketingCopy(val) {
  const s = String(val || '').trim()
  if (s.length >= 16) return true
  if (s.length >= 8 && /[，。；、：]|日常|期间|自动|勿扰|了解更多|点击|请|将/.test(s)) return true
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
  let base = table[type] ?? 10
  if (['content_desc', 'xpath_desc', 'xpath_desc_contains', 'text', 'xpath_text', 'ocr'].includes(type)) {
    if (isLongMarketingCopy(val)) base -= (type.startsWith('content') || type.startsWith('xpath_desc')) ? 55 : 40
    else if (isShortOptionLabel(val)) base += 18
    else if (val.length > 10) base -= Math.min(30, (val.length - 10) * 2)
  }
  if (type === 'relative_xpath' && val.length <= 80) base += 6
  if (type === 'absolute_xpath') base -= 4
  if (['relative_xpath', 'xpath_desc', 'xpath_text', 'absolute_xpath'].includes(type) && val.length > 120) base -= 15
  return base
}

/** 推荐分 → 预估通过率 % */
export function scoreToPassRate(score) {
  const s = Number(score)
  if (!Number.isFinite(s) || s <= -100) return 5
  return Math.max(5, Math.min(99, Math.round(40 + s * 0.55)))
}

/**
 * 按通过率/推荐分降序排列定位链，并重写 priority（1=最高）。
 * validateAttempts 存在时：可点击 > 仅存在 > 未命中，再比分数。
 */
export function sortLocatorChainByPassRate(chain, platform = 'android', validateAttempts = null) {
  const attemptMap = new Map()
  ;(validateAttempts || []).forEach(att => {
    if (!att?.type) return
    const key = `${att.type}:${String(att.value || '')}`
    let rank = 0
    if (att.clickable) rank = 3
    else if (att.found) rank = 2
    else if (att.reason && att.reason !== 'not_found') rank = 1
    const prev = attemptMap.get(key)
    if (!prev || rank > prev) attemptMap.set(key, rank)
  })

  const arr = (chain || []).map(item => {
    const type = item.type || item.key
    const value = String(item.value || '')
    const score = item.recommend_score ?? scoreLocatorType(type, value, platform)
    const passRate = item.pass_rate != null ? Number(item.pass_rate) : scoreToPassRate(score)
    const validateRank = attemptMap.get(`${type}:${value}`) || 0
    return { ...item, type, value, recommend_score: score, pass_rate: passRate, _validateRank: validateRank }
  })

  arr.sort((a, b) => {
    if (b._validateRank !== a._validateRank) return b._validateRank - a._validateRank
    if ((b.pass_rate || 0) !== (a.pass_rate || 0)) return (b.pass_rate || 0) - (a.pass_rate || 0)
    return (b.recommend_score || -999) - (a.recommend_score || -999)
  })

  const bestScore = Math.max(...arr.map(i => i.recommend_score ?? -999), -999)
  return arr.map((item, idx) => {
    const { _validateRank, ...rest } = item
    const recommended = idx === 0
    return {
      ...rest,
      priority: idx + 1,
      recommended,
      primary: recommended,
      recommend_reason: recommended
        ? (rest.recommend_reason || recommendReasonLabel(rest.type))
        : undefined
    }
  })
}

export function pickRecommendedIndex(chain, platform = 'android') {
  if (!chain?.length) return -1
  let best = 0
  let bestScore = -999
  chain.forEach((item, idx) => {
    if (item.enabled === false) return
    const score = item.pass_rate != null
      ? Number(item.pass_rate)
      : (item.recommend_score ?? scoreLocatorType(item.type, item.value, platform))
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
  if (!n) return '请填写控件名称'
  if (n.length > 256) return '控件名称不能超过 256 字'
  return ''
}

export function validateControlDisplayName(name) {
  return validateElementName(name)
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
    const normalized = pick.locator_chain.map((item, idx) => ({
      type: item.type || item.key,
      value: String(item.value || ''),
      enabled: item.enabled !== false,
      priority: item.priority ?? idx + 1,
      recommend_score: item.recommend_score ?? scoreLocatorType(item.type || item.key, item.value, platform),
      pass_rate: item.pass_rate != null
        ? Number(item.pass_rate)
        : scoreToPassRate(item.recommend_score ?? scoreLocatorType(item.type || item.key, item.value, platform)),
      recommend_reason: item.recommend_reason || ''
    }))
    return sortLocatorChainByPassRate(normalized, platform)
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
    const score = scoreLocatorType(type, String(value), platform)
    chain.push({
      type,
      value: String(value),
      enabled: true,
      recommend_score: score,
      pass_rate: scoreToPassRate(score)
    })
  }
  return sortLocatorChainByPassRate(chain, platform)
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
  if (!primary) return { locator_type: '', locator_value: '', raw_type: '' }
  const raw = primary.type || ''
  const value = primary.value || ''
  return {
    locator_type: normalizeSemanticLocatorType(raw, value),
    locator_value: value,
    raw_type: raw
  }
}

function looksLikeXPath(value) {
  const v = String(value || '').trim()
  return /^(\/|\.\/|\.\/\/|\()/.test(v)
}

function looksLikeBounds(value) {
  const v = String(value || '').trim()
  return /\[\d+\s*,\s*\d+\]\s*\[\d+\s*,\s*\d+\]/.test(v) || /^\d+\s*,\s*\d+$/.test(v)
}

/** 统一语义定位类型（供用例步骤 / 控件池等复用） */
export function normalizeSemanticLocatorType(type, value = '') {
  const t = type === 'resource_id' ? 'id' : String(type || '')
  const v = String(value || '').trim()
  if (t === 'id') return 'id'
  if (t === 'text') return 'text'
  if (['content_desc', 'accessibility', 'accessibility_id', 'desc'].includes(t)) return 'content_desc'
  if (['bounds', 'coordinate', 'xy', 'screen_ratio'].includes(t)) return 'bounds'
  if (t === 'ocr') return 'ocr'
  if (t === 'class_name') return 'class_name'
  if (t === 'xpath_text') return looksLikeXPath(v) ? 'xpath' : 'text'
  if (t === 'xpath_desc' || t === 'xpath_desc_contains') return looksLikeXPath(v) ? 'xpath' : 'content_desc'
  if (['xpath', 'relative_xpath', 'absolute_xpath', 'uiselector', 'parent_index', 'anchor_adjacent', 'region_locator'].includes(t)) {
    return 'xpath'
  }
  if (looksLikeXPath(v)) return 'xpath'
  if (looksLikeBounds(v)) return 'bounds'
  return t || 'id'
}

/**
 * 控件池表单支持的主定位类型：id / accessibility / text / xpath / bounds
 */
export function mapLocatorTypeForPool(type, value = '') {
  const semantic = normalizeSemanticLocatorType(type, value)
  if (semantic === 'id') return 'id'
  if (semantic === 'text' || semantic === 'ocr') return 'text'
  if (semantic === 'content_desc') return 'accessibility'
  if (semantic === 'bounds') return 'bounds'
  if (semantic === 'class_name') return 'xpath'
  if (['xpath', 'uiselector'].includes(semantic)) return 'xpath'
  if (['ai', 'image'].includes(semantic)) return semantic
  if (looksLikeXPath(value)) return 'xpath'
  if (looksLikeBounds(value)) return 'bounds'
  return 'id'
}

/** 写入控件池时的表达式：非路径值若被迫落为 xpath，则补成合法 xpath */
export function mapLocatorValueForPool(type, value) {
  const rawType = type === 'resource_id' ? 'id' : String(type || '')
  const v = String(value || '').trim()
  const poolType = mapLocatorTypeForPool(rawType, v)
  if (poolType !== 'xpath' || !v || looksLikeXPath(v)) return v
  const q = JSON.stringify(v)
  if (rawType === 'class_name') return `//*[@class=${q}]`
  if (rawType === 'xpath_text' || rawType === 'text') return `//*[@text=${q}]`
  if (rawType === 'xpath_desc' || rawType === 'content_desc' || rawType === 'accessibility') {
    return `//*[@content-desc=${q}]`
  }
  if (rawType === 'xpath_desc_contains') return `//*[contains(@content-desc,${q})]`
  return v
}
