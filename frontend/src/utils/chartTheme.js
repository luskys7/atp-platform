/** 图表配色 — 极光青蓝主题 */
export const CHART = {
  brand: ['#0284C7', '#38BDF8', '#6366F1', '#0369A1'],
  device: ['#10B981', '#F59E0B', '#BAE6FD'],
  task: ['#10B981', '#EF4444', '#3B82F6', '#0284C7'],
  passRateLine: '#0284C7',
  passRateArea: ['rgba(2, 132, 199, 0.22)', 'rgba(2, 132, 199, 0)'],
  threshold: '#EF4444',
  grid: '#E2E8F0',
  axis: '#64748B',
  dark: {
    grid: '#1E293B',
    axis: '#94A3B8',
    passRateArea: ['rgba(56, 189, 248, 0.35)', 'rgba(56, 189, 248, 0)']
  }
}

export function passRateColor(rate, min = 99) {
  if (rate >= min) return '#10B981'
  if (rate >= 80) return '#F59E0B'
  return '#EF4444'
}
