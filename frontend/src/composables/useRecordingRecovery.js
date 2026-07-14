/**
 * 录制异常兜底：断连/崩溃时本地缓存，支持恢复上传。
 */
const DRAFT_KEY = 'atp_recording_draft'
const IDB_NAME = 'atp_recording_recovery'
const IDB_STORE = 'blobs'

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(IDB_STORE)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function saveDraftBlob(sessionId, blob) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readwrite')
    tx.objectStore(IDB_STORE).put(blob, String(sessionId))
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function loadDraftBlob(sessionId) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readonly')
    const req = tx.objectStore(IDB_STORE).get(String(sessionId))
    req.onsuccess = () => resolve(req.result || null)
    req.onerror = () => reject(req.error)
  })
}

export async function clearDraftBlob(sessionId) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readwrite')
    tx.objectStore(IDB_STORE).delete(String(sessionId))
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export function saveDraftMeta(meta) {
  localStorage.setItem(DRAFT_KEY, JSON.stringify({ ...meta, saved_at: Date.now() }))
}

export function loadDraftMeta() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function clearDraftMeta() {
  localStorage.removeItem(DRAFT_KEY)
}

export async function clearDraft(sessionId) {
  clearDraftMeta()
  if (sessionId) await clearDraftBlob(sessionId).catch(() => {})
}

export async function persistEmergencyDraft({
  sessionId, deviceId, stepCount, durationMs, reason, tags
}) {
  saveDraftMeta({
    session_id: sessionId,
    device_id: deviceId,
    step_count: stepCount,
    duration_ms: durationMs,
    reason: reason || 'unknown',
    tags: tags || null
  })
}

export function draftReasonLabel(reason) {
  return {
    upload_failed: '视频上传失败',
    disconnect: '投屏断连',
    periodic_autosave: '自动草稿',
    unknown: '未知异常'
  }[reason] || reason || '异常中断'
}

/** 重试上传 IndexedDB 中的草稿视频 */
export async function uploadDraftVideo(draft, recordApi, operator) {
  if (!draft?.session_id) throw new Error('草稿会话无效')
  const blob = await loadDraftBlob(draft.session_id)
  if (!blob) throw new Error('草稿视频不存在或已过期')
  await recordApi.uploadVideo(
    draft.session_id,
    blob,
    Math.max(1, Math.round((draft.duration_ms || 0) / 1000)),
    operator || '',
    null,
    draft.tags || {}
  )
  await clearDraft(draft.session_id)
}

export function captureCanvasThumb(canvas, maxW = 160) {
  if (!canvas?.width) return null
  const scale = maxW / canvas.width
  const h = Math.round(canvas.height * scale)
  const c = document.createElement('canvas')
  c.width = maxW
  c.height = h
  const ctx = c.getContext('2d')
  ctx.drawImage(canvas, 0, 0, maxW, h)
  return c.toDataURL('image/jpeg', 0.65)
}

export function captureCanvasSnapshot(canvas, maxW = 320) {
  if (!canvas?.width) return null
  const scale = maxW / canvas.width
  const h = Math.round(canvas.height * scale)
  const c = document.createElement('canvas')
  c.width = maxW
  c.height = h
  const ctx = c.getContext('2d')
  ctx.drawImage(canvas, 0, 0, maxW, h)
  return c.toDataURL('image/jpeg', 0.72)
}
