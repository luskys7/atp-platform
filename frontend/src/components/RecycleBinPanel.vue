<template>
  <div class="recycle-panel" v-loading="loading">
    <!-- 模块 1：头部 -->
    <div class="rp-header">
      <div>
        <h2 class="rp-title">回收站</h2>
        <p class="rp-sub">存放已删除的用例、套件、公共步骤、数据集；支持还原，超保存周期将自动永久清理</p>
      </div>
      <div class="rp-actions" v-if="isAdmin">
        <el-button type="primary" :disabled="!selected.length" @click="batchRestore">批量还原</el-button>
        <el-button type="danger" plain :disabled="!selected.length" @click="batchPurge">批量彻底删除</el-button>
        <el-button type="danger" :disabled="!items.length" @click="clearAll">清空全部回收站</el-button>
      </div>
    </div>

    <!-- 模块 2：统计 -->
    <div class="overview-grid">
      <div class="ov-card" :class="expiredCorner(stats.expired) ? 'tone-alert' : 'tone-blue'">
        <div class="ov-label">回收站总条目</div>
        <div class="ov-value">{{ stats.total ?? 0 }}</div>
        <div class="ov-desc">所有已删除资源总数</div>
        <div v-if="expiredCorner(stats.expired)" class="ov-corner warn">{{ stats.expired }} 条已过期待清理</div>
      </div>
      <div class="ov-card" :class="expiredCorner(stats.expired_common_step) ? 'tone-alert' : 'tone-green'">
        <div class="ov-label">公共步骤</div>
        <div class="ov-value">{{ stats.common_step ?? 0 }}</div>
        <div class="ov-desc">回收的公共复用步骤数量</div>
        <div v-if="expiredCorner(stats.expired_common_step)" class="ov-corner warn">{{ stats.expired_common_step }} 条已过期待清理</div>
      </div>
      <div class="ov-card" :class="expiredCorner(stats.expired_case_suite) ? 'tone-alert' : 'tone-cream'">
        <div class="ov-label">用例 / 套件</div>
        <div class="ov-value">{{ stats.case_suite ?? 0 }}</div>
        <div class="ov-desc">回收自动化用例、测试套件总数</div>
        <div v-if="expiredCorner(stats.expired_case_suite)" class="ov-corner warn">{{ stats.expired_case_suite }} 条已过期待清理</div>
      </div>
      <div class="ov-card" :class="expiredCorner(stats.expired_data_set) ? 'tone-alert' : 'tone-gray'">
        <div class="ov-label">数据集</div>
        <div class="ov-value">{{ stats.data_set ?? 0 }}</div>
        <div class="ov-desc">回收参数化数据集数量</div>
        <div v-if="expiredCorner(stats.expired_data_set)" class="ov-corner warn">{{ stats.expired_data_set }} 条已过期待清理</div>
      </div>
    </div>

    <!-- 模块 3：筛选 -->
    <section class="filter-bar">
      <el-select v-model="filters.resource_type" clearable size="small" placeholder="资源类型" style="width:140px">
        <el-option label="全部" value="" />
        <el-option label="公共步骤" value="common_step" />
        <el-option label="用例" value="test_case" />
        <el-option label="套件" value="test_suite" />
        <el-option label="数据集" value="data_set" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        clearable
        size="small"
        placeholder="名称模糊搜索"
        style="width:180px"
      />
      <el-radio-group v-model="rangePreset" size="small" @change="onRangePreset">
        <el-radio-button value="all">全部时间</el-radio-button>
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="7d">近 7 天</el-radio-button>
        <el-radio-button value="30d">近 30 天</el-radio-button>
        <el-radio-button value="custom">自定义时间</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="rangePreset === 'custom'"
        v-model="customRange"
        type="datetimerange"
        size="small"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
      />
      <el-select v-model="filters.status" clearable size="small" placeholder="状态筛选" style="width:120px">
        <el-option label="全部" value="" />
        <el-option label="未过期" value="active" />
        <el-option label="已过期" value="expired" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置筛选</el-button>
      <el-button type="primary" size="small" @click="applyFilter">查询</el-button>
    </section>

    <!-- 模块 4：表格 -->
    <el-table
      ref="tableRef"
      :data="pagedRows"
      stripe
      size="small"
      row-key="id"
      :row-class-name="rowClassName"
      max-height="480"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="isAdmin" type="selection" width="48" />
      <el-table-column label="资源类型" width="110">
        <template #default="{ row }">{{ typeLabel(row.resource_type) }}</template>
      </el-table-column>
      <el-table-column label="资源名称" min-width="160">
        <template #default="{ row }">
          <el-tooltip :content="row.resource_name" placement="top" :show-after="300">
            <span class="name-cell">{{ row.resource_name }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="所属项目" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.project_name || '—' }}</template>
      </el-table-column>
      <el-table-column label="所属团队" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.team_name || '—' }}</template>
      </el-table-column>
      <el-table-column label="删除操作人" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deleted_by_name || '—' }}</template>
      </el-table-column>
      <el-table-column label="删除时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.deleted_at) }}</template>
      </el-table-column>
      <el-table-column label="自动过期时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.expire_at) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="160">
        <template #default="{ row }">
          <span :class="row.expired ? 'status-expired' : 'status-ok'">
            {{ row.expired ? '已过期，即将自动清除' : '可正常还原' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="previewItem(row)">预览详情</el-button>
          <el-button type="primary" link size="small" :disabled="row.expired" @click="restoreOne(row)">还原</el-button>
          <el-button type="danger" link size="small" @click="purgeOne(row)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 模块 5：分页 + 说明 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="filteredRows.length"
        layout="total, sizes, prev, pager, next"
        :pager-count="5"
      />
      <span class="pager-extra">共 {{ filteredRows.length }} 条回收资源</span>
    </div>

    <section class="guide-panel">
      <div class="guide-title">回收站完整规则</div>
      <ul>
        <li><b>回收保存周期：</b>资源删除后默认留存 90 天，到达过期时间系统自动彻底删除，无法恢复；</li>
        <li><b>还原规则：</b>还原后资源回到删除前原项目、原团队目录，原有绑定关系（控件、数据集、环境）全部保留；</li>
        <li><b>彻底删除风险：</b>手动点击彻底删除 / 过期自动清理后，资源无备份，不可找回；</li>
        <li><b>批量操作限制：</b>批量还原仅可一次性处理同项目同类型资源，跨团队资源需分开操作。</li>
      </ul>
    </section>

    <el-dialog v-model="previewVisible" title="预览删除前资源详情" width="720px" destroy-on-close>
      <div class="preview-meta" v-if="previewRow">
        <div><b>资源类型：</b>{{ typeLabel(previewRow.resource_type) }}</div>
        <div><b>资源名称：</b>{{ previewRow.resource_name }}</div>
        <div><b>所属项目：</b>{{ previewRow.project_name || '—' }}</div>
        <div><b>所属团队：</b>{{ previewRow.team_name || '—' }}</div>
        <div><b>删除操作人：</b>{{ previewRow.deleted_by_name || '—' }}</div>
        <div><b>删除时间：</b>{{ fmtTime(previewRow.deleted_at) }}</div>
        <div><b>自动过期时间：</b>{{ fmtTime(previewRow.expire_at) }}</div>
      </div>
      <el-divider content-position="left">删除前完整快照</el-divider>
      <pre class="preview-json">{{ previewJson }}</pre>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          v-if="isAdmin && previewRow && !previewRow.expired"
          type="primary"
          @click="restoreOne(previewRow); previewVisible = false"
        >确认还原</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { recycleApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  active: { type: Boolean, default: true }
})
const emit = defineEmits(['updated'])

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const loading = ref(false)
const items = ref([])
const stats = ref({
  total: 0,
  common_step: 0,
  case_suite: 0,
  data_set: 0,
  expired: 0,
  expired_common_step: 0,
  expired_case_suite: 0,
  expired_data_set: 0
})
const selected = ref([])
const tableRef = ref(null)
const page = ref(1)
const pageSize = ref(20)

const rangePreset = ref('all')
const customRange = ref(null)
const filters = reactive({
  resource_type: '',
  keyword: '',
  status: ''
})

const previewVisible = ref(false)
const previewRow = ref(null)
const previewJson = ref('')

function expiredCorner(n) {
  return Number(n || 0) > 0
}

function typeLabel(t) {
  return ({
    test_case: '用例',
    test_suite: '套件',
    common_step: '公共步骤',
    data_set: '数据集'
  })[t] || t || '—'
}

function parseTime(v) {
  if (!v) return null
  const d = new Date(String(v).replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

function rangeBounds() {
  const now = new Date()
  if (rangePreset.value === 'today') {
    return { start: new Date(now.getFullYear(), now.getMonth(), now.getDate()), end: now }
  }
  if (rangePreset.value === '7d') {
    return { start: new Date(now.getTime() - 7 * 86400000), end: now }
  }
  if (rangePreset.value === '30d') {
    return { start: new Date(now.getTime() - 30 * 86400000), end: now }
  }
  if (rangePreset.value === 'custom' && customRange.value?.length === 2) {
    return { start: customRange.value[0], end: customRange.value[1] }
  }
  return { start: null, end: null }
}

const filteredRows = computed(() => {
  const kw = (filters.keyword || '').trim().toLowerCase()
  const { start, end } = rangeBounds()
  return (items.value || []).filter((row) => {
    if (filters.resource_type && row.resource_type !== filters.resource_type) return false
    if (filters.status === 'expired' && !row.expired) return false
    if (filters.status === 'active' && row.expired) return false
    if (kw && !String(row.resource_name || '').toLowerCase().includes(kw)) return false
    const t = parseTime(row.deleted_at)
    if (start && t && t < start) return false
    if (end && t && t > end) return false
    return true
  })
})

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

function rowClassName({ row }) {
  return row.expired ? 'expired-row' : ''
}

function onSelectionChange(rows) {
  selected.value = rows || []
}

function onRangePreset() {
  if (rangePreset.value !== 'custom') customRange.value = null
}

function resetFilters() {
  filters.resource_type = ''
  filters.keyword = ''
  filters.status = ''
  rangePreset.value = 'all'
  customRange.value = null
  page.value = 1
}

function applyFilter() {
  page.value = 1
}

async function refresh() {
  loading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      recycleApi.list(),
      recycleApi.stats().catch(() => ({ data: null }))
    ])
    items.value = listRes.data || []
    if (statsRes?.data) {
      stats.value = statsRes.data
    } else {
      rebuildLocalStats()
    }
    emit('updated', { items: items.value, stats: stats.value })
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function rebuildLocalStats() {
  const list = items.value || []
  stats.value = {
    total: list.length,
    common_step: list.filter((i) => i.resource_type === 'common_step').length,
    case_suite: list.filter((i) => i.resource_type === 'test_case' || i.resource_type === 'test_suite').length,
    data_set: list.filter((i) => i.resource_type === 'data_set').length,
    expired: list.filter((i) => i.expired).length,
    expired_common_step: list.filter((i) => i.resource_type === 'common_step' && i.expired).length,
    expired_case_suite: list.filter((i) => (i.resource_type === 'test_case' || i.resource_type === 'test_suite') && i.expired).length,
    expired_data_set: list.filter((i) => i.resource_type === 'data_set' && i.expired).length
  }
}

async function previewItem(row) {
  try {
    const res = await recycleApi.preview(row.id)
    previewRow.value = res.data || row
    const snap = previewRow.value.snapshot
    previewJson.value = snap == null
      ? '（无快照内容）'
      : (typeof snap === 'string' ? snap : JSON.stringify(snap, null, 2))
    previewVisible.value = true
  } catch (e) {
    ElMessage.error(e?.message || '预览失败')
  }
}

async function restoreOne(row) {
  if (row.expired) {
    ElMessage.warning('已过期资源无法还原')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定将「${row.resource_name}」还原至删除前原目录？`,
      '还原确认',
      { type: 'warning', confirmButtonText: '确认还原' }
    )
  } catch {
    return
  }
  try {
    await recycleApi.restore(row.id)
    ElMessage.success('已还原')
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '还原失败')
  }
}

async function purgeOne(row) {
  try {
    await ElMessageBox.confirm(
      `彻底删除「${row.resource_name}」后无法找回，是否继续？`,
      '高危操作确认',
      { type: 'error', confirmButtonText: '永久删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    await recycleApi.purge(row.id)
    ElMessage.success('已彻底删除')
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function assertBatchSameGroup(rows) {
  const types = new Set(rows.map((r) => r.resource_type))
  if (types.size > 1) {
    ElMessage.warning('批量还原仅可一次性处理同类型资源，请分开操作')
    return false
  }
  const projects = new Set(rows.map((r) => r.project_key || 'default'))
  if (projects.size > 1) {
    ElMessage.warning('批量还原仅可一次性处理同项目资源，请分开操作')
    return false
  }
  const teams = new Set(rows.map((r) => String(r.team_id ?? '')))
  if (teams.size > 1) {
    ElMessage.warning('批量还原仅可一次性处理同团队资源，跨团队请分开操作')
    return false
  }
  if (rows.some((r) => r.expired)) {
    ElMessage.warning('选中项包含已过期资源，无法批量还原')
    return false
  }
  return true
}

async function batchRestore() {
  const rows = selected.value
  if (!rows.length) return
  if (!assertBatchSameGroup(rows)) return
  try {
    await ElMessageBox.confirm(`确定批量还原选中的 ${rows.length} 条资源？`, '批量还原', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await recycleApi.batchRestore(rows.map((r) => r.id))
    ElMessage.success(`已还原 ${res.data?.restored ?? 0} 项`)
    if (res.data?.errors?.length) ElMessage.warning(res.data.errors.join('; '))
    selected.value = []
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '批量还原失败')
  }
}

async function batchPurge() {
  const rows = selected.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(
      `即将永久清除选中的 ${rows.length} 条资源，删除后无法找回，是否继续？`,
      '批量彻底删除',
      { type: 'error', confirmButtonText: '永久删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    const res = await recycleApi.batchPurge(rows.map((r) => r.id))
    ElMessage.success(`已彻底删除 ${res.data?.purged ?? rows.length} 项`)
    selected.value = []
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '批量删除失败')
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm(
      '将清空全部回收站条目，此操作不可恢复。请再次确认是否继续？',
      '清空全部回收站',
      { type: 'error', confirmButtonText: '确认清空', confirmButtonClass: 'el-button--danger' }
    )
    await ElMessageBox.confirm(
      '二次确认：清空后所有可还原资源将永久丢失，确定执行？',
      '强风险确认',
      { type: 'error', confirmButtonText: '仍然清空', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    const res = await recycleApi.clearAll()
    ElMessage.success(`已清空 ${res.data?.purged ?? 0} 条`)
    selected.value = []
    await refresh()
  } catch (e) {
    ElMessage.error(e?.message || '清空失败')
  }
}

watch(() => props.active, (v) => { if (v) refresh() })
watch(filteredRows, () => {
  const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

onMounted(() => {
  if (props.active) refresh()
})

defineExpose({ refresh })
</script>

<style scoped>
.recycle-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rp-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  align-items: flex-start;
}
.rp-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.rp-sub {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  max-width: 640px;
}
.rp-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
@media (max-width: 1100px) {
  .overview-grid { grid-template-columns: repeat(2, 1fr); }
}
.ov-card {
  position: relative;
  border-radius: 12px;
  padding: 14px 16px;
  min-height: 108px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.ov-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.ov-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.ov-value { font-size: 26px; font-weight: 700; color: #0f172a; }
.ov-desc { margin-top: 8px; font-size: 12px; color: #64748b; }
.ov-corner {
  position: absolute;
  right: 12px;
  bottom: 10px;
  font-size: 12px;
  color: #64748b;
}
.ov-corner.warn { color: #c2410c; font-weight: 600; }
.tone-blue { background: #eff6ff; border-color: #bfdbfe; }
.tone-green { background: #ecfdf5; border-color: #a7f3d0; }
.tone-cream { background: #fffbeb; border-color: #fde68a; }
.tone-gray { background: #f8fafc; border-color: #e2e8f0; }
.tone-alert { background: #fff7ed; border-color: #fed7aa; }

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.name-cell {
  display: inline-block;
  max-width: 100%;
  white-space: normal;
  word-break: break-word;
  line-height: 1.4;
}
.status-ok { color: #16a34a; font-weight: 600; font-size: 12px; }
.status-expired { color: #dc2626; font-weight: 600; font-size: 12px; }

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
.pager-extra {
  font-size: 12px;
  color: #64748b;
}

.guide-panel {
  padding: 14px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
}
.guide-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 8px;
}
.guide-panel ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #334155;
  line-height: 1.7;
}

.preview-meta {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #334155;
}
.preview-json {
  margin: 0;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

:deep(.expired-row) {
  --el-table-tr-bg-color: #fff7ed;
}
:deep(.el-table .expired-row td.el-table__cell) {
  background: #fff7ed !important;
}
</style>
