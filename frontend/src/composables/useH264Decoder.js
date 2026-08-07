/** WebCodecs H.264 解码 → Canvas（scrcpy 低延迟模式） */

import { paintCanvasSource } from './canvasPaint'

function codecFromAvcDescription(desc) {
  if (!desc || desc.length < 8) return 'avc1.42E01E'
  const spsLen = (desc[6] << 8) | desc[7]
  const sps = desc.subarray(8, 8 + spsLen)
  if (sps.length < 4) return 'avc1.42E01E'
  const profile = sps[1].toString(16).padStart(2, '0')
  const compat = sps[2].toString(16).padStart(2, '0')
  const level = sps[3].toString(16).padStart(2, '0')
  return `avc1.${profile}${compat}${level}`
}

function sameDescription(a, b) {
  if (!a || !b || a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false
  }
  return true
}

export function createH264Decoder(canvasRef, onDraw) {
  let decoder = null
  let ts = 0
  let pendingConfig = null
  let configuredCodec = 'avc1.42E01E'
  let needKeyframe = true
  let decodeErrors = 0
  let drawing = false
  let latestFrame = null
  let rafPending = false
  let preferBitmap = false

  function supported() {
    return typeof VideoDecoder !== 'undefined'
  }

  function destroy() {
    pendingConfig = null
    needKeyframe = true
    decodeErrors = 0
    rafPending = false
    if (latestFrame) {
      try { latestFrame.close() } catch { /* ignore */ }
      latestFrame = null
    }
    if (decoder) {
      try {
        if (decoder.state !== 'closed') decoder.close()
      } catch { /* ignore */ }
      decoder = null
    }
  }

  async function paintOne(frame) {
    const canvas = canvasRef.value
    if (!canvas) {
      frame.close()
      return
    }
    const w = frame.displayWidth || frame.codedWidth
    const h = frame.displayHeight || frame.codedHeight
    if (!w || !h) {
      frame.close()
      return
    }

    let painted = null
    try {
      if (!preferBitmap) {
        try {
          painted = paintCanvasSource(canvas, frame, w, h)
        } catch {
          painted = null
          preferBitmap = true
        }
      }
      if ((!painted || preferBitmap) && typeof createImageBitmap === 'function') {
        try {
          const bitmap = await createImageBitmap(frame)
          painted = paintCanvasSource(canvas, bitmap, w, h)
          bitmap.close?.()
          if (painted) preferBitmap = true
        } catch {
          /* keep painted as-is */
        }
      }
      if (painted) {
        needKeyframe = false
        decodeErrors = 0
        onDraw?.(painted.nativeW, painted.nativeH)
      }
    } finally {
      try { frame.close() } catch { /* ignore */ }
    }
  }

  function schedulePaint(frame) {
    // 只保留最新帧，用 rAF 合并绘制，避免主线程排队导致卡顿
    if (latestFrame) {
      try { latestFrame.close() } catch { /* ignore */ }
    }
    latestFrame = frame
    if (rafPending) return
    rafPending = true
    const pump = () => {
      rafPending = false
      const next = latestFrame
      latestFrame = null
      if (!next) return
      if (drawing) {
        // 上一帧还在画：丢弃本帧保流畅
        try { next.close() } catch { /* ignore */ }
        return
      }
      drawing = true
      Promise.resolve(paintOne(next)).finally(() => {
        drawing = false
        if (latestFrame && !rafPending) {
          rafPending = true
          requestAnimationFrame(pump)
        }
      })
    }
    requestAnimationFrame(pump)
  }

  function ensureDecoder(descriptionBytes) {
    const desc = descriptionBytes || pendingConfig
    if (!desc) return false
    const codec = codecFromAvcDescription(desc)
    if (
      decoder
      && decoder.state === 'configured'
      && configuredCodec === codec
      && sameDescription(pendingConfig, desc)
    ) {
      return true
    }

    destroy()
    configuredCodec = codec
    needKeyframe = true
    try {
      decoder = new VideoDecoder({
        output: (frame) => { schedulePaint(frame) },
        error: (e) => {
          console.warn('[H264] decode error', e)
          decodeErrors += 1
          if (decodeErrors >= 3) {
            destroy()
          }
        }
      })
      const base = {
        codec,
        optimizeForLatency: true,
        description: desc
      }
      try {
        decoder.configure({ ...base, hardwareAcceleration: 'prefer-hardware' })
      } catch {
        decoder.configure({ ...base, hardwareAcceleration: 'prefer-software' })
      }
      pendingConfig = desc
      return true
    } catch (e) {
      console.warn('[H264] configure failed', codec, e)
      decoder = null
      return false
    }
  }

  function handleMeta(meta) {
    if (!supported()) return false
    if (meta.description) {
      const raw = Uint8Array.from(atob(meta.description), c => c.charCodeAt(0))
      if (decoder && decoder.state === 'configured' && sameDescription(pendingConfig, raw)) {
        return true
      }
      pendingConfig = raw
      return ensureDecoder(raw)
    }
    if (meta.width && meta.height) {
      onDraw?.(meta.width, meta.height, false)
    }
    return !!decoder
  }

  function feedChunk(buffer) {
    if (!supported()) return false
    const u8 = new Uint8Array(buffer)
    if (u8.length < 2) return false
    const flags = u8[0]
    const data = u8.slice(1)
    const isConfig = (flags & 1) !== 0
    const isKey = (flags & 2) !== 0

    if (isConfig) {
      if (decoder && decoder.state === 'configured' && sameDescription(pendingConfig, data)) {
        return true
      }
      pendingConfig = data
      return ensureDecoder(data)
    }

    if (!ensureDecoder()) return false
    if (!decoder || decoder.state !== 'configured') return false
    if (needKeyframe && !isKey) return false
    if (decoder.decodeQueueSize > 2) {
      // 积压时丢弃非关键帧，保持低延迟
      if (!isKey) return true
    }

    try {
      ts += 33_000
      decoder.decode(new EncodedVideoChunk({
        type: isKey ? 'key' : 'delta',
        timestamp: ts,
        data
      }))
      return true
    } catch (e) {
      console.warn('[H264] feed failed', e)
      if (isKey) {
        destroy()
        pendingConfig = null
      }
      return false
    }
  }

  return { supported, handleMeta, feedChunk, destroy }
}
