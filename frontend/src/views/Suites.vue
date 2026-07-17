<template>
  <div class="page-container suites-page">
    <PageHeader title="测试套件" subtitle="批量编排自动化用例、配置执行策略，一键发起版本回归测试">
      <template #actions>
        <div class="header-actions">
          <el-tooltip content="请先勾选目标套件后再执行批量操作" :disabled="hasSelection" placement="bottom">
            <span class="batch-wrap">
              <el-button :disabled="!hasSelection" :loading="batchLoading" @click="batchRun">批量执行</el-button>
              <el-button :disabled="!hasSelection" :loading="batchLoading" @click="batchCopy">批量复制</el-button>
              <el-button :disabled="!hasSelection" type="danger" plain :loading="batchLoading" @click="batchDelete">批量删除</el-button>
            </span>
          </el-tooltip>
          <el-button type="primary" class="btn-create" @click="openDialog()">
            <el-icon><Plus /></el-icon> 新建套件
          </el-button>
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：统计卡片 -->
    <div class="stats-row">
      <div class="stat-card tone-total" @click="resetFilters">
        <div class="stat-icon"><el-icon :size="22"><FolderOpened /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ suites.length }}</div>
          <div class="stat-label">全部套件</div>
        </div>
      </div>
      <div class="stat-card tone-regression" @click="filters.tag = '回归'; page = 1">
        <div class="stat-icon"><el-icon :size="22"><Collection /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ regressionCount }}</div>
          <div class="stat-label">常用回归套件</div>
        </div>
      </div>
      <div class="stat-card tone-today">
        <div class="stat-icon"><el-icon :size="22"><Calendar /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ todayRunCount }}</div>
          <div class="stat-label">今日执行套件</div>
        </div>
      </div>
      <div class="stat-card tone-running" :class="{ highlight: runningCount > 0 }">
        <div class="stat-icon"><el-icon :size="22"><VideoPlay /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value" :class="{ running: runningCount > 0 }">{{ runningCount }}</div>
          <div class="stat-label">运行中套件</div>
        </div>
      </div>
    </div>

    <!-- 模块 3：筛选 -->
    <AppCard :hover="false" class="list-card">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索套件名称"
          clearable
          prefix-icon="Search"
          style="width:240px"
          @change="page = 1"
          @clear="page = 1"
        />
        <el-select v-model="filters.tag" placeholder="标签" clearable filterable style="width:150px" @change="page = 1">
          <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="filters.exec_mode" placeholder="执行模式" clearable style="width:130px" @change="page = 1">
          <el-option label="串行" value="serial" />
          <el-option label="并行" value="parallel" />
        </el-select>
        <div class="filter-right">
          <el-button type="primary" plain :loading="loading" @click="refreshAll">
            <el-icon><Refresh /></el-icon> 刷新列表
          </el-button>
          <el-button @click="resetFilters">重置筛选条件</el-button>
        </div>
      </div>

      <!-- 模块 4：表格 -->
      <el-table
        :data="pagedSuites"
        v-loading="loading"
        stripe
        empty-text=""
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="套件名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <strong class="suite-name">{{ displaySuiteName(row.name) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.tags || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行模式" width="110">
          <template #default="{ row }">
            <span class="mode-text" :class="row.exec_mode === 'parallel' ? 'is-parallel' : 'is-serial'">
              {{ row.exec_mode === 'parallel' ? '并行' : '串行' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="失败处理策略" width="150">
          <template #default="{ row }">
            <span class="policy-text" :class="row.fail_policy === 'stop' ? 'is-stop' : 'is-continue'">
              {{ row.fail_policy === 'stop' ? '失败立即停止' : '失败继续执行' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button size="small" type="success" @click="runSuite(row)">执行</el-button>
              <el-button size="small" class="btn-copy" @click="copySuite(row)">复制</el-button>
              <el-button size="small" type="danger" plain @click="deleteSuite(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !filteredSuites.length" class="table-empty">
        <p>暂无测试套件</p>
        <el-button type="primary" @click="openDialog()">新建套件</el-button>
        <p class="empty-hint">新建套件可批量编排多条测试用例，一键发起回归</p>
      </div>

      <div class="pager-bar">
        <div class="pager-stats">当前筛选结果共 <strong>{{ filteredSuites.length }}</strong> 套测试套件</div>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredSuites.length"
          layout="total, prev, pager, next"
        />
      </div>
    </AppCard>

    <!-- 执行批次（保留能力） -->
    <AppCard v-if="suiteRuns.length" title="执行批次" :hover="false" style="margin-top:16px">
      <el-table :data="suiteRuns" size="small" stripe>
        <el-table-column prop="id" label="批次ID" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="runStatusType(row.status)">{{ runStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="success_count" label="成功" width="70" align="center" />
        <el-table-column prop="failed_count" label="失败" width="70" align="center" />
        <el-table-column prop="total_items" label="总数" width="70" align="center" />
        <el-table-column prop="started_at" label="开始时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="viewRun(row)">详情</el-button>
            <el-button v-if="row.status === 'running'" size="small" type="warning" plain @click="pauseRun(row)">暂停</el-button>
            <el-button v-if="row.status === 'failed' || row.status === 'paused'" size="small" type="warning" plain @click="resumeRun(row)">断点续跑</el-button>
            <el-button size="small" plain @click="restoreConfig(row)">还原配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>

    <!-- 模块 5：底部快捷栏 -->
    <section class="shortcut-bar">
      <div class="shortcut-card" @click="$router.push('/cases')">
        <el-icon :size="22"><DocumentCopy /></el-icon>
        <div>
          <h4>前往测试用例</h4>
          <p>跳转用例页面，批量勾选用例快速新建套件</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/reports')">
        <el-icon :size="22"><Document /></el-icon>
        <div>
          <h4>查看执行报告</h4>
          <p>查看所有套件历史回归结果</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push({ path: '/platform-config', query: { tab: 'schedule' } })">
        <el-icon :size="22"><Timer /></el-icon>
        <div>
          <h4>定时回归配置</h4>
          <p>设置套件定时自动执行（每日 / 每周版本回归）</p>
        </div>
      </div>
      <div class="shortcut-card" @click="showTemplateDialog = true">
        <el-icon :size="22"><CopyDocument /></el-icon>
        <div>
          <h4>套件模板库</h4>
          <p>保存常用回归模板，一键复制快速创建套件</p>
        </div>
      </div>
    </section>

    <SuiteEditorDialog
      v-model="showDialog"
      :suite-id="editingSuiteId"
      :all-cases="allCases"
      @saved="onSuiteSaved"
      @save-template="onUserTemplateSaved"
    />

    <el-dialog v-model="showRunDialog" title="批次详情" width="720px">
      <el-table v-if="runDetail.items" :data="runDetail.items" size="small">
        <el-table-column prop="case_name" label="用例" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">{{ runStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="failed_step_index" label="失败步骤" width="90" />
        <el-table-column prop="task_id" label="任务ID" width="80" />
        <el-table-column prop="error_message" label="错误" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog v-model="showTemplateDialog" title="套件模板库" width="560px">
      <div v-for="tpl in allTemplates" :key="tpl.key" class="tpl-item">
        <div>
          <strong>{{ tpl.name }}</strong>
          <p>{{ tpl.desc }}</p>
        </div>
        <el-button type="primary" size="small" :loading="tplLoading === tpl.key" @click="createFromTemplate(tpl)">
          一键复制创建
        </el-button>
      </div>
      <el-empty v-if="!allTemplates.length" description="暂无模板" :image-size="64" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { suiteApi, caseApi, checkpointApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import SuiteEditorDialog from '@/components/SuiteEditorDialog.vue'

const TODAY_RUN_KEY = 'atp_suite_runs_today'
const USER_TPL_KEY = 'atp_suite_user_templates'
const SUITE_TEMPLATES = [
  {
    key: 'full_regression',
    name: '版本全量回归套件',
    desc: '覆盖核心模块的完整回归编排模板',
    tags: '回归,全量',
    exec_mode: 'serial',
    fail_policy: 'continue_on_fail'
  },
  {
    key: 'login_smoke',
    name: '登录模块冒烟套件',
    desc: '登录相关用例的快速冒烟模板',
    tags: '冒烟,登录',
    exec_mode: 'serial',
    fail_policy: 'stop'
  },
  {
    key: 'pay_special',
    name: '支付流程专项测试套件',
    desc: '支付链路专项验证模板',
    tags: '专项,支付',
    exec_mode: 'parallel',
    fail_policy: 'continue_on_fail'
  },
  {
    key: 'profile_iter',
    name: '个人中心迭代回归套件',
    desc: '个人中心迭代场景回归模板',
    tags: '回归,个人中心',
    exec_mode: 'serial',
    fail_policy: 'continue_on_fail'
  }
]

const loading = ref(false)
const batchLoading = ref(false)
const suites = ref([])
const allCases = ref([])
const suiteRuns = ref([])
const currentSuiteId = ref(null)
const showRunDialog = ref(false)
const runDetail = ref({ items: [] })
const showDialog = ref(false)
const editingSuiteId = ref(null)
const selectedRows = ref([])
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ keyword: '', tag: '', exec_mode: '' })
const showTemplateDialog = ref(false)
const tplLoading = ref('')
const userTemplates = ref(loadUserTemplates())
const todayRunIds = ref(loadTodayRunIds())
const runningSuiteIds = ref(new Set())

const hasSelection = computed(() => selectedRows.value.length > 0)

const allTemplates = computed(() => [...userTemplates.value, ...SUITE_TEMPLATES])

const tagOptions = computed(() => {
  const set = new Set()
  for (const s of suites.value) {
    String(s.tags || '').split(/[,，]/).map(x => x.trim()).filter(Boolean).forEach(t => set.add(t))
  }
  return [...set].sort()
})

const filteredSuites = computed(() => {
  let list = [...suites.value]
  if (filters.keyword) {
    const k = filters.keyword.trim().toLowerCase()
    list = list.filter(s => String(s.name || '').toLowerCase().includes(k))
  }
  if (filters.tag) {
    list = list.filter(s => String(s.tags || '').split(/[,，]/).map(x => x.trim()).includes(filters.tag))
  }
  if (filters.exec_mode) list = list.filter(s => s.exec_mode === filters.exec_mode)
  return list
})

const pagedSuites = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredSuites.value.slice(start, start + pageSize.value)
})

const regressionCount = computed(() =>
  suites.value.filter(s => String(s.tags || '').includes('回归')).length
)

const todayRunCount = computed(() => {
  const ids = new Set(todayRunIds.value)
  return suites.value.filter(s => ids.has(String(s.id))).length
})

const runningCount = computed(() => runningSuiteIds.value.size)

function loadUserTemplates() {
  try {
    const list = JSON.parse(localStorage.getItem(USER_TPL_KEY) || '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function loadTodayRunIds() {
  try {
    const raw = JSON.parse(localStorage.getItem(TODAY_RUN_KEY) || '{}')
    const today = new Date().toISOString().slice(0, 10)
    if (raw.date !== today) return []
    return Array.isArray(raw.ids) ? raw.ids.map(String) : []
  } catch {
    return []
  }
}

function rememberTodayRun(suiteId) {
  const today = new Date().toISOString().slice(0, 10)
  const ids = new Set(todayRunIds.value)
  ids.add(String(suiteId))
  todayRunIds.value = [...ids]
  localStorage.setItem(TODAY_RUN_KEY, JSON.stringify({ date: today, ids: todayRunIds.value }))
}

function displaySuiteName(name) {
  return name || '未命名套件'
}

function runStatusLabel(s) {
  return {
    completed: '已完成',
    success: '成功',
    failed: '失败',
    running: '运行中',
    paused: '已暂停',
    pending: '等待中'
  }[s] || s || '-'
}

function runStatusType(s) {
  return { completed: 'success', success: 'success', failed: 'danger', running: 'warning', paused: 'info' }[s] || 'info'
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function resetFilters() {
  filters.keyword = ''
  filters.tag = ''
  filters.exec_mode = ''
  page.value = 1
}

async function refreshAll() {
  await loadSuites()
  await loadRunStats()
}

async function loadSuites() {
  loading.value = true
  try {
    suites.value = (await suiteApi.list()).data || []
  } finally {
    loading.value = false
  }
}

async function loadAllCases() {
  const res = await caseApi.list({ page: 1, page_size: 200 })
  allCases.value = res.data?.list || []
}

async function loadRunStats() {
  const running = new Set()
  const today = new Date().toISOString().slice(0, 10)
  const todayIds = new Set(todayRunIds.value)
  const list = suites.value.slice(0, 30)
  await Promise.allSettled(list.map(async (s) => {
    try {
      const runs = (await checkpointApi.listSuiteRuns(s.id)).data || []
      if (runs.some(r => r.status === 'running')) running.add(s.id)
      for (const r of runs) {
        const day = String(r.started_at || r.created_at || '').slice(0, 10)
        if (day === today) todayIds.add(String(s.id))
      }
    } catch { /* ignore */ }
  }))
  runningSuiteIds.value = running
  todayRunIds.value = [...todayIds]
  localStorage.setItem(TODAY_RUN_KEY, JSON.stringify({ date: today, ids: todayRunIds.value }))
}

function openDialog(row) {
  editingSuiteId.value = row?.id || null
  showDialog.value = true
}

async function onSuiteSaved() {
  await loadSuites()
}

function onUserTemplateSaved() {
  userTemplates.value = loadUserTemplates()
}

async function createFromTemplate(tpl) {
  tplLoading.value = tpl.key
  try {
    await suiteApi.create({
      name: tpl.name,
      tags: tpl.tags,
      exec_mode: tpl.exec_mode,
      fail_policy: tpl.fail_policy,
      hook_before: tpl.hook_before || null,
      hook_after: tpl.hook_after || null,
      items: (tpl.items || []).map((item, idx) => ({
        case_id: item.case_id,
        sort_order: idx,
        enabled: item.enabled !== false
      }))
    })
    ElMessage.success('模板套件已创建，请编辑并确认关联用例')
    showTemplateDialog.value = false
    await loadSuites()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    tplLoading.value = ''
  }
}

async function runSuite(row) {
  const res = await suiteApi.run(row.id)
  const skipped = res.data.skipped || 0
  ElMessage.success(`套件已启动，批次 #${res.data.suite_run_id}，执行 ${res.data.total} 个用例${skipped ? `，跳过 ${skipped} 个` : ''}`)
  rememberTodayRun(row.id)
  runningSuiteIds.value = new Set([...runningSuiteIds.value, row.id])
  currentSuiteId.value = row.id
  loadSuiteRuns()
}

async function copySuite(row, { silent = false } = {}) {
  try {
    const detail = (await suiteApi.get(row.id)).data
    const payload = {
      name: `${displaySuiteName(detail.name || row.name)}_副本`,
      tags: detail.tags || row.tags || '',
      exec_mode: detail.exec_mode || row.exec_mode || 'serial',
      fail_policy: detail.fail_policy || row.fail_policy || 'continue_on_fail',
      hook_before: detail.hook_before || null,
      hook_after: detail.hook_after || null,
      items: (detail.items || []).map((item, idx) => ({
        case_id: item.case_id,
        sort_order: idx,
        enabled: item.enabled !== false
      }))
    }
    await suiteApi.create(payload)
    if (!silent) ElMessage.success('套件已复制')
    return true
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '复制失败')
    return false
  }
}

async function batchRun() {
  if (!hasSelection.value) return
  batchLoading.value = true
  try {
    let ok = 0
    for (const row of selectedRows.value) {
      try {
        await suiteApi.run(row.id)
        rememberTodayRun(row.id)
        runningSuiteIds.value = new Set([...runningSuiteIds.value, row.id])
        ok++
      } catch { /* continue */ }
    }
    ElMessage.success(`已启动 ${ok} 个套件执行`)
  } finally {
    batchLoading.value = false
  }
}

async function batchCopy() {
  if (!hasSelection.value) return
  batchLoading.value = true
  try {
    let ok = 0
    for (const row of selectedRows.value) {
      if (await copySuite(row, { silent: true })) ok++
    }
    ElMessage.success(`已复制 ${ok} 个套件`)
    await loadSuites()
  } finally {
    batchLoading.value = false
  }
}

async function batchDelete() {
  if (!hasSelection.value) return
  await ElMessageBox.confirm(`确定将选中的 ${selectedRows.value.length} 个套件移入回收站？`, '批量删除', { type: 'warning' })
  batchLoading.value = true
  try {
    for (const row of selectedRows.value) {
      await suiteApi.delete(row.id)
    }
    ElMessage.success('已移入回收站')
    selectedRows.value = []
    await loadSuites()
  } finally {
    batchLoading.value = false
  }
}

async function loadSuiteRuns() {
  if (!currentSuiteId.value) return
  suiteRuns.value = (await checkpointApi.listSuiteRuns(currentSuiteId.value)).data || []
}

async function viewRun(row) {
  const res = await checkpointApi.getRun(row.id)
  runDetail.value = res.data
  showRunDialog.value = true
}

async function pauseRun(row) {
  await checkpointApi.pauseRun(row.id)
  ElMessage.success('套件批次已暂停')
  loadSuiteRuns()
  loadRunStats()
}

async function resumeRun(row) {
  const res = await checkpointApi.resumeRun(row.id)
  ElMessage.success(`已续跑 ${res.data.resumed_count} 个用例/步骤`)
  loadSuiteRuns()
  loadRunStats()
}

async function restoreConfig(row) {
  await ElMessageBox.confirm('将把套件配置还原为该批次执行时的快照，是否继续？', '还原配置', { type: 'warning' })
  await suiteApi.restoreConfig(row.id)
  ElMessage.success('套件配置已还原')
  loadSuites()
}

async function deleteSuite(row) {
  await ElMessageBox.confirm('套件将移入回收站，支持后续恢复。', '确认删除', { type: 'warning' })
  await suiteApi.delete(row.id)
  ElMessage.success('已移入回收站')
  loadSuites()
}

watch(() => filteredSuites.value.length, (n) => {
  const maxPage = Math.max(1, Math.ceil(n / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

onMounted(async () => {
  await loadSuites()
  loadAllCases()
  loadRunStats()
})
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.batch-wrap {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
.batch-wrap :deep(.el-button:not(.el-button--danger)) {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
}
.btn-create { font-weight: 700; }

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 16px;
  padding: 18px 20px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
}
.stat-card.tone-total {
  background: var(--atp-info-bg, #eff6ff);
}
.stat-card.tone-total .stat-icon {
  background: rgba(59, 130, 246, 0.18);
  color: var(--atp-info, #3b82f6);
}
.stat-card.tone-regression {
  background: var(--atp-warning-bg, #fffbeb);
}
.stat-card.tone-regression .stat-icon {
  background: rgba(217, 119, 6, 0.15);
  color: var(--atp-warning, #d97706);
}
.stat-card.tone-today {
  background: var(--atp-primary-bg, rgba(2, 132, 199, 0.1));
}
.stat-card.tone-today .stat-icon {
  background: rgba(2, 132, 199, 0.16);
  color: var(--atp-primary, #0284c7);
}
.stat-card.tone-running {
  background: var(--atp-success-bg, #ecfdf5);
}
.stat-card.tone-running .stat-icon {
  background: rgba(16, 185, 129, 0.16);
  color: var(--atp-success, #10b981);
}
.stat-card.highlight {
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.35);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--atp-text);
  line-height: 1.15;
}
.stat-value.running { color: var(--atp-success, #059669); }
.stat-label {
  margin-top: 2px;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}
.filter-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.suite-name { font-size: 14px; color: var(--atp-text); }
.mode-text { font-size: 13px; font-weight: 600; }
.mode-text.is-serial { color: #94a3b8; }
.mode-text.is-parallel { color: #059669; }
.policy-text { font-size: 13px; font-weight: 600; }
.policy-text.is-continue { color: #ca8a04; }
.policy-text.is-stop { color: #ea580c; }

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.btn-copy {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.table-empty {
  text-align: center;
  padding: 48px 16px 24px;
  color: var(--atp-text-secondary);
}
.table-empty p { margin: 0 0 12px; }
.empty-hint {
  margin-top: 10px !important;
  font-size: 12px;
  color: #94a3b8;
}

.pager-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}
.pager-stats {
  font-size: 13px;
  color: var(--atp-text-secondary);
}

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

.tpl-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--atp-border-neutral);
}
.tpl-item:last-child { border-bottom: none; }
.tpl-item p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
}

@media (max-width: 1100px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
