/** 从任务日志解析步骤执行进度 */

const STEP_START = /ATP_STEP_(?:START|BEGIN)[^\d]*step=(\d+)/
const STEP_OK = /ATP_STEP_(?:OK|END)[^\d]*step=(\d+)(?:[^\n]*status=ok)?/
const STEP_END = /ATP_STEP_END step=(\d+) status=(ok|fail|skip|interrupt|exception|ignore)/
const STEP_FAIL = /CHECKPOINT_FAILED:step=(\d+)|ATP_STEP_END step=(\d+) status=(?:fail|interrupt|exception)/
const STEP_SKIP = /STEP_SKIPPED:step=(\d+)|ATP_STEP_END step=(\d+) status=skip/

export function parseTaskStepProgress(logs = [], executions = []) {
  let activeStep = null
  let failedStep = null
  const passed = new Set()

  for (const log of logs) {
    const msg = log.message || ''
    let m = msg.match(STEP_END)
    if (m) {
      const n = Number(m[1])
      const status = m[2]
      if (status === 'ok' || status === 'skip' || status === 'ignore') {
        passed.add(n)
        activeStep = n + 1
      } else if (status === 'fail' || status === 'interrupt' || status === 'exception') {
        failedStep = n
      }
      continue
    }
    m = msg.match(STEP_OK)
    if (m && !msg.includes('ATP_STEP_END')) {
      passed.add(Number(m[1]))
      activeStep = Number(m[1]) + 1
    }
    m = msg.match(STEP_START)
    if (m) activeStep = Number(m[1])
    m = msg.match(STEP_FAIL) || msg.match(STEP_SKIP)
    if (m) failedStep = Number(m[1] || m[2])
  }

  for (const exec of executions) {
    if (exec.failed_step_index) failedStep = exec.failed_step_index
  }

  return { activeStep, failedStep, passedSteps: passed }
}

export function stepStatus(index, { activeStep, failedStep, passedSteps }, taskStatus) {
  const n = index + 1
  if (failedStep === n) return 'failed'
  if (passedSteps.has(n)) return 'passed'
  if (activeStep === n && ['running', 'waiting_manual', 'queued'].includes(taskStatus)) return 'active'
  if (failedStep != null && n < failedStep) return 'passed'
  return 'pending'
}

/** 将原始日志整理为步骤列表事件（类似 Sonic 步骤列表） */
export function parseStepEvents(logs = []) {
  const events = []
  for (const log of logs) {
    const msg = String(log.message || '')
    const time = log.created_at
    let m = msg.match(/ATP_STEP_BEGIN step=(\d+) type=(\w+)(?:[^\n]*display=([^\s]+))?/)
    if (m) {
      events.push({
        id: `b-${log.id}`,
        kind: 'begin',
        step: Number(m[1]),
        type: m[2],
        display: m[3] || m[2],
        time,
        message: `开始执行 '${m[3] || m[2]}' 步骤`,
        tone: 'info'
      })
      continue
    }
    m = msg.match(/ATP_STEP_END step=(\d+) status=(\w+)(?: error=(.+))?/)
    if (m) {
      const status = m[2]
      const err = (m[3] || '').trim()
      const ok = status === 'ok'
      events.push({
        id: `e-${log.id}`,
        kind: 'end',
        step: Number(m[1]),
        status,
        time,
        message: ok
          ? `步骤 #${m[1]} 执行完成`
          : `步骤 #${m[1]} ${status}${err ? `：${err}` : ''}`,
        tone: ok ? 'success' : (status === 'skip' || status === 'ignore' ? 'warn' : 'error'),
        detail: err || ''
      })
      continue
    }
    if (/AssertionError|RuntimeError|Traceback|Script error|CHECKPO?INT_FAILED/i.test(msg)) {
      events.push({
        id: `x-${log.id}`,
        kind: 'error',
        time,
        message: msg.length > 240 ? `${msg.slice(0, 240)}…` : msg,
        tone: 'error',
        detail: msg
      })
    }
  }
  return events
}
