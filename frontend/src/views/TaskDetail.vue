<template>
  <div class="page-container">
    <PageHeader :title="task ? task.name : `任务 #${taskId}`" subtitle="执行详情、控件绑定与日志">
      <template #actions>
        <el-button v-if="task" @click="exportOffline">导出离线包</el-button>
        <el-button v-if="sourceCaseId" type="warning" @click="openDebugWorkbench">同屏调试</el-button>
        <el-button v-if="sourceCaseId" @click="$router.push(`/cases/editor/${sourceCaseId}?asset=1`)">编辑用例</el-button>
        <el-button v-if="task && ['failed','timeout','cancelled'].includes(task.status)" type="warning" @click="resumeTask">断点续跑</el-button>
        <el-button @click="$router.push('/tasks')">任务列表</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="16">
        <AppCard title="基本信息" :hover="false">
          <el-descriptions :column="2" border v-if="task">
            <el-descriptions-item label="任务名称">{{ task.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="taskStatusMap[task.status]?.type" effect="light">{{ taskStatusMap[task.status]?.label || task.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="平台">{{ task.platform }}</el-descriptions-item>
            <el-descriptions-item label="脚本类型">{{ task.script_type }}</el-descriptions-item>
            <el-descriptions-item label="应用包名">{{ task.app_package || '-' }}</el-descriptions-item>
            <el-descriptions-item label="并行数">{{ task.parallel_count }}</el-descriptions-item>
            <el-descriptions-item label="录屏">{{ task.enable_recording ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="重试">{{ task.retry_count || 0 }} / {{ task.max_retries || 0 }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ fmtTime(task.started_at) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ fmtTime(task.finished_at) }}</el-descriptions-item>
            <el-descriptions-item v-if="task.error_code" label="错误码">
              <el-tag type="danger">{{ task.error_code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="task.error_message" label="错误信息" :span="2">
              {{ task.error_message }}
            </el-descriptions-item>
          </el-descriptions>
        </AppCard>

        <AppCard
          v-if="failurePlaybackInfo?.recording_id"
          title="失败片段回放"
          :hover="false"
          style="margin-top:20px"
        >
          <p v-if="failurePlaybackInfo.failed_step_index" class="failure-playback-meta">
            失败步骤 #{{ failurePlaybackInfo.failed_step_index }}
            <span v-if="failurePlaybackInfo.step_display_name"> · {{ failurePlaybackInfo.step_display_name }}</span>
            <span v-if="failurePlaybackInfo.video_offset_ms != null">
              · 视频偏移 {{ Math.round(failurePlaybackInfo.video_offset_ms / 1000) }}s
            </span>
          </p>
          <p v-if="failurePlaybackInfo.failure_message" class="failure-playback-msg">
            {{ failurePlaybackInfo.failure_message }}
          </p>
          <el-button type="danger" @click="openFailurePlayback">回放失败片段</el-button>
        </AppCard>

        <AppCard :hover="false" style="margin-top:20px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>私有控件绑定</span>
              <el-button type="primary" size="small" @click="openBindingDialog()">
                <el-icon><Plus /></el-icon> 添加绑定
              </el-button>
            </div>
          </template>
          <el-table :data="bindings" size="small" empty-text="暂无私有控件，执行时将回退全局控件池">
            <el-table-column prop="step_index" label="步骤" width="70" />
            <el-table-column prop="element_name" label="控件名称" min-width="120" />
            <el-table-column prop="locator_type" label="类型" width="90" />
            <el-table-column prop="locator_value" label="定位表达式" min-width="180" show-overflow-tooltip />
            <el-table-column label="来源" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.pool_id ? 'warning' : 'success'">
                  {{ row.pool_id ? '池引用' : '私有' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="testResolve(row)">试解析</el-button>
                <el-button size="small" type="danger" plain @click="deleteBinding(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="healingRecords.length" style="margin-top:12px">
            <div class="heal-title">自愈记录</div>
            <el-timeline>
              <el-timeline-item v-for="r in healingRecords" :key="r.id" size="small"
                :type="r.success ? 'success' : 'warning'"
                :timestamp="fmtTime(r.created_at)">
                [{{ r.heal_strategy }}] {{ r.original_locator }} → {{ r.healed_locator }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </AppCard>

        <AppCard v-if="task?.status === 'waiting_manual' || pendingIntervention" title="人工介入" :hover="false" style="margin-top:20px">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">
            任务已暂停，等待人工处理。确认完成后任务将从当前步骤续跑。
          </el-alert>
          <div v-if="pendingIntervention">
            <p><strong>步骤 #{{ pendingIntervention.step_index }}</strong></p>
            <p class="iv-prompt">{{ pendingIntervention.prompt }}</p>
            <div style="margin-top:12px;display:flex;gap:8px">
              <el-button type="primary" :loading="resolving" @click="resolveIntervention('continue')">确认继续</el-button>
              <el-button type="warning" :loading="resolving" @click="resolveIntervention('skip')">跳过此步</el-button>
              <el-button type="danger" plain :loading="resolving" @click="cancelIntervention">取消任务</el-button>
            </div>
          </div>
          <el-empty v-else description="暂无待处理介入记录" />
        </AppCard>

        <AppCard v-if="defectInfo?.defect_id || defectInfo?.perf_metrics" title="缺陷与性能" :hover="false" style="margin-top:20px">
          <el-descriptions :column="1" border>
            <el-descriptions-item v-if="defectInfo?.defect_id" label="关联缺陷">{{ defectInfo.defect_id }}</el-descriptions-item>
            <el-descriptions-item v-if="defectInfo?.perf_metrics" label="性能指标">
              <pre class="snap-pre">{{ JSON.stringify(defectInfo.perf_metrics, null, 2) }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </AppCard>

        <AppCard v-if="failureSnapshots.length" title="异常现场快照" :hover="false" style="margin-top:20px">
          <el-collapse>
            <el-collapse-item v-for="snap in failureSnapshots" :key="snap.id" :title="`快照 #${snap.id} · 执行 #${snap.execution_id || '-'}`">
              <div v-if="snap.crash_log" class="snap-block">
                <div class="snap-label">崩溃日志</div>
                <pre class="snap-pre">{{ formatCrashLog(snap.crash_log) }}</pre>
              </div>
              <div v-if="snap.snapshot_json" class="snap-block">
                <div class="snap-label">现场信息</div>
                <pre class="snap-pre">{{ formatSnapshotJson(snap.snapshot_json) }}</pre>
              </div>
              <p v-if="snap.screenshot_path" class="snap-path">截图：{{ snap.screenshot_path }}</p>
            </el-collapse-item>
          </el-collapse>
        </AppCard>

        <AppCard title="脚本内容" :hover="false" style="margin-top:20px">
          <pre class="script-content">{{ task?.script_content }}</pre>
          <el-alert type="info" :closable="false" style="margin-top:12px" show-icon>
            脚本中可使用 <code>from atp_controls import get, get_locator</code> 读取已解析的控件定位
          </el-alert>
        </AppCard>
      </el-col>

      <el-col :span="8">
        <AppCard v-if="isRunning" title="实时状态" :hover="false">
          <el-alert type="info" :closable="false" show-icon>
            任务执行中，页面每 3 秒自动刷新。可打开设备投屏同步观察。
          </el-alert>
          <div v-if="currentFailedStep" class="step-fail-hint">
            失败步骤：<strong>#{{ currentFailedStep }}</strong>
          </div>
        </AppCard>

        <AppCard
          title="实时日志"
          :hover="false"
          class="live-log-card"
          :style="{ marginTop: isRunning ? '16px' : '0' }"
        >
          <div class="live-log-toolbar">
            <el-input v-model="logKeyword" placeholder="关键词检索" clearable style="flex:1" @keyup.enter="searchLogs" />
            <el-button size="small" @click="searchLogs">检索</el-button>
            <el-button size="small" @click="logKeyword=''; loadData()">重置</el-button>
          </div>
          <el-scrollbar class="live-log-scroll">
            <el-timeline v-if="logs.length">
              <el-timeline-item v-for="log in logs" :key="log.id" :type="log.level === 'error' ? 'danger' : 'primary'" :timestamp="fmtTime(log.created_at)">
                [{{ log.log_type }}] {{ log.message }}
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无日志" :image-size="56" />
          </el-scrollbar>
        </AppCard>

        <AppCard title="执行实例" :hover="false" style="margin-top:16px">
          <div v-for="exec in executions" :key="exec.id" class="exec-item">
            <div class="exec-header">
              <span>设备 #{{ exec.device_id }}</span>
              <el-tag :type="taskStatusMap[exec.status]?.type" size="small" effect="light">
                {{ taskStatusMap[exec.status]?.label || exec.status }}
              </el-tag>
            </div>
            <div class="exec-meta">
              <span v-if="exec.failed_step_index">失败步骤 #{{ exec.failed_step_index }}</span>
              <span v-if="exec.started_at">开始 {{ fmtTime(exec.started_at) }}</span>
            </div>
            <div class="exec-actions">
              <el-button
                v-if="exec.device_id"
                link
                type="primary"
                size="small"
                @click="$router.push(`/devices/${exec.device_id}/screen`)"
              >
                打开投屏
              </el-button>
            </div>
          </div>
          <el-empty v-if="!executions.length" description="暂无执行实例" />
        </AppCard>

        <AppCard v-if="visualSteps.length" title="步骤进度" :hover="false" style="margin-top:16px">
          <div
            v-for="(step, idx) in visualSteps"
            :key="idx"
            class="vstep"
            :class="stepClass(idx)"
          >
            <span class="vstep-no">{{ idx + 1 }}</span>
            <span class="vstep-type">{{ step.type }}</span>
            <span class="vstep-desc">{{ stepSummary(step) }}</span>
          </div>
        </AppCard>

        <AppCard title="调试指引" :hover="false" style="margin-top:16px">
          <ol class="debug-guide">
            <li>点击「打开投屏」在设备上实时观察执行过程</li>
            <li>失败后查看右侧实时日志与异常快照，定位失败步骤</li>
            <li>修改用例步骤后，使用「断点续跑」从失败处继续</li>
            <li>可视化用例可点「编辑用例」返回步骤编辑器</li>
          </ol>
        </AppCard>
      </el-col>
    </el-row>

    <el-dialog v-model="showFailurePlayer" title="失败片段回放" width="900px" destroy-on-close @close="closeFailurePlayer">
      <RecordingPlayer v-if="failurePlaybackUrl" ref="failurePlayerRef" :src="failurePlaybackUrl" />
    </el-dialog>

    <el-dialog v-model="showBindingDialog" title="添加私有控件绑定" width="560px" destroy-on-close>
      <el-form :model="bindingForm" label-width="100px">
        <el-form-item label="步骤序号">
          <el-input-number v-model="bindingForm.step_index" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="控件名称" required>
          <el-input v-model="bindingForm.element_name" placeholder="如 login_button" />
        </el-form-item>
        <el-form-item label="绑定方式">
          <el-radio-group v-model="bindingForm.mode">
            <el-radio value="private">私有定位</el-radio>
            <el-radio value="pool">引用控件池</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="bindingForm.mode === 'private'">
          <el-form-item label="定位类型">
            <el-select v-model="bindingForm.locator_type" style="width:100%">
              <el-option label="ID" value="id" />
              <el-option label="XPath" value="xpath" />
              <el-option label="Accessibility" value="accessibility" />
            </el-select>
          </el-form-item>
          <el-form-item label="定位表达式" required>
            <el-input v-model="bindingForm.locator_value" type="textarea" :rows="2" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="池条目 ID">
            <el-input-number v-model="bindingForm.pool_id" :min="1" style="width:100%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showBindingDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBinding">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, controlApi, checkpointApi, interventionApi, recordingApi, fetchTaskMonitorBundle } from '@/api'
import { taskStatusMap, formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import RecordingPlayer from '@/components/RecordingPlayer.vue'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id
const loading = ref(false)
const task = ref(null)
const executions = ref([])
const logs = ref([])
const logKeyword = ref('')
const visualSteps = ref([])
const sourceCaseId = ref(null)

const isRunning = computed(() =>
  ['running', 'queued', 'waiting_manual'].includes(task.value?.status)
)

const currentFailedStep = computed(() => {
  const exec = executions.value.find(e => e.failed_step_index)
  return exec?.failed_step_index || null
})

function stepSummary(step) {
  if (step.type === 'click') return step.element_name || ''
  if (step.type === 'wait') return `${step.seconds || 0}s`
  if (step.type === 'input') return step.text || ''
  return step.element_name || step.app_package || ''
}

function stepClass(idx) {
  const fail = currentFailedStep.value
  if (fail != null && idx + 1 === fail) return 'failed'
  if (fail != null && idx + 1 < fail && task.value?.status === 'failed') return 'passed'
  if (isRunning.value && fail == null && idx === 0) return 'active'
  return ''
}

function parseVisualSteps(t) {
  visualSteps.value = []
  sourceCaseId.value = null
  if (!t || t.script_type !== 'visual') return
  try {
    const parsed = JSON.parse(t.script_content || '{}')
    visualSteps.value = (parsed.steps || []).filter(s => s.enabled !== false)
  } catch { /* ignore */ }
  if (t.source_case_id) sourceCaseId.value = t.source_case_id
}

async function searchLogs() {
  const res = await taskApi.logs(taskId, logKeyword.value ? { keyword: logKeyword.value } : {})
  logs.value = res.data
}
const bindings = ref([])
const healingRecords = ref([])
const showBindingDialog = ref(false)
const pendingIntervention = ref(null)
const resolving = ref(false)
const failureSnapshots = ref([])
const defectInfo = ref(null)
const failurePlaybackInfo = ref(null)
const showFailurePlayer = ref(false)
const failurePlaybackUrl = ref('')
const failurePlayerRef = ref(null)
let pollTimer = null

const bindingForm = reactive({
  step_index: 1,
  element_name: '',
  mode: 'private',
  locator_type: 'id',
  locator_value: '',
  pool_id: null
})

function openBindingDialog() {
  Object.assign(bindingForm, {
    step_index: 1, element_name: '', mode: 'private',
    locator_type: 'id', locator_value: '', pool_id: null
  })
  showBindingDialog.value = true
}

async function loadBindings() {
  const [bindRes, healRes] = await Promise.all([
    controlApi.getPrivateBindings(taskId),
    controlApi.getHealingRecords(taskId)
  ])
  bindings.value = bindRes.data
  healingRecords.value = healRes.data
}

async function loadDefectInfo() {
  try {
    const res = await taskApi.defectInfo(taskId)
    defectInfo.value = res.data
  } catch {
    defectInfo.value = null
  }
}

async function loadFailureSnapshots() {
  try {
    const res = await taskApi.failureSnapshots(taskId)
    failureSnapshots.value = res.data || []
  } catch {
    failureSnapshots.value = []
  }
}

function formatCrashLog(raw) {
  try {
    const obj = JSON.parse(raw)
    const lines = [...(obj.fatal_lines || []), ...(obj.anr_lines || [])]
    return lines.length ? lines.join('\n') : raw.slice(0, 2000)
  } catch {
    return raw?.slice(0, 2000) || ''
  }
}

function formatSnapshotJson(raw) {
  try {
    const obj = JSON.parse(raw)
    return [
      obj.process_info ? '【进程】\n' + obj.process_info : '',
      obj.memory_info ? '【内存】\n' + obj.memory_info.slice(0, 1500) : '',
      obj.ui_tree ? '【控件树摘要】长度 ' + (obj.ui_tree_len || obj.ui_tree.length) : ''
    ].filter(Boolean).join('\n\n')
  } catch {
    return raw?.slice(0, 2000) || ''
  }
}

async function loadInterventions() {
  const res = await interventionApi.byTask(taskId)
  pendingIntervention.value = (res.data || []).find(i => i.status === 'pending') || null
}

async function resolveIntervention(action) {
  if (!pendingIntervention.value) return
  resolving.value = true
  try {
    const res = await interventionApi.resolve(pendingIntervention.value.id, { action })
    ElMessage.success(`已${action === 'skip' ? '跳过' : '确认'}，续跑任务 #${res.data.task_id}`)
    await loadData()
  } finally {
    resolving.value = false
  }
}

async function cancelIntervention() {
  if (!pendingIntervention.value) return
  await ElMessageBox.confirm('确定取消该任务？', '确认', { type: 'warning' })
  resolving.value = true
  try {
    await interventionApi.cancel(pendingIntervention.value.id)
    ElMessage.success('任务已取消')
    await loadData()
  } finally {
    resolving.value = false
  }
}

async function loadFailurePlayback() {
  if (!task.value || !task.value.enable_recording || !['failed', 'timeout'].includes(task.value.status)) {
    failurePlaybackInfo.value = null
    return
  }
  try {
    const res = await taskApi.failurePlayback(taskId)
    failurePlaybackInfo.value = res.data?.recording_id ? res.data : null
  } catch {
    failurePlaybackInfo.value = null
  }
}

async function openFailurePlayback() {
  const info = failurePlaybackInfo.value
  if (!info?.recording_id) {
    ElMessage.warning('无可用录屏')
    return
  }
  failurePlaybackUrl.value = await recordingApi.playbackStream(info.recording_id)
  showFailurePlayer.value = true
  await nextTick()
  const offset = Math.max(0, (info.video_offset_ms || 0) - 2000)
  failurePlayerRef.value?.seekToMs(offset)
}

function closeFailurePlayer() {
  if (failurePlaybackUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(failurePlaybackUrl.value)
  }
  failurePlaybackUrl.value = ''
}

async function loadData() {
  loading.value = true
  try {
    const bundle = await fetchTaskMonitorBundle(taskId)
    if (!bundle) {
      stopPolling()
      ElMessage.warning('任务不存在或已删除')
      router.replace('/tasks')
      return
    }
    task.value = bundle.task
    executions.value = bundle.executions
    logs.value = bundle.logs
    parseVisualSteps(task.value)
    await Promise.all([loadBindings(), loadInterventions(), loadFailureSnapshots(), loadDefectInfo(), loadFailurePlayback()])
  } finally {
    loading.value = false
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function saveBinding() {
  const payload = {
    task_id: Number(taskId),
    step_index: bindingForm.step_index,
    element_name: bindingForm.element_name
  }
  if (bindingForm.mode === 'pool') {
    payload.locator_type = 'pool'
    payload.pool_id = bindingForm.pool_id
    payload.locator_value = ''
  } else {
    payload.locator_type = bindingForm.locator_type
    payload.locator_value = bindingForm.locator_value
  }
  await controlApi.createPrivateBinding(payload)
  ElMessage.success('私有控件绑定已保存')
  showBindingDialog.value = false
  loadBindings()
}

async function deleteBinding(row) {
  await ElMessageBox.confirm('确定删除该私有控件绑定？不会影响全局控件池。', '确认', { type: 'warning' })
  await controlApi.deletePrivateBinding(row.id)
  ElMessage.success('已删除')
  loadBindings()
}

async function testResolve(row) {
  try {
    const res = await controlApi.resolve({
      task_id: Number(taskId),
      step_index: row.step_index,
      element_name: row.element_name,
      app_package: task.value?.app_package
    })
    const rc = res.data
    ElMessage.success(`解析成功 [${rc.source}]: ${rc.locator_type} = ${rc.locator_value}`)
  } catch {
    /* error handled by interceptor */
  }
}

async function exportOffline() {
  const res = await taskApi.offlinePackage(taskId)
  const filename = res.data.filename
  await taskApi.downloadOfflinePackage(filename)
  ElMessage.success(`离线包已下载 (${Math.round((res.data.size_bytes || 0) / 1024)} KB)`)
}

async function resumeTask() {
  const failedExec = executions.value.find(e => e.failed_step_index)
  const fromStep = failedExec?.failed_step_index
  const res = await checkpointApi.resumeTask(taskId, fromStep)
  ElMessage.success(`续跑任务已创建 #${res.data.id}${fromStep ? '，从步骤 ' + fromStep : ''}`)
}

function openDebugWorkbench() {
  if (!sourceCaseId.value) return
  const exec = executions.value.find(e => e.device_id)
  router.push({
    path: `/cases/${sourceCaseId.value}/debug`,
    query: {
      taskId: taskId,
      deviceId: exec?.device_id || undefined
    }
  })
}

onMounted(() => {
  loadData()
  pollTimer = setInterval(() => {
    if (isRunning.value) loadData()
  }, 3000)
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.live-log-card {
  position: sticky;
  top: 12px;
  z-index: 2;
}
.live-log-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.live-log-scroll {
  max-height: calc(100vh - 280px);
  min-height: 280px;
  padding-right: 4px;
}
.script-content {
  background: var(--atp-code-bg);
  color: var(--atp-screen-text);
  padding: 16px;
  border-radius: var(--atp-radius-md, 12px);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.exec-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--atp-border-light, #eee);
}
.exec-item:last-child {
  border-bottom: none;
}
.exec-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.exec-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--atp-text-secondary);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.exec-actions {
  margin-top: 8px;
}
.step-fail-hint {
  margin-top: 10px;
  font-size: 13px;
  color: #c8875e;
}
.failure-playback-meta {
  font-size: 13px;
  color: var(--atp-text-secondary);
  margin-bottom: 8px;
}
.failure-playback-msg {
  font-size: 13px;
  color: #c45656;
  margin-bottom: 12px;
}
.vstep {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--atp-border-light, #eee);
  font-size: 13px;
}
.vstep:last-child { border-bottom: none; }
.vstep.failed { background: var(--atp-danger-bg); border-radius: 6px; padding: 8px; }
.vstep.passed { opacity: 0.55; }
.vstep.active { background: var(--atp-primary-bg); border-radius: 6px; padding: 8px; }
.vstep-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--atp-primary);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.vstep-type { font-weight: 600; min-width: 56px; }
.vstep-desc { color: var(--atp-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.debug-guide {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--atp-text-secondary);
}
code {
  background: var(--atp-primary-bg);
  color: var(--atp-primary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.iv-prompt {
  margin: 8px 0 0;
  padding: 12px;
  background: var(--atp-warning-bg);
  border-radius: 8px;
  color: #c8875e;
  line-height: 1.6;
}
.snap-block { margin-bottom: 12px; }
.snap-label { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.snap-pre {
  background: var(--atp-code-bg);
  color: var(--atp-screen-text);
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
  margin: 0;
}
.snap-path { font-size: 12px; color: var(--atp-text-secondary); }
.heal-title { font-size: 13px; color: var(--atp-text-secondary); margin-bottom: 8px; }
</style>
