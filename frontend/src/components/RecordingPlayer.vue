<template>
  <div class="recording-player">
    <div class="player-toolbar">
      <el-button-group>
        <el-button size="small" :type="speed === 0.5 ? 'primary' : 'default'" @click="speed = 0.5">0.5x</el-button>
        <el-button size="small" :type="speed === 1 ? 'primary' : 'default'" @click="speed = 1">1x</el-button>
        <el-button size="small" :type="speed === 1.5 ? 'primary' : 'default'" @click="speed = 1.5">1.5x</el-button>
        <el-button size="small" :type="speed === 2 ? 'primary' : 'default'" @click="speed = 2">2x</el-button>
        <el-button size="small" :type="speed === 3 ? 'primary' : 'default'" @click="speed = 3">3x</el-button>
      </el-button-group>
      <el-button
        v-if="editableAnnotations"
        size="small"
        :type="annotateMode ? 'warning' : 'default'"
        @click="toggleAnnotate"
      >
        {{ annotateMode ? '完成标注' : '画面标注' }}
      </el-button>
      <el-slider v-model="zoom" :min="100" :max="300" :step="10" style="width:120px;margin-left:12px" />
      <span class="zoom-label">{{ zoom }}%</span>
    </div>

    <div v-if="editableAnnotations && savedAnnotations.length" class="annotation-panel">
      <div class="annotation-panel-head">
        <span>标注 ({{ savedAnnotations.length }})</span>
        <el-button size="small" link type="danger" @click="clearAllAnnotations">清空</el-button>
      </div>
      <div
        v-for="(a, idx) in savedAnnotations"
        :key="idx"
        class="annotation-item"
        :class="{ active: selectedAnnotationIdx === idx }"
        @click="selectAnnotation(idx)"
      >
        <span class="ann-time">{{ fmtSec(a.timeSec) }}</span>
        <el-input
          v-if="editingIdx === idx"
          v-model="editingLabel"
          size="small"
          @keyup.enter="commitEditLabel(idx)"
          @blur="commitEditLabel(idx)"
        />
        <span v-else class="ann-label">{{ a.label || `标注 ${idx + 1}` }}</span>
        <div class="ann-actions">
          <el-button size="small" link @click.stop="startEditLabel(idx)">编辑</el-button>
          <el-button size="small" link type="danger" @click.stop="removeAnnotation(idx)">删除</el-button>
        </div>
      </div>
    </div>

    <div class="player-viewport" ref="viewportRef" @wheel.prevent="onWheel">
      <div class="player-stage" :style="stageStyle">
        <video
          ref="videoRef"
          :src="src"
          class="player-video"
          @timeupdate="onTimeUpdate"
          @loadedmetadata="onLoaded"
          @click="togglePlay"
        />
        <canvas
          ref="annotateCanvasRef"
          class="annotate-canvas"
          :class="{ active: annotateMode }"
          @mousedown="onAnnotateDown"
          @mousemove="onAnnotateMove"
          @mouseup="onAnnotateUp"
        />
      </div>
    </div>

    <div class="player-controls">
      <el-button size="small" circle @click="togglePlay">
        <el-icon><VideoPlay v-if="!playing" /><VideoPause v-else /></el-icon>
      </el-button>
      <div class="timeline-wrap">
        <div v-if="markerItems.length" class="marker-track">
          <button
            v-for="m in markerItems"
            :key="m.key"
            type="button"
            class="marker-dot"
            :class="{ active: m.index === activeMarkerIndex }"
            :style="{ left: markerLeft(m) }"
            :title="m.label"
            @click.stop="seekMarker(m)"
          />
        </div>
        <el-slider
          v-model="currentSec"
          :max="durationSec || 1"
          :step="0.1"
          :format-tooltip="fmtSec"
          class="timeline"
          @input="seekTo"
        />
      </div>
      <span class="time-label">{{ fmtSec(currentSec) }} / {{ fmtSec(durationSec) }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  src: { type: String, required: true },
  markers: { type: Array, default: () => [] },
  activeMarkerIndex: { type: Number, default: -1 },
  annotations: { type: Array, default: () => [] },
  editableAnnotations: { type: Boolean, default: false }
})

const emit = defineEmits(['timeupdate', 'seek', 'marker-click', 'marker-active', 'annotations-change'])

const videoRef = ref(null)
const viewportRef = ref(null)
const annotateCanvasRef = ref(null)
const playing = ref(false)
const speed = ref(1)
const zoom = ref(100)
const currentSec = ref(0)
const durationSec = ref(0)
const annotateMode = ref(false)
const savedAnnotations = ref([])
const selectedAnnotationIdx = ref(-1)
const editingIdx = ref(-1)
const editingLabel = ref('')
let drawing = false
let startPt = null
let draftEnd = null

const stageStyle = computed(() => ({
  transform: `scale(${zoom.value / 100})`,
  transformOrigin: 'center center'
}))

const markerItems = computed(() =>
  (props.markers || [])
    .map((m, i) => ({
      key: `${m.index ?? i}-${m.timeSec ?? m.offsetMs ?? 0}`,
      index: m.index ?? i,
      timeSec: m.timeSec != null ? m.timeSec : (m.offsetMs || 0) / 1000,
      label: m.label || `步骤 ${(m.index ?? i) + 1}`
    }))
    .filter(m => m.timeSec >= 0)
)

function markerLeft(m) {
  const max = durationSec.value || 1
  const pct = Math.min(100, Math.max(0, (m.timeSec / max) * 100))
  return `${pct}%`
}

function seekMarker(m) {
  seekTo(m.timeSec)
  emit('marker-click', m.index)
}

function fmtSec(v) {
  const s = Math.floor(v || 0)
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function togglePlay() {
  const v = videoRef.value
  if (!v) return
  if (v.paused) {
    v.play()
    playing.value = true
  } else {
    v.pause()
    playing.value = false
  }
}

function seekTo(val) {
  const v = videoRef.value
  if (!v) return
  v.currentTime = val
  emit('seek', val)
}

function onTimeUpdate() {
  const v = videoRef.value
  if (!v) return
  currentSec.value = v.currentTime
  emit('timeupdate', v.currentTime)
  syncMarkers()
  redrawAnnotations()
}

function onLoaded() {
  const v = videoRef.value
  if (v) durationSec.value = v.duration || 0
  resizeAnnotateCanvas()
  redrawAnnotations()
}

function syncMarkers() {
  if (!markerItems.value.length) return
  const t = currentSec.value
  let nearest = -1
  let minDiff = Infinity
  markerItems.value.forEach(m => {
    const diff = Math.abs(m.timeSec - t)
    if (diff < minDiff && m.timeSec <= t + 0.8) {
      minDiff = diff
      nearest = m.index
    }
  })
  if (nearest >= 0) emit('marker-active', nearest)
}

watch(speed, (v) => {
  if (videoRef.value) videoRef.value.playbackRate = v
})

watch(() => props.src, () => {
  currentSec.value = 0
  playing.value = false
})

function seekToMs(ms) {
  seekTo(ms / 1000)
  if (videoRef.value) videoRef.value.play()
  playing.value = true
}

defineExpose({ seekToMs, seekTo })

function onWheel(e) {
  zoom.value = Math.min(300, Math.max(100, zoom.value + (e.deltaY > 0 ? -10 : 10)))
}

function emitAnnotations() {
  emit('annotations-change', [...savedAnnotations.value])
}

function selectAnnotation(idx) {
  selectedAnnotationIdx.value = idx
  const a = savedAnnotations.value[idx]
  if (a?.timeSec != null) seekTo(a.timeSec)
}

function startEditLabel(idx) {
  editingIdx.value = idx
  editingLabel.value = savedAnnotations.value[idx]?.label || ''
}

function commitEditLabel(idx) {
  if (editingIdx.value !== idx) return
  const item = savedAnnotations.value[idx]
  if (item) item.label = editingLabel.value.trim() || item.label
  editingIdx.value = -1
  emitAnnotations()
  redrawAnnotations()
}

function removeAnnotation(idx) {
  savedAnnotations.value.splice(idx, 1)
  if (selectedAnnotationIdx.value === idx) selectedAnnotationIdx.value = -1
  emitAnnotations()
  redrawAnnotations()
}

function clearAllAnnotations() {
  savedAnnotations.value = []
  selectedAnnotationIdx.value = -1
  emitAnnotations()
  redrawAnnotations()
}

function syncAnnotations(list) {
  savedAnnotations.value = Array.isArray(list) ? list.map(a => ({ ...a })) : []
  redrawAnnotations()
}

watch(() => props.annotations, (v) => syncAnnotations(v), { immediate: true, deep: true })

function visibleAnnotations() {
  const t = currentSec.value
  return savedAnnotations.value.filter(a => Math.abs((a.timeSec ?? 0) - t) <= 1.5)
}

function redrawAnnotations() {
  const c = annotateCanvasRef.value
  const v = videoRef.value
  if (!c || !v) return
  const ctx = c.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, c.width, c.height)
  const w = c.width
  const h = c.height
  const t = currentSec.value
  savedAnnotations.value.forEach((a, idx) => {
    if (Math.abs((a.timeSec ?? 0) - t) > 1.5) return
    const isSelected = idx === selectedAnnotationIdx.value
    ctx.strokeStyle = isSelected ? '#38bdf8' : '#f59e0b'
    ctx.lineWidth = isSelected ? 3 : 2
    ctx.strokeRect(a.x * w, a.y * h, a.w * w, a.h * h)
    if (a.label) {
      ctx.fillStyle = 'rgba(245,158,11,0.85)'
      ctx.font = '11px sans-serif'
      ctx.fillText(a.label, a.x * w + 4, a.y * h + 14)
    }
  })
  if (drawing && startPt && draftEnd) {
    ctx.strokeStyle = '#38bdf8'
    ctx.lineWidth = 2
    ctx.strokeRect(startPt.x, startPt.y, draftEnd.x - startPt.x, draftEnd.y - startPt.y)
  }
}

function toggleAnnotate() {
  if (!props.editableAnnotations && !annotateMode.value) return
  annotateMode.value = !annotateMode.value
  resizeAnnotateCanvas()
  redrawAnnotations()
}
function resizeAnnotateCanvas() {
  const v = videoRef.value
  const c = annotateCanvasRef.value
  if (!v || !c) return
  c.width = v.clientWidth
  c.height = v.clientHeight
  redrawAnnotations()
}

function onAnnotateDown(e) {
  if (!annotateMode.value) return
  drawing = true
  const rect = annotateCanvasRef.value.getBoundingClientRect()
  startPt = { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

function onAnnotateMove(e) {
  if (!drawing || !startPt) return
  const rect = annotateCanvasRef.value.getBoundingClientRect()
  draftEnd = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  redrawAnnotations()
}

function onAnnotateUp(e) {
  if (!annotateMode.value || !drawing || !startPt) return
  const rect = annotateCanvasRef.value.getBoundingClientRect()
  const x2 = e.clientX - rect.left
  const y2 = e.clientY - rect.top
  const w = Math.abs(x2 - startPt.x)
  const h = Math.abs(y2 - startPt.y)
  if (w > 8 && h > 8) {
    const item = {
      timeSec: currentSec.value,
      x: Math.min(startPt.x, x2) / rect.width,
      y: Math.min(startPt.y, y2) / rect.height,
      w: w / rect.width,
      h: h / rect.height,
      label: `标注 ${savedAnnotations.value.length + 1}`
    }
    savedAnnotations.value.push(item)
    emitAnnotations()
  }
  drawing = false
  startPt = null
  draftEnd = null
  redrawAnnotations()
}

onMounted(() => {
  window.addEventListener('resize', resizeAnnotateCanvas)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeAnnotateCanvas)
})
</script>

<style scoped>
.recording-player {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.player-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.zoom-label {
  font-size: 12px;
  color: var(--atp-text-muted);
  min-width: 40px;
}
.player-viewport {
  overflow: auto;
  max-height: 480px;
  background: #0f172a;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.player-stage {
  position: relative;
  transition: transform 0.15s ease;
}
.player-video {
  display: block;
  max-width: 100%;
  max-height: 460px;
  cursor: pointer;
}
.annotate-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.annotate-canvas.active {
  pointer-events: auto;
  cursor: crosshair;
}
.player-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.timeline-wrap {
  flex: 1;
  position: relative;
  padding-top: 14px;
}
.marker-track {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 0;
  height: 12px;
  pointer-events: none;
}
.marker-dot {
  position: absolute;
  top: 2px;
  width: 8px;
  height: 8px;
  margin-left: -4px;
  border-radius: 50%;
  border: none;
  background: #94a3b8;
  cursor: pointer;
  pointer-events: auto;
  padding: 0;
}
.marker-dot.active {
  background: #f59e0b;
  transform: scale(1.25);
}
.timeline {
  flex: 1;
  width: 100%;
}
.time-label {
  font-size: 12px;
  color: var(--atp-text-muted);
  min-width: 90px;
  text-align: right;
}
.annotation-panel {
  border: 1px solid var(--atp-border, #e2e8f0);
  border-radius: 8px;
  padding: 8px 10px;
  max-height: 120px;
  overflow-y: auto;
}
.annotation-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--atp-text-muted);
  margin-bottom: 6px;
}
.annotation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.annotation-item:hover,
.annotation-item.active {
  background: rgba(245, 158, 11, 0.1);
}
.ann-time {
  color: var(--el-color-primary);
  min-width: 42px;
}
.ann-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ann-actions {
  flex-shrink: 0;
}
</style>
