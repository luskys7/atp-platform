/** Canvas 按显示尺寸 × DPR 绘制，避免 CSS 缩放导致模糊 */

export function paintCanvasSource(canvas, source, srcW, srcH) {
  if (!canvas || !source) return null
  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()
  const dw = rect.width
  const dh = rect.height
  if (dw < 1 || dh < 1) return null

  const sw = srcW || source.displayWidth || source.codedWidth || source.width
  const sh = srcH || source.displayHeight || source.codedHeight || source.height
  if (!sw || !sh) return null

  const bw = Math.round(dw * dpr)
  const bh = Math.round(dh * dpr)
  if (canvas.width !== bw || canvas.height !== bh) {
    canvas.width = bw
    canvas.height = bh
  }
  const ctx = canvas.getContext('2d', { alpha: false })
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.clearRect(0, 0, bw, bh)
  ctx.drawImage(source, 0, 0, sw, sh, 0, 0, bw, bh)
  return { nativeW: sw, nativeH: sh }
}
