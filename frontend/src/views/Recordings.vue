<template>
  <div class="page-container rec-page">
    <!-- 模块 1：标题区 -->
    <PageHeader
      title="录屏回放"
      subtitle="管理自动化任务执行录屏，支持倍速回放、问题标注、质量审计、合规归档导出"
    >
      <template #actions>
        <el-button type="primary" plain :disabled="!selectedIds.length" :loading="exporting" @click="batchExport">
          批量导出录屏
        </el-button>
        <el-button type="warning" plain :disabled="!selectedIds.length" :loading="archiving" @click="batchArchive">
          批量归档
        </el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" :loading="batchDeleting" @click="batchDelete">
          批量删除
        </el-button>
        <el-button :loading="loading" @click="refreshAll">
          <el-icon><Refresh /></el-icon> 刷新列表
        </el-button>
      </template>
    </PageHeader>

    <!-- 模块 2：复合筛选 -->
    <section class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="关键词：模块 / 文件名 / 任务 / 设备"
        clearable
        style="width:220px"
        @keyup.enter="search"
      />
      <el-select v-model="filters.module_name" placeholder="业务模块" clearable filterable style="width:140px">
        <el-option v-for="m in facets.modules" :key="m" :label="m" :value="m" />
      </el-select>
      <el-select v-model="filters.version_label" placeholder="版本标签" clearable filterable style="width:130px">
        <el-option v-for="v in facets.versions" :key="v" :label="v" :value="v" />
      </el-select>
      <el-input v-model="filters.task_id" placeholder="任务 ID" clearable style="width:110px" @keyup.enter="search" />
      <el-select v-model="filters.device_id" placeholder="设备编号" clearable filterable style="width:130px">
        <el-option v-for="d in facets.device_ids" :key="d" :label="`设备 #${d}`" :value="String(d)" />
      </el-select>
      <el-select v-model="filters.operator_label" placeholder="操作人账号" clearable filterable style="width:140px">
        <el-option v-for="o in facets.operators" :key="o" :label="o" :value="o" />
      </el-select>
      <el-select v-model="filters.duration_bucket" placeholder="录屏时长" clearable style="width:150px">
        <el-option label="短录屏 (<60s)" value="short" />
        <el-option label="中等 (60-300s)" value="medium" />
        <el-option label="长录屏 (≥300s)" value="long" />
      </el-select>
      <el-select v-model="filters.review_status" placeholder="审核状态" clearable style="width:130px">
        <el-option label="录制就绪" value="ready" />
        <el-option label="待审核" value="pending_review" />
        <el-option label="审核通过" value="approved" />
        <el-option label="已归档" value="archived" />
        <el-option label="已过期" value="expired" />
      </el-select>
      <el-select
        v-model="filters.created_range"
        placeholder="创建时间"
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

    <!-- 模块 3：表格 -->
    <AppCard :hover="false" class="table-card">
      <div class="table-toolbar">
        <span>已选 {{ selectedIds.length }} 条</span>
        <span class="hint">共 {{ total }} 条录屏文件</span>
      </div>
      <el-table
        :data="recordings"
        v-loading="loading"
        stripe
        row-key="id"
        :row-class-name="rowClassName"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" fixed />
        <el-table-column label="缩略预览" width="100">
          <template #default="{ row }">
            <el-popover
              v-if="thumbMap[row.id]"
              placement="right"
              :width="320"
              trigger="hover"
              :show-after="200"
            >
              <template #reference>
                <img :src="thumbMap[row.id]" class="rec-thumb" alt="缩略图" />
              </template>
              <div class="thumb-preview">
                <img :src="thumbMap[row.id]" alt="大图预览" />
                <p>录屏首帧预览 · #{{ row.id }}</p>
              </div>
            </el-popover>
            <div v-else class="rec-thumb rec-thumb--empty">
              <el-icon :size="22"><VideoCamera /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="录屏 ID" width="88" />
        <el-table-column label="关联任务 ID" width="110">
          <template #default="{ row }">
            <el-button v-if="row.task_id" link type="primary" @click="goTask(row.task_id)">{{ row.task_id }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="录制设备 ID" width="110">
          <template #default="{ row }">
            <el-button v-if="row.device_id" link type="primary" @click="goDevice(row.device_id)">{{ row.device_id }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="module_name" label="业务模块" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.module_name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="version_label" label="迭代版本" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.version_label || '-' }}</template>
        </el-table-column>
        <el-table-column prop="project_code" label="所属项目" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.project_code || '-' }}</template>
        </el-table-column>
        <el-table-column label="录屏文件名" min-width="220">
          <template #default="{ row }">
            <div class="fname-cell">
              <span v-if="isUnviewed(row)" class="recording-unviewed-dot" title="未查看" />
              <span class="fname">{{ row.file_name || '-' }}</span>
              <el-tooltip
                v-if="isSessionFile(row.file_name)"
                content="自动化会话录屏文件，自动生成不可修改"
                placement="top"
              >
                <el-tag size="small" type="info" effect="plain" class="fname-tip">会话录屏</el-tag>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="录制时长" width="90">
          <template #default="{ row }">{{ formatDuration(row.duration_seconds) }}</template>
        </el-table-column>
        <el-table-column prop="operator_label" label="操作执行人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operator_label || '-' }}</template>
        </el-table-column>
        <el-table-column label="录屏状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row)" size="small" :effect="statusEffect(row)" :class="statusClass(row)">
              {{ statusLabel(row) }}
            </el-tag>
            <div v-if="showExpireHint(row)" class="expire-hint">即将自动清理</div>
          </template>
        </el-table-column>
        <el-table-column label="安全水印" width="130">
          <template #default="{ row }">
            <template v-if="row.watermark_hash">
              <span class="watermark-hash" :title="row.watermark_hash">{{ row.watermark_hash.slice(0, 8) }}…</span>
              <el-button size="small" link type="primary" @click="verifyWatermark(row)">核验</el-button>
            </template>
            <span v-else class="muted">已内置</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="单条操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                size="small"
                type="primary"
                :disabled="row.status !== 'ready'"
                @click="playRecording(row)"
              >回放</el-button>
              <el-button size="small" type="primary" plain @click="goQuality(row)">质量审核</el-button>
              <el-button
                v-if="userStore.isAdmin"
                size="small"
                type="danger"
                @click="deleteRecording(row)"
              >删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 模块 4：分页 -->
      <div class="pager-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadRecordings"
        />
        <span class="pager-extra">共 {{ total }} 条录屏文件</span>
      </div>
    </AppCard>

    <section class="guide-panel">
      <div class="guide-title">录屏存储合规说明</div>
      <div class="guide-grid">
        <div>
          <div class="guide-h">1. 存储留存规则</div>
          <p>自动化录屏默认留存 {{ retainDays }} 天，到期系统自动清理；需要长期复盘的录屏请使用「归档」功能永久保存。</p>
        </div>
        <div>
          <div class="guide-h">2. 水印安全机制</div>
          <p>所有录屏内置操作人、时间、设备水印，用于安全审计溯源，不可手动去除。</p>
        </div>
        <div>
          <div class="guide-h">3. 回放与审核用途</div>
          <ul>
            <li>回放：定位用例失败步骤，截取故障帧留存缺陷；</li>
            <li>质量审核：校验控件识别率、启动性能，完成 PO 版本验收；</li>
            <li>归档导出：用于线上事故复盘、版本迭代评审、合规安全检查。</li>
          </ul>
        </div>
        <div>
          <div class="guide-h">4. 删除风险提示</div>
          <p>手动删除 / 过期自动清理的录屏无备份，删除后无法恢复，重要录屏务必提前归档导出。</p>
        </div>
      </div>
    </section>

    <el-dialog v-model="showPlayer" title="录屏回放" width="900px" destroy-on-close class="player-dialog" @close="closePlayer">
      <div v-if="playbackMeta.recognition_rate != null" class="playback-meta">
        <el-tag size="small" type="success">识别率 {{ playbackMeta.recognition_rate }}%</el-tag>
        <el-tag v-if="playbackMeta.startup_ms != null" size="small" :type="playbackMeta.startup_ms <= 3000 ? 'success' : 'warning'">
          启动 {{ playbackMeta.startup_ms }}ms
        </el-tag>
        <el-tag v-if="playbackMeta.performance_grade" size="small" :type="perfTagType(playbackMeta.performance_grade)">
          {{ perfTagLabel(playbackMeta.performance_grade) }}
        </el-tag>
        <el-tag v-if="playbackMeta.cpu_ok != null" size="small" :type="playbackMeta.cpu_ok ? 'success' : 'warning'">
          CPU {{ playbackMeta.cpu_ok ? '达标' : '偏高' }}
        </el-tag>
      </div>
      <RecordingPlayer
        v-if="playbackUrl"
        :src="playbackUrl"
        :markers="playbackMarkers"
        :annotations="playbackAnnotations"
        editable-annotations
        @annotations-change="onPlaybackAnnotationsChange"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { recordingApi, recordApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import RecordingPlayer from '@/components/RecordingPlayer.vue'

const VIEWED_KEY = 'atp_recording_viewed'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const archiving = ref(false)
const batchDeleting = ref(false)
const recordings = ref([])
const selectedRows = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const retainDays = ref(90)
const facets = reactive({ modules: [], versions: [], operators: [], device_ids: [] })
const filters = reactive({
  keyword: '',
  module_name: '',
  version_label: '',
  task_id: '',
  device_id: '',
  operator_label: '',
  duration_bucket: '',
  review_status: '',
  created_range: ''
})
const customRange = ref(null)
const showPlayer = ref(false)
const playbackUrl = ref('')
const playbackMarkers = ref([])
const playbackAnnotations = ref([])
const playbackSessionId = ref(null)
const playbackMeta = reactive({
  recognition_rate: null,
  startup_ms: null,
  performance_grade: null,
  cpu_ok: null
})
const viewedIds = ref(loadViewedIds())
const thumbMap = ref({})

const selectedIds = computed(() => selectedRows.value.map(r => r.id))

function loadViewedIds() {
  try {
    return new Set(JSON.parse(localStorage.getItem(VIEWED_KEY) || '[]'))
  } catch {
    return new Set()
  }
}

function persistViewedIds() {
  localStorage.setItem(VIEWED_KEY, JSON.stringify([...viewedIds.value]))
}

function isUnviewed(row) {
  return row.status === 'ready' && !viewedIds.value.has(row.id)
}

function markViewed(id) {
  viewedIds.value.add(id)
  viewedIds.value = new Set(viewedIds.value)
  persistViewedIds()
}

function isSessionFile(name) {
  return !!name && /^session[_-]/i.test(name)
}

function formatDuration(sec) {
  const s = Number(sec) || 0
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const r = s % 60
  return r ? `${m}分${r}秒` : `${m}分钟`
}

function statusLabel(row) {
  return row.display_status_label || ({
    ready: '录制就绪',
    pending_review: '待审核',
    approved: '审核通过',
    archived: '已归档',
    expired: '已过期',
    recording: '录制中',
    uploading: '上传中',
    failed: '录制失败'
  }[row.display_status || row.status] || row.status || '-')
}

function statusTagType(row) {
  const s = row.display_status || row.status
  return {
    ready: 'success',
    pending_review: 'warning',
    approved: 'success',
    archived: 'primary',
    expired: 'info',
    recording: 'warning',
    uploading: 'warning',
    failed: 'danger'
  }[s] || 'info'
}

function statusEffect(row) {
  return row.display_status === 'approved' ? 'dark' : 'light'
}

function statusClass(row) {
  return row.display_status === 'approved' ? 'tag-approved' : ''
}

function showExpireHint(row) {
  if (row.display_status === 'archived') return false
  return !!row.expire_soon || row.display_status === 'expired'
}

function rowClassName({ row }) {
  if (row.display_status === 'expired' || row.expire_soon) return 'row-expire'
  return ''
}

function perfTagLabel(g) {
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
}

function perfTagType(g) {
  return { good: 'success', fair: 'warning', heavy: 'danger' }[g] || 'info'
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
  if (filters.device_id) params.device_id = filters.device_id
  if (filters.operator_label) params.operator_label = filters.operator_label
  if (filters.duration_bucket) params.duration_bucket = filters.duration_bucket
  if (filters.review_status) params.review_status = filters.review_status
  if (filters.created_range && filters.created_range !== 'custom') {
    params.created_range = filters.created_range
  }
  if (filters.created_range === 'custom' && customRange.value?.length === 2) {
    params.created_from = customRange.value[0]
    params.created_to = customRange.value[1] + 'T23:59:59'
  }
  return params
}

async function loadFacets() {
  try {
    const res = await recordingApi.facets()
    const d = res.data || {}
    facets.modules = d.modules || []
    facets.versions = d.versions || []
    facets.operators = d.operators || []
    facets.device_ids = d.device_ids || []
    if (d.retain_days) retainDays.value = d.retain_days
  } catch { /* ignore */ }
}

async function loadRecordings() {
  loading.value = true
  try {
    const res = await recordingApi.list(buildParams())
    recordings.value = res.data.list || []
    total.value = res.data.total || 0
    for (const row of recordings.value) {
      if (row.thumbnail_path && !thumbMap.value[row.id]) {
        recordingApi.thumbnailStream(row.id).then(url => {
          if (url) thumbMap.value[row.id] = url
        })
      }
    }
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadRecordings()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    module_name: '',
    version_label: '',
    task_id: '',
    device_id: '',
    operator_label: '',
    duration_bucket: '',
    review_status: '',
    created_range: ''
  })
  customRange.value = null
  search()
}

function refreshAll() {
  loadFacets()
  loadRecordings()
}

function goTask(id) {
  router.push(`/tasks/${id}`)
}

function goDevice(id) {
  router.push({ path: '/devices', query: { highlight: id } })
}

function goQuality(row) {
  router.push({
    path: '/recording-quality',
    query: row.session_id ? { session_id: row.session_id } : { recording_id: row.id }
  })
}

async function playRecording(row) {
  playbackMarkers.value = []
  playbackAnnotations.value = []
  playbackSessionId.value = row.session_id || null
  Object.assign(playbackMeta, {
    recognition_rate: null,
    startup_ms: null,
    performance_grade: null,
    cpu_ok: null
  })
  if (row.session_id) {
    try {
      const ctxRes = await recordingApi.context(row.id)
      const ctx = ctxRes.data || {}
      playbackMarkers.value = (ctx.markers || []).map(m => ({
        index: m.index,
        offsetMs: m.offsetMs,
        label: m.label
      }))
      playbackAnnotations.value = ctx.annotations || []
      playbackMeta.recognition_rate = ctx.recognition_rate ?? null
      playbackMeta.startup_ms = ctx.startup_ms ?? null
      playbackMeta.performance_grade = ctx.performance_grade ?? null
      playbackMeta.cpu_ok = ctx.cpu_ok ?? null
    } catch { /* 无关联会话时仅播放视频 */ }
  }
  const res = await recordingApi.playback(row.id)
  const url = res.data.url
  if (url.startsWith('/api/')) {
    playbackUrl.value = await recordingApi.playbackStream(row.id)
  } else {
    playbackUrl.value = url
  }
  markViewed(row.id)
  showPlayer.value = true
}

async function onPlaybackAnnotationsChange(list) {
  playbackAnnotations.value = list
  if (!playbackSessionId.value) return
  try {
    await recordApi.updateSteps(playbackSessionId.value, {
      meta: { video_annotations: list }
    })
  } catch {
    ElMessage.warning('标注保存失败')
  }
}

function closePlayer() {
  if (playbackUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(playbackUrl.value)
  }
  playbackUrl.value = ''
  playbackMarkers.value = []
  playbackAnnotations.value = []
  playbackSessionId.value = null
}

async function deleteRecording(row) {
  await ElMessageBox.confirm(
    `删除后录屏文件不可恢复，确认删除「${row.file_name || '#' + row.id}」？`,
    '删除确认',
    { type: 'error', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消' }
  )
  await recordingApi.delete(row.id)
  viewedIds.value.delete(row.id)
  persistViewedIds()
  ElMessage.success('录屏已删除')
  refreshAll()
}

async function batchExport() {
  if (!selectedIds.value.length) return
  exporting.value = true
  try {
    await recordingApi.batchExport(selectedIds.value)
    ElMessage.success(`已打包导出 ${selectedIds.value.length} 条录屏`)
  } catch (e) {
    ElMessage.error(e?.message || '批量导出失败，请确认本地录屏文件可用')
  } finally {
    exporting.value = false
  }
}

async function batchArchive() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(
    `确认将选中的 ${selectedIds.value.length} 条录屏标记为归档？归档后长期留存，用于版本复盘。`,
    '批量归档',
    { type: 'warning', confirmButtonText: '确认归档' }
  )
  archiving.value = true
  try {
    const res = await recordingApi.batchArchive(selectedIds.value)
    ElMessage.success(`已归档 ${res.data?.archived ?? selectedIds.value.length} 条`)
    refreshAll()
  } finally {
    archiving.value = false
  }
}

async function batchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(
    `即将永久删除 ${selectedIds.value.length} 条录屏，删除后不可恢复。请再次确认是否继续？`,
    '批量删除风险确认',
    { type: 'error', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消' }
  )
  batchDeleting.value = true
  try {
    const res = await recordingApi.batchDelete(selectedIds.value)
    ElMessage.success(`已删除 ${res.data?.deleted ?? selectedIds.value.length} 条`)
    selectedRows.value = []
    refreshAll()
  } finally {
    batchDeleting.value = false
  }
}

async function verifyWatermark(row) {
  try {
    const res = await recordingApi.verifyWatermark(row.id)
    const d = res.data || {}
    const ok = d.verified === true
    await ElMessageBox.alert(
      `Payload: ${d.watermark_payload || '-'}\n\n存储 Hash: ${d.watermark_hash || '-'}\n计算 Hash: ${d.computed_hash || '-'}\n\n核验结果: ${ok ? '通过' : '未通过'}`,
      `水印核验 #${row.id}`,
      { type: ok ? 'success' : 'error', confirmButtonText: '关闭' }
    )
  } catch (e) {
    ElMessage.error(e?.message || '水印核验失败')
  }
}

onMounted(() => {
  loadFacets()
  loadRecordings()
})
</script>

<style scoped>
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
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}
.table-toolbar .hint { color: #94a3b8; }

.rec-thumb {
  width: 72px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
  background: #0f172a;
  cursor: zoom-in;
  display: block;
}
.rec-thumb--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  cursor: default;
}
.thumb-preview img {
  width: 100%;
  border-radius: 8px;
  background: #0f172a;
}
.thumb-preview p {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
  text-align: center;
}

.fname-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.fname {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fname-tip { flex-shrink: 0; }
.recording-unviewed-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3b82f6;
  flex-shrink: 0;
}

.watermark-hash {
  font-size: 12px;
  color: var(--atp-text-muted, #94a3b8);
  margin-right: 4px;
}
.muted { color: #94a3b8; font-size: 12px; }

.expire-hint {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}
.tag-approved {
  background: #047857 !important;
  border-color: #047857 !important;
  color: #fff !important;
}

:deep(.row-expire) {
  background: #f8fafc !important;
  color: #94a3b8;
}
:deep(.row-expire td) {
  color: #94a3b8;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
.pager-extra {
  font-size: 13px;
  color: #64748b;
}

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
.guide-grid ul {
  padding-left: 18px;
}

.playback-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .guide-grid { grid-template-columns: 1fr; }
}
</style>
