/** 投屏 Canvas 绘制 — 组件内 H264/JPEG/PNG 解码 */

import { createH264Decoder } from './useH264Decoder'
import { paintCanvasSource } from './canvasPaint'

function frameMime(buffer) {
  const u8 = new Uint8Array(buffer)
  if (u8.length >= 2 && u8[0] === 0xff && u8[1] === 0xd8) return 'image/jpeg'
  return 'image/png'
}

function isRasterBuffer(buffer) {
  const u8 = new Uint8Array(buffer)
  if (u8.length < 2) return false
  if (u8[0] === 0xff && u8[1] === 0xd8) return true
  return u8.length >= 4 && u8[0] === 0x89 && u8[1] === 0x50 && u8[2] === 0x4e && u8[3] === 0x47
}

export function createScreenCanvasRenderer(canvasRef, { onFrameDrawn, onDecodeError } = {}) {
  let streamMode = ''
  let h264Active = false
  let pendingRaster = false
  let latestRaster = null
  let pendingFrames = []

  const h264 = createH264Decoder(canvasRef, (w, h, painted = true) => {
    if (painted !== false) onFrameDrawn?.(w, h)
  })

  async function drawRaster(buffer) {
    const canvas = canvasRef.value
    if (!canvas || !buffer) return false
    const blob = new Blob([buffer], { type: frameMime(buffer) })
    const bitmap = await createImageBitmap(blob)
    const painted = paintCanvasSource(canvas, bitmap, bitmap.width, bitmap.height)
    bitmap.close()
    if (painted) onFrameDrawn?.(painted.nativeW, painted.nativeH)
    return !!painted
  }

  function scheduleRasterDraw(buffer) {
    latestRaster = buffer
    if (pendingRaster) return
    pendingRaster = true
    requestAnimationFrame(async () => {
      pendingRaster = false
      const buf = latestRaster
      latestRaster = null
      if (!buf) return
      try {
        await drawRaster(buf)
      } catch (e) {
        console.warn('[screen] raster decode failed', e)
        onDecodeError?.('jpeg_decode_failed')
      }
    })
  }

  function feedH264(buffer) {
    if (h264.feedChunk(buffer)) {
      h264Active = true
      return true
    }
    pendingFrames.push(buffer)
    if (pendingFrames.length > 12) pendingFrames.shift()
    return false
  }

  function flushPendingFrames() {
    if (!pendingFrames.length || !h264Active) return
    const queued = pendingFrames.splice(0)
    for (const buffer of queued) feedH264(buffer)
  }

  function onMeta(meta) {
    if (meta.mode === 'h264') {
      streamMode = 'h264'
      h264Active = h264.handleMeta(meta)
      if (!h264Active) onDecodeError?.('h264_unsupported')
      else flushPendingFrames()
    } else if (meta.mode === 'jpeg') {
      streamMode = 'jpeg'
      h264Active = false
      pendingFrames = []
      h264.destroy()
    }
  }

  function onFrame(buffer) {
    if (!buffer || !canvasRef.value) return

    if (streamMode === 'h264' || h264Active) {
      feedH264(buffer)
      return
    }

    if (streamMode === 'jpeg' || isRasterBuffer(buffer)) {
      scheduleRasterDraw(buffer)
      return
    }

    if (feedH264(buffer)) {
      streamMode = 'h264'
      return
    }
    onDecodeError?.('h264_decode_failed')
  }

  function destroy() {
    streamMode = ''
    h264Active = false
    pendingFrames = []
    latestRaster = null
    h264.destroy()
  }

  return { onMeta, onFrame, destroy }
}
