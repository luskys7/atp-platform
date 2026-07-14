/** 投屏画面容器 — 固定手机比例，自适应视口不裁切 */

export const NAV_BAR_HEIGHT = 36

/** 从 maxHeight 字符串中扣除导航栏等额外高度 */
export function frameMaxHeight(base, extraPx = 0) {
  if (!extraPx) return base
  if (typeof base === 'string' && base.startsWith('calc(') && base.endsWith(')')) {
    return `calc(${base.slice(5, -1)} - ${extraPx}px)`
  }
  return base
}

/** 解析 maxHeight 为像素值（仅支持 calc(100vh - Npx) 或数字） */
export function parseCssMaxHeight(maxHeight, extraPx = 0) {
  if (typeof maxHeight === 'number') return Math.max(160, maxHeight - extraPx)
  const m = String(maxHeight).match(/calc\(\s*100vh\s*-\s*(\d+)px\s*\)/)
  if (m) return Math.max(160, window.innerHeight - parseInt(m[1], 10) - extraPx)
  return Math.max(160, window.innerHeight - 220 - extraPx)
}

/** 根据设备比例与视口上限，计算固定布局像素尺寸 */
export function computeFrameLayoutPixels(streamW, streamH, maxHeight = 'calc(100vh - 200px)', maxWidthPx = null) {
  const w = Math.max(1, streamW || 9)
  const h = Math.max(1, streamH || 16)
  const ratio = w / h
  const maxH = parseCssMaxHeight(maxHeight)
  let height = maxH
  let width = height * ratio
  const capW = maxWidthPx ?? Math.min(window.innerWidth * 0.42, 400)
  if (width > capW) {
    width = capW
    height = width / ratio
  }
  return { width: Math.round(width), height: Math.round(height) }
}

/** 固定宽高样式 — 连接/断开时不随流分辨率变化 */
export function fixedScreenFrameStyle(streamW, streamH, maxHeight = 'calc(100vh - 200px)', maxWidthPx = null) {
  const { width, height } = computeFrameLayoutPixels(streamW, streamH, maxHeight, maxWidthPx)
  return {
    width: `${width}px`,
    height: `${height}px`,
    flexShrink: 0
  }
}

export function screenFrameStyle(streamW, streamH, maxHeight = 'calc(100vh - 200px)', maxWidth = '100%') {
  const w = Math.max(1, streamW || 9)
  const h = Math.max(1, streamH || 16)
  return {
    aspectRatio: `${w} / ${h}`,
    maxHeight,
    maxWidth,
    width: 'auto',
    height: 'auto',
    flexShrink: 0
  }
}

export function frameSizeFromDevice(device) {
  const w = device?.screen_width || device?.screenWidth || 1080
  const h = device?.screen_height || device?.screenHeight || 1920
  return { w, h }
}

export function androidVersionLabel(device) {
  const v = device?.os_version ?? device?.osVersion
  if (!v) return '-'
  const s = String(v).trim()
  return s.toLowerCase().startsWith('android') ? s : `Android ${s}`
}
