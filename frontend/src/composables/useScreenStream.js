import { ref, shallowRef, toValue, computed } from 'vue'
import { deviceApi } from '@/api'
import { ElMessage } from 'element-plus'

/** 跨页面保活的投屏 WebSocket 会话（按 deviceId） */
const sessions = new Map()
const sessionLabels = new Map()

export const activeScreenSessions = ref([])

function refreshActiveSessions() {
  activeScreenSessions.value = [...sessions.entries()]
    .filter(([, s]) => {
      const wsOpen = s.ws?.readyState === WebSocket.OPEN
      const wsConnecting = s.ws?.readyState === WebSocket.CONNECTING
      return s.connected.value || s.connecting.value || wsOpen || wsConnecting
    })
    .map(([deviceId, s]) => ({
      deviceId,
      name: sessionLabels.get(deviceId)?.name || sessionLabels.get(String(deviceId))?.name || `设备 #${deviceId}`,
      connected: s.connected.value || s.ws?.readyState === WebSocket.OPEN,
      connecting: s.connecting.value || s.ws?.readyState === WebSocket.CONNECTING,
      streamMode: s.streamMode.value,
      fps: s.fps.value
    }))
}

export function syncActiveScreenSessions() {
  for (const [, s] of sessions) {
    if (s.ws?.readyState === WebSocket.OPEN) {
      s.connected.value = true
      s.connecting.value = false
    }
  }
  refreshActiveSessions()
}

export function setScreenDeviceInfo(deviceId, info = {}) {
  const key = String(deviceId)
  sessionLabels.set(key, {
    name: info.name || info.serial_number || `设备 #${key}`,
    serial: info.serial_number || ''
  })
  refreshActiveSessions()
}

export function stopAllScreenStreams() {
  for (const [, session] of sessions) {
    if (session.ws) {
      session.ws.close()
      session.ws = null
    }
    session.connected.value = false
    session.connecting.value = false
    session.streamMode.value = ''
    session.fps.value = 0
  }
  refreshActiveSessions()
  ElMessage.info('所有投屏已断开')
}

export function captureSessionSnapshot(deviceId, canvas) {
  const session = sessions.get(String(deviceId))
  if (!session || !canvas?.width) return
  if (!session.snapshotCanvas) {
    session.snapshotCanvas = document.createElement('canvas')
  }
  const snap = session.snapshotCanvas
  if (snap.width !== canvas.width || snap.height !== canvas.height) {
    snap.width = canvas.width
    snap.height = canvas.height
  }
  snap.getContext('2d').drawImage(canvas, 0, 0)
  session.hasSnapshot = true
}

export function getSessionSnapshot(deviceId) {
  const session = sessions.get(String(deviceId))
  if (!session?.hasSnapshot || !session.snapshotCanvas?.width) return ''
  try {
    return session.snapshotCanvas.toDataURL('image/jpeg', 0.75)
  } catch {
    return ''
  }
}

function wsUrl(proxyPath) {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}${proxyPath}`
}

function getSession(deviceId) {
  const key = String(deviceId)
  if (!sessions.has(key)) {
    sessions.set(key, {
      ws: null,
      connected: ref(false),
      connecting: ref(false),
      lastFrame: shallowRef(null),
      lastMeta: null,
      nativeW: ref(1080),
      nativeH: ref(1920),
      streamW: ref(1080),
      streamH: ref(1920),
      statusText: ref('点击「开始投屏」连接设备'),
      streamMode: ref(''),
      fps: ref(0),
      latencyMs: ref(0),
      onFrameCallbacks: new Set(),
      onMetaCallbacks: new Set(),
      hasSnapshot: false,
      snapshotCanvas: null,
      streamFormat: ref('')
    })
  }
  return sessions.get(key)
}

function resolveDeviceId(deviceIdInput) {
  const raw = toValue(deviceIdInput)
  if (raw == null || raw === '') return ''
  const key = String(raw)
  if (key === 'undefined' || key === 'null' || key === '[object Object]') return ''
  return key
}

export function useScreenStream(deviceIdInput) {
  const sessionKey = computed(() => resolveDeviceId(deviceIdInput))

  function activeSession() {
    const key = sessionKey.value
    return key ? getSession(key) : null
  }

  let lastFrameAt = 0

  function emitFrame(buffer) {
    const session = activeSession()
    if (!session) return
    const now = performance.now()
    if (lastFrameAt > 0) {
      session.latencyMs.value = Math.round(now - lastFrameAt)
      session.fps.value = Math.min(60, Math.round(1000 / Math.max(session.latencyMs.value, 1)))
    }
    lastFrameAt = now
    session.lastFrame.value = buffer
    if (session.connected.value) refreshActiveSessions()
    session.onFrameCallbacks.forEach(fn => fn(buffer))
  }

  function emitMeta(meta) {
    const session = activeSession()
    if (!session) return
    if (meta.mode === 'h264') {
      session.streamMode.value = 'scrcpy'
      session.streamFormat.value = 'h264'
    } else if (meta.mode === 'jpeg') {
      session.streamMode.value = meta.fallback ? 'adb' : 'jpeg'
      session.streamFormat.value = 'jpeg'
    }
    session.lastMeta = meta
    if (meta.width) session.streamW.value = meta.width
    if (meta.height) session.streamH.value = meta.height
    if (meta.native_width) session.nativeW.value = meta.native_width
    if (meta.native_height) session.nativeH.value = meta.native_height
    refreshActiveSessions()
    session.onMetaCallbacks.forEach(fn => fn(meta))
  }

  function bindWsHandlers(ws) {
    ws.onopen = () => {
      const session = activeSession()
      if (!session) return
      session.connected.value = true
      session.connecting.value = false
      session.statusText.value = '投屏中'
      refreshActiveSessions()
      ElMessage.success('投屏已连接')
    }
    ws.onmessage = (ev) => {
      if (typeof ev.data === 'string') {
        try {
          emitMeta(JSON.parse(ev.data))
        } catch { /* ignore */ }
        return
      }
      emitFrame(ev.data)
    }
    ws.onerror = () => {
      ElMessage.error('WebSocket 连接失败')
      refreshActiveSessions()
      stopStream(true)
    }
    ws.onclose = () => {
      const session = activeSession()
      if (!session) return
      session.connected.value = false
      session.connecting.value = false
      session.fps.value = 0
      session.streamMode.value = ''
      refreshActiveSessions()
      if (session.ws === ws) {
        session.statusText.value = '连接已断开'
        session.ws = null
      }
    }
  }

  async function startStream(options = {}) {
    const id = sessionKey.value
    if (!id) {
      ElMessage.warning('设备 ID 无效')
      return
    }
    const session = getSession(id)
    if (session.connecting.value) return
    if (session.ws?.readyState === WebSocket.OPEN) {
      session.connected.value = true
      session.statusText.value = '投屏中'
      refreshActiveSessions()
      return
    }
    if (session.connected.value) return

    session.connecting.value = true
    session.statusText.value = '正在建立 WebSocket 连接...'
    refreshActiveSessions()
    try {
      const res = await deviceApi.startScreen(id)
      const data = res.data
      session.nativeW.value = data.screen_width || 1080
      session.nativeH.value = data.screen_height || 1920
      session.streamW.value = session.nativeW.value
      session.streamH.value = session.nativeH.value
      let wsPath = data.proxy_ws_url
      if (options.forceJpeg) {
        wsPath += `${wsPath.includes('?') ? '&' : '?'}mode=jpeg`
      }
      const ws = new WebSocket(wsUrl(wsPath))
      ws.binaryType = 'arraybuffer'
      session.ws = ws
      bindWsHandlers(ws)
    } catch {
      session.connecting.value = false
      session.statusText.value = '连接失败'
      refreshActiveSessions()
    }
  }

  function stopStream(silent = false) {
    const session = activeSession()
    if (!session) return
    if (session.ws) {
      session.ws.close()
      session.ws = null
    }
    session.connected.value = false
    session.connecting.value = false
    session.lastFrame.value = null
    session.hasSnapshot = false
    session.fps.value = 0
    session.latencyMs.value = 0
    session.streamMode.value = ''
    session.streamFormat.value = ''
    lastFrameAt = 0
    session.statusText.value = '点击「开始投屏」连接设备'
    refreshActiveSessions()
    if (!silent) ElMessage.info('投屏已断开')
  }

  function attachFrameListener(fn) {
    const session = activeSession()
    if (!session) return () => {}
    session.onFrameCallbacks.add(fn)
    const fmt = session.streamFormat.value
    if (session.lastFrame.value && fmt && fmt !== 'h264') {
      fn(session.lastFrame.value)
    }
    return () => session.onFrameCallbacks.delete(fn)
  }

  function attachMetaListener(fn) {
    const session = activeSession()
    if (!session) return () => {}
    session.onMetaCallbacks.add(fn)
    if (session.lastMeta) fn(session.lastMeta)
    return () => session.onMetaCallbacks.delete(fn)
  }

  function resumeIfAlive() {
    const session = activeSession()
    if (!session) return false
    const ws = session.ws
    if (ws && ws.readyState === WebSocket.OPEN) {
      session.connected.value = true
      session.connecting.value = false
      session.statusText.value = '投屏中'
      refreshActiveSessions()
      return true
    }
    if (ws && ws.readyState === WebSocket.CONNECTING) {
      session.connecting.value = true
      session.statusText.value = '正在建立 WebSocket 连接...'
      refreshActiveSessions()
      return true
    }
    return false
  }

  return {
    connected: computed({
      get: () => activeSession()?.connected.value ?? false,
      set: (v) => { const s = activeSession(); if (s) s.connected.value = v }
    }),
    connecting: computed(() => activeSession()?.connecting.value ?? false),
    nativeW: computed({
      get: () => activeSession()?.nativeW.value ?? 1080,
      set: (v) => { const s = activeSession(); if (s) s.nativeW.value = v }
    }),
    nativeH: computed({
      get: () => activeSession()?.nativeH.value ?? 1920,
      set: (v) => { const s = activeSession(); if (s) s.nativeH.value = v }
    }),
    streamW: computed(() => activeSession()?.streamW.value ?? 1080),
    streamH: computed(() => activeSession()?.streamH.value ?? 1920),
    statusText: computed(() => activeSession()?.statusText.value ?? '点击「开始投屏」连接设备'),
    streamMode: computed(() => activeSession()?.streamMode.value ?? ''),
    fps: computed(() => activeSession()?.fps.value ?? 0),
    latencyMs: computed(() => activeSession()?.latencyMs.value ?? 0),
    lastFrame: computed(() => activeSession()?.lastFrame.value ?? null),
    startStream,
    stopStream: () => stopStream(false),
    attachFrameListener,
    attachMetaListener,
    resumeIfAlive
  }
}
