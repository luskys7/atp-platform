<template>
  <div class="page-container">
    <PageHeader title="测试任务" subtitle="创建、调度与管理自动化测试任务">
      <template #actions>
        <el-button v-if="userStore.canEdit" @click="$router.push('/cases/editor')">
          <el-icon><EditPen /></el-icon> 可视化用例
        </el-button>
        <el-button v-if="userStore.canEdit" type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 创建任务
        </el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false" style="margin-bottom:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
          <span>任务队列看板</span>
          <span class="queue-meta">队列深度 {{ queueBoard.queue_depth || 0 }} · 排队 {{ queueBoard.queued_tasks || 0 }} · 运行 {{ queueBoard.running_tasks || 0 }}</span>
        </div>
      </template>
      <el-table :data="queueBoard.items || []" size="small" stripe max-height="220" @row-click="row => row.task_id && $router.push(`/tasks/${row.task_id}`)" class="queue-table">
        <el-table-column prop="position" label="#" width="50" />
        <el-table-column prop="name" label="任务" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <span :class="{ 'tag-running-pulse': row.status === 'running' }">
              <el-tag size="small" :type="taskStatusMap[row.status]?.type">{{ taskStatusMap[row.status]?.label || row.status }}</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column prop="wait_seconds" label="等待(秒)" width="90" align="center" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-if="row.task_id" size="small" type="primary" plain @click.stop="$router.push(`/tasks/${row.task_id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>

    <AppCard :hover="false">
      <div class="filter-bar" style="margin-bottom:0;border:none;padding:0;background:transparent">
        <el-select v-model="filters.status" placeholder="任务状态" clearable style="width:150px" @change="loadTasks">
          <el-option v-for="(v, k) in taskStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button @click="loadTasks"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe style="margin-top:16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="platform" label="平台" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.platform }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="script_type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.script_type === 'visual' ? 'warning' : 'info'" effect="light">
              {{ row.script_type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parallel_count" label="并行" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span :class="{ 'tag-running-pulse': row.status === 'running' }">
              <el-tag :type="taskStatusMap[row.status]?.type" size="small" effect="light">
                {{ taskStatusMap[row.status]?.label || row.status }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <div class="table-actions table-actions--grid">
            <el-button size="small" type="primary" plain @click="$router.push(`/tasks/${row.id}`)">详情</el-button>
            <el-button v-if="row.script_type === 'visual' && userStore.canEdit" size="small" plain @click="$router.push(`/cases/editor/${row.id}`)">编辑</el-button>
            <el-button v-if="userStore.canEdit && row.status === 'pending'" size="small" type="success" plain @click="submitTask(row)">提交</el-button>
            <el-button v-if="userStore.canEdit && row.status === 'queued'" size="small" plain @click="pauseTask(row)">暂停</el-button>
            <el-button v-if="userStore.canEdit && row.status === 'paused'" size="small" type="success" plain @click="resumeTask(row)">恢复</el-button>
            <el-button v-if="userStore.canEdit && ['queued','paused','pending'].includes(row.status)" size="small" type="warning" plain @click="pinTask(row)">置顶</el-button>
            <el-button v-if="userStore.canEdit && ['running','queued'].includes(row.status)" size="small" type="warning" plain @click="cancelTask(row)">取消</el-button>
            <el-button v-if="userStore.isAdmin && row.status !== 'running'" size="small" type="danger" plain @click="deleteTask(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:20px;justify-content:flex-end"
        @change="loadTasks"
      />
    </AppCard>

    <el-dialog v-model="showCreateDialog" title="创建测试任务" width="640px" destroy-on-close>
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.name" placeholder="例：登录流程冒烟测试" />
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
                <el-option label="Python" value="python" />
                <el-option label="Appium" value="appium" />
                <el-option label="可视化" value="visual" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="脚本内容" required>
          <el-input v-model="taskForm.script_content" type="textarea" :rows="6" placeholder="输入自动化测试脚本" />
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
        <el-form-item label="账号池互斥">
          <el-switch v-model="taskForm.use_account_pool" />
          <span style="margin-left:8px;font-size:12px;color:var(--atp-text-secondary)">执行时自动占用空闲账号，完成后释放</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi, reportApi, fetchTaskMonitorBundle, notifyTaskDeleted, isNotFoundError } from '@/api'
import { useUserStore } from '@/stores/user'
import { taskStatusMap, formatTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const tasks = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ status: '' })
const showCreateDialog = ref(false)
const queueBoard = ref({ items: [] })
let pollTimer = null

const taskForm = reactive({
  name: '', platform: 'android', script_type: 'python', script_content: '',
  app_package: '', parallel_count: 1, timeout_seconds: 3600, enable_recording: true,
  use_account_pool: false
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await taskApi.list({ page: page.value, page_size: pageSize.value, ...filters })
    tasks.value = res.data.list
    total.value = res.data.total
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
  await Promise.all([loadTasks(), loadQueueBoard()])
}

async function createTask() {
  creating.value = true
  try {
    await taskApi.create(taskForm)
    ElMessage.success('任务创建成功')
    showCreateDialog.value = false
    loadTasks()
  } finally {
    creating.value = false
  }
}

async function submitTask(row) {
  await taskApi.submit(row.id)
  ElMessage.success('任务已提交调度')
  loadTasks()
}

async function cancelTask(row) {
  await ElMessageBox.confirm('确定要取消该任务吗？', '二次确认', { type: 'warning' })
  await taskApi.cancel(row.id)
  ElMessage.success('任务已取消')
  loadTasks()
}

async function pauseTask(row) {
  await taskApi.pauseQueue(row.id)
  ElMessage.success('任务已暂停')
  loadTasks()
}

async function resumeTask(row) {
  await taskApi.resumeQueue(row.id)
  ElMessage.success('任务已恢复排队')
  loadTasks()
}

async function pinTask(row) {
  await taskApi.pinQueue(row.id)
  ElMessage.success('任务已置顶')
  loadTasks()
}

async function deleteTask(row) {
  await ElMessageBox.confirm('确定要删除该任务吗？此操作不可撤销。', '二次确认', { type: 'warning' })
  try {
    await taskApi.delete(row.id)
  } catch (e) {
    if (!isNotFoundError(e)) throw e
  }
  notifyTaskDeleted(row.id)
  ElMessage.success('任务已删除')
  await loadTasks()
}

onMounted(() => {
  refreshAll()
  pollTimer = setInterval(() => {
    if ((queueBoard.value.running_tasks || 0) > 0 || tasks.value.some(t => ['running', 'queued'].includes(t.status))) {
      refreshAll()
    }
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.queue-meta { font-size: 12px; color: var(--atp-text-secondary); }
.queue-table :deep(tbody tr) { cursor: pointer; }
.row-running { animation: blink 1.2s infinite; }
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}
</style>
