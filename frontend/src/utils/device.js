/** 统一设备 API 字段（兼容 snake_case / camelCase） */
export function normalizeDevice(raw) {
  if (!raw) return null
  const d = raw.data && !raw.serial_number && !raw.serialNumber ? raw.data : raw
  const tags = d.tags || ''
  const groupFromTags = (() => {
    if (d.device_group || d.deviceGroup) return d.device_group || d.deviceGroup
    const m = String(tags).match(/(?:^|,)\s*group:([^,]+)/i)
    if (m) return m[1].trim()
    // 标签直接命中分组名
    for (const g of ['录制专用组', '回归测试组', '线上功能组']) {
      if (String(tags).includes(g)) return g
    }
    return ''
  })()
  return {
    id: d.id,
    name: d.name || '',
    serial_number: d.serial_number ?? d.serialNumber ?? '',
    platform: d.platform || '',
    model: d.model || '',
    os_version: d.os_version ?? d.osVersion ?? '',
    screen_width: d.screen_width ?? d.screenWidth ?? 0,
    screen_height: d.screen_height ?? d.screenHeight ?? 0,
    battery_level: d.battery_level ?? d.batteryLevel ?? null,
    status: d.status || '',
    tags,
    device_group: groupFromTags,
    calibration_json: d.calibration_json ?? d.calibrationJson ?? '',
    locked_by_task_id: d.locked_by_task_id ?? d.lockedByTaskId ?? null,
    lock_expires_at: d.lock_expires_at ?? d.lockExpiresAt ?? null,
    occupied_by: d.occupied_by ?? d.occupiedBy ?? null,
    occupied_at: d.occupied_at ?? d.occupiedAt ?? null,
    is_whitelisted: d.is_whitelisted ?? d.isWhitelisted ?? false
  }
}

/** 从标签字符串写入/更新 group:xxx */
export function mergeDeviceGroupTag(tags, groupLabel) {
  const parts = String(tags || '')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
    .filter(s => !/^group:/i.test(s) && !['录制专用组', '回归测试组', '线上功能组'].includes(s))
  if (groupLabel) parts.push(`group:${groupLabel}`)
  return parts.join(',')
}
