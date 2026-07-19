<template>
  <div class="audit-panel" v-loading="loading">
    <!-- 模块 1：头部 -->
    <div class="ap-header">
      <div>
        <h2 class="ap-title">安全审计日志</h2>
        <p class="ap-sub">全平台操作行为溯源记录，用于权限变更、资源删除、设备重置等行为合规取证</p>
      </div>
      <div class="ap-actions">
        <el-button type="primary" plain :loading="exporting" @click="exportLogs">批量导出审计日志</el-button>
        <el-button type="primary" :loading="archiving" @click="archiveLogs">归档备份日志</el-button>
        <el-button @click="openVerify">凭证校验</el-button>
      </div>
    </div>

    <!-- 模块 2：统计卡片 -->
    <div class="overview-grid">
      <div class="ov-card tone-blue">
        <div class="ov-label">审计日志总数</div>
        <div class="ov-value">{{ stats.total ?? 0 }}</div>
        <div class="ov-desc">记录平台全部用户操作日志总条数</div>
      </div>
      <div class="ov-card tone-green">
        <div class="ov-label">当前分页展示条数</div>
        <div class="ov-value">{{ logs.length }}</div>
        <div class="ov-desc">当前页面加载日志数量</div>
      </div>
      <div class="ov-card" :class="(stats.evidence_count || 0) > 0 ? 'tone-cream' : 'tone-alert'">
        <div class="ov-label">有效取证凭证数</div>
        <div class="ov-value" :class="{ 'is-warn': !(stats.evidence_count > 0) }">{{ stats.evidence_count ?? 0 }}</div>
        <div class="ov-desc">{{ (stats.evidence_count || 0) > 0 ? '已归档加密备份的审计凭证数量' : '0 条，请及时归档备份' }}</div>
      </div>
      <div class="ov-card tone-gray">
        <div class="ov-label">历史备份份数</div>
        <div class="ov-value">{{ stats.backup_count ?? 0 }}</div>
        <div class="ov-desc">本地留存的平台备份包总数</div>
      </div>
    </div>

    <!-- 模块 3：筛选 -->
    <section class="filter-bar">
      <el-radio-group v-model="rangePreset" size="small" @change="onRangePreset">
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
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <el-select v-model="filters.user_id" clearable filterable size="small" placeholder="操作用户" style="width:150px">
        <el-option
          v-for="u in users"
          :key="u.id"
          :label="userLabel(u)"
          :value="String(u.id)"
        />
      </el-select>
      <el-select v-model="filters.action" clearable size="small" placeholder="操作类型" style="width:160px">
        <el-option v-for="a in ACTION_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
      </el-select>
      <el-select v-model="filters.resource_type" clearable size="small" placeholder="资源类型" style="width:140px">
        <el-option v-for="r in RESOURCE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <el-input v-model="filters.ip" clearable size="small" placeholder="操作终端 IP" style="width:140px" />
      <el-button size="small" @click="resetFilters">重置筛选</el-button>
      <el-button type="primary" size="small" @click="search">查询检索</el-button>
    </section>

    <!-- 模块 4：表格 -->
    <el-table
      :data="logs"
      stripe
      size="small"
      :row-class-name="rowClassName"
      max-height="480"
    >
      <el-table-column prop="created_at" label="操作时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作用户" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.display_name || row.username || row.user_id || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作行为" width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="isHighRisk(row) ? 'warning' : 'info'" effect="plain">
            {{ actionLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="资源分类" width="120">
        <template #default="{ row }">{{ resourceLabel(row.resource_type) }}</template>
      </el-table-column>
      <el-table-column label="资源唯一 ID" width="130">
        <template #default="{ row }">
          <el-button
            v-if="row.resource_id"
            type="primary"
            link
            @click="openResource(row)"
          >{{ row.resource_id }}</el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="完整操作详情" min-width="160">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDetail(row)">展开</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作终端 IP" width="150">
        <template #default="{ row }">
          <span class="ip-cell">
            {{ row.ip || '—' }}
            <el-button
              v-if="row.ip"
              type="primary"
              link
              size="small"
              @click="copyIp(row.ip)"
            >复制</el-button>
          </span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="onPageSizeChange"
        @current-change="loadLogs"
      />
    </div>

    <!-- 模块 5：说明 -->
    <section class="guide-panel">
      <div class="guide-title">合规审计说明</div>
      <div class="guide-grid">
        <div class="guide-block">
          <div class="guide-h">日志留存规范</div>
          <p>平台审计日志默认留存 180 天，超过周期建议使用「归档备份」生成加密凭证长期留存，满足等保操作溯源审计要求。</p>
        </div>
        <div class="guide-block">
          <div class="guide-h">高危操作说明</div>
          <p>删除任务、重置设备、修改加密凭据、变更团队权限属于高危操作，日志永久不可删除，全部橙色高亮标记便于排查。</p>
        </div>
        <div class="guide-block">
          <div class="guide-h">凭证备份用途</div>
          <p>归档后的备份文件为加密不可篡改文件，可通过右上角「凭证校验」功能验证日志完整性，用于安全复盘、合规检查。</p>
        </div>
      </div>
    </section>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="完整操作详情" width="640px" destroy-on-close>
      <div class="detail-meta">
        <div><b>操作时间：</b>{{ fmtTime(detailRow?.created_at) }}</div>
        <div><b>操作用户：</b>{{ detailRow?.display_name || detailRow?.username || detailRow?.user_id || '—' }}</div>
        <div><b>操作行为：</b>{{ actionLabel(detailRow?.action) }}</div>
        <div><b>资源分类：</b>{{ resourceLabel(detailRow?.resource_type) }} / ID {{ detailRow?.resource_id || '—' }}</div>
        <div><b>操作终端 IP：</b>{{ detailRow?.ip || '—' }}</div>
      </div>
      <el-divider />
      <div class="detail-section">
        <div class="guide-h">操作入参</div>
        <pre class="detail-pre">{{ detailParsed.params }}</pre>
      </div>
      <div class="detail-section">
        <div class="guide-h">修改前内容</div>
        <pre class="detail-pre">{{ detailParsed.before }}</pre>
      </div>
      <div class="detail-section">
        <div class="guide-h">修改后内容</div>
        <pre class="detail-pre">{{ detailParsed.after }}</pre>
      </div>
      <div class="detail-section">
        <div class="guide-h">操作原因备注</div>
        <pre class="detail-pre">{{ detailParsed.reason }}</pre>
      </div>
    </el-dialog>

    <!-- 资源跳转确认 -->
    <el-dialog v-model="resourceVisible" title="资源溯源跳转" width="440px">
      <p>即将前往「{{ resourceLabel(jumpRow?.resource_type) }}」详情，资源 ID：{{ jumpRow?.resource_id }}</p>
      <template #footer>
        <el-button @click="resourceVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmJump">确认跳转</el-button>
      </template>
    </el-dialog>

    <!-- 凭证校验 -->
    <el-dialog v-model="verifyVisible" title="凭证校验" width="520px" destroy-on-close>
      <p class="verify-hint">选择已归档的加密备份包，校验日志完整性与防篡改指纹。</p>
      <el-select v-model="verifyFile" filterable placeholder="选择归档凭证文件" style="width:100%">
        <el-option
          v-for="a in archives"
          :key="a.filename"
          :label="a.filename"
          :value="a.filename"
        />
      </el-select>
      <div v-if="verifyResult" class="verify-result" :class="verifyResult.valid ? 'ok' : 'bad'">
        {{ verifyResult.message }}
      </div>
      <template #footer>
        <el-button @click="verifyVisible = false">关闭</el-button>
        <el-button type="primary" :loading="verifying" :disabled="!verifyFile" @click="runVerify">开始校验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { auditApi, authApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const ACTION_OPTIONS = [
  { value: 'delete', label: '删除资源' },
  { value: 'reset_health', label: '重置设备状态' },
  { value: 'create', label: '新增' },
  { value: 'update', label: '编辑' },
  { value: 'update_status', label: '启用 / 停用' },
  { value: 'batch_run', label: '批量执行' },
  { value: 'restore', label: '还原备份' },
  { value: 'wda_deploy', label: '部署 WDA' }
]

const RESOURCE_OPTIONS = [
  { value: 'task', label: '自动化任务' },
  { value: 'device', label: '测试设备' },
  { value: 'env', label: '环境配置' },
  { value: 'environment', label: '环境配置' },
  { value: 'credential', label: '账号凭据' },
  { value: 'account', label: '账号凭据' },
  { value: 'suite', label: '套件' },
  { value: 'backup', label: '备份包' },
  { value: 'team', label: '团队' }
]

const HIGH_RISK_ACTIONS = new Set(['delete', 'reset_health', 'restore'])

const props = defineProps({
  active: { type: Boolean, default: true }
})
const emit = defineEmits(['updated'])

const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const archiving = ref(false)
const verifying = ref(false)
const logs = ref([])
const users = ref([])
const archives = ref([])
const stats = ref({ total: 0, evidence_count: 0, backup_count: 0 })
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const rangePreset = ref('30d')
const customRange = ref(null)
const filters = reactive({
  user_id: '',
  action: '',
  resource_type: '',
  ip: ''
})

const detailVisible = ref(false)
const detailRow = ref(null)
const resourceVisible = ref(false)
const jumpRow = ref(null)
const verifyVisible = ref(false)
const verifyFile = ref('')
const verifyResult = ref(null)

const detailParsed = computed(() => parseDetail(detailRow.value?.detail))

function userLabel(u) {
  const name = u.display_name || u.username || `用户${u.id}`
  const role = u.role === 'super_admin' || u.role === 'test_admin' ? 'Admin' : '普通测试员'
  return `${name}（${role}）`
}

function actionLabel(a) {
  const hit = ACTION_OPTIONS.find((x) => x.value === a)
  if (hit) return hit.label
  const map = {
    delete: '删除资源',
    reset_health: '重置设备健康状态',
    create: '新增',
    update: '编辑',
    update_status: '启用 / 停用',
    batch_run: '批量执行'
  }
  return map[a] || a || '—'
}

function resourceLabel(t) {
  const hit = RESOURCE_OPTIONS.find((x) => x.value === t)
  if (hit) return hit.label
  const map = {
    task: '自动化任务',
    device: '测试设备',
    env: '环境配置',
    environment: '环境配置',
    credential: '账号凭据',
    account: '账号凭据',
    suite: '套件',
    backup: '备份包',
    team: '团队'
  }
  return map[t] || t || '—'
}

function isHighRisk(row) {
  if (!row) return false
  if (HIGH_RISK_ACTIONS.has(row.action)) return true
  if (row.resource_type === 'credential' && ['update', 'create', 'delete'].includes(row.action)) return true
  if (row.resource_type === 'team' && ['update', 'create', 'delete'].includes(row.action)) return true
  return false
}

function rowClassName({ row }) {
  return isHighRisk(row) ? 'risk-row' : ''
}

function parseDetail(raw) {
  const empty = { params: '无', before: '无', after: '无', reason: '无' }
  if (raw == null || raw === '') return empty
  if (typeof raw === 'object') {
    return {
      params: formatBlock(raw.params ?? raw.input ?? raw),
      before: formatBlock(raw.before ?? raw.old ?? raw.before_value),
      after: formatBlock(raw.after ?? raw.new ?? raw.after_value),
      reason: formatBlock(raw.reason ?? raw.remark ?? raw.note)
    }
  }
  const text = String(raw)
  try {
    const obj = JSON.parse(text)
    if (obj && typeof obj === 'object') {
      return {
        params: formatBlock(obj.params ?? obj.input ?? obj),
        before: formatBlock(obj.before ?? obj.old ?? obj.before_value),
        after: formatBlock(obj.after ?? obj.new ?? obj.after_value),
        reason: formatBlock(obj.reason ?? obj.remark ?? obj.note)
      }
    }
  } catch { /* plain text */ }
  return { params: text, before: '无', after: '无', reason: '无' }
}

function formatBlock(v) {
  if (v == null || v === '') return '无'
  if (typeof v === 'string') return v
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

function rangeBounds() {
  const now = new Date()
  const end = formatLocal(now)
  if (rangePreset.value === 'today') {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    return { from: formatLocal(start), to: end }
  }
  if (rangePreset.value === '7d') {
    return { from: formatLocal(new Date(now.getTime() - 7 * 86400000)), to: end }
  }
  if (rangePreset.value === '30d') {
    return { from: formatLocal(new Date(now.getTime() - 30 * 86400000)), to: end }
  }
  if (customRange.value?.length === 2) {
    return { from: customRange.value[0], to: customRange.value[1] }
  }
  return { from: '', to: '' }
}

function formatLocal(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function buildParams(extra = {}) {
  const { from, to } = rangeBounds()
  const params = {
    page: page.value,
    page_size: pageSize.value,
    ...extra
  }
  if (from) params.from = from
  if (to) params.to = to
  if (filters.user_id) params.user_id = filters.user_id
  if (filters.action) params.action = filters.action
  if (filters.resource_type) params.resource_type = filters.resource_type
  if (filters.ip?.trim()) params.ip = filters.ip.trim()
  return params
}

async function loadStats() {
  try {
    stats.value = (await auditApi.stats()).data || { total: 0, evidence_count: 0, backup_count: 0 }
  } catch {
    stats.value = { total: total.value, evidence_count: 0, backup_count: 0 }
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await auditApi.list(buildParams())
    logs.value = res.data?.list || []
    total.value = res.data?.total || 0
    emit('updated', { logs: logs.value, total: total.value, stats: stats.value })
  } catch {
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    users.value = (await authApi.listUsers()).data || []
  } catch {
    users.value = []
  }
}

async function refreshAll() {
  await Promise.all([loadStats(), loadLogs(), loadUsers()])
  emit('updated', { logs: logs.value, total: total.value, stats: stats.value })
}

function onRangePreset() {
  if (rangePreset.value !== 'custom') customRange.value = null
}

function resetFilters() {
  rangePreset.value = '30d'
  customRange.value = null
  filters.user_id = ''
  filters.action = ''
  filters.resource_type = ''
  filters.ip = ''
  page.value = 1
  search()
}

function search() {
  page.value = 1
  loadLogs()
}

function onPageSizeChange() {
  page.value = 1
  loadLogs()
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function openResource(row) {
  jumpRow.value = row
  resourceVisible.value = true
}

function confirmJump() {
  const row = jumpRow.value
  resourceVisible.value = false
  if (!row) return
  const id = row.resource_id
  const type = row.resource_type
  if (type === 'task') router.push(`/tasks/${id}`)
  else if (type === 'device') router.push(`/devices/${id}/screen`)
  else if (type === 'suite') router.push({ path: '/suites', query: { id } })
  else if (type === 'env' || type === 'environment') router.push({ path: '/platform-config', query: { tab: 'env' } })
  else if (type === 'credential') router.push({ path: '/platform-config', query: { tab: 'credentials' } })
  else if (type === 'account') router.push({ path: '/platform-config', query: { tab: 'accounts' } })
  else if (type === 'backup') router.push({ path: '/platform-config', query: { tab: 'backup' } })
  else if (type === 'team') router.push({ path: '/platform-config', query: { tab: 'teams' } })
  else ElMessage.info('暂不支持该资源类型的一键跳转')
}

async function copyIp(ip) {
  try {
    await navigator.clipboard.writeText(ip)
    ElMessage.success('已复制 IP')
  } catch {
    ElMessage.error('复制失败')
  }
}

function downloadCsv(filename, header, rows) {
  const bom = '\uFEFF'
  const csv = [header.join(','), ...rows.map((r) => r.map((c) => {
    const s = String(c ?? '')
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }).join(','))].join('\n')
  const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function exportLogs() {
  exporting.value = true
  try {
    const res = await auditApi.list(buildParams({ page: 1, page_size: 100 }))
    const list = res.data?.list || []
    const stamp = new Date().toISOString().slice(0, 10)
    downloadCsv(`安全审计日志_${stamp}.csv`, [
      '操作时间', '操作用户', '操作行为', '资源分类', '资源唯一 ID', '完整操作详情', '操作终端 IP'
    ], list.map((r) => [
      fmtTime(r.created_at),
      r.display_name || r.username || r.user_id || '',
      actionLabel(r.action),
      resourceLabel(r.resource_type),
      r.resource_id || '',
      typeof r.detail === 'string' ? r.detail : JSON.stringify(r.detail || ''),
      r.ip || ''
    ]))
    ElMessage.success(`已按筛选条件导出 ${list.length} 条审计日志`)
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function archiveLogs() {
  try {
    await ElMessageBox.confirm('将生成加密审计归档包，并计入有效取证凭证统计，是否继续？', '归档备份日志', { type: 'warning' })
  } catch {
    return
  }
  archiving.value = true
  try {
    const res = await auditApi.archive()
    ElMessage.success(`归档成功：${res.data?.filename || '已生成凭证'}`)
    await loadStats()
    emit('updated', { logs: logs.value, total: total.value, stats: stats.value })
  } catch (e) {
    ElMessage.error(e?.message || '归档失败')
  } finally {
    archiving.value = false
  }
}

async function openVerify() {
  verifyResult.value = null
  verifyFile.value = ''
  verifyVisible.value = true
  try {
    archives.value = (await auditApi.archives()).data || []
    if (!archives.value.length) ElMessage.warning('暂无归档凭证，请先执行「归档备份日志」')
  } catch {
    archives.value = []
  }
}

async function runVerify() {
  if (!verifyFile.value) return
  verifying.value = true
  try {
    const res = await auditApi.verify(verifyFile.value)
    verifyResult.value = res.data || {}
    if (verifyResult.value.valid) ElMessage.success('凭证校验通过')
    else ElMessage.error('凭证校验失败')
  } catch (e) {
    ElMessage.error(e?.message || '校验失败')
  } finally {
    verifying.value = false
  }
}

watch(() => props.active, (v) => {
  if (v) refreshAll()
})

onMounted(() => {
  if (props.active) refreshAll()
})

defineExpose({ refresh: refreshAll })
</script>

<style scoped>
.audit-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ap-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  align-items: flex-start;
}
.ap-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.ap-sub {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  max-width: 640px;
}
.ap-actions {
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
  border-radius: 12px;
  padding: 14px 16px;
  min-height: 100px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.ov-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.ov-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.ov-value { font-size: 26px; font-weight: 700; color: #0f172a; line-height: 1.2; }
.ov-value.is-warn { color: #c2410c; }
.ov-desc { margin-top: 8px; font-size: 12px; color: #64748b; line-height: 1.4; }
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

.ip-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.pager {
  display: flex;
  justify-content: flex-end;
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
  margin-bottom: 10px;
}
.guide-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
@media (max-width: 960px) {
  .guide-grid { grid-template-columns: 1fr; }
}
.guide-block {
  font-size: 12px;
  color: #334155;
  line-height: 1.65;
}
.guide-h {
  font-weight: 700;
  color: #1e3a8a;
  margin-bottom: 4px;
}
.guide-block p { margin: 0; }

.detail-meta {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #334155;
}
.detail-section { margin-bottom: 12px; }
.detail-pre {
  margin: 6px 0 0;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 160px;
  overflow: auto;
}
.verify-hint {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 12px;
}
.verify-result {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.verify-result.ok { background: #ecfdf5; color: #047857; }
.verify-result.bad { background: #fff1f2; color: #be123c; }

:deep(.risk-row) {
  --el-table-tr-bg-color: #fff7ed;
}
:deep(.el-table .risk-row td.el-table__cell) {
  background: #fff7ed !important;
}
</style>
