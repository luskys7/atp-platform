const BOOT_TS_KEY = 'atp_record_boot_ts'

export function markRecordingBoot() {
  sessionStorage.setItem(BOOT_TS_KEY, String(Date.now()))
}

export function consumeStartupMs() {
  const raw = sessionStorage.getItem(BOOT_TS_KEY)
  sessionStorage.removeItem(BOOT_TS_KEY)
  if (!raw) return null
  const ms = Date.now() - Number(raw)
  return Number.isFinite(ms) && ms >= 0 ? ms : null
}
