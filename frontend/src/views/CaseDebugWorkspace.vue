<template>
  <div class="debug-workbench">
    <header class="debug-header">
      <div class="debug-title">
        <h1>同屏调试</h1>
        <span class="case-name">{{ caseMeta.name || '用例' }}</span>
        <el-tag v-if="task" :type="taskStatusMap[task.status]?.type" size="small" effect="light">
          任务 #{{ task.id }} · {{ taskStatusMap[task.status]?.label || task.status }}
        </el-tag>
      </div>
      <div class="debug-actions">
        <el-button
          v-if="canStopDebug"
          size="small"
          type="danger"
          :loading="stopping"
          @click="stopDebug"
        >强制终止</el-button>
        <el-button size="small" @click="$router.back()">返回</el-button>
      </div>
    </header>

    <div class="debug-body" v-loading="loading">
      <!-- 左：实时投屏 -->
      <main class="col-screen">
        <ScreenCanvasPanel
          v-if="selectedDeviceId"
          :key="selectedDeviceId"
          :device-id="selectedDeviceId"
          :auto-connect="true"
          :max-height="'calc(100vh - 168px)'"
        />
        <div v-else class="no-device">
          <el-icon :size="48"><Iphone /></el-icon>
          <p>请选择在线 Android 设备</p>
        </div>
      </main>

      <!-- 中：调试控制 -->
      <aside class="col-ops">
        <div class="ops-block">
          <div class="ops-head">调试控制</div>
          <div class="ops-controls">
            <el-select
              v-model="selectedDeviceId"
              placeholder="选择设备"
              style="width:100%"
              size="default"
              @change="onDeviceChange"
            >
              <el-option
                v-for="d in onlineDevices"
                :key="d.id"
                :label="`${d.name || d.serial_number} (${d.status})`"
                :value="d.id"
                :disabled="d.platform !== 'android' || d.status === 'offline'"
              />
            </el-select>
            <el-input
              v-model="appPackage"
              placeholder="目标 App 包名（坐标步骤必填，如 com.example.app）"
              clearable
              size="default"
            />
            <el-alert
              v-if="needsAppPackageWarning"
              type="warning"
              :closable="false"
              show-icon
              title="当前用例含坐标点击步骤且未设置包名：调试时不会自动启动被测 App，坐标可能点到桌面图标从而打开其他应用。"
            />
            <el-button type="warning" :loading="running" style="width:100%" @click="runDebug">
              执行整用例调试
            </el-button>
            <el-button
              v-if="canStopDebug"
              type="danger"
              plain
              :loading="stopping"
              style="width:100%"
              @click="stopDebug"
            >
              强制终止
            </el-button>
            <div class="ops-links">
              <el-button size="small" plain @click="goEditor">编辑步骤</el-button>
              <el-button size="small" plain @click="goScreenPage">回到投屏页</el-button>
              <el-button size="small" plain @click="$router.push('/tasks')">任务列表</el-button>
            </div>
          </div>
        </div>

        <div class="ops-block">
          <div class="ops-head">任务监控</div>
          <div v-if="task" class="task-brief">
            <div class="brief-row">
              <span>任务 #{{ task.id }}</span>
              <el-tag :type="taskStatusMap[task.status]?.type" size="small">
                {{ taskStatusMap[task.status]?.label }}
              </el-tag>
            </div>
            <div v-if="taskErrorText" class="brief-row fail task-error">
              {{ taskErrorText }}
            </div>
            <div v-if="stepProgress.failedStep" class="brief-row fail">
              失败步骤 <strong>#{{ stepProgress.failedStep }}</strong>
            </div>
            <div class="brief-actions">
              <el-button
                v-if="['failed','timeout','cancelled'].includes(task.status)"
                type="warning"
                size="small"
                @click="resumeTask"
              >断点续跑</el-button>
              <el-button size="small" @click="$router.push(`/tasks/${task.id}`)">详情</el-button>
            </div>
          </div>
          <el-empty v-else description="点击「执行整用例调试」" :image-size="48" />
        </div>

        <div class="ops-block flex-grow">
          <div class="ops-head">
            <span>用例步骤 ({{ steps.length }})</span>
            <el-tag v-if="stepProgress.activeStep" type="primary" size="small" effect="plain">
              #{{ stepProgress.activeStep }}
            </el-tag>
          </div>
          <el-scrollbar class="steps-scroll">
            <div
              v-for="(step, idx) in steps"
              :key="idx"
              class="step-row"
              :class="getStepClass(idx)"
              @click="onCaseStepClick(idx)"
            >
              <span class="step-no">{{ idx + 1 }}</span>
              <div class="step-body">
                <div class="step-type">{{ stepTypeLabel(step.type) }}</div>
                <div class="step-desc">{{ stepSummary(step) }}</div>
                <div v-if="stepLocator(step)" class="step-locator">{{ stepLocator(step) }}</div>
              </div>
              <el-icon v-if="getStepClass(idx) === 'active'" class="spin"><Loading /></el-icon>
              <el-icon v-else-if="getStepClass(idx) === 'passed'" class="ok"><CircleCheck /></el-icon>
              <el-icon v-else-if="getStepClass(idx) === 'failed'" class="fail"><CircleClose /></el-icon>
            </div>
            <el-empty v-if="!steps.length" description="无用例步骤" :image-size="56" />
          </el-scrollbar>
        </div>
      </aside>

      <!-- 右：步骤列表 / 运行日志 -->
      <aside class="col-monitor">
        <div class="monitor-toolbar">
          <el-tabs v-model="monitorTab" class="monitor-tabs">
            <el-tab-pane name="events" label="步骤列表" />
            <el-tab-pane name="logs" label="运行日志" />
          </el-tabs>
          <div class="monitor-actions">
            <el-button
              size="small"
              type="danger"
              :disabled="!canStopDebug"
              :loading="stopping"
              @click="stopDebug"
            >强制终止</el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="!recentLogs.length && !stepEvents.length"
              @click="clearLogs"
            >清空</el-button>
          </div>
        </div>

        <div v-show="monitorTab === 'events'" class="monitor-body">
          <el-scrollbar class="monitor-scroll" ref="eventScrollRef">
            <div
              v-for="ev in stepEvents"
              :key="ev.id"
              class="event-row"
              :class="[`tone-${ev.tone}`, { expandable: !!ev.detail && ev.tone === 'error' }]"
            >
              <span class="event-icon">
                <el-icon v-if="ev.tone === 'success'" class="ok"><CircleCheck /></el-icon>
                <el-icon v-else-if="ev.tone === 'error'" class="fail"><CircleClose /></el-icon>
                <el-icon v-else-if="ev.tone === 'warn'" class="warn"><WarningFilled /></el-icon>
                <el-icon v-else class="info"><InfoFilled /></el-icon>
              </span>
              <span class="event-time">{{ fmtShort(ev.time) }}</span>
              <div class="event-main">
                <div class="event-msg">{{ ev.message }}</div>
                <details v-if="ev.detail && ev.tone === 'error'" class="event-detail">
                  <summary>点击展开/收起详情</summary>
                  <pre>{{ ev.detail }}</pre>
                </details>
              </div>
            </div>
            <el-empty v-if="!stepEvents.length" description="执行后将在此展示步骤事件" :image-size="56" />
          </el-scrollbar>
        </div>

        <div v-show="monitorTab === 'logs'" class="monitor-body">
          <el-scrollbar class="monitor-scroll" ref="logScrollRef">
            <div
              v-for="log in recentLogs"
              :key="log.id"
              class="log-line"
              :class="logTone(log)"
              :data-step="logStepHint(log.message)"
            >
              <span class="event-icon">
                <el-icon v-if="logTone(log) === 'error'" class="fail"><CircleClose /></el-icon>
                <el-icon v-else-if="logTone(log) === 'warn'" class="warn"><WarningFilled /></el-icon>
                <el-icon v-else-if="logTone(log) === 'success'" class="ok"><CircleCheck /></el-icon>
                <el-icon v-else class="info"><InfoFilled /></el-icon>
              </span>
              <span class="log-time">{{ fmtShort(log.created_at) }}</span>
              <span class="log-msg">{{ log.message }}</span>
            </div>
            <el-empty v-if="!recentLogs.length" description="暂无运行日志" :image-size="56" />
          </el-scrollbar>
        </div>

        <div class="monitor-footer">
          <template v-if="isPolling">
            <el-icon class="spin"><Loading /></el-icon>
            <span>运行中 · 实时刷新</span>
          </template>
          <template v-else-if="task">
            <span>状态：{{ taskStatusMap[task.status]?.label || task.status }}</span>
          </template>
          <template v-else>
            <span>等待开始调试</span>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { caseApi, deviceApi, checkpointApi, taskApi, fetchTaskMonitorBundle } from '@/api'
import { taskStatusMap } from '@/utils/status'
import { parseTaskStepProgress, stepStatus, parseStepEvents } from '@/composables/useTaskStepProgress'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import ScreenCanvasPanel from '@/components/ScreenCanvasPanel.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const caseId = route.params.id
const taskIdFromQuery = route.query.taskId
const deviceIdFromQuery = route.query.deviceId

const loading = ref(false)
const running = ref(false)
const stopping = ref(false)
const monitorTab = ref('events')
const caseMeta = reactive({ name: '', platform: 'android', appPackage: '' })
const steps = ref([])
const task = ref(null)
const executions = ref([])
const logs = ref([])
/** 清空日志后仅展示此 id 之后的新日志（轮询不会把旧日志刷回来） */
const logFloorId = ref(0)
const onlineDevices = ref([])
const selectedDeviceId = ref(deviceIdFromQuery ? Number(deviceIdFromQuery) : null)
const appPackage = ref('')
const logScrollRef = ref(null)
const eventScrollRef = ref(null)
let pollTimer = null
const POLL_MS = 1000

const typeLabels = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', launch: '启动', swipe: '滑动',
  assert_text: '断言', assert_exists: '存在', tap_ocr: 'OCR点击', manual_wait: '人工',
  set_relative_time: '设置相对时间'
}

function stepTypeLabel(t) { return typeLabels[t] || t }

function stepLocator(step) {
  return formatStepLocator(step)
}

function stepSummary(step) {
  const target = formatStepTarget(step)
  if (target) return target
  if (step.type === 'wait') return `${step.seconds || 0}s`
  return step.element_name || step.expected || ''
}

const hasTapXySteps = computed(() =>
  steps.value.some(s => s.type === 'tap_xy' || (s.type === 'click' && !s.element_name && !s.locators))
)

const needsAppPackageWarning = computed(() =>
  hasTapXySteps.value && !(appPackage.value || '').trim()
)

const stepProgress = computed(() =>
  parseTaskStepProgress(logs.value, executions.value)
)

const recentLogs = computed(() =>
  logs.value.filter(l => (l.id || 0) > logFloorId.value).slice(-200)
)

const stepEvents = computed(() => parseStepEvents(recentLogs.value))

const canStopDebug = computed(() =>
  !!task.value?.id && ['running', 'queued', 'waiting_manual', 'pending'].includes(task.value.status)
)

const taskErrorText = computed(() => {
  const t = task.value
  if (!t) return ''
  if (t.error_code === 'E1002') {
    return '设备被占用或调度失败，请取消旧任务后重试'
  }
  return t.error_message || t.error_code || ''
})

function getStepClass(idx) {
  return stepStatus(idx, stepProgress.value, task.value?.status)
}

function fmtShort(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(11, 19)
}

function logStepHint(msg) {
  const m = (msg || '').match(/step=(\d+)/)
  return m ? m[1] : ''
}

function logTone(log) {
  const level = String(log?.level || '').toLowerCase()
  const msg = String(log?.message || '')
  if (level === 'error' || /ATP_STEP_END[^\n]*status=(fail|interrupt|exception)|AssertionError|RuntimeError|Traceback|CHECKPOINT_FAILED/i.test(msg)) {
    return 'error'
  }
  if (level === 'warn' || /WARN|STEP_SKIPPED|status=skip/i.test(msg)) return 'warn'
  if (/ATP_STEP_END[^\n]*status=ok|ATP_STEP_BEGIN/i.test(msg)) return 'success'
  return 'info'
}

function scrollMonitorToBottom() {
  nextTick(() => {
    const refs = [logScrollRef.value, eventScrollRef.value]
    for (const r of refs) {
      const wrap = r?.wrapRef || r?.$el?.querySelector?.('.el-scrollbar__wrap')
      if (wrap) wrap.scrollTop = wrap.scrollHeight
    }
  })
}

function onCaseStepClick(idx) {
  monitorTab.value = 'logs'
  nextTick(() => {
    const n = idx + 1
    const el = document.querySelector(`.log-line[data-step="${n}"]`)
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

async function loadCase() {
  const res = await caseApi.get(caseId)
  const c = res.data
  caseMeta.name = c.name
  caseMeta.platform = c.platform
  caseMeta.appPackage = c.app_package || ''
  appPackage.value = c.app_package || ''
  try {
    const parsed = JSON.parse(c.steps_content || '{}')
    steps.value = (parsed.steps || []).filter(s => s.enabled !== false)
  } catch {
    steps.value = []
  }
}

async function loadDevices() {
  const res = await deviceApi.list({ page: 1, page_size: 50, platform: 'android' })
  onlineDevices.value = (res.data.list || []).filter(d => d.status !== 'offline')
  if (!selectedDeviceId.value && onlineDevices.value.length) {
    const online = onlineDevices.value.find(d => d.status === 'online')
    selectedDeviceId.value = online?.id || onlineDevices.value[0].id
  }
}

async function loadTaskData() {
  if (!task.value?.id) return
  const prevLen = logs.value.length
  const bundle = await fetchTaskMonitorBundle(task.value.id)
  if (!bundle) {
    stopTaskPolling()
    task.value = null
    executions.value = []
    logs.value = []
    return
  }
  task.value = bundle.task
  executions.value = bundle.executions
  logs.value = bundle.logs
  if ((bundle.logs || []).length > prevLen) {
    scrollMonitorToBottom()
  }
}

function stopTaskPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function initTask() {
  if (taskIdFromQuery) {
    task.value = { id: Number(taskIdFromQuery) }
    await loadTaskData()
    const exec = executions.value.find(e => e.device_id)
    if (exec?.device_id && !selectedDeviceId.value) {
      selectedDeviceId.value = exec.device_id
    }
  }
}

async function runDebug() {
  if (!selectedDeviceId.value) {
    ElMessage.warning('请先选择设备')
    return
  }
  if (needsAppPackageWarning.value) {
    try {
      await ElMessageBox.confirm(
        '未填写目标 App 包名，且用例含坐标点击。继续执行可能误点桌面图标并打开其他 App。是否仍要继续？',
        '调试风险提示',
        { type: 'warning', confirmButtonText: '仍要执行', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  running.value = true
  try {
    const payload = { device_id: selectedDeviceId.value }
    if (appPackage.value?.trim()) {
      payload.app_package = appPackage.value.trim()
    }
    const runRes = await caseApi.run(caseId, payload)
    task.value = { id: runRes.data.id }
    logFloorId.value = 0
    logs.value = []
    monitorTab.value = 'events'
    ElMessage.success(`调试任务 #${runRes.data.id} 已提交`)
    router.replace({
      path: `/cases/${caseId}/debug`,
      query: {
        taskId: runRes.data.id,
        deviceId: selectedDeviceId.value || undefined
      }
    })
    await loadTaskData()
    if (isPolling.value && !pollTimer) {
      pollTimer = setInterval(loadTaskData, POLL_MS)
    }
  } finally {
    running.value = false
  }
}

async function stopDebug() {
  if (!task.value?.id) return
  try {
    await ElMessageBox.confirm('确认结束当前调试任务？执行将被终止。', '结束调试', {
      type: 'warning',
      confirmButtonText: '结束',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  stopping.value = true
  try {
    await taskApi.cancel(task.value.id)
    ElMessage.success(`已结束调试任务 #${task.value.id}`)
    await loadTaskData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '结束调试失败')
  } finally {
    stopping.value = false
  }
}

function clearLogs() {
  const maxId = logs.value.reduce((m, l) => Math.max(m, l.id || 0), 0)
  logFloorId.value = maxId
  ElMessage.success('已清空显示')
}

async function resumeTask() {
  if (!task.value?.id) return
  const from = stepProgress.value.failedStep
  const res = await checkpointApi.resumeTask(task.value.id, from)
  ElMessage.success(`续跑任务 #${res.data.id}`)
  task.value = { id: res.data.id }
  router.replace({
    path: `/cases/${caseId}/debug`,
    query: { taskId: res.data.id, deviceId: selectedDeviceId.value || undefined }
  })
  await loadTaskData()
}

function goEditor() {
  router.push(`/cases/editor/${caseId}?asset=1`)
}

function goScreenPage() {
  if (selectedDeviceId.value) {
    router.push(`/devices/${selectedDeviceId.value}/screen`)
  }
}

function onDeviceChange() {
  router.replace({
    path: `/cases/${caseId}/debug`,
    query: {
      taskId: task.value?.id || undefined,
      deviceId: selectedDeviceId.value || undefined
    }
  })
}

const isPolling = computed(() =>
  task.value && ['running', 'queued', 'waiting_manual'].includes(task.value.status)
)

watch(isPolling, (v) => {
  if (v && !pollTimer) {
    pollTimer = setInterval(loadTaskData, POLL_MS)
  } else if (!v) {
    stopTaskPolling()
  }
})

function onTaskDeleted(event) {
  const deletedId = event?.detail?.id
  if (!deletedId || task.value?.id !== deletedId) return
  stopTaskPolling()
  task.value = null
  executions.value = []
  logs.value = []
  logFloorId.value = 0
}

onMounted(async () => {
  window.addEventListener('atp-task-deleted', onTaskDeleted)
  loading.value = true
  try {
    await Promise.all([loadCase(), loadDevices()])
    await initTask()
    if (isPolling.value) {
      pollTimer = setInterval(loadTaskData, POLL_MS)
    }
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('atp-task-deleted', onTaskDeleted)
  stopTaskPolling()
})
</script>

<style scoped lang="scss">
.debug-workbench {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: var(--atp-bg);
  overflow: hidden;
}

.debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--atp-bg-elevated);
  border-bottom: 1px solid var(--atp-border-light);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.debug-title {
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
}

.case-name {
  font-size: 14px;
  color: var(--atp-text-secondary);
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.debug-body {
  flex: 1;
  display: grid;
  grid-template-columns: auto minmax(280px, 360px) minmax(320px, 1fr);
  gap: 0;
  min-height: 0;
}

.col-screen {
  padding: 12px 16px;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  background: var(--atp-brand-50);
  border-right: 1px solid var(--atp-border-light);

  :deep(.screen-panel) {
    max-height: calc(100vh - 168px);
  }
}

.col-ops {
  background: var(--atp-bg-elevated);
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  border-right: 1px solid var(--atp-border-light);
}

.col-monitor {
  background: #fff;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.monitor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border-bottom: 1px solid var(--atp-border-light);
  flex-shrink: 0;
  min-height: 46px;
}

.monitor-tabs {
  flex: 1;
  min-width: 0;

  :deep(.el-tabs__header) {
    margin: 0;
    border-bottom: none;
  }
  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }
  :deep(.el-tabs__item) {
    height: 46px;
    line-height: 46px;
    font-weight: 600;
  }
}

.monitor-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.monitor-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.monitor-scroll {
  flex: 1;
  padding: 8px 10px;
  min-height: 0;
}

.monitor-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--atp-border-light);
  font-size: 13px;
  color: var(--atp-text-secondary);
  background: #fafbfc;
}

.ops-block {
  border-bottom: 1px solid var(--atp-border-light);
  flex-shrink: 0;

  &.flex-grow {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    flex-shrink: 1;
  }
}

.ops-head {
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--atp-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.ops-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ops-controls {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ops-links {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.steps-scroll {
  flex: 1;
  padding: 8px;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: background 0.15s;
  border: 1px solid transparent;

  &:hover { background: var(--atp-primary-bg); }

  &.active {
    background: var(--atp-primary-bg);
    border-color: rgba(8, 145, 178, 0.35);
  }
  &.passed { opacity: 0.55; }
  &.failed {
    background: var(--atp-danger-bg);
    border-color: rgba(255, 138, 138, 0.35);
  }
}

.step-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--atp-primary);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-body { flex: 1; min-width: 0; }
.step-type { font-size: 12px; font-weight: 600; }
.step-desc {
  font-size: 11px;
  color: var(--atp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-locator {
  font-size: 10px;
  color: var(--el-color-info);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spin { color: var(--atp-accent); animation: spin 1s linear infinite; }
.ok { color: var(--atp-success); }
.fail { color: var(--atp-danger); }

@keyframes spin {
  to { transform: rotate(360deg); }
}

.no-device {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--atp-text-secondary);
  gap: 12px;
}

.task-brief {
  padding: 12px 14px;
  font-size: 13px;
}

.brief-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;

  &.fail { color: #c8875e; }
}

.task-error {
  display: block;
  font-size: 12px;
  line-height: 1.5;
  color: var(--atp-danger);
  background: var(--atp-danger-bg);
  padding: 8px 10px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.brief-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.log-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  font-family: Consolas, 'SF Mono', monospace;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 6px;
  line-height: 1.45;
  background: #f8fafc;
  border: 1px solid #eef2f7;

  &.success { background: #f0fdf4; border-color: #bbf7d0; }
  &.error { background: #fef2f2; border-color: #fecaca; }
  &.warn { background: #fffbeb; border-color: #fde68a; }
  &.info { background: #eff6ff; border-color: #bfdbfe; }
}

.log-time,
.event-time {
  color: var(--atp-text-muted);
  flex-shrink: 0;
  width: 58px;
  font-variant-numeric: tabular-nums;
}

.log-msg,
.event-msg {
  word-break: break-word;
  flex: 1;
  min-width: 0;
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 6px;
  border: 1px solid #eef2f7;
  background: #f8fafc;

  &.tone-success { background: #f0fdf4; border-color: #bbf7d0; }
  &.tone-error { background: #fef2f2; border-color: #fecaca; }
  &.tone-warn { background: #fffbeb; border-color: #fde68a; }
  &.tone-info { background: #eff6ff; border-color: #bfdbfe; }
}

.event-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.event-main {
  flex: 1;
  min-width: 0;
}

.event-detail {
  margin-top: 6px;
  font-size: 12px;

  summary {
    cursor: pointer;
    color: var(--atp-primary);
    user-select: none;
  }

  pre {
    margin: 8px 0 0;
    padding: 8px 10px;
    background: rgba(0, 0, 0, 0.04);
    border-radius: 6px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 220px;
    overflow: auto;
  }
}

.warn { color: #d97706; }
.info { color: #2563eb; }

@media (max-width: 1280px) {
  .debug-body {
    grid-template-columns: auto minmax(260px, 320px) minmax(280px, 1fr);
  }
}

@media (max-width: 1100px) {
  .debug-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }
  .col-screen,
  .col-ops,
  .col-monitor {
    border-right: none;
    border-bottom: 1px solid var(--atp-border-light);
    max-height: 50vh;
  }
  .col-monitor {
    max-height: 45vh;
  }
}
</style>
