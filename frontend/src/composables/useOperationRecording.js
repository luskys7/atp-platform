/**
 * 全局操作录制会话状态，供投屏页与悬浮入口共享。
 */
import { reactive, computed } from 'vue'

export const operationRecordingState = reactive({
  active: false,
  paused: false,
  sessionId: null,
  deviceId: null,
  deviceName: '',
  stepCount: 0,
  segmentCount: 0,
  durationMs: 0,
  fileSizeBytes: 0,
  watermarkEnabled: true,
  videoRecording: false,
  effectiveFps: 12,
  avgPaintMs: 0,
  startupMs: null,
  performanceGrade: 'good',
  error: null
})

/** 由 DeviceScreen 注册，供顶部浮窗调用 */
export const recordingActionHandlers = {
  pause: null,
  resume: null,
  markSegment: null,
  finish: null,
  cancel: null
}

export function registerRecordingActions(handlers) {
  Object.assign(recordingActionHandlers, handlers)
}

export function unregisterRecordingActions() {
  recordingActionHandlers.pause = null
  recordingActionHandlers.resume = null
  recordingActionHandlers.markSegment = null
  recordingActionHandlers.finish = null
  recordingActionHandlers.cancel = null
}

export function invokeRecordingAction(action) {
  const fn = recordingActionHandlers[action]
  if (typeof fn === 'function') {
    fn()
    return true
  }
  return false
}

export function useOperationRecording() {
  const statusLabel = computed(() => {
    if (operationRecordingState.error) return '录制异常'
    if (!operationRecordingState.active) return '未录制'
    if (operationRecordingState.paused) return '已暂停'
    return '录制中'
  })

  function reset() {
    operationRecordingState.active = false
    operationRecordingState.paused = false
    operationRecordingState.sessionId = null
    operationRecordingState.deviceId = null
    operationRecordingState.deviceName = ''
    operationRecordingState.stepCount = 0
    operationRecordingState.segmentCount = 0
    operationRecordingState.durationMs = 0
    operationRecordingState.fileSizeBytes = 0
    operationRecordingState.videoRecording = false
    operationRecordingState.effectiveFps = 12
    operationRecordingState.avgPaintMs = 0
    operationRecordingState.startupMs = null
    operationRecordingState.performanceGrade = 'good'
    operationRecordingState.error = null
  }

  function syncFromSession(sessionId, deviceId, deviceName = '') {
    operationRecordingState.active = true
    operationRecordingState.sessionId = sessionId
    operationRecordingState.deviceId = deviceId
    operationRecordingState.deviceName = deviceName
    operationRecordingState.stepCount = 0
    operationRecordingState.segmentCount = 0
    operationRecordingState.error = null
  }

  return {
    state: operationRecordingState,
    statusLabel,
    reset,
    syncFromSession,
    registerRecordingActions,
    unregisterRecordingActions,
    invokeRecordingAction
  }
}
