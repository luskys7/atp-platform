/** 图表配色 — 薰衣草紫主题 */
export const CHART = {
  brand: ['#8B6CF0', '#9E82F3', '#3489EB', '#7454D9'],
  device: ['#36C972', '#FF9A3C', '#C9CDD4'],
  task: ['#36C972', '#F25757', '#3489EB', '#8B6CF0'],
  passRateLine: '#8B6CF0',
  passRateArea: ['rgba(139, 108, 240, 0.22)', 'rgba(139, 108, 240, 0)'],
  threshold: '#F25757',
  grid: '#E5E6EB',
  axis: '#86909C',
  dark: {
    grid: '#363B44',
    axis: '#86909C',
    passRateArea: ['rgba(139, 108, 240, 0.35)', 'rgba(139, 108, 240, 0)']
  }
}

export function passRateColor(rate, min = 99) {
  if (rate >= min) return '#36C972'
  if (rate >= 80) return '#FF9A3C'
  return '#F25757'
}
