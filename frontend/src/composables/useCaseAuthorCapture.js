/**
 * 同屏编写：手势 → 可视化步骤 + 异步 inspect 补定位
 */
import { ref } from 'vue'
import { deviceApi } from '@/api'

let stepSeq = 1

export function resetStepSeq(n = 1) {
  stepSeq = Math.max(1, Number(n) || 1)
}

export function createEmptyStep(partial = {}) {
  return {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    retry_count: 0,
    on_fail: 'fail',
    ...partial
  }
}

export function applyInspectToStep(step, data) {
  if (!step || !data) return step
  const next = { ...step }
  if (data.display_name) next.display_name = data.display_name
  if (data.element_name) next.element_name = data.element_name
  if (data.locators && typeof data.locators === 'object') next.locators = { ...data.locators }
  if (data.locator_type) next.locator_type = data.locator_type
  if (data.locator_value) next.locator_value = data.locator_value
  if (data.locator_chain) next.locator_chain = data.locator_chain
  if (data.widget_type) next.widget_type = data.widget_type

  const locCount = next.locators ? Object.keys(next.locators).length : 0
  const valid = !!(data.valid || next.element_name || locCount || next.locator_value)
  next.locator_valid = valid

  if (valid && (next.type === 'tap_xy' || next.type === 'click')) {
    if (data.suggested_step_type === 'tap_ocr') {
      next.type = 'tap_ocr'
      next.expected = next.locator_value || next.display_name || next.element_name || ''
    } else {
      next.type = 'click'
    }
  }
  return next
}

export function gestureToStep(kind, payload = {}) {
  if (kind === 'tap') {
    const { x, y } = payload
    return createEmptyStep({
      type: 'tap_xy',
      x,
      y,
      locator_valid: false,
      display_name: `坐标点击 (${x}, ${y})`
    })
  }
  if (kind === 'swipe') {
    const { x1, y1, x2, y2, duration_ms = 300 } = payload
    return createEmptyStep({
      type: 'swipe',
      x1, y1, x2, y2,
      duration_ms,
      display_name: `滑动 (${x1},${y1})→(${x2},${y2})`
    })
  }
  if (kind === 'longpress') {
    const { x, y, duration_ms = 800 } = payload
    return createEmptyStep({
      type: 'long_press',
      x, y,
      duration_ms,
      display_name: `长按 (${x}, ${y})`
    })
  }
  if (kind === 'input') {
    return createEmptyStep({
      type: 'input',
      text: payload.text || '',
      element_name: payload.element_name || '',
      display_name: payload.text ? `输入「${payload.text}」` : '输入文本'
    })
  }
  if (kind === 'wait') {
    return createEmptyStep({
      type: 'wait',
      seconds: payload.seconds ?? 2,
      display_name: `等待 ${payload.seconds ?? 2}s`
    })
  }
  if (kind === 'set_relative_time') {
    return createEmptyStep({
      type: 'set_relative_time',
      offset_minutes: payload.offset_minutes ?? 5,
      confirm: !!payload.confirm,
      display_name: `当前时间+${payload.offset_minutes ?? 5}分钟`
    })
  }
  if (kind === 'assert_text') {
    return createEmptyStep({
      type: 'assert_text',
      expected: payload.expected || '',
      display_name: payload.expected ? `断言「${payload.expected}」` : '断言文本'
    })
  }
  if (kind === 'launch') {
    return createEmptyStep({
      type: 'launch',
      app_package: payload.app_package || '',
      display_name: payload.app_package ? `启动 ${payload.app_package}` : '启动应用'
    })
  }
  if (kind === 'press_key') {
    return createEmptyStep({
      type: 'press_key',
      key: payload.key || 'back',
      display_name: `按键 ${payload.key || 'back'}`
    })
  }
  return createEmptyStep({ type: 'wait', seconds: 1 })
}

/**
 * @param {import('vue').Ref<Array>} stepsRef
 * @param {{ getDeviceId: () => number|string|null, getNativeSize: () => {w:number,h:number}, autoCapture: import('vue').Ref<boolean>, inspectEnabled: import('vue').Ref<boolean> }} opts
 */
export function useCaseAuthorCapture(stepsRef, opts) {
  const pendingManual = ref(null)
  let inspectChain = Promise.resolve()
  let failStreak = 0

  function appendStep(step) {
    stepsRef.value = [...stepsRef.value, step]
    return step.id
  }

  function patchStep(id, patcher) {
    stepsRef.value = stepsRef.value.map(s => (s.id === id ? patcher(s) : s))
  }

  function removeStep(id) {
    stepsRef.value = stepsRef.value.filter(s => s.id !== id)
  }

  function moveStep(id, dir) {
    const list = [...stepsRef.value]
    const idx = list.findIndex(s => s.id === id)
    if (idx < 0) return
    const j = idx + dir
    if (j < 0 || j >= list.length) return
    const tmp = list[idx]
    list[idx] = list[j]
    list[j] = tmp
    stepsRef.value = list
  }

  async function inspectPoint(x, y) {
    const deviceId = opts.getDeviceId?.()
    if (!deviceId) return null
    const { w, h } = opts.getNativeSize?.() || { w: 0, h: 0 }
    try {
      const res = await deviceApi.screenInspect(deviceId, {
        x, y,
        display_width: w || undefined,
        display_height: h || undefined,
        blocking: false
      })
      return res?.data || null
    } catch {
      return null
    }
  }

  function scheduleInspectPatch(stepId, x, y) {
    if (!opts.inspectEnabled?.value) return
    if (failStreak >= 3) return
    inspectChain = inspectChain.then(async () => {
      await sleep(900)
      const data = await inspectPoint(x, y)
      if (!data) {
        failStreak += 1
        return
      }
      const locCount = data.locators && typeof data.locators === 'object'
        ? Object.keys(data.locators).length : 0
      if (!data.valid && !data.element_name && !locCount && !data.locator_value) {
        failStreak += 1
        return
      }
      failStreak = 0
      patchStep(stepId, (s) => applyInspectToStep(s, data))
    }).catch(() => {})
  }

  function handleTap(payload) {
    if (!opts.autoCapture?.value) {
      pendingManual.value = { kind: 'tap', payload }
      return null
    }
    const step = gestureToStep('tap', payload)
    const id = appendStep(step)
    scheduleInspectPatch(id, payload.x, payload.y)
    return id
  }

  function handleSwipe(payload) {
    if (!opts.autoCapture?.value) {
      pendingManual.value = { kind: 'swipe', payload }
      return null
    }
    return appendStep(gestureToStep('swipe', payload))
  }

  function handleLongPress(payload) {
    if (!opts.autoCapture?.value) {
      pendingManual.value = { kind: 'longpress', payload }
      return null
    }
    const step = gestureToStep('longpress', payload)
    const id = appendStep(step)
    scheduleInspectPatch(id, payload.x, payload.y)
    return id
  }

  function commitPendingManual() {
    const pending = pendingManual.value
    if (!pending) return null
    pendingManual.value = null
    if (pending.kind === 'tap') {
      const step = gestureToStep('tap', pending.payload)
      const id = appendStep(step)
      scheduleInspectPatch(id, pending.payload.x, pending.payload.y)
      return id
    }
    if (pending.kind === 'swipe') return appendStep(gestureToStep('swipe', pending.payload))
    if (pending.kind === 'longpress') {
      const step = gestureToStep('longpress', pending.payload)
      const id = appendStep(step)
      scheduleInspectPatch(id, pending.payload.x, pending.payload.y)
      return id
    }
    return null
  }

  function addToolStep(kind, payload) {
    return appendStep(gestureToStep(kind, payload))
  }

  return {
    pendingManual,
    appendStep,
    patchStep,
    removeStep,
    moveStep,
    handleTap,
    handleSwipe,
    handleLongPress,
    commitPendingManual,
    addToolStep,
    inspectPoint
  }
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}
