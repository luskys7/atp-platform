/** Canvas 按显示尺寸 × DPR 绘制，避免 CSS 缩放导致模糊 */

export function paintCanvasSource(canvas, source, srcW, srcH) {
  if (!canvas || !source) return null

  const sw = srcW || source.displayWidth || source.codedWidth || source.width
  const sh = srcH || source.displayHeight || source.codedHeight || source.height
  if (!sw || !sh) return null

  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()
  // 布局尚未完成时 getBoundingClientRect 可能为 0，回退到 client/offset/父级尺寸，
  // 避免 H.264 已出帧却永远画不上 → 被误切到 1FPS 的 ADB JPEG。
  let dw = rect.width || canvas.clientWidth || canvas.offsetWidth || 0
  let dh = rect.height || canvas.clientHeight || canvas.offsetHeight || 0
  if (dw < 1 || dh < 1) {
    const parent = canvas.parentElement
    if (parent) {
      const pr = parent.getBoundingClientRect()
      dw = pr.width || parent.clientWidth || 0
      dh = pr.height || parent.clientHeight || 0
    }
  }
  if (dw < 1 || dh < 1) {
    dw = sw
    dh = sh
  }

  const bw = Math.max(1, Math.round(dw * dpr))
  const bh = Math.max(1, Math.round(dh * dpr))
  if (canvas.width !== bw || canvas.height !== bh) {
    canvas.width = bw
    canvas.height = bh
  }
  const ctx = canvas.getContext('2d', { alpha: false })
  if (!ctx) return null
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'medium'
  ctx.drawImage(source, 0, 0, sw, sh, 0, 0, bw, bh)
  return { nativeW: sw, nativeH: sh }
}
