/**
 * 敏感数据脱敏：手机号、身份证、邮箱、银行卡、订单号等。
 */

const RULES = [
  { name: 'phone', re: /(?<!\d)(1[3-9]\d{9})(?!\d)/g, mask: (m) => m.slice(0, 3) + '****' + m.slice(-4) },
  { name: 'id_card', re: /(?<!\d)(\d{17}[\dXx])(?!\d)/g, mask: (m) => m.slice(0, 4) + '**********' + m.slice(-4) },
  { name: 'email', re: /([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g, mask: (m) => {
    const [user, domain] = m.split('@')
    return `${user.slice(0, 2)}***@${domain}`
  }},
  { name: 'bank_card', re: /(?<!\d)(\d{16,19})(?!\d)/g, mask: (m) => m.slice(0, 4) + ' **** **** ' + m.slice(-4) },
  { name: 'order', re: /(ORD|ORDER|SN|NO)[-_]?(\d{6,})/gi, mask: (m) => m.replace(/\d/g, (c, i, s) => (i > 4 && i < s.length - 3 ? '*' : c)) }
]

export function containsSensitive(text) {
  if (!text || typeof text !== 'string') return false
  return RULES.some(r => {
    r.re.lastIndex = 0
    return r.re.test(text)
  })
}

export function desensitizeText(text) {
  if (!text || typeof text !== 'string') return text
  let out = text
  for (const rule of RULES) {
    rule.re.lastIndex = 0
    out = out.replace(rule.re, (match) => rule.mask(match))
  }
  return out
}

export function desensitizeStep(step) {
  if (!step || typeof step !== 'object') return step
  const copy = { ...step }
  if (copy.text) copy.text = desensitizeText(copy.text)
  if (copy.display_name) copy.display_name = desensitizeText(copy.display_name)
  if (copy.expected) copy.expected = desensitizeText(copy.expected)
  if (copy.locator_value) copy.locator_value = desensitizeText(copy.locator_value)
  if (copy.locators && typeof copy.locators === 'object') {
    copy.locators = Object.fromEntries(
      Object.entries(copy.locators).map(([k, v]) => [k, desensitizeText(String(v))])
    )
  }
  copy.desensitized = true
  return copy
}

/** 根据输入/点击位置生成视频模糊区域（归一化 0-1） */
export function blurRegionForInput(x, y, nativeW, nativeH) {
  const nx = nativeW ? x / nativeW : 0.5
  const ny = nativeH ? y / nativeH : 0.5
  return {
    x: Math.max(0, nx - 0.25),
    y: Math.max(0, ny - 0.04),
    w: 0.5,
    h: 0.08
  }
}

export function blurRegionForKeyboard() {
  return { x: 0, y: 0.72, w: 1, h: 0.28 }
}
