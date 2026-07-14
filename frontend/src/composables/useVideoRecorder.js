/**
 * 轻量化 Canvas 录屏：低帧率采集 + 自适应降帧 + 可选水印，控制 CPU 占用。
 */
import { ref, computed } from 'vue'

const DEFAULT_FPS = 12
const MIN_FPS = 6
const MAX_FPS = 12
const DEFAULT_BITRATE = 600_000

/** 窗口模式默认裁剪：去掉侧边空白，保留主内容区（相对 Canvas 归一化坐标） */
export const WINDOW_CROP_RECT = { x: 0.04, y: 0.03, w: 0.92, h: 0.85 }

export function useVideoRecorder() {
  const recording = ref(false)
  const paused = ref(false)
  const durationMs = ref(0)
  const fileSizeBytes = ref(0)
  const error = ref(null)
  const effectiveFps = ref(DEFAULT_FPS)
  const avgPaintMs = ref(0)

  let mediaRecorder = null
  let captureCanvas = null
  let captureCtx = null
  let sourceCanvas = null
  let animFrameId = null
  let chunks = []
  let startedAt = 0
  let pausedTotalMs = 0
  let pauseStartedAt = 0
  let tickTimer = null
  let adaptTimer = null
  let watermark = null
  let cropRect = null
  let blurRegions = []
  let desensitizeEnabled = false
  let intervalMs = Math.floor(1000 / DEFAULT_FPS)
  let lastPaint = 0
  let paintSamples = []
  let longTaskCount = 0
  let maxLongTaskMs = 0
  let perfObserver = null

  const performanceGrade = ref('good')

  const formattedDuration = computed(() => formatMs(durationMs.value))
  const formattedSize = computed(() => formatBytes(fileSizeBytes.value))

  function formatMs(ms) {
    const s = Math.floor(ms / 1000)
    const m = Math.floor(s / 60)
    const sec = s % 60
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  }

  function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  }

  function drawWatermark(ctx, w, h) {
    if (!watermark?.enabled) return
    const lines = watermark.lines || []
    if (!lines.length) return
    ctx.save()
    ctx.globalAlpha = 0.55
    ctx.fillStyle = 'rgba(255,255,255,0.92)'
    ctx.font = '12px sans-serif'
    ctx.textAlign = 'right'
    lines.forEach((line, i) => {
      ctx.fillText(line, w - 10, h - 10 - i * 16)
    })
    ctx.restore()
  }

  function drawBlurRegions(ctx, w, h) {
    if (!desensitizeEnabled || !blurRegions.length) return
    for (const r of blurRegions) {
      const bx = Math.floor(r.x * w)
      const by = Math.floor(r.y * h)
      const bw = Math.max(1, Math.floor(r.w * w))
      const bh = Math.max(1, Math.floor(r.h * h))
      ctx.save()
      ctx.fillStyle = 'rgba(15,23,42,0.85)'
      ctx.fillRect(bx, by, bw, bh)
      ctx.fillStyle = 'rgba(255,255,255,0.5)'
      ctx.font = '11px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText('***', bx + bw / 2, by + bh / 2 + 4)
      ctx.restore()
    }
  }

  function paintFrame() {
    if (!captureCtx || !sourceCanvas) return
    const t0 = performance.now()
    const sw = sourceCanvas.width
    const sh = sourceCanvas.height
    if (!sw || !sh) return

    let sx = 0; let sy = 0; let sWidth = sw; let sHeight = sh
    if (cropRect) {
      sx = Math.max(0, Math.floor(cropRect.x * sw))
      sy = Math.max(0, Math.floor(cropRect.y * sh))
      sWidth = Math.max(1, Math.floor(cropRect.w * sw))
      sHeight = Math.max(1, Math.floor(cropRect.h * sh))
    }

    if (captureCanvas.width !== sWidth || captureCanvas.height !== sHeight) {
      captureCanvas.width = sWidth
      captureCanvas.height = sHeight
    }
    captureCtx.drawImage(sourceCanvas, sx, sy, sWidth, sHeight, 0, 0, sWidth, sHeight)
    drawBlurRegions(captureCtx, sWidth, sHeight)
    drawWatermark(captureCtx, sWidth, sHeight)
    const dt = performance.now() - t0
    paintSamples.push(dt)
    if (paintSamples.length > 24) paintSamples.shift()
    avgPaintMs.value = Math.round(paintSamples.reduce((a, b) => a + b, 0) / paintSamples.length)
    updatePerformanceGrade()
  }

  function startPerfWatch() {
    longTaskCount = 0
    maxLongTaskMs = 0
    if (typeof PerformanceObserver === 'undefined') return
    try {
      perfObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          if (entry.duration > 50) {
            longTaskCount += 1
            maxLongTaskMs = Math.max(maxLongTaskMs, entry.duration)
          }
        }
        updatePerformanceGrade()
      })
      perfObserver.observe({ entryTypes: ['longtask'] })
    } catch { /* longtask 不可用 */ }
  }

  function stopPerfWatch() {
    if (perfObserver) {
      perfObserver.disconnect()
      perfObserver = null
    }
  }

  function updatePerformanceGrade() {
    const paint = avgPaintMs.value
    const fps = effectiveFps.value
    if (paint <= 25 && fps >= 10 && longTaskCount <= 3) {
      performanceGrade.value = 'good'
    } else if (paint <= 45 && fps >= 7 && longTaskCount <= 8) {
      performanceGrade.value = 'fair'
    } else {
      performanceGrade.value = 'heavy'
    }
  }

  function adaptFrameRate() {
    const avg = avgPaintMs.value
    const minInterval = Math.floor(1000 / MAX_FPS)
    const maxInterval = Math.floor(1000 / MIN_FPS)
    if (avg > 45 && intervalMs < maxInterval) {
      intervalMs = Math.min(maxInterval, intervalMs + 25)
    } else if (avg > 0 && avg < 18 && intervalMs > minInterval) {
      intervalMs = Math.max(minInterval, intervalMs - 15)
    }
    effectiveFps.value = Math.max(MIN_FPS, Math.min(MAX_FPS, Math.round(1000 / intervalMs)))
    updatePerformanceGrade()
  }

  function startTick() {
    stopTick()
    tickTimer = setInterval(() => {
      if (!recording.value || paused.value) return
      durationMs.value = Date.now() - startedAt - pausedTotalMs
      fileSizeBytes.value = chunks.reduce((sum, c) => sum + (c.size || 0), 0)
    }, 500)
    adaptTimer = setInterval(adaptFrameRate, 2000)
  }

  function stopTick() {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
    if (adaptTimer) {
      clearInterval(adaptTimer)
      adaptTimer = null
    }
  }

  function start(canvas, options = {}) {
    if (recording.value) return false
    sourceCanvas = canvas
    error.value = null
    chunks = []
    durationMs.value = 0
    fileSizeBytes.value = 0
    pausedTotalMs = 0
    paintSamples = []
    avgPaintMs.value = 0
    watermark = {
      enabled: options.watermark !== false,
      lines: options.watermarkLines || []
    }
    cropRect = options.cropRect || null
    blurRegions = []
    desensitizeEnabled = options.desensitize !== false

    const fps = options.fps || DEFAULT_FPS
    intervalMs = Math.max(Math.floor(1000 / MAX_FPS), Math.floor(1000 / fps))
    effectiveFps.value = Math.round(1000 / intervalMs)
    lastPaint = 0

    captureCanvas = document.createElement('canvas')
    captureCtx = captureCanvas.getContext('2d')
    paintFrame()

    const stream = captureCanvas.captureStream(fps)
    const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
      ? 'video/webm;codecs=vp9'
      : (MediaRecorder.isTypeSupported('video/webm;codecs=vp8') ? 'video/webm;codecs=vp8' : 'video/webm')

    mediaRecorder = new MediaRecorder(stream, {
      mimeType,
      videoBitsPerSecond: options.bitrate || DEFAULT_BITRATE
    })
    mediaRecorder.ondataavailable = (e) => {
      if (e.data?.size) chunks.push(e.data)
    }
    mediaRecorder.onerror = (e) => {
      error.value = e.error?.message || '录屏失败'
    }

    const loop = (ts) => {
      if (!recording.value) return
      if (!paused.value && ts - lastPaint >= intervalMs) {
        paintFrame()
        lastPaint = ts
      }
      animFrameId = requestAnimationFrame(loop)
    }

    mediaRecorder.start(1000)
    recording.value = true
    paused.value = false
    startedAt = Date.now()
    startTick()
    startPerfWatch()
    animFrameId = requestAnimationFrame(loop)
    return true
  }

  function pause() {
    if (!recording.value || paused.value) return
    if (mediaRecorder?.state === 'recording') mediaRecorder.pause()
    paused.value = true
    pauseStartedAt = Date.now()
  }

  function resume() {
    if (!recording.value || !paused.value) return
    if (mediaRecorder?.state === 'paused') mediaRecorder.resume()
    pausedTotalMs += Date.now() - pauseStartedAt
    paused.value = false
  }

  function stop() {
    return new Promise((resolve) => {
      if (!recording.value) {
        resolve(null)
        return
      }
      recording.value = false
      paused.value = false
      stopTick()
      stopPerfWatch()
      if (animFrameId) {
        cancelAnimationFrame(animFrameId)
        animFrameId = null
      }
      if (!mediaRecorder || mediaRecorder.state === 'inactive') {
        resolve(buildBlob())
        return
      }
      mediaRecorder.onstop = () => resolve(buildBlob())
      try {
        mediaRecorder.stop()
      } catch {
        resolve(buildBlob())
      }
    })
  }

  function buildBlob() {
    if (!chunks.length) return null
    const mime = mediaRecorder?.mimeType || 'video/webm'
    const blob = new Blob(chunks, { type: mime })
    fileSizeBytes.value = blob.size
    mediaRecorder = null
    captureCanvas = null
    captureCtx = null
    sourceCanvas = null
    chunks = []
    return blob
  }

  function getPartialBlob() {
    if (!chunks.length) return null
    const mime = mediaRecorder?.mimeType || 'video/webm'
    return new Blob(chunks, { type: mime })
  }

  function getClientMetrics() {
    const durationMin = Math.max(0.1, durationMs.value / 60000)
    const longTasksPerMin = Math.round(longTaskCount / durationMin)
    return {
      record_fps_avg: effectiveFps.value,
      paint_ms_avg: avgPaintMs.value,
      performance_grade: performanceGrade.value,
      long_task_count: longTaskCount,
      max_long_task_ms: Math.round(maxLongTaskMs),
      long_tasks_per_min: longTasksPerMin,
      cpu_ok: performanceGrade.value === 'good'
    }
  }

  function addBlurRegion(rect) {
    if (!rect) return
    blurRegions.push({ ...rect })
    if (blurRegions.length > 12) blurRegions.shift()
  }

  function getDurationSeconds() {
    return Math.max(1, Math.round(durationMs.value / 1000))
  }

  function destroy() {
    stopTick()
    stopPerfWatch()
    if (animFrameId) cancelAnimationFrame(animFrameId)
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      try { mediaRecorder.stop() } catch { /* ignore */ }
    }
    recording.value = false
    paused.value = false
    mediaRecorder = null
    chunks = []
  }

  return {
    recording,
    paused,
    durationMs,
    fileSizeBytes,
    error,
    effectiveFps,
    avgPaintMs,
    performanceGrade,
    formattedDuration,
    formattedSize,
    start,
    pause,
    resume,
    stop,
    getDurationSeconds,
    getPartialBlob,
    getClientMetrics,
    addBlurRegion,
    destroy
  }
}
