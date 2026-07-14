<template>
  <div class="page-container">
    <PageHeader title="录屏回放" subtitle="查看与管理测试执行录屏，支持倍速回放与标注" />

    <AppCard :hover="false">
      <div class="filter-bar" style="margin-bottom:0;border:none;padding:0;background:transparent">
        <el-input v-model="filters.keyword" placeholder="关键词搜索" style="width:180px" clearable @change="loadRecordings" />
        <el-input v-model="filters.module_name" placeholder="模块名称" style="width:140px" clearable @change="loadRecordings" />
        <el-input v-model="filters.version_label" placeholder="版本标签" style="width:120px" clearable @change="loadRecordings" />
        <el-input v-model="filters.task_id" placeholder="任务 ID" style="width:120px" clearable @change="loadRecordings" />
        <el-button @click="loadRecordings"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>

      <el-table :data="recordings" v-loading="loading" stripe style="margin-top:16px">
        <el-table-column label="预览" width="90">
          <template #default="{ row }">
            <img
              v-if="thumbMap[row.id]"
              :src="thumbMap[row.id]"
              class="rec-thumb"
              alt=""
            />
            <el-icon v-else :size="28" color="#94a3b8"><VideoCamera /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="task_id" label="任务ID" width="90">
          <template #default="{ row }">{{ row.task_id || '-' }}</template>
        </el-table-column>
        <el-table-column prop="device_id" label="设备ID" width="90" />
        <el-table-column prop="module_name" label="模块" width="120">
          <template #default="{ row }">{{ row.module_name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="version_label" label="版本" width="90">
          <template #default="{ row }">{{ row.version_label || '-' }}</template>
        </el-table-column>
        <el-table-column prop="project_code" label="项目" width="90">
          <template #default="{ row }">{{ row.project_code || '-' }}</template>
        </el-table-column>
        <el-table-column prop="file_name" label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="recording-title">
              <span v-if="isUnviewed(row)" class="recording-unviewed-dot" title="未查看" />
              {{ row.file_name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="duration_seconds" label="时长" width="80">
          <template #default="{ row }">{{ row.duration_seconds }}s</template>
        </el-table-column>
        <el-table-column prop="operator_label" label="操作人" width="100">
          <template #default="{ row }">{{ row.operator_label || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ready' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="watermark_hash" label="水印" width="140">
          <template #default="{ row }">
            <template v-if="row.watermark_hash">
              <span class="watermark-hash">{{ row.watermark_hash.slice(0,8) }}...</span>
              <el-button size="small" link type="primary" @click="verifyWatermark(row)">核验</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'ready'" @click="playRecording(row)">回放</el-button>
            <el-button v-if="row.session_id" size="small" plain @click="goReview(row)">审阅</el-button>
            <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteRecording(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end"
        @change="loadRecordings"
      />
    </AppCard>

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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { recordingApi, recordApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import RecordingPlayer from '@/components/RecordingPlayer.vue'

const VIEWED_KEY = 'atp_recording_viewed'

const userStore = useUserStore()
const loading = ref(false)
const recordings = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ task_id: '', keyword: '', module_name: '', version_label: '' })
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
const router = useRouter()

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

function perfTagLabel(g) {
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
}

function perfTagType(g) {
  return { good: 'success', fair: 'warning', heavy: 'danger' }[g] || 'info'
}

async function loadRecordings() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filters.task_id) params.task_id = filters.task_id
    if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
    if (filters.module_name?.trim()) params.module_name = filters.module_name.trim()
    if (filters.version_label?.trim()) params.version_label = filters.version_label.trim()
    const res = await recordingApi.list(params)
    recordings.value = res.data.list
    total.value = res.data.total
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

function goReview(row) {
  if (row.session_id) router.push(`/recordings/review/${row.session_id}`)
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
  await ElMessageBox.confirm('确定要删除该录屏文件吗？此操作不可撤销。', '二次确认', { type: 'warning' })
  await recordingApi.delete(row.id)
  viewedIds.value.delete(row.id)
  persistViewedIds()
  ElMessage.success('录屏已删除')
  loadRecordings()
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

onMounted(loadRecordings)
</script>

<style scoped>
.rec-thumb {
  width: 72px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
  background: #0f172a;
}
.watermark-hash {
  font-size: 12px;
  color: var(--atp-text-muted);
}
.playback-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
