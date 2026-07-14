/** 从任务日志解析步骤执行进度 */

const STEP_START = /ATP_STEP_START:step=(\d+)/
const STEP_OK = /ATP_STEP_OK:step=(\d+)/
const STEP_FAIL = /CHECKPOINT_FAILED:step=(\d+)/
const STEP_SKIP = /STEP_SKIPPED:step=(\d+)/

export function parseTaskStepProgress(logs = [], executions = []) {
  let activeStep = null
  let failedStep = null
  const passed = new Set()

  for (const log of logs) {
    const msg = log.message || ''
    let m = msg.match(STEP_OK)
    if (m) {
      passed.add(Number(m[1]))
      activeStep = Number(m[1]) + 1
    }
    m = msg.match(STEP_START)
    if (m) activeStep = Number(m[1])
    m = msg.match(STEP_FAIL) || msg.match(STEP_SKIP)
    if (m) failedStep = Number(m[1])
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
