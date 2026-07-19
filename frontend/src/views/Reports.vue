<template>
  <div class="page-container rpt-page">
    <!-- 模块 1：标题区 -->
    <PageHeader
      title="测试报告"
      subtitle="自动化任务执行结果汇总报告，支持在线查看、PDF/Excel 导出、归档留存，统计用例执行通过率"
    >
      <template #actions>
        <el-button type="primary" plain :disabled="!selectedIds.length" :loading="exporting" @click="batchExport">
          批量导出报告
        </el-button>
        <el-button type="warning" plain :disabled="!selectedIds.length" :loading="archiving" @click="batchArchive">
          批量归档
        </el-button>
        <el-button type="danger" plain :loading="purging" @click="purgeExpired">
          批量清理过期
        </el-button>
        <el-button :loading="loading" @click="refreshAll">
          <el-icon><Refresh /></el-icon> 刷新列表
        </el-button>
      </template>
    </PageHeader>

    <!-- 模块 2：统计看板 -->
    <div class="stats-grid">
      <div class="stat-tile tone-blue">
        <div class="st-label">报告总份数</div>
        <div class="st-value">{{ stats.report_count ?? 0 }}</div>
        <div class="st-desc">当前筛选条件下全部执行报告数量</div>
      </div>
      <div class="stat-tile tone-green">
        <div class="st-label">累计执行用例总数</div>
        <div class="st-value">{{ stats.total_cases ?? 0 }}</div>
        <div class="st-desc">所有报告合计执行自动化用例条数</div>
      </div>
      <div class="stat-tile" :class="stats.pass_rate_alert ? 'tone-warn' : 'tone-cream'">
        <div class="st-label">全局平均通过率</div>
        <div class="st-value" :class="{ 'is-warn': stats.pass_rate_alert }">
          {{ formatRate(stats.avg_pass_rate) }}
        </div>
        <div class="st-desc">所有报告综合用例通过率{{ stats.pass_rate_alert ? '，低于 90% 需复盘' : '' }}</div>
      </div>
      <div class="stat-tile" :class="stats.failed_alert ? 'tone-warn' : 'tone-gray'">
        <div class="st-label">失败用例总条数</div>
        <div class="st-value" :class="{ 'is-warn': stats.failed_alert }">{{ stats.failed_cases ?? 0 }}</div>
        <div class="st-desc">{{ stats.failed_alert ? '存在失败用例，建议复盘缺陷' : '全部执行失败用例合计' }}</div>
      </div>
    </div>

    <!-- 模块 3：筛选 -->
    <section class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="报告标题关键词"
        clearable
        style="width:200px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.module_name" placeholder="所属业务模块" clearable filterable style="width:150px">
        <el-option v-for="m in facets.modules" :key="m" :label="m" :value="m" />
      </el-select>
      <el-select v-model="filters.version_label" placeholder="基线版本标签" clearable filterable style="width:150px">
        <el-option v-for="v in facets.versions" :key="v" :label="v" :value="v" />
      </el-select>
      <el-input v-model="filters.task_id" placeholder="任务 ID" clearable style="width:110px" @keyup.enter="search" />
      <el-select v-model="filters.pass_bucket" placeholder="通过率区间" clearable style="width:170px">
        <el-option label="0%-60%（高风险）" value="low" />
        <el-option label="60%-90%" value="mid" />
        <el-option label="90%-100%（合格）" value="high" />
      </el-select>
      <el-select v-model="filters.status" placeholder="报告状态" clearable style="width:120px">
        <el-option label="未归档" value="active" />
        <el-option label="已归档" value="archived" />
        <el-option label="已过期" value="expired" />
      </el-select>
      <el-select
        v-model="filters.created_range"
        placeholder="生成时间"
        clearable
        style="width:130px"
        @change="onCreatedRangeChange"
      >
        <el-option label="今日" value="today" />
        <el-option label="近 7 天" value="7d" />
        <el-option label="近 30 天" value="30d" />
        <el-option label="自定义时段" value="custom" />
      </el-select>
      <el-date-picker
        v-if="filters.created_range === 'custom'"
        v-model="customRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始"
        end-placeholder="结束"
        value-format="YYYY-MM-DD"
        style="width:240px"
      />
      <el-button @click="resetFilters">重置筛选</el-button>
      <el-button type="primary" @click="search">查询检索</el-button>
    </section>

    <!-- 模块 4：表格 -->
    <AppCard :hover="false" class="table-card">
      <div class="table-toolbar">
        <span>已选 {{ selectedIds.length }} 条</span>
        <span class="hint">共 {{ total }} 份测试报告</span>
      </div>
      <el-table
        :data="reports"
        v-loading="loading"
        stripe
        row-key="id"
        :row-class-name="rowClassName"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" fixed />
        <el-table-column prop="title" label="报告标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="任务 ID" width="120">
          <template #default="{ row }">
            <el-dropdown v-if="row.task_id" trigger="click">
              <el-button link type="primary">{{ row.task_id }}</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goTask(row.task_id)">任务详情</el-dropdown-item>
                  <el-dropdown-item @click="goRecordings(row.task_id)">录屏回放</el-dropdown-item>
                  <el-dropdown-item @click="goQuality(row.task_id)">录屏质量审计</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="project_code" label="所属项目" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.project_code || '-' }}</template>
        </el-table-column>
        <el-table-column prop="module_name" label="业务模块" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.module_name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="version_label" label="基线版本" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.version_label || '-' }}</template>
        </el-table-column>
        <el-table-column prop="total_executions" label="执行用例总数" width="110" align="center" />
        <el-table-column label="成功用例" width="90" align="center">
          <template #default="{ row }">
            <span class="text-success">{{ row.success_count ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败用例" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.failed_count > 0 ? 'text-danger' : ''">{{ row.failed_count ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="130">
          <template #default="{ row }">
            <el-progress
              :percentage="Number(row.pass_rate) || 0"
              :color="passRateColor(row.pass_rate)"
              :stroke-width="8"
              :format="(p) => Number(p).toFixed(1) + '%'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="operator_label" label="执行人" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operator_label || '-' }}</template>
        </el-table-column>
        <el-table-column prop="device_label" label="执行设备" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.device_label || '-' }}</template>
        </el-table-column>
        <el-table-column label="报告状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row)" size="small" :effect="row.display_status === 'expired' ? 'plain' : 'light'">
              {{ statusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="生成时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="单条操作" width="300" fixed="right" align="left" header-align="left">
          <template #default="{ row }">
            <div class="row-ops">
              <el-button size="small" type="primary" @click="openPreview(row)">查看</el-button>
              <el-dropdown trigger="click" @command="(cmd) => onExportCmd(cmd, row)">
                <el-button size="small" type="success">
                  导出<el-icon class="ops-caret"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="pdf">导出 PDF</el-dropdown-item>
                    <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="row.archived"
                @click="archiveOne(row)"
              >归档</el-button>
              <el-button size="small" type="danger" @click="deleteOne(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadReports"
        />
        <span class="pager-extra">共 {{ total }} 份测试报告</span>
      </div>
    </AppCard>

    <!-- 模块 5：说明 -->
    <section class="guide-panel">
      <div class="guide-title">报告归档 & 导出说明</div>
      <div class="guide-grid">
        <div>
          <div class="guide-h">1. 报告留存周期规则</div>
          <p>普通未归档报告默认留存 {{ retainDays }} 天，到达周期系统自动清理；点击「归档」可永久留存，用于版本复盘、上线评审。</p>
        </div>
        <div>
          <div class="guide-h">2. 两种导出文件用途区分</div>
          <ul>
            <li>PDF：用于版本上线 PO 评审、合规交付，包含执行汇总、失败截图、录屏链接；</li>
            <li>Excel：用于测试工作量统计、缺陷批量导出、线下二次数据分析。</li>
          </ul>
        </div>
        <div>
          <div class="guide-h">3. 通过率验收标准</div>
          <p>版本迭代基线验收标准：整体通过率≥90%，低于该阈值建议复盘失败用例、修复控件识别问题。</p>
        </div>
        <div>
          <div class="guide-h">4. 归档建议</div>
          <p>大版本上线、线上故障复盘对应的报告，务必执行归档操作，避免到期自动清理丢失执行记录。</p>
        </div>
      </div>
    </section>

    <!-- 在线预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="preview?.report?.title || '测试报告预览'"
      width="920px"
      top="4vh"
      destroy-on-close
      class="preview-dlg"
    >
      <div v-loading="previewLoading">
        <template v-if="preview?.report">
          <div class="preview-stats">
            <el-tag type="info">任务 #{{ preview.report.task_id }}</el-tag>
            <el-tag type="success">成功 {{ preview.report.success_count ?? 0 }}</el-tag>
            <el-tag :type="preview.report.failed_count > 0 ? 'danger' : 'info'">
              失败 {{ preview.report.failed_count ?? 0 }}
            </el-tag>
            <el-tag :type="(preview.report.pass_rate || 0) >= 90 ? 'success' : 'warning'">
              通过率 {{ formatRate(preview.report.pass_rate) }}
            </el-tag>
            <el-button size="small" link type="primary" @click="goTask(preview.report.task_id)">打开任务详情</el-button>
            <el-button size="small" link type="primary" @click="goRecordings(preview.report.task_id)">查看录屏</el-button>
          </div>
          <p v-if="preview.report.summary" class="preview-summary">{{ preview.report.summary }}</p>
          <el-row :gutter="12">
            <el-col :span="12">
              <div class="preview-block-title">用例 / 执行明细</div>
              <el-table :data="preview.executions || []" size="small" max-height="280" stripe>
                <el-table-column prop="device_id" label="设备" width="70" />
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="result_summary" label="结果" show-overflow-tooltip />
              </el-table>
            </el-col>
            <el-col :span="12">
              <div class="preview-block-title">关联录屏</div>
              <el-table :data="preview.recordings || []" size="small" max-height="280" stripe>
                <el-table-column prop="id" label="ID" width="60" />
                <el-table-column prop="file_name" label="文件名" show-overflow-tooltip />
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button size="small" link type="primary" @click="goRecordings(preview.report.task_id, row.id)">回放</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!(preview.recordings || []).length" description="暂无录屏" :image-size="48" />
            </el-col>
          </el-row>
        </template>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="success" :disabled="!preview?.report" @click="exportPDF(preview.report)">导出 PDF</el-button>
        <el-button type="success" plain :disabled="!preview?.report" @click="exportExcel(preview.report)">导出 Excel</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { passRateColor as chartPassRateColor } from '@/utils/chartTheme'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const archiving = ref(false)
const purging = ref(false)
const reports = ref([])
const selectedRows = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const retainDays = ref(90)
const facets = reactive({ modules: [], versions: [] })
const stats = reactive({
  report_count: 0,
  total_cases: 0,
  avg_pass_rate: 0,
  failed_cases: 0,
  pass_rate_alert: false,
  failed_alert: false
})
const filters = reactive({
  keyword: '',
  module_name: '',
  version_label: '',
  task_id: '',
  pass_bucket: '',
  status: '',
  created_range: ''
})
const customRange = ref(null)
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref(null)

const selectedIds = computed(() => selectedRows.value.map(r => r.id))

function formatRate(v) {
  if (v == null || Number.isNaN(Number(v))) return '0%'
  return `${Number(v).toFixed(1)}%`
}

function passRateColor(rate) {
  return chartPassRateColor(rate, 99)
}

function statusLabel(row) {
  return row.display_status_label || ({
    archived: '已归档',
    expired: '已过期',
    active: '未归档'
  }[row.display_status] || '未归档')
}

function statusTagType(row) {
  return {
    archived: 'primary',
    expired: 'info',
    active: 'info'
  }[row.display_status] || 'info'
}

function rowClassName({ row }) {
  if (row.full_pass) return 'row-full-pass'
  if (row.low_pass) return 'row-low-pass'
  if (row.display_status === 'expired') return 'row-expired'
  return ''
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function onCreatedRangeChange(v) {
  if (v !== 'custom') customRange.value = null
}

function buildParams() {
  const params = { page: page.value, page_size: pageSize.value }
  if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
  if (filters.module_name) params.module_name = filters.module_name
  if (filters.version_label) params.version_label = filters.version_label
  if (filters.task_id) params.task_id = filters.task_id
  if (filters.pass_bucket) params.pass_bucket = filters.pass_bucket
  if (filters.status) params.status = filters.status
  if (filters.created_range && filters.created_range !== 'custom') {
    params.created_range = filters.created_range
  }
  if (filters.created_range === 'custom' && customRange.value?.length === 2) {
    params.created_from = customRange.value[0]
    params.created_to = customRange.value[1] + 'T23:59:59'
  }
  return params
}

function filterParamsOnly() {
  const p = buildParams()
  delete p.page
  delete p.page_size
  return p
}

async function loadFacets() {
  try {
    const res = await reportApi.facets()
    const d = res.data || {}
    facets.modules = d.modules || []
    facets.versions = d.versions || []
    if (d.retain_days) retainDays.value = d.retain_days
  } catch { /* ignore */ }
}

async function loadStats() {
  try {
    const res = await reportApi.stats(filterParamsOnly())
    Object.assign(stats, res.data || {})
    if (res.data?.retain_days) retainDays.value = res.data.retain_days
  } catch { /* ignore */ }
}

async function loadReports() {
  loading.value = true
  try {
    const res = await reportApi.list(buildParams())
    reports.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadFacets(), loadStats(), loadReports()])
}

function search() {
  page.value = 1
  loadStats()
  loadReports()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    module_name: '',
    version_label: '',
    task_id: '',
    pass_bucket: '',
    status: '',
    created_range: ''
  })
  customRange.value = null
  search()
}

function goTask(taskId) {
  router.push(`/tasks/${taskId}`)
}

function goRecordings(taskId, recordingId) {
  router.push({
    path: '/recordings',
    query: recordingId ? { task_id: taskId, highlight: recordingId } : { task_id: taskId }
  })
}

function goQuality(taskId) {
  router.push({ path: '/recording-quality', query: { task_id: taskId } })
}

async function openPreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  preview.value = null
  try {
    const res = await reportApi.detail(row.task_id)
    preview.value = res.data
  } catch (e) {
    ElMessage.error(e?.message || '加载报告失败')
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

async function exportPDF(row) {
  if (!row?.task_id) return
  await reportApi.exportPDF(row.task_id)
  ElMessage.success('PDF 报告已下载')
}

async function exportExcel(row) {
  if (!row?.task_id) return
  await reportApi.exportExcel(row.task_id)
  ElMessage.success('Excel 报告已下载')
}

function onExportCmd(cmd, row) {
  if (cmd === 'pdf') exportPDF(row)
  else if (cmd === 'excel') exportExcel(row)
}

async function archiveOne(row) {
  await ElMessageBox.confirm(
    `确认归档报告「${row.title || '#' + row.id}」？归档后永久留存，不会被自动清理。`,
    '归档确认',
    { type: 'warning', confirmButtonText: '确认归档' }
  )
  await reportApi.archive(row.id)
  ElMessage.success('已归档')
  refreshAll()
}

async function deleteOne(row) {
  await ElMessageBox.confirm(
    `删除后报告文件无法恢复，确认删除「${row.title || '#' + row.id}」？`,
    '删除确认',
    { type: 'error', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger' }
  )
  await reportApi.delete(row.id)
  ElMessage.success('报告已删除')
  refreshAll()
}

async function batchExport() {
  if (!selectedIds.value.length) return
  exporting.value = true
  try {
    await reportApi.batchExport(selectedIds.value)
    ElMessage.success(`已打包导出 ${selectedIds.value.length} 份报告（PDF + Excel）`)
  } catch (e) {
    ElMessage.error(e?.message || '批量导出失败')
  } finally {
    exporting.value = false
  }
}

async function batchArchive() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(
    `确认将选中的 ${selectedIds.value.length} 份报告标记为归档长期留存？`,
    '批量归档',
    { type: 'warning', confirmButtonText: '确认归档' }
  )
  archiving.value = true
  try {
    const res = await reportApi.batchArchive(selectedIds.value)
    ElMessage.success(`已归档 ${res.data?.archived ?? selectedIds.value.length} 份`)
    refreshAll()
  } finally {
    archiving.value = false
  }
}

async function purgeExpired() {
  const selectedExpired = selectedRows.value.filter(r => r.display_status === 'expired' || r.expired)
  const tip = selectedExpired.length
    ? `确认删除选中的 ${selectedExpired.length} 份过期报告？删除后不可恢复。`
    : '未勾选过期报告时，将清理全部超出留存周期且未归档的报告。是否继续？'
  await ElMessageBox.confirm(tip, '批量清理过期', {
    type: 'error',
    confirmButtonText: '确认清理',
    confirmButtonClass: 'el-button--danger'
  })
  purging.value = true
  try {
    const ids = selectedExpired.length ? selectedExpired.map(r => r.id) : []
    const res = await reportApi.purgeExpired(ids)
    ElMessage.success(`已清理 ${res.data?.deleted ?? 0} 份过期报告`)
    selectedRows.value = []
    refreshAll()
  } finally {
    purging.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.stat-tile {
  border-radius: 12px;
  padding: 16px 18px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  min-height: 110px;
}
.stat-tile:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.tone-blue { background: #eff6ff; border-color: #bfdbfe; }
.tone-green { background: #ecfdf5; border-color: #a7f3d0; }
.tone-cream { background: #fffbeb; border-color: #fde68a; }
.tone-gray { background: #f8fafc; border-color: #e2e8f0; }
.tone-warn { background: #fff7ed; border-color: #fdba74; }
.st-label { font-size: 13px; color: #64748b; margin-bottom: 6px; }
.st-value { font-size: 28px; font-weight: 700; color: #0f172a; line-height: 1.2; }
.st-value.is-warn { color: #c2410c; }
.st-desc { margin-top: 8px; font-size: 12px; color: #94a3b8; line-height: 1.4; }

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.table-card { margin-bottom: 14px; }
.table-card :deep(.el-table .el-table__cell) {
  padding: 12px 0;
}
.table-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}
.table-toolbar .hint { color: #94a3b8; }

.text-success { color: var(--atp-success, #16a34a); font-weight: 600; }
.text-danger { color: var(--atp-danger, #dc2626); font-weight: 600; }

.row-ops {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  box-sizing: border-box;
  padding-right: 4px;
}
.row-ops :deep(.el-button) {
  margin: 0;
  flex-shrink: 0;
}
.row-ops :deep(.el-dropdown) {
  flex-shrink: 0;
}
.ops-caret {
  margin-left: 2px;
  font-size: 12px;
}

:deep(.row-low-pass) { background: #fff7ed !important; }
:deep(.row-full-pass) { background: #ecfdf5 !important; }
:deep(.row-expired) { background: #f8fafc !important; color: #94a3b8; }
:deep(.row-expired td) { color: #94a3b8; }

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
.pager-extra { font-size: 13px; color: #64748b; }

.guide-panel {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 8px;
}
.guide-title {
  font-weight: 700;
  font-size: 14px;
  color: #1e3a8a;
  margin-bottom: 12px;
}
.guide-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
}
.guide-h {
  font-weight: 600;
  font-size: 13px;
  color: #1e40af;
  margin-bottom: 6px;
}
.guide-grid p,
.guide-grid ul {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}
.guide-grid ul { padding-left: 18px; }

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.preview-summary {
  margin: 0 0 14px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}
.preview-block-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  color: #334155;
}

@media (max-width: 1100px) {
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .guide-grid { grid-template-columns: 1fr; }
}
</style>
