/** 统一设备 API 字段（兼容 snake_case / camelCase） */
export function normalizeDevice(raw) {
  if (!raw) return null
  const d = raw.data && !raw.serial_number && !raw.serialNumber ? raw.data : raw
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
    tags: d.tags || '',
    calibration_json: d.calibration_json ?? d.calibrationJson ?? ''
  }
}
