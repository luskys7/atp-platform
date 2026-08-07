<template>
  <div class="screen-panel" :class="{ compact }">
    <div class="screen-toolbar" v-if="showToolbar">
      <el-tag v-if="connected" type="success" size="small">已连接</el-tag>
      <el-tag v-else-if="connecting" type="warning" size="small">连接中</el-tag>
      <el-tag v-else type="info" size="small">未连接</el-tag>
      <el-tag v-if="connected && streamMode === 'scrcpy'" type="success" size="small" effect="plain">scrcpy</el-tag>
      <el-tag v-if="connected && streamMode === 'jpeg'" type="warning" size="small" effect="plain">adb</el-tag>
      <el-tag v-if="connected && fps > 0" type="info" size="small">{{ fps }} FPS</el-tag>
      <el-button v-if="!connected" type="primary" size="small" :loading="connecting" @click="connectStream">连接</el-button>
      <el-button v-else size="small" @click="stopStream">断开</el-button>
    </div>

    <div class="screen-wrap">
      <div class="screen-device" :style="deviceShellStyle">
        <div class="screen-frame" :style="frameStyle">
          <canvas
            ref="canvasRef"
            class="screen-canvas"
            :class="{ controllable: controllable && connected }"
            @mousedown.prevent="onMouseDown"
            @mouseup.prevent="onMouseUp"
            @mouseleave="onMouseLeave"
          />
          <div v-if="!hasFrame" class="screen-placeholder" :class="{ connected: connected }">
            <el-icon :size="compact ? 32 : 48"><Monitor /></el-icon>
            <p>{{ statusText }}</p>
            <p class="frame-dim">{{ resolutionLabel }}</p>
            <el-button v-if="!connecting && !connected" type="primary" size="small" @click="connectStream">开始投屏</el-button>
          </div>
          <div v-if="connected && !hasFrame" class="screen-loading">
            <el-icon class="spin"><Loading /></el-icon>
            <span>等待画面...</span>
          </div>
        </div>
        <ScreenNavBar
          v-if="showNavBar"
          :disabled="!connected"
          bar-width="100%"
          @key="pressNavKey"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, onActivated, nextTick } from 'vue'
import { deviceApi } from '@/api'
import {
  useScreenStream, setScreenDeviceInfo, captureSessionSnapshot
} from '@/composables/useScreenStream'
import { fixedScreenFrameStyle, frameSizeFromDevice, frameMaxHeight, NAV_BAR_HEIGHT } from '@/composables/screenFrameStyle'
import { createScreenCanvasRenderer } from '@/composables/useScreenCanvas'
import { normalizeDevice } from '@/utils/device'
import ScreenNavBar from '@/components/ScreenNavBar.vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  deviceId: { type: [String, Number], required: true },
  compact: { type: Boolean, default: false },
  autoConnect: { type: Boolean, default: false },
  showToolbar: { type: Boolean, default: true },
  showNavBar: { type: Boolean, default: true },
  /** 为 true 时允许在画面上点击/滑动控机，并向外 emit 操作事件 */
  controllable: { type: Boolean, default: true },
  maxHeight: { type: String, default: 'calc(100vh - 160px)' }
})

const emit = defineEmits(['control-tap', 'control-swipe', 'control-longpress', 'control-nav'])

const canvasRef = ref(null)
const hasFrame = ref(false)
const frameW = ref(1080)
const frameH = ref(1920)
const layoutStyle = ref(fixedScreenFrameStyle(1080, 1920))
let dragStart = null
let longPressTimer = null
let longPressFired = false
let detachFrame = null
let detachMeta = null
let renderer = null
let noFrameTimer = null
let triedJpegFallback = false
let scrcpyRetryCount = 0
const LONG_PRESS_MS = 550
const SWIPE_THRESHOLD = 20

function clearLongPressTimer() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function clearNoFrameTimer() {
  if (noFrameTimer) {
    clearTimeout(noFrameTimer)
    noFrameTimer = null
  }
}

function scheduleNoFrameCheck() {
  clearNoFrameTimer()
  if (!connected.value || hasFrame.value) return
  noFrameTimer = setTimeout(async () => {
    if (hasFrame.value || !connected.value) return
    if (scrcpyRetryCount < 2) {
      scrcpyRetryCount += 1
      stopStream()
      await startStream()
      scheduleNoFrameCheck()
      return
    }
    if (!triedJpegFallback) {
      triedJpegFallback = true
      stopStream()
      await startStream({ forceJpeg: true })
      scheduleNoFrameCheck()
    }
  }, 6000)
}

async function connectStream() {
  triedJpegFallback = false
  scrcpyRetryCount = 0
  hasFrame.value = false
  await startStream()
  scheduleNoFrameCheck()
}

const {
  connected, connecting, nativeW, nativeH, statusText, streamMode, fps,
  startStream, stopStream, resumeIfAlive,
  attachFrameListener, attachMetaListener
} = useScreenStream(props.deviceId)

const resolutionLabel = computed(() => {
  const w = nativeW.value || frameW.value
  const h = nativeH.value || frameH.value
  return `${w} × ${h}`
})

const frameStyle = computed(() => layoutStyle.value)

function updateLayoutSize() {
  const { w, h } = frameSizeFromDevice({ screen_width: frameW.value, screen_height: frameH.value })
  const extra = props.showNavBar ? NAV_BAR_HEIGHT : 0
  layoutStyle.value = fixedScreenFrameStyle(
    w,
    h,
    frameMaxHeight(props.maxHeight, extra),
    props.compact ? 280 : null
  )
}

const deviceShellStyle = computed(() => ({
  maxHeight: props.maxHeight
}))

function applyDeviceFrame(d) {
  const dev = normalizeDevice(d)
  const { w, h } = frameSizeFromDevice(dev)
  frameW.value = w
  frameH.value = h
  if (dev?.screen_width) nativeW.value = dev.screen_width
  if (dev?.screen_height) nativeH.value = dev.screen_height
  updateLayoutSize()
  return dev
}

async function initDevice() {
  const res = await deviceApi.get(props.deviceId)
  const dev = applyDeviceFrame(res.data)
  setScreenDeviceInfo(props.deviceId, {
    name: dev?.name || dev?.serial_number,
    serial_number: dev?.serial_number
  })
}

function bindCanvasPipeline() {
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()

  renderer = createScreenCanvasRenderer(canvasRef, {
    onFrameDrawn: () => {
      hasFrame.value = true
      clearNoFrameTimer()
      if (canvasRef.value) captureSessionSnapshot(props.deviceId, canvasRef.value)
    },
    onDecodeError: () => {
      if (connected.value && !hasFrame.value) scheduleNoFrameCheck()
    }
  })

  detachMeta = attachMetaListener(meta => {
    renderer.onMeta(meta)
    if (meta.native_width) nativeW.value = meta.native_width
    if (meta.native_height) nativeH.value = meta.native_height
    else if (meta.width && meta.height) {
      nativeW.value = meta.width
      nativeH.value = meta.height
    }
  })
  detachFrame = attachFrameListener(buffer => renderer.onFrame(buffer))
}

function restoreStreamView() {
  hasFrame.value = false
  resumeIfAlive()
  nextTick(() => nextTick(bindCanvasPipeline))
}

function mapCoords(event) {
  const canvas = canvasRef.value
  if (!canvas?.width) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  const cx = (event.clientX - rect.left) * (canvas.width / rect.width)
  const cy = (event.clientY - rect.top) * (canvas.height / rect.height)
  const nw = nativeW.value || canvas.width
  const nh = nativeH.value || canvas.height
  return {
    x: Math.round(Math.max(0, Math.min(cx, canvas.width)) * nw / canvas.width),
    y: Math.round(Math.max(0, Math.min(cy, canvas.height)) * nh / canvas.height)
  }
}

function onMouseDown(event) {
  if (!props.controllable || !connected.value) return
  dragStart = mapCoords(event)
  longPressFired = false
  clearLongPressTimer()
  longPressTimer = setTimeout(async () => {
    if (!dragStart || !connected.value || !props.controllable) return
    longPressFired = true
    const point = { ...dragStart }
    try {
      // 长按：先短点一次近似；多数 Android 用 swipe 同点长 duration
      await deviceApi.screenSwipe(props.deviceId, {
        x1: point.x, y1: point.y, x2: point.x, y2: point.y, duration_ms: 800
      })
      emit('control-longpress', { x: point.x, y: point.y, duration_ms: 800 })
    } catch (e) {
      ElMessage.error(e?.message || '长按发送失败')
    }
  }, LONG_PRESS_MS)
}

async function onMouseUp(event) {
  clearLongPressTimer()
  if (!dragStart || !connected.value || !props.controllable) {
    dragStart = null
    return
  }
  const start = dragStart
  const end = mapCoords(event)
  dragStart = null
  if (longPressFired) {
    longPressFired = false
    return
  }
  const dx = Math.abs(end.x - start.x)
  const dy = Math.abs(end.y - start.y)
  try {
    if (dx > SWIPE_THRESHOLD || dy > SWIPE_THRESHOLD) {
      await deviceApi.screenSwipe(props.deviceId, {
        x1: start.x, y1: start.y, x2: end.x, y2: end.y, duration_ms: 300
      })
      emit('control-swipe', {
        x1: start.x, y1: start.y, x2: end.x, y2: end.y, duration_ms: 300
      })
    } else {
      await deviceApi.screenTap(props.deviceId, { x: end.x, y: end.y })
      emit('control-tap', { x: end.x, y: end.y })
    }
  } catch (e) {
    ElMessage.error(e?.message || '操作发送失败')
  }
}

function onMouseLeave() {
  clearLongPressTimer()
  if (!longPressFired) dragStart = null
  longPressFired = false
}

async function pressNavKey(key) {
  if (!connected.value) return
  try {
    await deviceApi.screenKey(props.deviceId, { key })
    emit('control-nav', { key })
  } catch {
    ElMessage.error('按键发送失败')
  }
}

watch(() => props.deviceId, async () => {
  hasFrame.value = false
  await initDevice()
  restoreStreamView()
})

watch(connected, (v) => {
  if (!v) {
    hasFrame.value = false
    triedJpegFallback = false
    scrcpyRetryCount = 0
    clearNoFrameTimer()
    if (renderer) {
      renderer.destroy()
      renderer = null
    }
  } else {
    nextTick(() => {
      if (!renderer) bindCanvasPipeline()
      scheduleNoFrameCheck()
    })
  }
})

onMounted(async () => {
  setScreenDeviceInfo(props.deviceId, { name: `设备 #${props.deviceId}` })
  await initDevice()
  bindCanvasPipeline()
  resumeIfAlive()
  window.addEventListener('resize', updateLayoutSize)
  if (props.autoConnect && !connected.value) connectStream()
  else if (connected.value) scheduleNoFrameCheck()
})

onActivated(() => restoreStreamView())

onUnmounted(() => {
  window.removeEventListener('resize', updateLayoutSize)
  clearNoFrameTimer()
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()
})

defineExpose({ connected, startStream, stopStream, nativeW, nativeH })
</script>

<style scoped lang="scss">
.screen-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
  width: fit-content;
}

.screen-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.screen-wrap {
  line-height: 0;
  width: fit-content;
  max-height: 100%;
}

.screen-device {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  max-height: inherit;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.15);
}

.screen-frame {
  position: relative;
  background: var(--atp-screen-bg);
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
  border: 2px solid var(--atp-dark-border, #334155);
}

.screen-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: default;
  user-select: none;
  touch-action: none;
  vertical-align: top;
}
.screen-canvas.controllable {
  cursor: crosshair;
}

.screen-placeholder {
  position: absolute;
  inset: 0;
  z-index: 2;
  color: var(--atp-screen-text-muted);
  text-align: center;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  line-height: 1.5;
  background: var(--atp-screen-bg);

  &.connected {
    background: transparent;
    pointer-events: none;
  }
}

.frame-dim {
  font-size: 12px;
  color: var(--atp-screen-text-muted);
  font-family: ui-monospace, Consolas, monospace;
  margin: 0;
}

.screen-loading {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--atp-screen-text-muted);
  background: rgba(45, 42, 62, 0.75);
  padding: 4px 10px;
  border-radius: 12px;
}

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
