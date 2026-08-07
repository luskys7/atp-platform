import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const DEFAULT_TIMEOUT = 30000
/** 上传 APK、批量安装等长耗时操作 */
const LONG_TIMEOUT = 600000

const request = axios.create({
  baseURL: '/api/v1',
  timeout: DEFAULT_TIMEOUT
})

function authHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function isNotFoundError(error) {
  const status = error?.response?.status
  const code = error?.response?.data?.error?.code
  return status === 404 || code === 'NOT_FOUND'
}

const silentCfg = { silent: true, ignoreNotFound: true }

/** 轮询任务详情时使用；任务已删除时返回 null，不弹 toast */
export async function fetchTaskMonitorBundle(taskId) {
  if (!taskId) return null
  try {
    const [taskRes, execRes, logRes] = await Promise.all([
      request.get(`/tasks/${taskId}`, silentCfg),
      request.get(`/tasks/${taskId}/executions`, silentCfg),
      request.get(`/tasks/${taskId}/logs`, silentCfg)
    ])
    return {
      task: taskRes.data,
      executions: execRes.data || [],
      logs: logRes.data || []
    }
  } catch (e) {
    if (isNotFoundError(e)) return null
    throw e
  }
}

export function notifyTaskDeleted(taskId) {
  window.dispatchEvent(new CustomEvent('atp-task-deleted', { detail: { id: taskId } }))
}

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const method = (config.method || 'get').toLowerCase()
  if (['post', 'put', 'patch'].includes(method)) {
    if (config.data === undefined || config.data === null) {
      config.data = {}
    }
    if (!(config.data instanceof FormData) && !config.headers['Content-Type']) {
      config.headers['Content-Type'] = 'application/json'
    }
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    // 文件下载（blob）直接返回
    if (res instanceof Blob || response.config?.responseType === 'blob') {
      return res
    }
    if (res.code !== 0 && res.code !== undefined) {
      ElMessage.error(res.message || '请求失败')
      if (res.error?.code) {
        console.error(`[${res.error.code}] ${res.error.message}`)
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const status = error.response?.status
    const errCode = error.response?.data?.error?.code
    const msg = error.response?.data?.message
    const needLogin = status === 401
      || errCode === 'UNAUTHORIZED'
      || (status === 403 && errCode !== 'FORBIDDEN' && errCode !== 'IP_FORBIDDEN')
    if (needLogin) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
        ElMessage.error(msg || '登录已过期，请重新登录')
      }
    } else if (!error.config?.silent) {
      const ignoreNotFound = error.config?.ignoreNotFound && isNotFoundError(error)
      if (!ignoreNotFound) {
        let displayMsg
        if (error.code === 'ECONNABORTED') {
          displayMsg = '请求超时，操作耗时较长，请稍后刷新页面查看结果'
        } else if (!error.response) {
          displayMsg = '无法连接后端服务，请确认 Java 后端已在 8080 端口运行'
        } else if ([500, 502, 503, 504].includes(error.response.status) && !error.response.data?.message) {
          displayMsg = '后端服务不可用（可能未启动），请重启 backend-java'
        } else {
          displayMsg = error.response?.data?.message || error.message || '网络错误'
        }
        ElMessage.error(displayMsg)
      }
    }
    return Promise.reject(error)
  }
)

export default request

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  ssoConfig: () => request.get('/auth/sso/config'),
  ssoLogin: (data) => request.post('/auth/sso/login', data),
  profile: () => request.get('/auth/profile'),
  updateProfile: (data) => request.put('/auth/profile', data),
  changePassword: (data) => request.put('/auth/password', data),
  uploadAvatar: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/auth/avatar', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  sessions: () => request.get('/auth/sessions'),
  revokeSession: (id) => request.delete(`/auth/sessions/${id}`),
  revokeOtherSessions: () => request.post('/auth/sessions/revoke-others'),
  loginLogs: () => request.get('/auth/login-logs'),
  apiKeys: () => request.get('/auth/api-keys'),
  createApiKey: (data) => request.post('/auth/api-keys', data || {}),
  revokeApiKey: (id) => request.post(`/auth/api-keys/${id}/revoke`),
  listUsers: () => request.get('/auth/users')
}

export const deviceApi = {
  list: (params) => request.get('/devices', { params }),
  get: (id) => request.get(`/devices/${id}`),
  updateStatus: (id, data) => request.put(`/devices/${id}/status`, data),
  delete: (id) => request.delete(`/devices/${id}`),
  listWhitelist: (params) => request.get('/devices/whitelist', { params }),
  addWhitelist: (data) => request.post('/devices/whitelist', data),
  removeWhitelist: (id) => request.delete(`/devices/whitelist/${id}`),
  startScreen: (id) => request.post(`/devices/${id}/screen/start`, {}),
  screenTap: (id, data) => request.post(`/devices/${id}/screen/tap`, data),
  screenSwipe: (id, data) => request.post(`/devices/${id}/screen/swipe`, data),
  screenInput: (id, data) => request.post(`/devices/${id}/screen/input`, data),
  screenKey: (id, data) => request.post(`/devices/${id}/screen/key`, data),
  screenInspect: (id, data) => request.post(`/devices/${id}/screen/inspect`, data, { timeout: 20000 }),
  screenValidateLocator: (id, data) => request.post(`/devices/${id}/screen/validate-locator`, data, { timeout: 15000 }),
  screenWarmUi: (id, data = {}) => request.post(`/devices/${id}/screen/warm-ui`, data, {
    silent: !data.blocking,
    timeout: data.blocking ? 20000 : 6000
  }),
  screenUiHierarchy: (id, data = {}) => request.post(`/devices/${id}/screen/ui-hierarchy`, data, {
    timeout: data.force ? 25000 : 15000
  }),
  screenInspectBounds: (id, data) => request.post(`/devices/${id}/screen/inspect-bounds`, data, { timeout: 20000 }),
  screenPrepareUi: (id) => request.post(`/devices/${id}/screen/prepare-ui`, {}, {
    timeout: 45000
  }),
  screenSwitchContext: (id, data) => request.post(`/devices/${id}/screen/switch-context`, data),
  updateCalibration: (id, data) => request.put(`/devices/${id}/calibration`, data),
  updateTags: (id, data) => request.put(`/devices/${id}/tags`, data),
  resetHealth: (id) => request.post(`/devices/${id}/reset-health`),
  syncUsb: () => request.post('/devices/sync-usb'),
  wdaStatus: (id) => request.get(`/devices/${id}/wda/status`),
  deployWda: (id) => request.post(`/devices/${id}/wda/deploy`),
  launcherInfo: () => request.get('/downloads/executor-launcher/info'),
  /** 下载访客本机执行器 exe（blob） */
  downloadLauncher: () => request.get('/downloads/executor-launcher', {
    responseType: 'blob',
    timeout: 600000
  })
}

export const taskApi = {
  list: (params) => request.get('/tasks', { params }),
  get: (id) => request.get(`/tasks/${id}`),
  create: (data) => request.post('/tasks', data),
  update: (id, data) => request.put(`/tasks/${id}`, data),
  previewVisual: (visualJson) => request.post('/tasks/visual/preview', { visual_json: visualJson }),
  submit: (id) => request.post(`/tasks/${id}/submit`),
  cancel: (id) => request.post(`/tasks/${id}/cancel`),
  pauseQueue: (id) => request.post(`/tasks/${id}/queue/pause`),
  resumeQueue: (id) => request.post(`/tasks/${id}/queue/resume`),
  pinQueue: (id) => request.post(`/tasks/${id}/queue/pin`),
  delete: (id) => request.delete(`/tasks/${id}`),
  executions: (id) => request.get(`/tasks/${id}/executions`),
  logs: (id, params) => request.get(`/tasks/${id}/logs`, { params }),
  failureSnapshots: (id) => request.get(`/tasks/${id}/failure-snapshots`),
  failurePlayback: (id) => request.get(`/tasks/${id}/failure-playback`),
  stepMarkers: (id) => request.get(`/tasks/${id}/step-markers`),
  defectInfo: (id) => request.get(`/tasks/${id}/defect-info`),
  offlinePackage: (id) => request.post(`/tasks/${id}/offline-package`),
  async downloadOfflinePackage(filename) {
    const res = await axios.get(`/api/v1/offline-packages/${encodeURIComponent(filename)}/download`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }
}

export const interventionApi = {
  pending: () => request.get('/interventions/pending'),
  byTask: (taskId) => request.get(`/interventions/task/${taskId}`),
  resolve: (id, data) => request.post(`/interventions/${id}/resolve`, data),
  cancel: (id) => request.post(`/interventions/${id}/cancel`)
}

export const messageApi = {
  list: () => request.get('/messages'),
  unreadCount: () => request.get('/messages/unread-count'),
  markRead: (ids) => request.post('/messages/mark-read', { ids }),
  markAllRead: () => request.post('/messages/mark-all-read')
}

export const recordingApi = {
  list: (params) => request.get('/recordings', { params }),
  facets: () => request.get('/recordings/facets'),
  playback: (id) => request.get(`/recordings/${id}/playback`),
  context: (id) => request.get(`/recordings/${id}/context`),
  thumbnailUrl: (id) => `/api/v1/recordings/${id}/thumbnail`,
  async thumbnailStream(id) {
    try {
      const res = await axios.get(`/api/v1/recordings/${id}/thumbnail`, {
        responseType: 'blob',
        headers: authHeaders()
      })
      return URL.createObjectURL(res.data)
    } catch {
      return ''
    }
  },
  async playbackStream(id) {
    const res = await axios.get(`/api/v1/recordings/${id}/stream`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    return URL.createObjectURL(res.data)
  },
  delete: (id) => request.delete(`/recordings/${id}`),
  archive: (id) => request.post(`/recordings/${id}/archive`),
  batchArchive: (ids) => request.post('/recordings/batch-archive', { ids }),
  batchDelete: (ids) => request.post('/recordings/batch-delete', { ids }),
  async batchExport(ids) {
    try {
      const res = await axios.post('/api/v1/recordings/batch-export', { ids }, {
        responseType: 'blob',
        headers: authHeaders()
      })
      const ct = res.headers['content-type'] || ''
      if (ct.includes('application/json')) {
        const text = await res.data.text()
        const json = JSON.parse(text)
        throw new Error(json?.error?.message || json?.message || '导出失败')
      }
      const cd = res.headers['content-disposition'] || ''
      const m = /filename="?([^"]+)"?/.exec(cd)
      const filename = m?.[1] || `recordings_${Date.now()}.zip`
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      if (e?.response?.data instanceof Blob) {
        try {
          const text = await e.response.data.text()
          const json = JSON.parse(text)
          throw new Error(json?.error?.message || json?.message || '导出失败')
        } catch (inner) {
          if (inner?.message && inner.message !== '导出失败') throw inner
        }
      }
      throw e
    }
  },
  verifyWatermark: (id) => request.get(`/recordings/${id}/watermark-verify`),
  features: () => request.get('/recording/features'),
  updateFeatures: (data) => request.put('/recording/features', data),
  resetFeatures: () => request.post('/recording/features/reset')
}

export const reportApi = {
  list: (params) => request.get('/reports', { params }),
  facets: () => request.get('/reports/facets'),
  stats: (params) => request.get('/reports/stats', { params }),
  detail: (taskId) => request.get(`/reports/${taskId}`),
  archive: (id) => request.post(`/reports/${id}/archive`),
  delete: (id) => request.delete(`/reports/${id}`),
  batchArchive: (ids) => request.post('/reports/batch-archive', { ids }),
  batchDelete: (ids) => request.post('/reports/batch-delete', { ids }),
  purgeExpired: (ids = []) => request.post('/reports/purge-expired', { ids }),
  async batchExport(ids) {
    try {
      const res = await axios.post('/api/v1/reports/batch-export', { ids }, {
        responseType: 'blob',
        headers: authHeaders()
      })
      const ct = res.headers['content-type'] || ''
      if (ct.includes('application/json')) {
        const text = await res.data.text()
        const json = JSON.parse(text)
        throw new Error(json?.error?.message || json?.message || '导出失败')
      }
      const cd = res.headers['content-disposition'] || ''
      const m = /filename="?([^"]+)"?/.exec(cd)
      const filename = m?.[1] || `reports_${Date.now()}.zip`
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      if (e?.response?.data instanceof Blob) {
        try {
          const text = await e.response.data.text()
          const json = JSON.parse(text)
          throw new Error(json?.error?.message || json?.message || '导出失败')
        } catch (inner) {
          if (inner?.message && !String(inner.message).includes('JSON')) throw inner
        }
      }
      throw e
    }
  },
  async exportPDF(taskId) {
    const res = await axios.get(`/api/v1/reports/${taskId}/export`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `report_${taskId}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  },
  async exportExcel(taskId) {
    const res = await axios.get(`/api/v1/reports/${taskId}/export/excel`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `report_${taskId}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  },
  dashboard: () => request.get('/dashboard'),
  wallboard: (params) => request.get('/dashboard/wallboard', { params }),
  coverage: () => request.get('/dashboard/coverage'),
  queueBoard: () => request.get('/dashboard/queue-board')
}

export const controlApi = {
  listPool: (params) => request.get('/controls/pool', { params }),
  createPool: (data) => request.post('/controls/pool', data),
  updatePool: (id, data) => request.put(`/controls/pool/${id}`, data),
  scanDependencies: (id) => request.get(`/controls/pool/${id}/dependencies`),
  changeLogs: (id, params) => request.get(`/controls/pool/${id}/change-logs`, { params }),
  listVersions: (id) => request.get(`/controls/pool/${id}/versions`),
  rollback: (id, versionId, data) => request.post(`/controls/pool/${id}/rollback/${versionId}`, data || {}),
  lookup: (params) => request.get('/controls/lookup', { params }),
  resolve: (data) => request.post('/controls/resolve', data),
  aiLocate: (data) => request.post('/controls/ai-locate', data),
  createPrivateBinding: (data) => request.post('/controls/private-bindings', data),
  getPrivateBindings: (taskId) => request.get(`/controls/private-bindings/${taskId}`),
  deletePrivateBinding: (id) => request.delete(`/controls/private-bindings/${id}`),
  getHealingRecords: (taskId) => request.get(`/controls/healing-records/${taskId}`),
  batchReplace: (data) => request.post('/controls/pool/batch-replace', data),
  batchValidate: (data) => request.post('/controls/batch-validate', data),
  unstableStats: (params) => request.get('/controls/unstable-stats', { params }),
  locatorFailureStats: (params) => request.get('/controls/locator-failure-stats', { params }),
  archivePool: (id, data) => request.post(`/controls/pool/${id}/archive`, data || {}),
  deletePool: (id, force = false) => request.delete(`/controls/pool/${id}`, { params: { force } })
}

export const ciApi = {
  getConfig: () => request.get('/ci/config'),
  updateConfig: (data) => request.put('/ci/config', data),
  recentJobs: () => request.get('/ci/jobs/recent'),
  exportJobs: () => request.get('/ci/jobs/export', { responseType: 'blob' }),
  taskStatus: (taskId) => request.get(`/ci/tasks/${taskId}/status`)
}

export const caseApi = {
  folderTree: () => request.get('/cases/folders/tree'),
  createFolder: (data) => request.post('/cases/folders', data),
  updateFolder: (id, data) => request.put(`/cases/folders/${id}`, data),
  deleteFolder: (id) => request.delete(`/cases/folders/${id}`),
  reorderFolders: (items) => request.put('/cases/folders/reorder', { items }),
  list: (params) => request.get('/cases', { params }),
  get: (id) => request.get(`/cases/${id}`),
  create: (data) => request.post('/cases', data),
  update: (id, data) => request.put(`/cases/${id}`, data),
  delete: (id) => request.delete(`/cases/${id}`),
  versions: (id) => request.get(`/cases/${id}/versions`),
  compareVersions: (id, versionA, versionB) => request.get(`/cases/${id}/versions/compare`, {
    params: { version_a: versionA, version_b: versionB }
  }),
  rollback: (id, versionId) => request.post(`/cases/${id}/rollback/${versionId}`),
  run: (id, data) => request.post(`/cases/${id}/run`, data || {}),
  dependencies: (id) => request.get(`/cases/${id}/dependencies`),
  transfer: (ids, ownerId) => request.post('/cases/transfer', { ids, owner_id: ownerId }),
  submitReview: (id) => request.post(`/cases/${id}/submit-review`),
  approve: (id) => request.post(`/cases/${id}/approve`),
  reject: (id, reason) => request.post(`/cases/${id}/reject`, { reason }),
  offlinePackage: (id) => request.post(`/cases/${id}/offline-package`)
}

/** AI 用例生成（TestBrain/LLM 外挂） */
export const aiCaseApi = {
  status: () => request.get('/ai-cases/status'),
  generate: (data) => request.post('/ai-cases/generate', data, { timeout: 180000 }),
  importDrafts: (data) => request.post('/ai-cases/import', data),
  parseDocument: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/ai-cases/parse-document', fd, { timeout: 120000 })
  },
  fetchConfluence: (data) => request.post('/ai-cases/fetch-confluence', data, { timeout: 60000 }),
  ingestPrd: (data) => request.post('/ai-cases/knowledge/ingest-prd', data, { timeout: 120000 }),
  ingestKnowledge: (data) => request.post('/ai-cases/knowledge/ingest', data, { timeout: 120000 }),
  listKnowledge: () => request.get('/ai-cases/knowledge/list')
}

export const commonStepApi = {
  list: () => request.get('/common-steps'),
  get: (id) => request.get(`/common-steps/${id}`),
  create: (data) => request.post('/common-steps', data),
  update: (id, data) => request.put(`/common-steps/${id}`, data),
  delete: (id) => request.delete(`/common-steps/${id}`),
  dependencies: (id) => request.get(`/common-steps/${id}/dependencies`),
  transfer: (ids, ownerId) => request.post('/common-steps/transfer', { ids, owner_id: ownerId })
}

export const suiteApi = {
  list: () => request.get('/suites'),
  get: (id) => request.get(`/suites/${id}`),
  create: (data) => request.post('/suites', data),
  update: (id, data) => request.put(`/suites/${id}`, data),
  delete: (id) => request.delete(`/suites/${id}`),
  run: (id) => request.post(`/suites/${id}/run`),
  restoreConfig: (runId) => request.post(`/suites/runs/${runId}/restore-config`)
}

export const envApi = {
  list: () => request.get('/environments'),
  create: (data) => request.post('/environments', data),
  update: (id, data) => request.put(`/environments/${id}`, data),
  delete: (id) => request.delete(`/environments/${id}`)
}

export const datasetApi = {
  list: () => request.get('/datasets'),
  get: (id) => request.get(`/datasets/${id}`),
  create: (data) => request.post('/datasets', data),
  update: (id, data) => request.put(`/datasets/${id}`, data),
  delete: (id) => request.delete(`/datasets/${id}`),
  importCsv: (id, csvContent) => request.post(`/datasets/${id}/import`, { csv_content: csvContent })
}

export const scheduleApi = {
  list: () => request.get('/schedules'),
  create: (data) => request.post('/schedules', data),
  update: (id, data) => request.put(`/schedules/${id}`, data),
  delete: (id) => request.delete(`/schedules/${id}`),
  toggle: (id, enabled) => request.post(`/schedules/${id}/toggle`, { enabled }),
  cronPreview: (expression) => request.post('/schedules/cron-preview', { expression })
}

export const recycleApi = {
  list: () => request.get('/recycle-bin'),
  stats: () => request.get('/recycle-bin/stats'),
  preview: (id) => request.get(`/recycle-bin/${id}/preview`),
  restore: (id) => request.post(`/recycle-bin/${id}/restore`),
  batchRestore: (ids) => request.post('/recycle-bin/batch-restore', { ids }),
  purge: (id) => request.delete(`/recycle-bin/${id}`),
  batchPurge: (ids) => request.post('/recycle-bin/batch-purge', { ids }),
  clearAll: () => request.delete('/recycle-bin')
}

export const recordApi = {
  start: (deviceId, meta = {}) => request.post('/operation-records/start', { device_id: deviceId, ...meta }),
  append: (id, event) => request.post(`/operation-records/${id}/events`, event),
  pause: (id) => request.post(`/operation-records/${id}/pause`),
  resume: (id) => request.post(`/operation-records/${id}/resume`),
  markSegment: (id, body) => request.post(`/operation-records/${id}/segments`, body),
  finish: (id, denoise = true) => request.post(
    `/operation-records/${id}/finish?denoise=${denoise}`,
    {},
    { timeout: LONG_TIMEOUT }
  ),
  cancel: (id) => request.post(`/operation-records/${id}/cancel`),
  get: (id) => request.get(`/operation-records/${id}`),
  validateLocators: (id) => request.post(`/operation-records/${id}/validate-locators`, {}, { timeout: LONG_TIMEOUT }),
  list: (params) => request.get('/operation-records', { params }),
  auditSummary: (limit = 10) => request.get('/operation-records/audit-summary', { params: { limit } }),
  updateSteps: (id, body) => request.put(`/operation-records/${id}/steps`, body),
  previewDenoise: (id) => request.get(`/operation-records/${id}/preview-denoise`),
  inspect: (id, x, y, displayWidth, displayHeight, blocking = false) => request.post(
    `/operation-records/${id}/inspect`,
    {
      x,
      y,
      blocking,
      ...(displayWidth ? { display_width: displayWidth } : {}),
      ...(displayHeight ? { display_height: displayHeight } : {})
    },
    { silent: true, timeout: blocking ? 15000 : 3500 }
  ),
  patchLastClick: (id, patch) => request.post(`/operation-records/${id}/last-click`, patch, { silent: true }),
  patchStepLocator: (id, stepIndex, patch) => request.post(
    `/operation-records/${id}/steps/${stepIndex}/locator`,
    patch
  ),
  warmInspect: (id) => request.post(`/operation-records/${id}/warm-inspect`, {}, { silent: true, timeout: 20000 }),
  uploadVideo: (id, blob, durationSeconds, operatorLabel, thumbnailBlob, tags = {}) => {
    const form = new FormData()
    form.append('file', blob, `session_${id}.webm`)
    form.append('duration_seconds', String(durationSeconds || 0))
    if (operatorLabel) form.append('operator_label', operatorLabel)
    if (tags.module_name) form.append('module_name', tags.module_name)
    if (tags.version_label) form.append('version_label', tags.version_label)
    if (tags.project_code) form.append('project_code', tags.project_code)
    if (tags.crop_rect_json) form.append('crop_rect_json', tags.crop_rect_json)
    if (tags.client_metrics_json) form.append('client_metrics_json', tags.client_metrics_json)
    if (thumbnailBlob) form.append('thumbnail', thumbnailBlob, `session_${id}_thumb.jpg`)
    return request.post(`/operation-records/${id}/video`, form, {
      timeout: LONG_TIMEOUT,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  emergencySave: (id, reason) => request.post(`/operation-records/${id}/emergency-save`, { reason }),
  toAutomationCase: (id, meta) => request.post(`/operation-records/${id}/to-automation-case`, meta, { timeout: LONG_TIMEOUT }),
  toCase: (id, meta) => request.post(`/operation-records/${id}/to-case`, meta, { timeout: LONG_TIMEOUT }),
  toCasesBySegments: (id, meta) => request.post(`/operation-records/${id}/to-cases-by-segments`, meta, { timeout: LONG_TIMEOUT })
}

export const commentApi = {
  list: (assetType, assetId) => request.get('/asset-comments', { params: { asset_type: assetType, asset_id: assetId } }),
  create: (data) => request.post('/asset-comments', data),
  delete: (id) => request.delete(`/asset-comments/${id}`)
}

export const baselineApi = {
  list: () => request.get('/version-baselines'),
  get: (id) => request.get(`/version-baselines/${id}`),
  create: (data) => request.post('/version-baselines', data),
  update: (id, data) => request.put(`/version-baselines/${id}`, data),
  archive: (id) => request.post(`/version-baselines/${id}/archive`),
  compare: (id) => request.get(`/version-baselines/${id}/compare`)
}

export const teamApi = {
  list: () => request.get('/teams'),
  get: (id) => request.get(`/teams/${id}`),
  create: (data) => request.post('/teams', data),
  update: (id, data) => request.put(`/teams/${id}`, data),
  assignUser: (userId, teamId) => request.put(`/teams/users/${userId}/team`, { team_id: teamId })
}

export const auditApi = {
  list: (params) => request.get('/audit-logs', { params }),
  stats: () => request.get('/audit-logs/stats'),
  archive: () => request.post('/audit-logs/archive'),
  archives: () => request.get('/audit-logs/archives'),
  verify: (filename) => request.post('/audit-logs/archives/verify', { filename })
}

export const credentialApi = {
  list: () => request.get('/credentials'),
  create: (data) => request.post('/credentials', data),
  update: (id, data) => request.put(`/credentials/${id}`, data),
  delete: (id) => request.delete(`/credentials/${id}`)
}

export const globalParamApi = {
  list: () => request.get('/global-parameters'),
  create: (data) => request.post('/global-parameters', data),
  update: (id, data) => request.put(`/global-parameters/${id}`, data),
  toggle: (id, enabled) => request.post(`/global-parameters/${id}/toggle`, { enabled }),
  logs: (id) => request.get(`/global-parameters/${id}/logs`)
}

export const assertPolicyApi = {
  list: () => request.get('/assert-policies'),
  create: (data) => request.post('/assert-policies', data),
  update: (id, data) => request.put(`/assert-policies/${id}`, data),
  delete: (id) => request.delete(`/assert-policies/${id}`)
}

export const dataFactoryApi = {
  listTemplates: () => request.get('/data-factory/templates'),
  getTemplate: (id) => request.get(`/data-factory/templates/${id}`),
  createTemplate: (data) => request.post('/data-factory/templates', data),
  updateTemplate: (id, data) => request.put(`/data-factory/templates/${id}`, data),
  cleanupTask: (taskId) => request.post(`/data-factory/cleanup/task/${taskId}`)
}

export const backupApi = {
  list: () => request.get('/backups'),
  create: () => request.post('/backups'),
  restore: (filename) => request.post(`/backups/${encodeURIComponent(filename)}/restore`),
  delete: (filename) => request.delete(`/backups/${encodeURIComponent(filename)}`),
  downloadUrl: (filename) => `/api/v1/backups/${encodeURIComponent(filename)}/download`
}

export const monitorApi = {
  snapshot: () => request.get('/platform/monitor'),
  executorEvents: () => request.get('/platform/executor-events'),
  clearExecutorEvents: () => request.delete('/platform/executor-events')
}

export const accountApi = {
  list: () => request.get('/accounts'),
  create: (data) => request.post('/accounts', data),
  update: (id, data) => request.put(`/accounts/${id}`, data),
  delete: (id) => request.delete(`/accounts/${id}`),
  release: (id) => request.post(`/accounts/${id}/release`),
  acquire: (data) => request.post('/accounts/acquire', data),
  archive: (id) => request.post(`/accounts/${id}/archive`)
}

export const appPackageApi = {
  list: () => request.get('/app-packages'),
  get: (id) => request.get(`/app-packages/${id}`),
  upload: (formData) => request.post('/app-packages', formData, { timeout: LONG_TIMEOUT }),
  delete: (id) => request.delete(`/app-packages/${id}`),
  batchInstall: (id, deviceIds) => request.post(`/app-packages/${id}/batch-install`, { device_ids: deviceIds }, { timeout: LONG_TIMEOUT }),
  reverify: (id) => request.post(`/app-packages/${id}/reverify`),
  async download(id, filename) {
    const res = await axios.get(`/api/v1/app-packages/${id}/download`, {
      responseType: 'blob',
      headers: authHeaders(),
      timeout: LONG_TIMEOUT
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename || `app_package_${id}`
    a.click()
    URL.revokeObjectURL(url)
  }
}

export const checkpointApi = {
  listSuiteRuns: (suiteId) => request.get(`/suites/${suiteId}/runs`),
  getRun: (runId) => request.get(`/suite-runs/${runId}`),
  pauseRun: (runId) => request.post(`/suite-runs/${runId}/pause`),
  resumeRun: (runId) => request.post(`/suite-runs/${runId}/resume`),
  resumeTask: (taskId, fromStep) => request.post(`/tasks/${taskId}/resume`, fromStep ? { from_step: fromStep } : {})
}
