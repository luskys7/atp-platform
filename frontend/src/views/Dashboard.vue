<template>
  <div class="page-container page-overview">
    <!-- 1. 置顶操作栏 -->
    <div class="dash-sticky">
      <div class="dash-sticky__info">
        <h2>首页概览</h2>
        <p>平台运行与测试质量一览</p>
      </div>
      <div class="dash-sticky__actions">
        <el-button type="primary" @click="openCreateCase">
          <el-icon><DocumentAdd /></el-icon>
          一键新建用例
        </el-button>
        <el-button class="btn-record" type="warning" @click="goRecordSelectDevice">
          <el-icon><VideoCamera /></el-icon>
          一键录制
        </el-button>
        <el-button type="success" @click="showTaskDialog = true">
          <el-icon><VideoPlay /></el-icon>
          批量执行任务
        </el-button>
        <el-button class="btn-devices" @click="$router.push('/devices')">
          <el-icon><Iphone /></el-icon>
          设备池管理
        </el-button>
      </div>
    </div>

    <!-- 2. 核心指标：统一 4 列网格，与下方风险卡对齐 -->
    <div class="metrics-block" v-loading="loading">
      <div class="metrics-titles">
        <div class="metrics-group-title">用例 & 测试质量</div>
        <div class="metrics-group-title">设备 & 运行任务</div>
      </div>
      <div class="metrics-grid">
        <StatCard
          label="用例总数"
          :value="coverage.total_cases || 0"
          icon="Document"
          tone="case"
          variant="primary"
          clickable
          @click="$router.push('/cases')"
        />
        <StatCard
          label="生效可用用例"
          :value="coverage.active_cases || 0"
          icon="CircleCheck"
          tone="pass"
          variant="success"
          clickable
          @click="$router.push({ path: '/cases', query: { status: 'active' } })"
        />
        <StatCard
          label="设备总数"
          :value="stats.devices?.total || 0"
          icon="Iphone"
          tone="device"
          variant="primary"
          clickable
          @click="$router.push('/devices')"
        />
        <StatCard
          label="在线可用设备"
          :value="stats.devices?.online || 0"
          icon="CircleCheck"
          tone="pass"
          variant="success"
          :risk="onlineAlert"
          clickable
          @click="$router.push({ path: '/devices', query: { status: 'online' } })"
        >
          {{ stats.devices?.online || 0 }}
          <template #sub>
            离线 {{ deviceOffline }} 台
          </template>
        </StatCard>
        <StatCard
          label="自动化覆盖率"
          :value="`${Number(coverage.automation_rate || 0).toFixed(1)}%`"
          icon="PieChart"
          tone="cover"
          variant="warning"
          clickable
          @click="$router.push('/cases')"
        />
        <StatCard
          label="历史通过率"
          icon="TrendCharts"
          tone="pass"
          :variant="passRateVariant"
          :risk="passRateLow"
          clickable
          @click="$router.push('/reports')"
        >
          {{ Number(stats.pass_rate || 0).toFixed(1) }}%
          <template #extra>
            <el-tag v-if="passRateLow" type="danger" size="small" effect="plain" style="margin-left:6px">偏低</el-tag>
          </template>
        </StatCard>
        <StatCard
          label="当前运行中任务"
          :value="stats.tasks?.running || 0"
          icon="Sunny"
          tone="cover"
          variant="warning"
          clickable
          @click="$router.push({ path: '/tasks', query: { status: 'running' } })"
        />
        <StatCard
          label="待评审用例"
          :value="coverage.review_cases || 0"
          icon="Clock"
          tone="case"
          variant="primary"
          :risk="(coverage.review_cases || 0) > 0"
          clickable
          @click="$router.push({ path: '/cases', query: { status: 'review' } })"
        />
      </div>
    </div>

    <!-- 3. 风险待办（与上方 4 列同宽对齐） -->
    <div class="risk-section">
      <div class="board-head risk-section__head">
        <span class="risk-title">
          <el-icon><WarningFilled /></el-icon>
          待处理风险提醒
        </span>
        <el-tag v-if="riskTotal > 0" type="warning" effect="dark" round>{{ riskTotal }} 项待处理</el-tag>
        <el-tag v-else type="success" effect="plain" round>暂无风险</el-tag>
      </div>
      <div class="risk-grid">
        <div class="risk-item" :class="{ active: riskRecording > 0 }">
          <div class="risk-item__label">录制识别风险</div>
          <div class="risk-item__desc">
            <strong :class="{ danger: riskRecording > 0 }">{{ riskRecording }}</strong>
            个会话识别率低于 95%
          </div>
          <el-button class="risk-action" size="small" type="warning" @click="$router.push('/recording-quality')">去处理</el-button>
        </div>
        <div class="risk-item" :class="{ active: deviceOffline > 0 }">
          <div class="risk-item__label">离线设备提醒</div>
          <div class="risk-item__desc">
            <strong :class="{ danger: deviceOffline > 0 }">{{ deviceOffline }}</strong>
            台设备离线不可用
          </div>
          <el-button class="risk-action" size="small" type="warning" @click="$router.push('/devices')">去处理</el-button>
        </div>
        <div class="risk-item" :class="{ active: (coverage.review_cases || 0) > 0 }">
          <div class="risk-item__label">待评审用例</div>
          <div class="risk-item__desc">
            <strong :class="{ danger: (coverage.review_cases || 0) > 0 }">{{ coverage.review_cases || 0 }}</strong>
            条用例等待审核生效
          </div>
          <el-button class="risk-action" size="small" type="warning" @click="$router.push({ path: '/cases', query: { status: 'review' } })">去处理</el-button>
        </div>
        <div class="risk-item" :class="{ active: failedTaskCount > 0 }">
          <div class="risk-item__label">失败执行任务</div>
          <div class="risk-item__desc">
            <strong :class="{ danger: failedTaskCount > 0 }">{{ failedTaskCount }}</strong>
            个任务执行失败
          </div>
          <el-button class="risk-action" size="small" type="warning" @click="$router.push('/reports')">去处理</el-button>
        </div>
      </div>
    </div>

    <!-- 4. 录制双栏 -->
    <div class="dual-grid section-gap">
      <AppCard class="dash-panel" shadow="never" :hover="false">
        <template #header>
          <span>录制自动化用例</span>
        </template>
        <div class="record-panel">
          <div v-if="recordingActive" class="record-status live">
            设备 <strong>{{ recordingDeviceLabel }}</strong> 正在录制中
          </div>
          <div v-else class="record-status">
            上次录制设备 ID：
            <strong>{{ lastRecordDeviceId || '暂无' }}</strong>
          </div>
          <div class="record-actions">
            <el-button type="warning" size="large" :disabled="!canContinueRecord" @click="goContinueOrQuick">
              <el-icon><VideoCamera /></el-icon>
              一键继续录制
            </el-button>
            <el-button type="warning" plain size="large" @click="goNewRecordSession">
              <el-icon><Plus /></el-icon>
              新建录制会话
            </el-button>
          </div>
          <el-button type="primary" link class="record-history-link" @click="$router.push('/recordings')">
            历史录制记录 →
          </el-button>
        </div>
      </AppCard>
      <AppCard class="dash-panel" shadow="never" :hover="false" v-loading="auditLoading">
        <template #header>
          <div class="board-head">
            <span>录制质量审计</span>
            <el-button type="primary" link @click="$router.push('/recording-quality')">完整报告</el-button>
          </div>
        </template>
        <div v-if="audit.sample_count" class="audit-panel">
          <div class="audit-metrics">
            <div class="audit-metric">
              <div class="num">{{ audit.sample_count }}</div>
              <div class="lbl">样本总数</div>
            </div>
            <div class="audit-metric">
              <div class="num" :class="{ warn: (audit.avg_recognition_rate || 0) < 95 }">
                {{ audit.avg_recognition_rate != null ? `${audit.avg_recognition_rate}%` : '-' }}
              </div>
              <div class="lbl">平均控件识别率</div>
            </div>
            <div class="audit-metric">
              <div class="num" :class="{ warn: (audit.cpu_ok_rate || 0) < 80 }">
                {{ audit.cpu_ok_rate != null ? `${audit.cpu_ok_rate}%` : '-' }}
              </div>
              <div class="lbl">CPU 达标率</div>
            </div>
          </div>
          <div v-if="riskRecording > 0" class="audit-risk-banner">
            <el-icon><WarningFilled /></el-icon>
            {{ riskRecording }} 个会话识别率低于 95%
          </div>
          <div class="audit-actions">
            <el-button type="primary" @click="$router.push('/recording-quality')">查看完整质量报告</el-button>
            <el-button type="warning" plain :disabled="!riskRecording" @click="goOptimizeLowSessions">
              批量优化低识别会话
            </el-button>
          </div>
        </div>
        <el-empty v-else description="暂无已完成录制会话" :image-size="72" />
      </AppCard>
    </div>

    <!-- 5. 可视化统计 -->
    <div class="dual-grid section-gap">
      <AppCard class="dash-panel chart-card" shadow="never" :hover="false" @click="$router.push('/devices')">
        <template #header>
          <div class="board-head">
            <span>设备状态分布</span>
            <el-button type="primary" link @click.stop="$router.push('/devices')">设备池</el-button>
          </div>
        </template>
        <div ref="deviceChartRef" class="chart-box" @click.stop="$router.push('/devices')"></div>
        <div class="device-legend">
          <span class="lg online">在线 {{ stats.devices?.online || 0 }} 台</span>
          <span class="lg busy">忙碌 {{ stats.devices?.busy || 0 }} 台</span>
          <span class="lg offline" :class="{ danger: deviceOffline > 0 }">离线 {{ deviceOffline }} 台</span>
        </div>
      </AppCard>
      <AppCard class="dash-panel" shadow="never" :hover="false">
        <template #header>
          <div class="board-head">
            <span>近 7 日任务 & 用例执行统计</span>
            <span class="chart-summary">
              7 天平均覆盖率 {{ weekAvgCover }}% · 平均通过率 {{ weekAvgPass }}%
            </span>
          </div>
        </template>
        <div ref="taskChartRef" class="chart-box"></div>
      </AppCard>
    </div>

    <!-- 新建用例弹窗 -->
    <el-dialog v-model="showCaseDialog" title="一键新建用例" width="520px" destroy-on-close>
      <el-form :model="caseForm" label-width="96px">
        <el-form-item label="用例名称" required>
          <el-input v-model="caseForm.name" placeholder="例：登录页冒烟用例" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="caseForm.platform" style="width:100%">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="双端" value="both" />
          </el-select>
        </el-form-item>
        <el-form-item label="脚本类型">
          <el-select v-model="caseForm.script_type" style="width:100%">
            <el-option label="可视化" value="visual" />
            <el-option label="Python" value="python" />
            <el-option label="Appium" value="appium" />
          </el-select>
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="caseForm.app_package" placeholder="com.example.app（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCaseDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingCase" @click="submitCreateCase">创建并编辑</el-button>
      </template>
    </el-dialog>

    <!-- 批量执行任务弹窗 -->
    <el-dialog v-model="showTaskDialog" title="批量执行 · 创建测试任务" width="640px" destroy-on-close>
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.name" placeholder="例：回归套件批量跑测" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="平台">
              <el-select v-model="taskForm.platform" style="width:100%">
                <el-option label="Android" value="android" />
                <el-option label="iOS" value="ios" />
                <el-option label="双端" value="both" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="脚本类型">
              <el-select v-model="taskForm.script_type" style="width:100%">
                <el-option label="可视化" value="visual" />
                <el-option label="Python" value="python" />
                <el-option label="Appium" value="appium" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="脚本内容" required>
          <el-input v-model="taskForm.script_content" type="textarea" :rows="5" placeholder="输入自动化测试脚本，或选择可视化后填写 JSON 步骤" />
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="taskForm.app_package" placeholder="com.example.app" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="并行设备">
              <el-input-number v-model="taskForm.parallel_count" :min="1" :max="20" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="超时(秒)">
              <el-input-number v-model="taskForm.timeout_seconds" :min="60" :max="7200" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用录屏">
          <el-switch v-model="taskForm.enable_recording" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTaskDialog = false">取消</el-button>
        <el-button type="success" :loading="creatingTask" @click="submitCreateTask">创建并执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi, recordApi, taskApi, deviceApi, caseApi } from '@/api'
import { operationRecordingState } from '@/composables/useOperationRecording'
import { markRecordingBoot } from '@/composables/useRecordingStartup'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { CHART } from '@/utils/chartTheme'

const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'
const router = useRouter()

const loading = ref(false)
const auditLoading = ref(false)
const stats = ref({})
const coverage = ref({})
const audit = ref({})
const dailyStats = ref([])
const deviceChartRef = ref()
const taskChartRef = ref()
let deviceChart, taskChart

const showCaseDialog = ref(false)
const showTaskDialog = ref(false)
const creatingCase = ref(false)
const creatingTask = ref(false)

const caseForm = reactive({
  name: '',
  platform: 'android',
  script_type: 'visual',
  app_package: ''
})

const taskForm = reactive({
  name: '',
  platform: 'android',
  script_type: 'visual',
  script_content: '{"version":1,"steps":[]}',
  app_package: '',
  parallel_count: 1,
  timeout_seconds: 3600,
  enable_recording: true
})

const recordingActive = computed(() => operationRecordingState.active)
const recordingDeviceLabel = computed(() =>
  operationRecordingState.deviceName || `ID ${operationRecordingState.deviceId}`
)
const lastRecordDeviceId = ref(localStorage.getItem(LAST_RECORD_DEVICE_KEY) || '')
const canContinueRecord = computed(() =>
  recordingActive.value || !!lastRecordDeviceId.value
)

const deviceOffline = computed(() => Math.max(
  0,
  (stats.value.devices?.total || 0) - (stats.value.devices?.online || 0) - (stats.value.devices?.busy || 0)
))

const minPassRate = computed(() => stats.value.min_pass_rate || 99)
const passRateLow = computed(() => (stats.value.pass_rate || 0) < minPassRate.value)
const passRateVariant = computed(() => {
  const rate = stats.value.pass_rate || 0
  if (rate >= minPassRate.value) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
})
const onlineAlert = computed(() => (stats.value.devices?.total || 0) > 0 && (stats.value.devices?.online || 0) === 0)

const riskRecording = computed(() => audit.value.sessions_below_threshold?.length || 0)
const failedTaskCount = computed(() => stats.value.tasks?.failed || 0)
const riskTotal = computed(() =>
  (riskRecording.value > 0 ? 1 : 0)
  + (deviceOffline.value > 0 ? 1 : 0)
  + ((coverage.value.review_cases || 0) > 0 ? 1 : 0)
  + (failedTaskCount.value > 0 ? 1 : 0)
)

const weekAvgCover = computed(() => {
  if (coverage.value.automation_rate != null) return Number(coverage.value.automation_rate).toFixed(1)
  return '0.0'
})
const weekAvgPass = computed(() => {
  const days = dailyStats.value
  if (!days.length) return Number(stats.value.pass_rate || 0).toFixed(1)
  const withRate = days.filter(d => d.pass_rate != null)
  if (!withRate.length) return Number(stats.value.pass_rate || 0).toFixed(1)
  const avg = withRate.reduce((s, d) => s + d.pass_rate, 0) / withRate.length
  return avg.toFixed(1)
})

function openCreateCase() {
  caseForm.name = ''
  caseForm.platform = 'android'
  caseForm.script_type = 'visual'
  caseForm.app_package = ''
  showCaseDialog.value = true
}

async function submitCreateCase() {
  if (!caseForm.name?.trim()) {
    ElMessage.warning('请填写用例名称')
    return
  }
  creatingCase.value = true
  try {
    const payload = {
      name: caseForm.name.trim(),
      platform: caseForm.platform,
      script_type: caseForm.script_type,
      app_package: caseForm.app_package || '',
      case_status: 'draft',
      timeout_seconds: 3600,
      enable_recording: true
    }
    if (caseForm.script_type === 'visual') {
      payload.steps_content = JSON.stringify({ version: 1, human_delay: true, steps: [] })
    } else {
      payload.script_content = ''
    }
    const res = await caseApi.create(payload)
    const id = res.data?.id
    ElMessage.success('用例已创建')
    showCaseDialog.value = false
    if (id) router.push(`/cases/editor/${id}?asset=1`)
    else router.push('/cases')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    creatingCase.value = false
  }
}

async function submitCreateTask() {
  if (!taskForm.name?.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (!taskForm.script_content?.trim()) {
    ElMessage.warning('请填写脚本内容')
    return
  }
  creatingTask.value = true
  try {
    const res = await taskApi.create({ ...taskForm })
    ElMessage.success('任务创建成功')
    showTaskDialog.value = false
    const id = res.data?.id
    if (id) router.push(`/tasks/${id}`)
    else router.push('/tasks')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    creatingTask.value = false
  }
}

function goRecordSelectDevice() {
  markRecordingBoot()
  ElMessage.info('请选择设备开始录制')
  router.push({ path: '/devices', query: { intent: 'record' } })
}

function goContinueOrQuick() {
  markRecordingBoot()
  if (recordingActive.value && operationRecordingState.deviceId) {
    router.push(`/devices/${operationRecordingState.deviceId}/screen`)
    return
  }
  if (lastRecordDeviceId.value) {
    router.push(`/devices/${lastRecordDeviceId.value}/screen?auto_record=1`)
    return
  }
  goNewRecordSession()
}

function goNewRecordSession() {
  markRecordingBoot()
  ElMessage.info('请选择设备开启新录制会话')
  router.push({ path: '/devices', query: { intent: 'record' } })
}

function goOptimizeLowSessions() {
  router.push({ path: '/recording-quality', query: { focus: 'low' } })
}

function buildLast7Days() {
  const days = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() - i)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    days.push({ date: key, label: `${d.getMonth() + 1}/${d.getDate()}`, total: 0, failed: 0, pass_rate: null, pass_sum: 0, pass_n: 0 })
  }
  return days
}

function dayKey(raw) {
  if (!raw) return ''
  const s = String(raw)
  if (s.length >= 10) return s.slice(0, 10)
  try {
    const d = new Date(raw)
    if (Number.isNaN(d.getTime())) return ''
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

async function loadDailyStats() {
  const days = buildLast7Days()
  const map = Object.fromEntries(days.map(d => [d.date, d]))
  try {
    const [reportsRes, tasksRes] = await Promise.all([
      reportApi.list({ page: 1, page_size: 100 }).catch(() => ({ data: { list: [] } })),
      taskApi.list({ page: 1, page_size: 100 }).catch(() => ({ data: { list: [] } }))
    ])
    const reports = reportsRes.data?.list || reportsRes.data || []
    for (const r of reports) {
      const key = dayKey(r.created_at || r.finished_at || r.updated_at)
      const bucket = map[key]
      if (!bucket) continue
      const cases = Number(r.total_cases || r.case_count || r.total || 0)
      const failed = Number(r.fail_count || r.failed_count || 0)
      bucket.total += cases || 1
      bucket.failed += failed
      if (r.pass_rate != null) {
        bucket.pass_sum += Number(r.pass_rate)
        bucket.pass_n += 1
      }
    }
    // 无报告维度时用任务补齐执行量
    const tasks = tasksRes.data?.list || []
    for (const t of tasks) {
      const key = dayKey(t.created_at || t.finished_at || t.updated_at)
      const bucket = map[key]
      if (!bucket) continue
      if (!reports.length) bucket.total += 1
      if (t.status === 'failed') bucket.failed += 1
    }
    for (const d of days) {
      if (d.pass_n) d.pass_rate = d.pass_sum / d.pass_n
    }
  } catch { /* ignore */ }
  dailyStats.value = days
}

async function loadCoverageFallback() {
  if (coverage.value.total_cases != null) return
  try {
    const [all, active, review] = await Promise.all([
      caseApi.list({ page: 1, page_size: 1 }).catch(() => null),
      caseApi.list({ page: 1, page_size: 1, status: 'active' }).catch(() => null),
      caseApi.list({ page: 1, page_size: 1, status: 'review' }).catch(() => null)
    ])
    const total = all?.data?.total ?? 0
    const activeN = active?.data?.total ?? 0
    const reviewN = review?.data?.total ?? 0
    coverage.value = {
      total_cases: total,
      active_cases: activeN,
      review_cases: reviewN,
      automation_rate: total ? (activeN / total) * 100 : 0
    }
  } catch { /* ignore */ }
}

async function loadStats() {
  loading.value = true
  auditLoading.value = true
  try {
    const [dash, cov, auditRes] = await Promise.all([
      reportApi.dashboard(),
      reportApi.coverage().catch(() => ({ data: {} })),
      recordApi.auditSummary(10).catch(() => ({ data: {} }))
    ])
    stats.value = dash.data || {}
    coverage.value = cov.data || {}
    audit.value = auditRes.data || {}
    lastRecordDeviceId.value = localStorage.getItem(LAST_RECORD_DEVICE_KEY) || ''
    await loadCoverageFallback()
    await loadDailyStats()
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
    auditLoading.value = false
  }
}

function renderCharts() {
  const online = stats.value.devices?.online || 0
  const busy = stats.value.devices?.busy || 0
  const offline = deviceOffline.value

  if (deviceChartRef.value) {
    deviceChart?.dispose()
    deviceChart = echarts.init(deviceChartRef.value)
    deviceChart.setOption({
      color: ['#10B981', '#F59E0B', '#94A3B8'],
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '46%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { name: '在线', value: online },
          { name: '忙碌', value: busy },
          { name: '离线', value: offline }
        ]
      }]
    })
    deviceChart.off('click')
    deviceChart.on('click', () => router.push('/devices'))
  }

  if (taskChartRef.value) {
    const days = dailyStats.value.length ? dailyStats.value : buildLast7Days()
    taskChart?.dispose()
    taskChart = echarts.init(taskChartRef.value)
    taskChart.setOption({
      color: ['#0284C7', '#EF4444'],
      tooltip: {
        trigger: 'axis',
        formatter(params) {
          const idx = params[0]?.dataIndex ?? 0
          const d = days[idx] || {}
          const lines = [`${d.label || params[0]?.axisValue}`]
          for (const p of params) lines.push(`${p.marker}${p.seriesName}: ${p.value}`)
          if (d.pass_rate != null) lines.push(`当日通过率: ${Number(d.pass_rate).toFixed(1)}%`)
          return lines.join('<br/>')
        }
      },
      legend: { top: 0, right: 0, textStyle: { color: CHART.axis } },
      grid: { left: 44, right: 16, top: 36, bottom: 28 },
      xAxis: {
        type: 'category',
        data: days.map(d => d.label),
        axisLine: { lineStyle: { color: CHART.grid } },
        axisLabel: { color: CHART.axis }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: CHART.grid } },
        axisLabel: { color: CHART.axis }
      },
      series: [
        {
          name: '执行用例总数',
          type: 'bar',
          barGap: '20%',
          barWidth: 14,
          itemStyle: { borderRadius: [4, 4, 0, 0] },
          data: days.map(d => d.total)
        },
        {
          name: '失败用例数',
          type: 'bar',
          barWidth: 14,
          itemStyle: { borderRadius: [4, 4, 0, 0] },
          data: days.map(d => d.failed)
        }
      ]
    })
  }
}

function handleResize() {
  deviceChart?.resize()
  taskChart?.resize()
}

onMounted(() => {
  loadStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  deviceChart?.dispose()
  taskChart?.dispose()
})
</script>

<style scoped>
.dash-sticky {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: -8px -8px 20px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border: 1px solid var(--atp-border-neutral);
  border-radius: 14px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
}

.dash-sticky__info h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--atp-text);
  line-height: 1.3;
}

.dash-sticky__info p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.dash-sticky__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.btn-record {
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(245, 158, 11, 0.35);
}

.btn-devices {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
  --el-button-hover-text-color: #334155;
}

.metrics-block {
  margin-bottom: 16px;
}

.metrics-titles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 10px;
  /* 与 4 列指标对齐：左标题跨前两列，右标题跨后两列 */
}

.metrics-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--atp-text-secondary);
  padding-left: 2px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.section-gap {
  margin-top: 16px;
}

/* 与上方 metrics-titles / 4 列网格同宽对齐：两列 = 各占两列指标卡 */
.dual-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: stretch;
}

.dash-panel {
  margin-bottom: 0 !important;
  height: 100%;
  border: none !important;
  --el-card-border-color: transparent;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06) !important;
  border-radius: 14px !important;
  background: #fff;
}

.dash-panel:hover {
  border-color: transparent !important;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06) !important;
  transform: none !important;
}

.dash-panel :deep(.el-card__header) {
  border-bottom: none;
  background: transparent;
  padding: 16px 18px 8px;
}

.dash-panel :deep(.el-card__body) {
  padding: 8px 18px 18px;
  height: calc(100% - 52px);
  display: flex;
  flex-direction: column;
}

.board-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}

.risk-section {
  margin-top: 16px;
  padding: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

.risk-section__head {
  margin-bottom: 12px;
  padding: 0 2px;
}

.risk-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #c2410c;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.risk-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  background: #fff;
  border: none;
  outline: none;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  min-height: 128px;
}

.risk-item.active {
  background: #fff7ed;
}

.risk-item__label {
  font-size: 13px;
  font-weight: 600;
  color: #c2410c;
}

.risk-item__desc {
  flex: 1;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

.risk-item__desc strong {
  font-size: 20px;
  font-weight: 700;
  color: var(--atp-text);
  margin-right: 4px;
}

.risk-item__desc strong.danger {
  color: #ea580c;
}

.risk-action {
  align-self: flex-start;
  margin-top: 4px;
}

.record-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 180px;
}

.record-status {
  font-size: 14px;
  color: var(--atp-text-secondary);
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff7ed;
}

.record-status.live {
  background: #ecfdf5;
  color: #047857;
}

.record-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.record-history-link {
  align-self: flex-start;
  margin-top: auto;
}

.audit-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 180px;
}

.audit-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.audit-metric {
  text-align: center;
  padding: 12px 8px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.audit-metric .num {
  font-size: 22px;
  font-weight: 700;
  color: var(--atp-text);
}

.audit-metric .num.warn {
  color: #d97706;
}

.audit-metric .lbl {
  margin-top: 4px;
  font-size: 12px;
  color: var(--atp-text-secondary);
}

.audit-risk-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 13px;
  font-weight: 600;
}

.audit-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chart-box {
  height: 280px;
  flex: 1;
  min-height: 260px;
  cursor: default;
}

.chart-card {
  cursor: pointer;
}

.chart-card :deep(.el-card__body) {
  cursor: pointer;
}

.device-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding-bottom: 4px;
  font-size: 13px;
}

.device-legend .lg::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.device-legend .online::before { background: #10B981; }
.device-legend .busy::before { background: #F59E0B; }
.device-legend .offline::before { background: #94A3B8; }
.device-legend .offline.danger { color: #dc2626; font-weight: 600; }

.chart-summary {
  font-size: 12px;
  color: var(--atp-text-secondary);
  font-weight: 400;
}

@media (max-width: 1100px) {
  .metrics-grid,
  .risk-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .metrics-titles,
  .dual-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .metrics-grid,
  .risk-grid,
  .audit-metrics {
    grid-template-columns: 1fr;
  }

  .dash-sticky {
    flex-direction: column;
    align-items: stretch;
  }

  .dash-sticky__actions {
    width: 100%;
  }

  .dash-sticky__actions .el-button {
    flex: 1;
  }
}
</style>
