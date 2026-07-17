<template>
  <div class="page-container tasks-page">
    <PageHeader title="测试任务" subtitle="批量执行、定时调度与一键回归跑测（用例编辑请前往「测试用例」页面）">
      <template #actions>
        <div class="header-actions">
          <el-button class="btn-muted" @click="$router.push('/cases')">
            <el-icon><EditPen /></el-icon> 编辑用例
          </el-button>
          <el-button class="btn-muted" @click="$router.push({ path: '/platform-config', query: { tab: 'schedule' } })">
            <el-icon><Timer /></el-icon> 定时任务
          </el-button>

          <el-tooltip content="请先勾选任务后再执行批量操作" :disabled="hasSelection" placement="bottom">
            <span class="batch-wrap">
              <el-dropdown :disabled="!hasSelection" @command="onBatchCommand">
                <el-button class="btn-muted" :disabled="!hasSelection" :loading="batchLoading">
                  批量操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rerun">批量重跑</el-dropdown-item>
                    <el-dropdown-item command="cancel">批量终止</el-dropdown-item>
                    <el-dropdown-item command="export">批量导出报告</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </el-tooltip>

          <el-button v-if="userStore.canEdit" type="primary" class="btn-run" @click="openCreateDialog()">
            <el-icon><VideoPlay /></el-icon> 一键跑测
          </el-button>
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：实时统计 -->
    <div class="stats-row" v-loading="statsLoading">
      <div class="stat-card tone-queue" @click="quickFilterStatus('queued')">
        <div class="stat-value is-warn">{{ liveStats.queued }}</div>
        <div class="stat-label">排队等待任务</div>
      </div>
      <div class="stat-card tone-running" @click="quickFilterStatus('running')">
        <div class="stat-value is-ok">{{ liveStats.running }}</div>
        <div class="stat-label">正在运行任务</div>
      </div>
      <div class="stat-card tone-success" @click="quickFilterStatus('success')">
        <div class="stat-value">{{ liveStats.success }}</div>
        <div class="stat-label">执行成功任务</div>
      </div>
      <div
        class="stat-card tone-fail"
        :class="{ highlight: liveStats.failed > 0 }"
        @click="quickFilterStatus('failed')"
      >
        <div class="stat-value is-fail">{{ liveStats.failed }}</div>
        <div class="stat-label">执行失败任务</div>
      </div>
    </div>

    <!-- 模块 3：队列看板 -->
    <AppCard :hover="false" class="queue-card">
      <template #header>
        <div class="queue-header">
          <span class="queue-title">任务队列看板</span>
          <span class="queue-meta">
            队列深度 {{ queueBoard.queue_depth || 0 }}・排队 {{ queueBoard.queued_tasks || 0 }}・运行 {{ queueBoard.running_tasks || 0 }}
          </span>
        </div>
      </template>

      <el-table
        v-if="queueItems.length"
        :data="queueItems"
        size="small"
        stripe
        max-height="260"
        class="queue-table"
        empty-text=""
      >
        <el-table-column prop="position" label="序号" width="64" align="center" />
        <el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="执行平台" width="90">
          <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column label="任务类型" width="120">
          <template #default="{ row }">{{ scriptTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <span class="priority-text" :class="priorityClass(row.priority)">{{ priorityLabel(row.priority) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等待时长" width="110" align="center">
          <template #default="{ row }">{{ formatWait(row.wait_seconds) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.canEdit && row.task_id && ['running', 'queued', 'pending'].includes(row.status)"
              size="small"
              type="warning"
              @click.stop="cancelTaskById(row.task_id, row.name)"
            >终止任务</el-button>
            <el-button
              v-if="row.task_id"
              size="small"
              type="primary"
              @click.stop="$router.push(`/tasks/${row.task_id}`)"
            >查看实时日志</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="queue-empty">
        <p>当前无等待执行任务</p>
        <el-button v-if="userStore.canEdit" type="primary" @click="openCreateDialog()">一键跑测</el-button>
      </div>
    </AppCard>

    <!-- 模块 4 + 5：筛选 + 历史表格 -->
    <AppCard :hover="false" class="list-card">
      <div class="filter-bar">
        <el-select v-model="filters.status" placeholder="任务状态" clearable style="width:130px" @change="onFilterChange">
          <el-option label="全部" value="" />
          <el-option label="排队中" value="queued" />
          <el-option label="运行中" value="running" />
          <el-option label="执行成功" value="success" />
          <el-option label="执行失败" value="failed" />
          <el-option label="已终止" value="cancelled" />
        </el-select>
        <el-select v-model="filters.platform" placeholder="运行平台" clearable style="width:120px" @change="onFilterChange">
          <el-option label="安卓" value="android" />
          <el-option label="iOS" value="ios" />
          <el-option label="双端" value="both" />
        </el-select>
        <el-select v-model="filters.task_type" placeholder="任务类型" clearable style="width:150px" @change="onFilterChange">
          <el-option label="可视化用例" value="visual" />
          <el-option label="套件批量回归" value="suite" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="优先级" clearable style="width:110px" @change="onFilterChange">
          <el-option label="高" value="high" />
          <el-option label="中" value="mid" />
          <el-option label="低" value="low" />
        </el-select>
        <el-select v-model="filters.time_range" placeholder="时间区间" style="width:120px" @change="onTimeRangeChange">
          <el-option label="全部时间" value="" />
          <el-option label="今日" value="today" />
          <el-option label="近 7 天" value="7d" />
          <el-option label="自定义时间" value="custom" />
        </el-select>
        <el-date-picker
          v-if="filters.time_range === 'custom'"
          v-model="filters.customRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width:320px"
          @change="onFilterChange"
        />
        <div class="filter-right">
          <div class="auto-refresh">
            <span>自动刷新</span>
            <el-switch v-model="autoRefresh" />
            <span class="auto-hint">3 秒</span>
          </div>
          <el-button type="primary" plain :loading="loading" @click="refreshAll">
            <el-icon><Refresh /></el-icon> 手动刷新
          </el-button>
          <el-button @click="resetFilters">重置筛选条件</el-button>
        </div>
      </div>

      <el-table
        :data="pagedTasks"
        v-loading="loading"
        stripe
        empty-text=""
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="任务编号" width="90" />
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="运行平台" width="90">
          <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
        </el-table-column>
        <el-table-column label="任务类型" width="130">
          <template #default="{ row }">{{ scriptTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="parallel_count" label="并行数量" width="90" align="center" />
        <el-table-column label="执行状态" width="110">
          <template #default="{ row }">
            <span :class="{ 'tag-running-pulse': row.status === 'running' }">
              <el-tag size="small" effect="plain" :class="statusTagClass(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <span class="priority-text" :class="priorityClass(row.priority)">{{ priorityLabel(row.priority) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" @click="$router.push(`/tasks/${row.id}`)">详情</el-button>
              <el-button
                v-if="userStore.canEdit && canRerun(row)"
                size="small"
                type="success"
                @click="rerunTask(row)"
              >重跑</el-button>
              <el-button size="small" class="btn-muted-sm" @click="goEditSource(row)">去编辑用例</el-button>
              <el-button
                v-if="userStore.canEdit"
                size="small"
                type="warning"
                @click="copyTask(row)"
              >复制任务</el-button>
              <el-button
                v-if="userStore.isAdmin && row.status !== 'running'"
                size="small"
                type="danger"
                @click="deleteTask(row)"
              >删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !filteredTasks.length" class="table-empty">
        <p class="empty-title">暂无执行任务记录</p>
        <div class="empty-actions">
          <el-button v-if="userStore.canEdit" type="primary" @click="openCreateDialog()">一键跑测</el-button>
          <el-button @click="$router.push('/suites')">前往测试套件新建回归任务</el-button>
        </div>
      </div>

      <div class="pager-bar">
        <div class="pager-stats">当前筛选结果共 <strong>{{ filteredTasks.length }}</strong> 条任务</div>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredTasks.length"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
        />
      </div>
    </AppCard>

    <!-- 模块 6：底部快捷栏 -->
    <section class="shortcut-bar">
      <div class="shortcut-card" @click="$router.push('/cases')">
        <el-icon :size="22"><Document /></el-icon>
        <div>
          <h4>测试用例</h4>
          <p>单条用例快速发起执行</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/suites')">
        <el-icon :size="22"><Collection /></el-icon>
        <div>
          <h4>测试套件</h4>
          <p>批量回归套件一键跑测</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/reports')">
        <el-icon :size="22"><DataAnalysis /></el-icon>
        <div>
          <h4>测试报告</h4>
          <p>查看全部历史执行报告</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/devices')">
        <el-icon :size="22"><Monitor /></el-icon>
        <div>
          <h4>设备管理</h4>
          <p>查看设备占用、释放异常占用设备</p>
        </div>
      </div>
    </section>

    <!-- 一键跑测弹窗 -->
    <el-dialog v-model="showCreateDialog" title="一键跑测 · 创建执行任务" width="680px" destroy-on-close>
      <el-tabs v-model="createMode" class="create-tabs">
        <el-tab-pane label="选择用例" name="case" />
        <el-tab-pane label="选择套件" name="suite" />
        <el-tab-pane label="自定义脚本" name="custom" />
      </el-tabs>

      <el-form v-if="createMode === 'case'" label-width="100px">
        <el-form-item label="选择用例" required>
          <el-select v-model="runCaseId" filterable placeholder="搜索并选择可视化用例" style="width:100%">
            <el-option v-for="c in caseOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <span class="form-hint">将按用例当前步骤创建可视化任务并提交调度</span>
        </el-form-item>
      </el-form>

      <el-form v-else-if="createMode === 'suite'" label-width="100px">
        <el-form-item label="选择套件" required>
          <el-select v-model="runSuiteId" filterable placeholder="搜索并选择回归套件" style="width:100%">
            <el-option v-for="s in suiteOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <span class="form-hint">将发起套件批量回归跑测</span>
        </el-form-item>
      </el-form>

      <el-form v-else :model="taskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.name" placeholder="例：登录流程冒烟测试" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="运行平台">
              <el-select v-model="taskForm.platform" style="width:100%">
                <el-option label="安卓" value="android" />
                <el-option label="iOS" value="ios" />
                <el-option label="双端" value="both" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务类型">
              <el-select v-model="taskForm.script_type" style="width:100%">
                <el-option label="可视化用例任务" value="visual" />
                <el-option label="Python 脚本" value="python" />
                <el-option label="Appium 脚本" value="appium" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="脚本内容" required>
          <el-input v-model="taskForm.script_content" type="textarea" :rows="6" placeholder="输入自动化测试脚本或可视化步骤 JSON" />
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="taskForm.app_package" placeholder="com.example.app" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="并行数量">
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
        <el-form-item label="账号池互斥">
          <el-switch v-model="taskForm.use_account_pool" />
          <span class="form-hint" style="margin-left:8px">执行时自动占用空闲账号，完成后释放</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createTask">开始跑测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { taskApi, reportApi, caseApi, suiteApi, notifyTaskDeleted, isNotFoundError } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const statsLoading = ref(false)
const creating = ref(false)
const batchLoading = ref(false)
const allTasks = ref([])
const page = ref(1)
const pageSize = ref(20)
const showCreateDialog = ref(false)
const createMode = ref('case')
const runCaseId = ref(null)
const runSuiteId = ref(null)
const caseOptions = ref([])
const suiteOptions = ref([])
const queueBoard = ref({ items: [] })
const selectedRows = ref([])
const autoRefresh = ref(false)
const liveStats = reactive({ queued: 0, running: 0, success: 0, failed: 0 })
let pollTimer = null

const filters = reactive({
  status: '',
  platform: '',
  task_type: '',
  priority: '',
  time_range: '',
  customRange: null
})

const taskForm = reactive({
  name: '',
  platform: 'android',
  script_type: 'visual',
  script_content: '',
  app_package: '',
  parallel_count: 1,
  timeout_seconds: 3600,
  enable_recording: true,
  use_account_pool: false
})

const hasSelection = computed(() => selectedRows.value.length > 0)

const queueItems = computed(() => {
  const items = queueBoard.value.items || []
  return items.filter(i => ['queued', 'pending', 'running', 'paused'].includes(i.status))
})

function isSuiteTask(row) {
  return !!(row.suite_run_id || row.suite_run_item_id)
}

function priorityBucket(p) {
  const n = Number(p ?? 5)
  if (n >= 8) return 'high'
  if (n <= 3) return 'low'
  return 'mid'
}

function platformLabel(p) {
  return { android: '安卓', ios: 'iOS', both: '双端' }[p] || p || '—'
}

function scriptTypeLabel(row) {
  if (isSuiteTask(row)) return '套件回归任务'
  return {
    visual: '可视化用例任务',
    python: 'Python 脚本任务',
    appium: 'Appium 脚本任务'
  }[row.script_type] || row.script_type || '—'
}

function statusLabel(s) {
  return {
    pending: '排队中',
    queued: '排队中',
    running: '运行中',
    success: '执行成功',
    failed: '执行失败',
    cancelled: '手动终止',
    timeout: '执行失败',
    paused: '排队中',
    waiting_manual: '待人工'
  }[s] || s
}

function statusTagClass(s) {
  if (s === 'queued' || s === 'pending' || s === 'paused') return 'tag-queued'
  if (s === 'running') return 'tag-running'
  if (s === 'success') return 'tag-success'
  if (s === 'failed' || s === 'timeout') return 'tag-failed'
  if (s === 'cancelled') return 'tag-cancelled'
  return ''
}

function priorityLabel(p) {
  return { high: '高', mid: '中', low: '低' }[priorityBucket(p)]
}

function priorityClass(p) {
  return `priority-${priorityBucket(p)}`
}

function formatWait(sec) {
  const n = Number(sec || 0)
  if (n < 60) return `${n} 秒`
  const m = Math.floor(n / 60)
  const s = n % 60
  return s ? `${m} 分 ${s} 秒` : `${m} 分`
}

function inTimeRange(row) {
  if (!filters.time_range) return true
  const t = row.created_at ? new Date(row.created_at).getTime() : 0
  if (!t) return false
  const now = Date.now()
  if (filters.time_range === 'today') {
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    return t >= start.getTime()
  }
  if (filters.time_range === '7d') {
    return t >= now - 7 * 24 * 3600 * 1000
  }
  if (filters.time_range === 'custom' && filters.customRange?.length === 2) {
    const [a, b] = filters.customRange
    return t >= new Date(a).getTime() && t <= new Date(b).getTime()
  }
  return true
}

const filteredTasks = computed(() => {
  return (allTasks.value || []).filter(row => {
    if (filters.status) {
      if (filters.status === 'queued' && !['queued', 'pending', 'paused'].includes(row.status)) return false
      else if (filters.status === 'failed' && !['failed', 'timeout'].includes(row.status)) return false
      else if (!['queued', 'failed'].includes(filters.status) && row.status !== filters.status) return false
    }
    if (filters.platform && row.platform !== filters.platform) return false
    if (filters.task_type === 'visual' && (row.script_type !== 'visual' || isSuiteTask(row))) return false
    if (filters.task_type === 'suite' && !isSuiteTask(row)) return false
    if (filters.priority && priorityBucket(row.priority) !== filters.priority) return false
    if (!inTimeRange(row)) return false
    return true
  })
})

const pagedTasks = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredTasks.value.slice(start, start + pageSize.value)
})

function canRerun(row) {
  return ['pending', 'queued', 'paused', 'failed', 'success', 'cancelled', 'timeout'].includes(row.status)
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function onFilterChange() {
  page.value = 1
}

function onTimeRangeChange() {
  if (filters.time_range !== 'custom') filters.customRange = null
  onFilterChange()
}

function quickFilterStatus(status) {
  filters.status = status
  page.value = 1
}

function resetFilters() {
  filters.status = ''
  filters.platform = ''
  filters.task_type = ''
  filters.priority = ''
  filters.time_range = ''
  filters.customRange = null
  page.value = 1
}

async function loadStats() {
  statsLoading.value = true
  try {
    const [queuedRes, pendingRes, runningRes, successRes, failedRes] = await Promise.all([
      taskApi.list({ page: 1, page_size: 1, status: 'queued' }),
      taskApi.list({ page: 1, page_size: 1, status: 'pending' }),
      taskApi.list({ page: 1, page_size: 1, status: 'running' }),
      taskApi.list({ page: 1, page_size: 1, status: 'success' }),
      taskApi.list({ page: 1, page_size: 1, status: 'failed' })
    ])
    liveStats.queued = (queuedRes.data?.total || 0) + (pendingRes.data?.total || 0)
    liveStats.running = runningRes.data?.total || 0
    liveStats.success = successRes.data?.total || 0
    liveStats.failed = failedRes.data?.total || 0
  } catch {
    liveStats.queued = (queueBoard.value.queued_tasks || 0) + (queueBoard.value.pending_tasks || 0)
    liveStats.running = queueBoard.value.running_tasks || 0
  } finally {
    statsLoading.value = false
  }
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await taskApi.list({ page: 1, page_size: 200 })
    allTasks.value = res.data?.list || []
    const maxPage = Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } finally {
    loading.value = false
  }
}

async function loadQueueBoard() {
  try {
    queueBoard.value = (await reportApi.queueBoard()).data || { items: [] }
  } catch {
    queueBoard.value = { items: [] }
  }
}

async function refreshAll() {
  await Promise.all([loadTasks(), loadQueueBoard(), loadStats()])
}

async function loadCreateOptions() {
  try {
    const [casesRes, suitesRes] = await Promise.all([
      caseApi.list({ page: 1, page_size: 200 }),
      suiteApi.list()
    ])
    caseOptions.value = casesRes.data?.list || casesRes.data || []
    suiteOptions.value = Array.isArray(suitesRes.data) ? suitesRes.data : (suitesRes.data?.list || [])
  } catch {
    caseOptions.value = []
    suiteOptions.value = []
  }
}

function openCreateDialog() {
  createMode.value = 'case'
  runCaseId.value = null
  runSuiteId.value = null
  Object.assign(taskForm, {
    name: '',
    platform: 'android',
    script_type: 'visual',
    script_content: '',
    app_package: '',
    parallel_count: 1,
    timeout_seconds: 3600,
    enable_recording: true,
    use_account_pool: false
  })
  showCreateDialog.value = true
  loadCreateOptions()
}

async function createTask() {
  creating.value = true
  try {
    if (createMode.value === 'case') {
      if (!runCaseId.value) { ElMessage.warning('请选择用例'); return }
      const res = await caseApi.run(runCaseId.value, {})
      ElMessage.success('用例任务已创建并提交')
      showCreateDialog.value = false
      if (res.data?.id) {
        // stay on list; refresh
      }
    } else if (createMode.value === 'suite') {
      if (!runSuiteId.value) { ElMessage.warning('请选择套件'); return }
      await suiteApi.run(runSuiteId.value)
      ElMessage.success('套件回归任务已发起')
      showCreateDialog.value = false
    } else {
      if (!taskForm.name?.trim() || !taskForm.script_content?.trim()) {
        ElMessage.warning('请填写任务名称与脚本内容')
        return
      }
      const created = (await taskApi.create(taskForm)).data
      if (created?.id) await taskApi.submit(created.id)
      ElMessage.success('任务已创建并提交调度')
      showCreateDialog.value = false
    }
    await refreshAll()
  } finally {
    creating.value = false
  }
}

async function rerunTask(row) {
  await taskApi.submit(row.id)
  ElMessage.success('已提交重跑')
  refreshAll()
}

async function cancelTaskById(id, name) {
  await ElMessageBox.confirm(`确定终止任务「${name || id}」？`, '终止确认', { type: 'warning' })
  await taskApi.cancel(id)
  ElMessage.success('任务已终止')
  refreshAll()
}

async function copyTask(row) {
  const payload = {
    name: `${row.name}_副本`,
    platform: row.platform || 'android',
    script_type: row.script_type || 'visual',
    script_content: row.script_content || '',
    app_package: row.app_package || '',
    parallel_count: row.parallel_count || 1,
    timeout_seconds: row.timeout_seconds || 3600,
    enable_recording: row.enable_recording !== false,
    use_account_pool: !!row.use_account_pool,
    priority: row.priority ?? 5
  }
  // 列表项可能无 script_content，尝试拉取详情
  if (!payload.script_content) {
    try {
      const detail = (await taskApi.get(row.id)).data
      payload.script_content = detail.script_content || '{}'
      payload.app_package = detail.app_package || payload.app_package
    } catch {
      payload.script_content = '{}'
    }
  }
  await taskApi.create(payload)
  ElMessage.success('任务配置已复制')
  refreshAll()
}

function goEditSource(row) {
  if (isSuiteTask(row)) router.push('/suites')
  else if (row.source_case_id) router.push(`/cases/editor/${row.source_case_id}`)
  else router.push('/cases')
}

async function deleteTask(row) {
  await ElMessageBox.confirm(
    `确定删除任务「${row.name}」？仅删除任务记录，不影响原始用例 / 套件。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  try {
    await taskApi.delete(row.id)
  } catch (e) {
    if (!isNotFoundError(e)) throw e
  }
  notifyTaskDeleted(row.id)
  ElMessage.success('任务已删除')
  await refreshAll()
}

async function onBatchCommand(cmd) {
  if (!selectedRows.value.length) return
  batchLoading.value = true
  try {
    if (cmd === 'rerun') {
      await ElMessageBox.confirm(`确定批量重跑选中的 ${selectedRows.value.length} 个任务？`, '批量重跑', { type: 'warning' })
      for (const row of selectedRows.value) {
        if (canRerun(row)) {
          try { await taskApi.submit(row.id) } catch { /* skip */ }
        }
      }
      ElMessage.success('批量重跑已提交')
    } else if (cmd === 'cancel') {
      await ElMessageBox.confirm(`确定批量终止选中的 ${selectedRows.value.length} 个任务？`, '批量终止', { type: 'warning' })
      for (const row of selectedRows.value) {
        if (['running', 'queued', 'pending'].includes(row.status)) {
          try { await taskApi.cancel(row.id) } catch { /* skip */ }
        }
      }
      ElMessage.success('批量终止已完成')
    } else if (cmd === 'export') {
      let ok = 0
      for (const row of selectedRows.value) {
        try {
          await reportApi.exportPDF(row.id)
          ok += 1
        } catch { /* skip unfinished */ }
      }
      ElMessage.success(ok ? `已导出 ${ok} 份报告` : '所选任务暂无可导出报告')
    }
    selectedRows.value = []
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function setupPoll() {
  if (pollTimer) clearInterval(pollTimer)
  const ms = autoRefresh.value ? 3000 : 5000
  pollTimer = setInterval(() => {
    if (autoRefresh.value) {
      refreshAll()
      return
    }
    const busy = (queueBoard.value.running_tasks || 0) > 0
      || (queueBoard.value.queued_tasks || 0) > 0
      || allTasks.value.some(t => ['running', 'queued', 'pending'].includes(t.status))
    if (busy) refreshAll()
  }, ms)
}

watch(autoRefresh, () => setupPoll())

onMounted(() => {
  const status = route.query.status
  if (typeof status === 'string' && status) filters.status = status
  refreshAll()
  setupPoll()
})

watch(() => route.query.status, (status) => {
  filters.status = typeof status === 'string' ? status : ''
  page.value = 1
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.tasks-page :deep(.page-header__info h2) {
  font-size: 24px;
  font-weight: 700;
}
.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.batch-wrap { display: inline-flex; }
.btn-muted {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
.btn-muted-sm {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}
.btn-run { font-weight: 700; box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25); }

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 16px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.stat-card.tone-queue { background: #fffbeb; }
.stat-card.tone-running { background: #ecfdf5; }
.stat-card.tone-success { background: #eff6ff; }
.stat-card.tone-fail { background: #fff7ed; }
.stat-card.highlight {
  outline: 2px solid #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--atp-text);
}
.stat-value.is-warn { color: #d97706; }
.stat-value.is-ok { color: #059669; }
.stat-value.is-fail { color: #ea580c; }
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.queue-card { margin-bottom: 16px; }
.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
}
.queue-title { font-weight: 700; font-size: 15px; }
.queue-meta { font-size: 12px; color: var(--atp-text-secondary); white-space: nowrap; }
.queue-empty {
  text-align: center;
  padding: 28px 12px;
  color: var(--atp-text-secondary);
}
.queue-empty p { margin: 0 0 12px; font-size: 14px; }

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.filter-right {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--atp-text-secondary);
  padding-right: 4px;
}
.auto-hint { font-size: 12px; color: #94a3b8; }

.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }

.tag-queued { --el-tag-text-color: #b45309; --el-tag-bg-color: #fef3c7; --el-tag-border-color: #fde68a; }
.tag-running { --el-tag-text-color: #047857; --el-tag-bg-color: #d1fae5; --el-tag-border-color: #6ee7b7; }
.tag-success { --el-tag-text-color: #047857; --el-tag-bg-color: #ecfdf5; --el-tag-border-color: #a7f3d0; }
.tag-failed { --el-tag-text-color: #c2410c; --el-tag-bg-color: #ffedd5; --el-tag-border-color: #fdba74; }
.tag-cancelled { --el-tag-text-color: #64748b; --el-tag-bg-color: #f1f5f9; --el-tag-border-color: #e2e8f0; }

.priority-high { color: #ea580c; font-weight: 700; }
.priority-mid { color: #0f172a; font-weight: 600; }
.priority-low { color: #94a3b8; }

.table-empty {
  text-align: center;
  padding: 36px 16px 12px;
}
.empty-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
}
.empty-actions { display: flex; justify-content: center; gap: 10px; flex-wrap: wrap; }

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--atp-border-neutral);
}
.pager-stats { font-size: 13px; color: var(--atp-text-secondary); }

.shortcut-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 20px;
}
.shortcut-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 16px 18px;
  border-radius: 14px;
  background: #f8fafc;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}
.shortcut-card:hover {
  transform: translateY(-2px);
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
.shortcut-card h4 { margin: 0 0 4px; font-size: 14px; }
.shortcut-card p {
  margin: 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.45;
}
.shortcut-card .el-icon { color: var(--atp-primary); margin-top: 2px; }

.form-hint { font-size: 12px; color: var(--atp-text-secondary); }
.create-tabs { margin-bottom: 8px; }

@media (max-width: 960px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
