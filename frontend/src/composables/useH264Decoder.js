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

export function createH264Decoder(canvasRef, onDraw) {
  let decoder = null
  let ts = 0
  let pendingConfig = null
  let configuredCodec = 'avc1.42E01E'
  let needKeyframe = true
  let decodeErrors = 0

  function supported() {
    return typeof VideoDecoder !== 'undefined'
  }

  function destroy() {
    pendingConfig = null
    needKeyframe = true
    decodeErrors = 0
    if (decoder) {
      try {
        if (decoder.state !== 'closed') decoder.close()
      } catch { /* ignore */ }
      decoder = null
    }
  }

  function drawFrame(frame) {
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
    const painted = paintCanvasSource(canvas, frame, w, h)
    frame.close()
    if (painted) {
      needKeyframe = false
      decodeErrors = 0
      onDraw?.(painted.nativeW, painted.nativeH)
    }
  }

  function ensureDecoder(descriptionBytes) {
    const desc = descriptionBytes || pendingConfig
    if (!desc) return false
    const codec = codecFromAvcDescription(desc)
    if (decoder && decoder.state === 'configured' && configuredCodec === codec) {
      return true
    }

    destroy()
    configuredCodec = codec
    needKeyframe = true
    try {
      decoder = new VideoDecoder({
        output: drawFrame,
        error: (e) => {
          console.warn('[H264] decode error', e)
          decodeErrors += 1
          if (decodeErrors >= 3) {
            destroy()
          }
        }
      })
      decoder.configure({
        codec,
        optimizeForLatency: true,
        description: desc
      })
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
      pendingConfig = data
      return ensureDecoder(data)
    }

    if (!ensureDecoder()) return false
    if (!decoder || decoder.state !== 'configured') return false
    if (needKeyframe && !isKey) return false

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
