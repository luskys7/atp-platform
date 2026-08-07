<template>
  <div class="page-container screen-page">
    <PageHeader
      :title="`远程投屏 · ${device?.name || device?.serial_number || `#${deviceId}`}`"
      subtitle="实时查看设备屏幕并远程操控"
    >
      <template #actions>
        <el-tag v-if="connected" type="success" size="small" effect="light">已连接</el-tag>
        <el-tag v-else-if="connecting" type="warning" size="small" effect="light">连接中</el-tag>
        <el-tag v-else type="info" size="small" effect="light">未连接</el-tag>
        <el-tag v-if="connected && streamMode === 'scrcpy'" type="success" size="small" effect="plain" style="margin-left:8px">
          scrcpy 低延迟
        </el-tag>
        <el-tag v-else-if="connected && (streamMode === 'jpeg' || streamMode === 'adb')" type="warning" size="small" effect="plain" style="margin-left:8px">
          adb 慢速
        </el-tag>
        <el-tag v-if="connected && fps > 0" type="info" size="small" effect="plain" style="margin-left:8px">
          {{ fps }} FPS · ~{{ latencyMs }}ms
        </el-tag>
        <el-button style="margin-left:12px" @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <div class="screen-layout">
      <aside class="screen-panel">
        <div class="screen-wrap" ref="screenWrapRef">
          <div class="screen-device">
            <div class="screen-frame" :style="screenFrameStyle">
              <canvas
                ref="canvasRef"
                class="screen-canvas"
                @mousedown.prevent="onMouseDown"
                @mouseup.prevent="onMouseUp"
              />
              <div
                v-if="cropSelecting"
                class="crop-overlay"
                @mousedown.prevent="onCropDown"
                @mousemove.prevent="onCropMove"
                @mouseup.prevent="onCropUp"
              >
                <div v-if="cropPreview" class="crop-box" :style="cropBoxStyle" />
              </div>
              <div v-if="!hasFrame" class="screen-placeholder" :class="{ connected: connected }">
                <el-icon :size="48"><Monitor /></el-icon>
                <p>{{ statusText }}</p>
                <p class="frame-dim">{{ resolutionLabel }}</p>
                <el-button v-if="!connecting && !connected" type="primary" @click="connectStream">开始投屏</el-button>
              </div>
              <div v-if="connected && !hasFrame" class="screen-loading">
                <el-icon class="spin"><Loading /></el-icon>
                <span>等待画面...</span>
              </div>
            </div>
            <ScreenNavBar :disabled="!connected" @key="pressNavKey" />
          </div>
        </div>
      </aside>

      <section class="ops-panel">
        <AppCard title="投屏控制" :hover="false">
          <div class="screen-toolbar">
            <el-button type="primary" :disabled="connected" :loading="connecting" @click="connectStream">连接</el-button>
            <el-button :disabled="!connected" @click="stopStream">断开</el-button>
            <span class="hint">单击点击 · 拖拽滑动 · 切换页面不断开</span>
          </div>
        </AppCard>

        <AppCard title="设备信息" :hover="false">
          <el-descriptions :column="1" border v-if="device" size="small">
            <el-descriptions-item label="设备名称">{{ device.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="序列号">{{ device.serial_number || '-' }}</el-descriptions-item>
            <el-descriptions-item label="平台">{{ device.platform || '-' }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ device.model || '-' }}</el-descriptions-item>
            <el-descriptions-item label="系统">{{ androidVersion(device) }}</el-descriptions-item>
            <el-descriptions-item label="分辨率">{{ resolutionLabel }}</el-descriptions-item>
            <el-descriptions-item label="电量">{{ device.battery_level != null ? device.battery_level + '%' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ deviceStatusLabel(device.status) }}</el-descriptions-item>
          </el-descriptions>
        </AppCard>

        <AppCard
          v-if="connected && (manualPickActive || manualPickRecordId)"
          title="手动选控件"
          :hover="false"
        >
          <p class="record-hint">
            在左侧画面点击目标区域，系统会识别控件；识别失败时可手动填写 Resource ID / 文本 / XPath / OCR。
          </p>
          <div v-if="manualPickStepIndex != null" class="pick-target-hint">
            正在为审阅步骤 <strong>#{{ manualPickStepIndex + 1 }}</strong> 补定位
          </div>
          <div class="pick-toolbar">
            <el-switch v-model="manualPickPreviewTap" active-text="同步点击设备" />
            <el-button size="small" :loading="pickInspectLoading" @click="warmPickCache">刷新 UI 树</el-button>
            <el-button v-if="manualPickReturnTo" size="small" type="primary" @click="finishManualPick">完成并返回审阅</el-button>
            <el-button v-else size="small" @click="exitManualPick">退出选控件</el-button>
          </div>
          <p v-if="pickInspectLoading" class="pick-loading">识别中，请稍候…</p>
        </AppCard>

        <div class="ops-row-pair">
          <AppCard title="操作录制" :hover="false" class="ops-row-item">
            <p class="record-hint">一键开启后同步录制视频与操作步骤，支持暂停、切点与溯源水印</p>
            <div v-if="recordingV2 && !recording" class="record-mode-row">
              <span class="mode-label">录制范围</span>
              <el-radio-group v-model="recordMode" size="small">
                <el-radio-button value="full">全屏</el-radio-button>
                <el-radio-button value="window">窗口</el-radio-button>
                <el-radio-button value="crop">选区</el-radio-button>
              </el-radio-group>
              <el-button v-if="recordMode === 'crop' && cropRectNorm" size="small" link type="primary" @click="resetCrop">重选区域</el-button>
              <span v-if="recordMode === 'window'" class="hint">窗口模式自动裁剪主内容区</span>
            </div>
            <p v-if="cropSelecting" class="record-hint crop-hint">请在左侧投屏画面拖拽框选录制区域，完成后点「确认选区」</p>
            <div v-if="!recording" class="record-actions">
              <el-button type="warning" :disabled="!connected" @click="promptStartRecording">开始录制</el-button>
              <el-button v-if="cropSelecting" type="primary" :disabled="!cropRectNorm" @click="confirmCropAndStart">确认选区</el-button>
              <el-switch v-model="watermarkEnabled" active-text="水印" style="margin-left:12px" />
              <el-switch
                v-model="desensitizeEnabled"
                active-text="脱敏"
                :disabled="desensitizeLocked"
                style="margin-left:8px"
              />
              <el-switch
                v-if="recordingV2"
                v-model="recordNavKeys"
                active-text="录导航键"
                style="margin-left:8px"
                title="记录 Back/多任务/菜单，不含 Home"
              />
            </div>
            <template v-else>
              <div class="record-live">
                <el-tag type="danger" effect="dark">{{ recordingPaused ? '已暂停' : '录制中' }}</el-tag>
                <span class="record-stat">{{ liveDuration }} · {{ liveSize }} · {{ recordStepCount }} 步 · {{ videoRecorder.effectiveFps.value }}fps · {{ perfGradeLabel }}</span>
                <el-tag v-if="liveRecognitionStats.total" size="small" type="info" effect="plain">
                  定位 {{ liveRecognitionStats.hit }}/{{ liveRecognitionStats.total }}
                </el-tag>
              </div>
              <div v-if="liveRecentSteps.length" class="live-steps">
                <div
                  v-for="(s, idx) in liveRecentSteps"
                  :key="s.id"
                  class="live-step-row"
                  :class="{ warn: s.unrecognized, ok: s.locator_valid }"
                >
                  <span class="live-step-no">{{ recordStepCount - liveRecentSteps.length + idx + 1 }}</span>
                  <div class="live-step-body">
                    <span class="live-step-text">{{ s.target }}</span>
                    <span v-if="s.locator" class="live-step-locator">{{ s.locator }}</span>
                    <span v-else-if="s.pending" class="live-step-locator pending">识别中…</span>
                  </div>
                  <el-tag v-if="s.locator_valid" size="small" type="success">已定位</el-tag>
                  <el-tag v-else-if="s.unrecognized" size="small" type="warning">坐标</el-tag>
                </div>
              </div>
              <div class="record-actions" style="margin-top:8px">
                <el-button v-if="!recordingPaused" size="small" @click="pauseRecording">暂停</el-button>
                <el-button v-else size="small" type="warning" @click="resumeRecording">继续</el-button>
                <el-button size="small" @click="markSegment">标记切点</el-button>
                <el-button
                  size="small"
                  :type="manualPickActive ? 'primary' : 'default'"
                  @click="toggleManualPickDuringRecording"
                >
                  {{ manualPickActive ? '选控件中…' : '手动选控件' }}
                </el-button>
                <el-button type="primary" size="small" :loading="savingCase" @click="finishRecording">结束审阅</el-button>
                <el-button size="small" @click="cancelRecording">取消</el-button>
              </div>
            </template>
            <div v-if="segmentCount" class="record-count">已标记 {{ segmentCount }} 个切点</div>
          </AppCard>

          <AppCard title="快捷输入" :hover="false" class="ops-row-item">
            <p class="input-hint">
              <template v-if="lastTapPoint">已选位置 ({{ lastTapPoint.x }}, {{ lastTapPoint.y }})，请点在顶部搜索框/输入框内</template>
              <template v-else>请先在投屏画面点击搜索框或输入框（不要点列表项或底部按钮）</template>
            </p>
            <el-input v-model="inputText" placeholder="输入文本发送到设备" @keyup.enter="sendText" />
            <el-button class="send-btn" type="primary" :disabled="!connected || !lastTapPoint" @click="sendText">发送</el-button>
          </AppCard>
        </div>

        <AppCard v-if="showDebugPanel" title="整用例调试" :hover="false" class="debug-card">
          <p class="debug-hint">自动执行全部 {{ debugSteps.length }} 步，无需逐步操作</p>
          <div class="debug-toolbar">
            <el-tag v-if="debugTask" type="primary" size="small">任务 #{{ debugTask.id }}</el-tag>
            <el-tag v-if="debugTask" :type="debugTask.status === 'passed' ? 'success' : debugTask.status === 'failed' ? 'danger' : 'warning'" size="small">
              {{ debugTask.status }}
            </el-tag>
            <el-button v-if="!debugTask" type="warning" size="small" :loading="debugRunning" @click="runInlineDebug">立即调试执行</el-button>
            <el-button size="small" @click="goEditor">编辑步骤</el-button>
            <el-button size="small" plain @click="showDebugPanel = false">收起</el-button>
          </div>
          <div class="debug-split">
            <div class="debug-steps">
              <div class="debug-subhead">步骤 ({{ debugSteps.length }})</div>
              <el-scrollbar max-height="220px">
                <div v-for="(step, idx) in debugSteps" :key="idx" class="debug-step-row" :class="debugStepClass(idx)">
                  <span class="step-no">{{ idx + 1 }}</span>
                  <span class="step-text">{{ step.type }} {{ stepLabel(step) }}</span>
                </div>
              </el-scrollbar>
            </div>
            <div class="debug-logs">
              <div class="debug-subhead">实时日志</div>
              <el-scrollbar max-height="220px">
                <div v-for="log in debugLogs.slice(-40)" :key="log.id" class="debug-log-line" :class="log.level">
                  <span class="log-time">{{ (log.created_at || '').slice(11, 19) }}</span>
                  {{ log.message }}
                </div>
                <el-empty v-if="!debugLogs.length" description="等待日志..." :image-size="48" />
              </el-scrollbar>
            </div>
          </div>
        </AppCard>
      </section>
    </div>

    <el-dialog v-model="showRecordMetaDialog" title="录制归档标签" width="480px" :close-on-click-modal="false">
      <p class="record-hint">填写模块与版本信息，便于后续检索与溯源（将记住上次输入）</p>
      <el-form :model="recordMetaForm" label-width="88px">
        <el-form-item label="模块名称">
          <el-input v-model="recordMetaForm.module_name" placeholder="如 登录模块" />
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input v-model="recordMetaForm.version_label" placeholder="如 v2.3.1" />
        </el-form-item>
        <el-form-item label="项目编码">
          <el-input v-model="recordMetaForm.project_code" placeholder="如 PRJ-ATP" />
        </el-form-item>
        <el-form-item label="关联任务">
          <el-input v-model="recordMetaForm.task_id" placeholder="可选，测试任务 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRecordMetaDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmRecordMeta">开始录制</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDenoiseDialog" title="录制降噪预览" width="720px">
      <div v-if="denoisePreview" class="denoise-summary">
        <el-tag type="info">原始 {{ denoisePreview.original_count }} 步</el-tag>
        <el-tag type="success" style="margin-left:8px">降噪后 {{ denoisePreview.cleaned_count }} 步</el-tag>
        <el-tag v-if="denoisePreview.removed_count > 0" type="warning" style="margin-left:8px">移除 {{ denoisePreview.removed_count }} 步</el-tag>
      </div>
      <el-row :gutter="12" style="margin-top:12px" v-if="denoisePreview">
        <el-col :span="12">
          <div class="denoise-col-title">原始步骤</div>
          <el-scrollbar max-height="280px">
            <div v-for="(s, i) in denoisePreview.original_steps" :key="'o'+i" class="denoise-step">{{ i + 1 }}. {{ s.type }} {{ stepLabel(s) }}</div>
          </el-scrollbar>
        </el-col>
        <el-col :span="12">
          <div class="denoise-col-title">降噪后</div>
          <el-scrollbar max-height="280px">
            <div v-for="(s, i) in denoisePreview.cleaned_steps" :key="'c'+i" class="denoise-step">{{ i + 1 }}. {{ s.type }} {{ stepLabel(s) }}</div>
          </el-scrollbar>
        </el-col>
      </el-row>
      <el-form style="margin-top:12px">
        <el-form-item label="应用降噪">
          <el-switch v-model="applyDenoise" />
          <span class="denoise-hint">合并连续点击、移除无效 wait 步骤</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDenoiseDialog = false" :disabled="savingCase">取消</el-button>
        <el-button type="primary" :loading="savingCase" @click="confirmFinishRecording">确认生成用例</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRecoveryDialog" title="发现未完成的录制" width="480px" :close-on-click-modal="false">
      <p>检测到上次录制因异常中断，是否恢复并上传？</p>
      <p class="recovery-meta">会话 #{{ recoveryDraft?.session_id }} · {{ recoveryDraft?.step_count || 0 }} 步 · {{ draftReasonLabel(recoveryDraft?.reason) }}</p>
      <template #footer>
        <el-button @click="discardRecovery">丢弃</el-button>
        <el-button type="primary" :loading="recovering" @click="recoverDraft">
          {{ recoveryDraft?.reason === 'upload_failed' ? '重试上传' : '恢复审阅' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPickDialog" title="确认控件定位" width="520px" destroy-on-close>
      <div v-if="pickInspectResult" class="pick-dialog-body">
        <p class="pick-coord">坐标 ({{ pickInspectResult.x }}, {{ pickInspectResult.y }})</p>
        <el-alert
          v-if="!pickInspectResult.valid && !manualLocatorForm.locator_value && !manualLocatorForm.ocr_text"
          type="warning"
          :closable="false"
          show-icon
          title="未自动识别到稳定控件"
          description="可在下方手动填写定位，或使用 OCR 文本定位（适用于纯图片按钮）。"
          style="margin-bottom:12px"
        />
        <el-form label-width="96px" size="small">
          <el-form-item label="显示名称">
            <el-input v-model="manualLocatorForm.display_name" placeholder="如：登录按钮" />
          </el-form-item>
          <el-form-item label="元素名">
            <el-input v-model="manualLocatorForm.element_name" placeholder="脚本变量名，如 login_btn" />
          </el-form-item>
          <el-form-item v-if="pickCandidateLocators.length" label="识别结果">
            <el-radio-group v-model="manualLocatorForm.selectedKey" class="pick-locator-list">
              <el-radio
                v-for="item in pickCandidateLocators"
                :key="item.key + item.value"
                :value="item.key"
              >
                {{ locatorTypeLabel(item.key) }} · {{ item.value }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-divider content-position="left">手动定位</el-divider>
          <el-form-item label="定位方式">
            <el-select v-model="manualLocatorForm.locator_type" style="width:100%">
              <el-option label="Resource ID" value="id" />
              <el-option label="文本" value="text" />
              <el-option label="Content-Desc" value="content_desc" />
              <el-option label="XPath" value="xpath" />
              <el-option label="Class Name" value="class_name" />
            </el-select>
          </el-form-item>
          <el-form-item label="定位值">
            <el-input v-model="manualLocatorForm.locator_value" placeholder="如 com.app:id/btn 或 登录" />
          </el-form-item>
          <el-form-item label="OCR 文本">
            <el-input v-model="manualLocatorForm.ocr_text" placeholder="纯图片/Canvas 页面可填可见文字" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showPickDialog = false">取消</el-button>
        <el-button type="primary" :loading="pickSaving" @click="applyManualLocator">应用定位</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deviceApi, recordApi, caseApi, fetchTaskMonitorBundle } from '@/api'
import {
  useScreenStream, setScreenDeviceInfo, syncActiveScreenSessions, captureSessionSnapshot
} from '@/composables/useScreenStream'
import { useVideoRecorder, WINDOW_CROP_RECT } from '@/composables/useVideoRecorder'
import { useOperationRecording, operationRecordingState, registerRecordingActions, unregisterRecordingActions } from '@/composables/useOperationRecording'
import {
  persistEmergencyDraft, saveDraftBlob, loadDraftBlob, loadDraftMeta, clearDraft,
  draftReasonLabel, uploadDraftVideo,
  captureCanvasSnapshot, captureCanvasThumb
} from '@/composables/useRecordingRecovery'
import { consumeStartupMs, markRecordingBoot } from '@/composables/useRecordingStartup'
import { desensitizeText, containsSensitive, blurRegionForKeyboard, blurRegionForInput } from '@/utils/desensitize'
import { useRecordingFeatures } from '@/composables/useRecordingFeatures'

const { features: recordingFeatures, loadFeatures: loadRecordingFeatures } = useRecordingFeatures()
const recordingV2 = computed(() => recordingFeatures.value.recording_v2 !== false)
import { fixedScreenFrameStyle, fixedScreenFrameStyleInBox, frameSizeFromDevice, androidVersionLabel, frameMaxHeight, NAV_BAR_HEIGHT } from '@/composables/screenFrameStyle'
import { createScreenCanvasRenderer } from '@/composables/useScreenCanvas'
import { parseTaskStepProgress, stepStatus } from '@/composables/useTaskStepProgress'
import { deviceStatusMap } from '@/utils/status'
import { normalizeDevice } from '@/utils/device'
import { formatStepTarget, formatStepLocator, formatLocatorType } from '@/utils/stepDisplay'
import { useUserStore } from '@/stores/user'
import ScreenNavBar from '@/components/ScreenNavBar.vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'

defineOptions({ name: 'DeviceScreen' })

const RECORD_META_KEY = 'atp_record_meta'
const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'
const RECORD_FAST_START_KEY = 'atp_record_fast_start'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const deviceId = route.params.id
const device = ref(null)
const canvasRef = ref(null)
const screenWrapRef = ref(null)
const hasFrame = ref(false)
const inputText = ref('')
const recording = ref(false)
const recordingPaused = ref(false)
const recordSessionId = ref(null)
const recordStepCount = ref(0)
const liveRecentSteps = ref([])
const segmentCount = ref(0)
const watermarkEnabled = ref(true)
const desensitizeEnabled = ref(true)
const desensitizeLocked = ref(false)
const showRecoveryDialog = ref(false)
const recoveryDraft = ref(null)
const recovering = ref(false)
const showDenoiseDialog = ref(false)
const denoisePreview = ref(null)
const applyDenoise = ref(true)
const savingCase = ref(false)
const showRecordMetaDialog = ref(false)
const recordMetaForm = reactive({ module_name: '', version_label: '', project_code: '', task_id: '' })
const recordNavKeys = ref(false)
const NAV_KEY_LABELS = { back: '返回', recent: '多任务', menu: '菜单', home: '主屏幕' }
const NAV_RECORDABLE_KEYS = new Set(['back', 'recent', 'menu'])
const recordMode = ref('full')
const cropRectNorm = ref(null)
const cropSelecting = ref(false)
const cropPreview = ref(null)
let cropDragStart = null
let emergencyHandling = false
const { reset: resetRecordingState, syncFromSession } = useOperationRecording()
const videoRecorder = useVideoRecorder()
const liveDuration = computed(() => videoRecorder.formattedDuration.value)
const liveSize = computed(() => videoRecorder.formattedSize.value)
const perfGradeLabel = computed(() => {
  const g = videoRecorder.performanceGrade.value
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
})

function resolveRecordCropRect() {
  if (recordMode.value === 'crop') return cropRectNorm.value
  if (recordMode.value === 'window') return { ...WINDOW_CROP_RECT }
  return null
}
let statusSyncTimer = null
let autosaveTimer = null
let recordingStartupMs = null
const showDebugPanel = ref(false)
const createdCaseId = ref(null)
const debugCaseAppPackage = ref('')
const createdCaseSteps = ref(0)
const debugRunning = ref(false)
const debugTask = ref(null)
const debugSteps = ref([])
const debugLogs = ref([])
const debugExecutions = ref([])
let debugPollTimer = null

const debugStepProgress = computed(() =>
  parseTaskStepProgress(debugLogs.value, debugExecutions.value)
)

function debugStepClass(idx) {
  return stepStatus(idx, debugStepProgress.value, debugTask.value?.status)
}

async function loadDebugTaskData() {
  if (!debugTask.value?.id) return
  const bundle = await fetchTaskMonitorBundle(debugTask.value.id)
  if (!bundle) {
    stopDebugPolling()
    debugTask.value = null
    return
  }
  debugTask.value = bundle.task
  debugExecutions.value = bundle.executions
  debugLogs.value = bundle.logs
}

function stopDebugPolling() {
  if (debugPollTimer) {
    clearInterval(debugPollTimer)
    debugPollTimer = null
  }
}

function startDebugPolling() {
  stopDebugPolling()
  debugPollTimer = setInterval(async () => {
    await loadDebugTaskData()
    if (!debugTask.value || !['running', 'queued', 'waiting_manual'].includes(debugTask.value.status)) {
      stopDebugPolling()
    }
  }, 2500)
}

function onTaskDeleted(event) {
  const deletedId = event?.detail?.id
  if (!deletedId || debugTask.value?.id !== deletedId) return
  stopDebugPolling()
  debugTask.value = null
  debugExecutions.value = []
  debugLogs.value = []
}

async function loadDebugCaseSteps() {
  if (!createdCaseId.value) return
  const res = await caseApi.get(createdCaseId.value)
  debugCaseAppPackage.value = res.data?.app_package || ''
  try {
    const parsed = JSON.parse(res.data.steps_content || '{}')
    debugSteps.value = (parsed.steps || []).filter(s => s.enabled !== false)
  } catch {
    debugSteps.value = []
  }
}

const {
  connected,
  connecting,
  nativeW,
  nativeH,
  statusText,
  streamMode,
  fps,
  latencyMs,
  startStream,
  stopStream,
  resumeIfAlive,
  attachFrameListener,
  attachMetaListener
} = useScreenStream(deviceId)

const frameW = ref(1080)
const frameH = ref(1920)
const layoutStyle = ref(fixedScreenFrameStyle(1080, 1920, frameMaxHeight('calc(100vh - 220px)', NAV_BAR_HEIGHT)))
let detachFrame = null
let detachMeta = null
let renderer = null
let noFrameTimer = null
let snapshotTimer = null
let triedJpegFallback = false
let scrcpyRetryCount = 0

function clearNoFrameTimer() {
  if (noFrameTimer) {
    clearTimeout(noFrameTimer)
    noFrameTimer = null
  }
}

function scheduleSnapshotCapture() {
  if (snapshotTimer) return
  snapshotTimer = setTimeout(() => {
    snapshotTimer = null
    if (canvasRef.value) captureSessionSnapshot(deviceId, canvasRef.value)
  }, 1000)
}

function scheduleNoFrameCheck() {
  clearNoFrameTimer()
  if (!connected.value || hasFrame.value) return
  noFrameTimer = setTimeout(async () => {
    if (hasFrame.value || !connected.value) return
    if (scrcpyRetryCount < 2) {
      scrcpyRetryCount += 1
      ElMessage.warning(`投屏暂无画面，正在重连低延迟通道（${scrcpyRetryCount}/2）...`)
      stopStream()
      await startStream()
      scheduleNoFrameCheck()
      return
    }
    if (!triedJpegFallback) {
      triedJpegFallback = true
      ElMessage.warning('低延迟通道不可用，已临时降级 ADB（较卡）')
      stopStream()
      await startStream({ forceJpeg: true })
      scheduleNoFrameCheck()
    }
  }, 6000)
}

/** 已卡在 ADB/JPEG 低帧率时，自动切回 scrcpy（每个会话最多一次） */
let jpegUpgradeTimer = null
let jpegUpgradeTried = false
function clearJpegUpgradeTimer() {
  if (jpegUpgradeTimer) {
    clearTimeout(jpegUpgradeTimer)
    jpegUpgradeTimer = null
  }
}
function maybeUpgradeFromJpeg() {
  clearJpegUpgradeTimer()
  if (!connected.value || jpegUpgradeTried) return
  if (streamMode.value !== 'jpeg' && streamMode.value !== 'adb') return
  jpegUpgradeTimer = setTimeout(async () => {
    jpegUpgradeTimer = null
    if (!connected.value || jpegUpgradeTried) return
    if (streamMode.value !== 'jpeg' && streamMode.value !== 'adb') return
    if (fps.value >= 8) return
    jpegUpgradeTried = true
    ElMessage.info('检测到投屏卡顿，正在切换到低延迟通道...')
    triedJpegFallback = false
    scrcpyRetryCount = 0
    hasFrame.value = false
    stopStream()
    await startStream()
    scheduleNoFrameCheck()
  }, 2500)
}

async function connectStream() {
  triedJpegFallback = false
  scrcpyRetryCount = 0
  jpegUpgradeTried = false
  hasFrame.value = false
  await startStream()
  scheduleNoFrameCheck()
}

const displayW = computed(() => nativeW.value || frameW.value)
const displayH = computed(() => nativeH.value || frameH.value)
const resolutionLabel = computed(() => `${displayW.value} × ${displayH.value}`)

const screenFrameStyle = computed(() => layoutStyle.value)

function updateLayoutSize() {
  const { w, h } = frameSizeFromDevice(device.value || { screen_width: frameW.value, screen_height: frameH.value })
  const wrap = screenWrapRef.value
  if (wrap) {
    const rect = wrap.getBoundingClientRect()
    if (rect.width > 40 && rect.height > 40) {
      layoutStyle.value = fixedScreenFrameStyleInBox(w, h, rect.width - 4, rect.height - 4, NAV_BAR_HEIGHT)
      return
    }
  }
  layoutStyle.value = fixedScreenFrameStyle(
    w,
    h,
    frameMaxHeight('calc(100vh - 220px)', NAV_BAR_HEIGHT)
  )
}

let screenWrapObserver = null

function bindScreenWrapObserver() {
  if (screenWrapObserver || typeof ResizeObserver === 'undefined') return
  nextTick(() => {
    const wrap = screenWrapRef.value
    if (!wrap) return
    screenWrapObserver = new ResizeObserver(() => updateLayoutSize())
    screenWrapObserver.observe(wrap)
    updateLayoutSize()
  })
}

function unbindScreenWrapObserver() {
  if (screenWrapObserver) {
    screenWrapObserver.disconnect()
    screenWrapObserver = null
  }
}

function androidVersion(d) {
  return androidVersionLabel(d)
}

function deviceStatusLabel(status) {
  return deviceStatusMap[status]?.label || status || '-'
}

function applyDeviceFrame(d) {
  const dev = d?.screen_width ? d : normalizeDevice(d)
  const { w, h } = frameSizeFromDevice(dev)
  frameW.value = w
  frameH.value = h
  if (dev?.screen_width) nativeW.value = dev.screen_width
  if (dev?.screen_height) nativeH.value = dev.screen_height
  updateLayoutSize()
}

function bindCanvasPipeline() {
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()

  renderer = createScreenCanvasRenderer(canvasRef, {
    onFrameDrawn: () => {
      if (!hasFrame.value) hasFrame.value = true
      clearNoFrameTimer()
      // 快照节流：避免每帧 copy canvas 拖垮投屏主线程
      scheduleSnapshotCapture()
    },
    onDecodeError: (reason) => {
      console.warn('[screen]', reason)
      if (connected.value && !hasFrame.value) scheduleNoFrameCheck()
    }
  })

  detachMeta = attachMetaListener(meta => {
    renderer.onMeta(meta)
    if (meta.native_width) nativeW.value = meta.native_width
    if (meta.native_height) nativeH.value = meta.native_height
    else if (meta.width && meta.height) {
      nativeW.value = meta.width
      nativeH.value = meta.height
    }
  })
  detachFrame = attachFrameListener(buffer => renderer.onFrame(buffer))
}

function restoreStreamView() {
  hasFrame.value = false
  resumeIfAlive()
  nextTick(() => nextTick(bindCanvasPipeline))
}

async function loadDevice() {
  const res = await deviceApi.get(deviceId)
  device.value = normalizeDevice(res.data)
  applyDeviceFrame(device.value)
  setScreenDeviceInfo(deviceId, {
    name: device.value?.name || device.value?.serial_number,
    serial_number: device.value?.serial_number
  })
}

let dragStart = null
let dragStartClient = null
let dragStartTime = 0
const lastTapPoint = ref(null)
let uiRefreshTimer = null
let tapActionChain = Promise.resolve()
let recordingTapChain = Promise.resolve()
let recordClickChain = Promise.resolve()
let locatorPatchChain = Promise.resolve()
let lastInteractionAt = 0
let pendingClickInspect = null
let liveStepSeq = 0
let mousedownInspectGen = 0
let lastMousedownInspectAt = 0
let locatorPatchFailStreak = 0

const manualPickActive = ref(false)
const manualPickRecordId = ref(null)
const manualPickStepIndex = ref(null)
const manualPickReturnTo = ref('')
const manualPickPreviewTap = ref(false)
const pickInspectLoading = ref(false)
const pickInspectResult = ref(null)
const showPickDialog = ref(false)
const pickSaving = ref(false)
const manualLocatorForm = reactive({
  display_name: '',
  element_name: '',
  locator_type: 'id',
  locator_value: '',
  ocr_text: '',
  selectedKey: ''
})

const pickCandidateLocators = computed(() => {
  const locs = pickInspectResult.value?.locators
  if (!locs || typeof locs !== 'object') return []
  const priority = ['id', 'resource_id', 'content_desc', 'text', 'xpath_desc', 'xpath', 'absolute_xpath', 'class_name', 'bounds']
  const items = []
  const seen = new Set()
  for (const key of priority) {
    if (!locs[key] || seen.has(`${key}:${locs[key]}`)) continue
    items.push({ key, value: String(locs[key]) })
    seen.add(`${key}:${locs[key]}`)
  }
  Object.entries(locs).forEach(([key, val]) => {
    const value = String(val)
    const sig = `${key}:${value}`
    if (seen.has(sig) || key.startsWith('primary_') || key.startsWith('xpath_text')) return
    items.push({ key, value })
    seen.add(sig)
  })
  return items.slice(0, 12)
})

function locatorTypeLabel(type) {
  return formatLocatorType(type)
}

function initManualPickFromRoute() {
  const q = route.query
  if (!q.pickRecord) return
  manualPickRecordId.value = Number(q.pickRecord)
  manualPickStepIndex.value = q.pickStep != null && q.pickStep !== '' ? Number(q.pickStep) : null
  manualPickReturnTo.value = typeof q.returnTo === 'string' ? q.returnTo : ''
  manualPickActive.value = true
  manualPickPreviewTap.value = false
}

function toggleManualPickDuringRecording() {
  if (!recording.value || !recordSessionId.value) return
  manualPickActive.value = !manualPickActive.value
  if (manualPickActive.value) {
    manualPickRecordId.value = recordSessionId.value
    manualPickStepIndex.value = null
    manualPickReturnTo.value = ''
    manualPickPreviewTap.value = false
    ElMessage.info('手动选控件：点击画面识别控件，不会自动点击设备')
  }
}

function exitManualPick() {
  manualPickActive.value = false
  manualPickRecordId.value = null
  manualPickStepIndex.value = null
  manualPickReturnTo.value = ''
}

function finishManualPick() {
  const returnTo = manualPickReturnTo.value
  exitManualPick()
  if (returnTo) router.push(returnTo)
}

async function warmPickCache() {
  const rid = manualPickRecordId.value || recordSessionId.value
  if (!rid) return
  try {
    await recordApi.warmInspect(rid)
    ElMessage.success('UI 树刷新已触发')
  } catch {
    ElMessage.warning('UI 树刷新失败')
  }
}

function fillManualFormFromInspect(data) {
  manualLocatorForm.display_name = data?.display_name || ''
  manualLocatorForm.element_name = data?.element_name || ''
  manualLocatorForm.locator_type = data?.locator_type || 'id'
  manualLocatorForm.locator_value = data?.locator_value || ''
  manualLocatorForm.ocr_text = ''
  const candidates = pickCandidateLocators.value
  if (candidates.length) {
    manualLocatorForm.selectedKey = candidates[0].key
    manualLocatorForm.locator_type = candidates[0].key === 'resource_id' ? 'id' : candidates[0].key
    manualLocatorForm.locator_value = candidates[0].value
  } else {
    manualLocatorForm.selectedKey = ''
  }
}

function buildLocatorPatch() {
  const data = pickInspectResult.value || {}
  const patch = {
    x: data.x,
    y: data.y,
    locator_valid: false
  }
  if (data.display_name || manualLocatorForm.display_name) {
    patch.display_name = manualLocatorForm.display_name || data.display_name
  }
  if (data.element_name || manualLocatorForm.element_name) {
    patch.element_name = manualLocatorForm.element_name || data.element_name
  }
  if (data.widget_type) patch.widget_type = data.widget_type
  if (data.suggested_step_type) patch.suggested_step_type = data.suggested_step_type

  const locators = { ...(data.locators || {}) }
  if (manualLocatorForm.selectedKey) {
    const picked = pickCandidateLocators.value.find(i => i.key === manualLocatorForm.selectedKey)
    if (picked) {
      patch.locator_type = picked.key === 'resource_id' ? 'id' : picked.key
      patch.locator_value = picked.value
      locators[picked.key] = picked.value
    }
  }
  if (manualLocatorForm.locator_value?.trim()) {
    const lt = manualLocatorForm.locator_type || 'id'
    patch.locator_type = lt
    patch.locator_value = manualLocatorForm.locator_value.trim()
    locators[lt] = patch.locator_value
  }
  if (manualLocatorForm.ocr_text?.trim()) {
    const text = manualLocatorForm.ocr_text.trim()
    patch.locator_type = 'ocr'
    patch.locator_value = text
    patch.suggested_step_type = 'tap_ocr'
    locators.ocr = text
    locators.text = text
  }
  if (Object.keys(locators).length) patch.locators = locators
  patch.locator_valid = !!(patch.locator_value || Object.keys(locators).length > 0 || data.valid)
  return patch
}

async function handleManualPickClick(x, y) {
  const rid = manualPickRecordId.value || recordSessionId.value
  if (!rid) {
    ElMessage.warning('无可用录制会话')
    return
  }
  pickInspectLoading.value = true
  try {
    if (manualPickPreviewTap.value) {
      await deviceApi.screenTap(deviceId, { x, y })
      await sleepMs(280)
    }
    await recordApi.warmInspect(rid).catch(() => {})
    const dw = nativeW.value || canvasRef.value?.width || 0
    const dh = nativeH.value || canvasRef.value?.height || 0
    const res = await recordApi.inspect(rid, x, y, dw, dh, true)
    pickInspectResult.value = { ...(res?.data || {}), x, y }
    fillManualFormFromInspect(res?.data)
    showPickDialog.value = true
  } catch (e) {
    pickInspectResult.value = { x, y, valid: false }
    fillManualFormFromInspect(null)
    showPickDialog.value = true
    ElMessage.warning(e?.message || '自动识别失败，请手动填写定位')
  } finally {
    pickInspectLoading.value = false
  }
}

async function applyManualLocator() {
  const patch = buildLocatorPatch()
  if (!patch.locator_valid) {
    ElMessage.warning('请填写至少一种定位方式（Resource ID / 文本 / OCR 等）')
    return
  }
  const rid = manualPickRecordId.value || recordSessionId.value
  if (!rid) return
  pickSaving.value = true
  try {
    if (manualPickStepIndex.value != null) {
      await recordApi.patchStepLocator(rid, manualPickStepIndex.value, patch)
    } else {
      await recordApi.patchLastClick(rid, patch)
    }
    ElMessage.success('定位已保存')
    showPickDialog.value = false
    const returnTo = manualPickReturnTo.value
    if (returnTo && manualPickStepIndex.value != null) {
      exitManualPick()
      router.push(returnTo)
    }
  } catch (e) {
    ElMessage.error(e?.message || '保存定位失败')
  } finally {
    pickSaving.value = false
  }
}

const SWIPE_THRESHOLD_CSS = 12
const SWIPE_THRESHOLD_RECORDING_CSS = 22
const LONG_PRESS_MS = 750
const UI_REFRESH_INTERVAL_MS = 6000
const UI_REFRESH_INITIAL_DELAY_MS = 800
const WARM_IDLE_MS = 1800
const RAPID_CLICK_GAP_MS = 280
const MOUSEDOWN_INSPECT_GAP_MS = 220
const INSPECT_RACE_MS = 1200
const LOCATOR_PATCH_IDLE_MS = 900
const LOCATOR_PATCH_RETRY_IDLE_MS = 1600

function markRecordingInteraction() {
  lastInteractionAt = Date.now()
}

function sleepMs(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function mapCoords(event) {
  const canvas = canvasRef.value
  if (!canvas || !canvas.width || !canvas.height) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  const cx = (event.clientX - rect.left) * (canvas.width / rect.width)
  const cy = (event.clientY - rect.top) * (canvas.height / rect.height)
  const sx = Math.max(0, Math.min(cx, canvas.width))
  const sy = Math.max(0, Math.min(cy, canvas.height))
  const nw = nativeW.value || canvas.width
  const nh = nativeH.value || canvas.height
  return {
    x: Math.round(sx * nw / canvas.width),
    y: Math.round(sy * nh / canvas.height)
  }
}

async function appendRecordEvent(event, options = {}) {
  if (!recording.value || !recordSessionId.value || recordingPaused.value) return
  const payload = { ...event, video_offset_ms: videoRecorder.durationMs.value }
  if (payload.text && desensitizeEnabled.value) {
    if (containsSensitive(payload.text)) {
      videoRecorder.addBlurRegion(blurRegionForKeyboard())
      if (payload.x != null && payload.y != null) {
        videoRecorder.addBlurRegion(blurRegionForInput(
          payload.x, payload.y, nativeW.value || frameW.value, nativeH.value || frameH.value
        ))
      }
    }
    payload.text = desensitizeText(payload.text)
    payload.desensitized = true
  }
  if (!options.light && canvasRef.value && ['click', 'tap_xy'].includes(payload.type)) {
    payload.snapshot_thumb = captureCanvasSnapshot(canvasRef.value)
  }
  await recordApi.append(recordSessionId.value, payload)
  recordStepCount.value++
  operationRecordingState.stepCount = recordStepCount.value
  if (!options.skipPush) {
    pushLiveStep(payload)
  }
}

function pushLiveStep(payload) {
  const id = ++liveStepSeq
  const target = formatStepTarget(payload)
    || payload.display_name
    || payload.element_name
    || (payload.type === 'input' ? payload.text : '')
    || (payload.x != null ? `(${payload.x}, ${payload.y})` : payload.type)
  const locator = formatStepLocator(payload)
  const locatorValid = !!payload.locator_valid
  liveRecentSteps.value.push({
    id,
    type: payload.type,
    x: payload.x,
    y: payload.y,
    target,
    locator,
    locator_valid: locatorValid,
    pending: !locatorValid && payload.type === 'click',
    unrecognized: payload.type === 'click' && !locatorValid && !payload.element_name
  })
  if (liveRecentSteps.value.length > 8) liveRecentSteps.value.shift()
  return id
}

function updateLiveStep(stepId, payload) {
  const entry = liveRecentSteps.value.find(s => s.id === stepId)
  if (!entry) return
  entry.target = formatStepTarget(payload) || entry.target
  entry.locator = formatStepLocator(payload)
  entry.locator_valid = !!payload.locator_valid
  entry.pending = false
  entry.unrecognized = payload.type === 'click' && !payload.locator_valid && !payload.element_name
}

const liveRecognitionStats = computed(() => {
  const clicks = liveRecentSteps.value.filter(s => s.type === 'click')
  const hit = clicks.filter(s => s.locator_valid).length
  return { total: clicks.length, hit }
})

function dataUrlToBlob(dataUrl) {
  const [header, b64] = dataUrl.split(',')
  const mime = header.match(/:(.*?);/)?.[1] || 'image/jpeg'
  const bin = atob(b64)
  const arr = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i)
  return new Blob([arr], { type: mime })
}

function applyInspectToEvent(event, data) {
  if (!data) return
  // 录制中禁止用 UI 树尺寸改写投屏分辨率，否则底部弹层「确定」坐标会漂移
  if (!recording.value && data.ui_width && data.ui_height) {
    nativeW.value = data.ui_width
    nativeH.value = data.ui_height
  }
  if (data.display_name) event.display_name = data.display_name
  if (data.element_name) event.element_name = data.element_name
  if (data.locators) event.locators = data.locators
  if (data.locator_type) event.locator_type = data.locator_type
  if (data.locator_value) event.locator_value = data.locator_value
  if (data.widget_type) event.widget_type = data.widget_type
  if (data.suggested_step_type) event.suggested_step_type = data.suggested_step_type
  const locCount = data.locators && typeof data.locators === 'object'
    ? Object.keys(data.locators).length : 0
  event.locator_valid = !!(data.valid || data.element_name || locCount > 0 || data.locator_value)
  // 录制过程中不弹 inspect 告警，避免干扰操作；步骤仍会以坐标形式记录
}

function startUiRefreshInterval() {
  stopUiRefreshInterval()
  // 录制中默认不做后台 warm dump；仅在长时间完全空闲时轻量预热
  const tick = () => {
    if (!recording.value || !recordSessionId.value || recordingPaused.value) return
    if (Date.now() - lastInteractionAt < WARM_IDLE_MS) return
    recordApi.warmInspect(recordSessionId.value).catch(() => {})
  }
  setTimeout(tick, UI_REFRESH_INITIAL_DELAY_MS)
  uiRefreshTimer = setInterval(tick, UI_REFRESH_INTERVAL_MS)
}

function stopUiRefreshInterval() {
  if (uiRefreshTimer) {
    clearInterval(uiRefreshTimer)
    uiRefreshTimer = null
  }
}

async function captureInspectForClick(x, y, maxWaitMs = INSPECT_RACE_MS) {
  if (!recordSessionId.value) return null
  const dw = nativeW.value || canvasRef.value?.width || 0
  const dh = nativeH.value || canvasRef.value?.height || 0
  try {
    const res = await Promise.race([
      recordApi.inspect(recordSessionId.value, x, y, dw, dh, false),
      sleepMs(maxWaitMs).then(() => null)
    ])
    return res?.data || null
  } catch {
    return null
  }
}

function scheduleLocatorPatch(x, y, stepId) {
  if (locatorPatchFailStreak >= 4) return
  const runPatch = async (delayMs, minIdleMs = LOCATOR_PATCH_IDLE_MS, blocking = true) => {
    await sleepMs(delayMs)
    // 等用户停手后再 dump，避免与 tap 抢 ADB
    const idleWaitDeadline = Date.now() + 4000
    while (Date.now() - lastInteractionAt < minIdleMs) {
      if (Date.now() > idleWaitDeadline) return false
      if (!recording.value || recordingPaused.value) return false
      await sleepMs(120)
    }
    const dw = nativeW.value || canvasRef.value?.width || 0
    const dh = nativeH.value || canvasRef.value?.height || 0
    try {
      const res = await recordApi.inspect(recordSessionId.value, x, y, dw, dh, blocking)
      const data = res?.data
      const locCount = data?.locators && typeof data.locators === 'object'
        ? Object.keys(data.locators).length : 0
      if (!data?.valid && !data?.element_name && !locCount && !data?.locator_value) return false
      const patch = { x, y }
      applyInspectToEvent(patch, data)
      if (!patch.locator_valid) return false
      await recordApi.patchLastClick(recordSessionId.value, patch)
      updateLiveStep(stepId, { type: 'click', x, y, ...patch })
      locatorPatchFailStreak = 0
      return true
    } catch {
      return false
    }
  }
  locatorPatchChain = locatorPatchChain.then(async () => {
    const ok = await runPatch(350, LOCATOR_PATCH_IDLE_MS, true)
    if (!ok) {
      const ok2 = await runPatch(900, LOCATOR_PATCH_RETRY_IDLE_MS, true)
      if (!ok2) locatorPatchFailStreak += 1
      else locatorPatchFailStreak = 0
    }
  }).catch(() => {})
}

async function sendRecordingTap(x, y) {
  let lastErr = null
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      await deviceApi.screenTap(deviceId, { x, y })
      return
    } catch (e) {
      lastErr = e
      if (attempt < 2) await sleepMs(40 * (attempt + 1))
    }
  }
  ElMessage.error(lastErr?.message || '点击发送失败，请重试')
}

function enqueueRecordedClick(x, y, inspectPromise) {
  if (!recording.value || !recordSessionId.value || recordingPaused.value) return
  const event = { type: 'click', x, y, locator_valid: false }
  const stepId = pushLiveStep(event)

  recordClickChain = recordClickChain.then(async () => {
    const data = inspectPromise ? await inspectPromise.catch(() => null) : null
    if (data) applyInspectToEvent(event, data)
    updateLiveStep(stepId, event)
    try {
      await appendRecordEvent(event, { light: true, skipPush: true })
    } catch {
      /* 步骤写入失败不影响后续点击 */
    }
    // 连续操作时延后补定位；空闲后再尝试一次
    if (!event.locator_valid) {
      scheduleLocatorPatch(x, y, stepId)
    }
  }).catch(() => {})
}

function performTap(x, y) {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return
  lastTapPoint.value = { x, y }
  markRecordingInteraction()

  if (recording.value && recordSessionId.value && !recordingPaused.value) {
    // 录制点击串行：in-flight=1，丢掉过期堆积，保证每次 tap 都能尽快打到设备
    recordingTapChain = recordingTapChain
      .catch(() => {})
      .then(() => sendRecordingTap(x, y))
    const inspectP = pendingClickInspect
    pendingClickInspect = null
    enqueueRecordedClick(x, y, inspectP)
    return
  }

  tapActionChain = tapActionChain.then(async () => {
    try {
      await deviceApi.screenTap(deviceId, { x, y })
    } catch (e) {
      ElMessage.error(e?.message || '点击发送失败，请重试')
      throw e
    }
  }).catch(() => {})
}

function startMousedownInspect(x, y) {
  const now = Date.now()
  // 录制连点时跳过 mousedown 预识别，优先保证 tap 通路
  if (recording.value && now - lastInteractionAt < RAPID_CLICK_GAP_MS) return
  if (now - lastMousedownInspectAt < MOUSEDOWN_INSPECT_GAP_MS) return
  lastMousedownInspectAt = now
  const gen = ++mousedownInspectGen
  pendingClickInspect = captureInspectForClick(x, y).then(data => (
    gen === mousedownInspectGen ? data : null
  ))
}

function onMouseDown(event) {
  if (!connected.value || cropSelecting.value) return
  dragStart = mapCoords(event)
  dragStartClient = { x: event.clientX, y: event.clientY }
  dragStartTime = Date.now()
  if (recording.value && recordSessionId.value && !recordingPaused.value) {
    startMousedownInspect(dragStart.x, dragStart.y)
  }
}

function mapDisplayNorm(event) {
  const canvas = canvasRef.value
  if (!canvas) return null
  const rect = canvas.getBoundingClientRect()
  if (!rect.width || !rect.height) return null
  return {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))
  }
}

const cropBoxStyle = computed(() => {
  if (!cropPreview.value) return {}
  const { x, y, w, h } = cropPreview.value
  return {
    left: `${x * 100}%`,
    top: `${y * 100}%`,
    width: `${w * 100}%`,
    height: `${h * 100}%`
  }
})

function onCropDown(event) {
  cropDragStart = mapDisplayNorm(event)
  cropPreview.value = null
}

function onCropMove(event) {
  if (!cropDragStart) return
  const end = mapDisplayNorm(event)
  if (!end) return
  const x = Math.min(cropDragStart.x, end.x)
  const y = Math.min(cropDragStart.y, end.y)
  const w = Math.abs(end.x - cropDragStart.x)
  const h = Math.abs(end.y - cropDragStart.y)
  cropPreview.value = { x, y, w, h }
}

function onCropUp(event) {
  if (!cropDragStart) return
  const end = mapDisplayNorm(event)
  cropDragStart = null
  if (!end || !cropPreview.value) return
  const { w, h } = cropPreview.value
  if (w < 0.05 || h < 0.05) {
    cropPreview.value = null
    ElMessage.warning('选区过小，请重新拖拽')
    return
  }
  cropRectNorm.value = { ...cropPreview.value }
}

function resetCrop() {
  cropRectNorm.value = null
  cropPreview.value = null
  cropSelecting.value = true
}

async function confirmCropAndStart() {
  if (!cropRectNorm.value) return
  cropSelecting.value = false
  await doStartRecording()
}

async function onMouseUp(event) {
  if (!dragStart || !dragStartClient || !connected.value || cropSelecting.value) return
  const start = dragStart
  const startClient = dragStartClient
  const end = mapCoords(event)
  dragStart = null
  dragStartClient = null
  const dxCss = Math.abs(event.clientX - startClient.x)
  const dyCss = Math.abs(event.clientY - startClient.y)
  const holdMs = Date.now() - dragStartTime
  const swipeThreshold = (recording.value && !recordingPaused.value)
    ? SWIPE_THRESHOLD_RECORDING_CSS
    : SWIPE_THRESHOLD_CSS
  // 短按时间内的轻微拖动按 tap 处理，避免时间滚轮误判成 swipe
  const treatAsTap = holdMs < 220 && dxCss < swipeThreshold * 1.5 && dyCss < swipeThreshold * 1.5
  if (!treatAsTap && (dxCss > swipeThreshold || dyCss > swipeThreshold)) {
    pendingClickInspect = null
    markRecordingInteraction()
    await deviceApi.screenSwipe(deviceId, {
      x1: start.x, y1: start.y, x2: end.x, y2: end.y, duration_ms: 300
    })
    if (recording.value && recordSessionId.value) {
      void appendRecordEvent({ type: 'swipe', x: start.x, y: start.y, x2: end.x, y2: end.y })
    }
  } else if (holdMs > LONG_PRESS_MS) {
    markRecordingInteraction()
    await deviceApi.screenSwipe(deviceId, {
      x1: end.x, y1: end.y, x2: end.x, y2: end.y, duration_ms: holdMs
    })
    if (recording.value && recordSessionId.value) {
      void appendRecordEvent({ type: 'long_press', x: end.x, y: end.y, duration_ms: holdMs })
    }
  } else {
    if (manualPickActive.value) {
      void handleManualPickClick(end.x, end.y)
    } else {
      void performTap(end.x, end.y)
    }
  }
}

async function sendText() {
  if (!inputText.value) return
  if (!connected.value) {
    ElMessage.warning('请先连接投屏')
    return
  }
  if (!lastTapPoint.value) {
    ElMessage.warning('请先在左侧投屏画面点击要输入的位置')
    return
  }
  const raw = inputText.value
  const { x, y } = lastTapPoint.value
  try {
    // 发送前再次聚焦，避免录制过程中焦点偏移
    await deviceApi.screenTap(deviceId, { x, y })
    await new Promise(r => setTimeout(r, 280))
    await deviceApi.screenInput(deviceId, { text: raw, x, y })
    if (recording.value && recordSessionId.value) {
      if (desensitizeEnabled.value && containsSensitive(raw)) {
        videoRecorder.addBlurRegion(blurRegionForKeyboard())
        videoRecorder.addBlurRegion(blurRegionForInput(x, y, nativeW.value || frameW.value, nativeH.value || frameH.value))
      }
      await appendRecordEvent({ type: 'input', text: raw, x, y }, { light: true })
    }
    ElMessage.success('文本已发送至设备')
    inputText.value = ''
  } catch {
    /* 错误文案由 axios 拦截器统一展示，避免重复 toast */
  }
}

function bindRecordingActions() {
  registerRecordingActions({
    pause: pauseRecording,
    resume: resumeRecording,
    markSegment,
    finish: finishRecording,
    cancel: cancelRecording
  })
}

async function pressNavKey(key) {
  if (!connected.value) return
  try {
    await deviceApi.screenKey(deviceId, { key })
    if (recording.value && recordSessionId.value && !recordingPaused.value
        && recordNavKeys.value && NAV_RECORDABLE_KEYS.has(key)) {
      await appendRecordEvent({
        type: 'key',
        key,
        display_name: `${NAV_KEY_LABELS[key] || key}`
      })
    }
  } catch {
    ElMessage.error('按键发送失败')
  }
}

function watermarkLines() {
  const user = userStore.user?.display_name || userStore.user?.username || '测试员'
  const now = new Date().toLocaleString('zh-CN')
  const lines = [user, now, `设备 #${deviceId}`]
  if (recordMetaForm.project_code) lines.push(recordMetaForm.project_code)
  return lines
}

function loadRecordMeta() {
  try {
    const saved = JSON.parse(localStorage.getItem(RECORD_META_KEY) || '{}')
    recordMetaForm.module_name = saved.module_name || ''
    recordMetaForm.version_label = saved.version_label || ''
    recordMetaForm.project_code = saved.project_code || ''
    recordMetaForm.task_id = saved.task_id || ''
    recordNavKeys.value = !!saved.record_nav_keys
  } catch { /* ignore */ }
}

function recordTags() {
  const tags = {
    module_name: recordMetaForm.module_name || undefined,
    version_label: recordMetaForm.version_label || undefined,
    project_code: recordMetaForm.project_code || undefined
  }
  if (recordMode.value === 'crop' && cropRectNorm.value) {
    tags.crop_rect_json = JSON.stringify(cropRectNorm.value)
  } else if (recordMode.value === 'window') {
    tags.crop_rect_json = JSON.stringify(WINDOW_CROP_RECT)
  }
  const metrics = videoRecorder.getClientMetrics?.()
  if (metrics) {
    tags.client_metrics_json = JSON.stringify({
      ...metrics,
      startup_ms: recordingStartupMs
    })
  }
  return tags
}

function promptStartRecording() {
  if (!connected.value || !canvasRef.value) {
    ElMessage.warning('请先连接投屏后再录制')
    return
  }
  loadRecordMeta()
  applyDesensitizePolicy()
  showRecordMetaDialog.value = true
}

async function confirmRecordMeta() {
  localStorage.setItem(RECORD_META_KEY, JSON.stringify({ ...recordMetaForm }))
  applyDesensitizePolicy()
  showRecordMetaDialog.value = false
  if (recordMode.value === 'crop' && !cropRectNorm.value) {
    cropSelecting.value = true
    ElMessage.info('请在投屏画面上拖拽框选录制区域')
    return
  }
  await doStartRecording()
}

async function maybeAutoStartRecording() {
  if (route.query.auto_record !== '1' || recording.value) return
  if (!sessionStorage.getItem('atp_record_boot_ts')) markRecordingBoot()
  loadRecordMeta()
  const fastStart = localStorage.getItem(RECORD_FAST_START_KEY) !== '0'
  if (fastStart || (recordMetaForm.module_name && recordMetaForm.version_label)) {
    if (!recordMetaForm.module_name) recordMetaForm.module_name = '默认模块'
    if (!recordMetaForm.version_label) recordMetaForm.version_label = '未标记版本'
    if (recordMode.value === 'crop' && !cropRectNorm.value) {
      showRecordMetaDialog.value = false
      cropSelecting.value = true
      ElMessage.info('请在投屏画面上拖拽框选录制区域')
      return
    }
    await doStartRecording()
  } else {
    showRecordMetaDialog.value = true
  }
}

function startStatusSync() {
  stopStatusSync()
  statusSyncTimer = setInterval(() => {
    operationRecordingState.durationMs = videoRecorder.durationMs.value
    operationRecordingState.fileSizeBytes = videoRecorder.fileSizeBytes.value
    operationRecordingState.paused = recordingPaused.value
    operationRecordingState.effectiveFps = videoRecorder.effectiveFps.value
    operationRecordingState.avgPaintMs = videoRecorder.avgPaintMs.value
    operationRecordingState.performanceGrade = videoRecorder.performanceGrade.value
  }, 400)
}

function stopStatusSync() {
  if (statusSyncTimer) {
    clearInterval(statusSyncTimer)
    statusSyncTimer = null
  }
}

async function doStartRecording() {
  if (!connected.value || !canvasRef.value) {
    ElMessage.warning('请先连接投屏后再录制')
    return
  }
  localStorage.setItem(LAST_RECORD_DEVICE_KEY, String(deviceId))
  localStorage.setItem(RECORD_META_KEY, JSON.stringify({
    ...recordMetaForm,
    record_nav_keys: recordNavKeys.value
  }))
  applyDesensitizePolicy()
  recordingStartupMs = consumeStartupMs()
  operationRecordingState.startupMs = recordingStartupMs
  const startPayload = {
    module_name: recordMetaForm.module_name || undefined,
    version_label: recordMetaForm.version_label || undefined,
    project_code: recordMetaForm.project_code || undefined,
    startup_ms: recordingStartupMs,
    display_width: nativeW.value || frameW.value || undefined,
    display_height: nativeH.value || frameH.value || undefined
  }
  if (recordMetaForm.task_id?.trim()) {
    startPayload.task_id = Number(recordMetaForm.task_id.trim())
  }
  const res = await recordApi.start(deviceId, startPayload)
  recordSessionId.value = res.data.id
  recording.value = true
  recordingPaused.value = false
  recordStepCount.value = 0
  segmentCount.value = 0
  liveRecentSteps.value = []
  liveStepSeq = 0
  mousedownInspectGen = 0
  lastMousedownInspectAt = 0
  recordClickChain = Promise.resolve()
  recordingTapChain = Promise.resolve()
  locatorPatchChain = Promise.resolve()
  pendingClickInspect = null
  locatorPatchFailStreak = 0
  lastInteractionAt = 0
  syncFromSession(res.data.id, deviceId, device.value?.name || '')
  operationRecordingState.watermarkEnabled = watermarkEnabled.value
  // 录制开始立刻同步预热 UI 树，避免前几步全部「定位未识别」
  try {
    await recordApi.warmInspect(res.data.id)
  } catch {
    /* 预热失败不阻断录制，后续空闲仍会补刷 */
  }
  operationRecordingState.videoRecording = videoRecorder.start(canvasRef.value, {
    watermark: watermarkEnabled.value,
    watermarkLines: watermarkLines(),
    desensitize: desensitizeEnabled.value,
    // scrcpy 不占 ADB 截图带宽，可用更高采集帧率保证清晰；JPEG 兜底仍降到 8
    fps: streamMode.value === 'scrcpy' ? 14 : 8,
    cropRect: resolveRecordCropRect()
  })
  startStatusSync()
  startAutosave()
  startUiRefreshInterval()
  ElMessage.success('录制已开始（视频 + 操作步骤）')
}

function applyDesensitizePolicy() {
  const env = (recordMetaForm.project_code || '').toLowerCase()
  const prodLike = env.includes('prod') || env.includes('生产') || metaEnvironmentIsProd()
  if (prodLike) {
    desensitizeEnabled.value = true
    desensitizeLocked.value = true
  } else {
    desensitizeLocked.value = false
  }
}

function metaEnvironmentIsProd() {
  try {
    const saved = JSON.parse(localStorage.getItem(RECORD_META_KEY) || '{}')
    const label = (saved.environment || saved.version_label || '').toLowerCase()
    return label.includes('prod') || label.includes('生产')
  } catch {
    return false
  }
}

function startAutosave() {
  stopAutosave()
  autosaveTimer = setInterval(async () => {
    if (!recording.value || !recordSessionId.value || savingCase.value) return
    try {
      const blob = videoRecorder.getPartialBlob()
      if (blob?.size) {
        await saveDraftBlob(recordSessionId.value, blob)
        persistEmergencyDraft({
          sessionId: recordSessionId.value,
          deviceId,
          stepCount: recordStepCount.value,
          durationMs: videoRecorder.durationMs.value,
          reason: 'periodic_autosave'
        })
      }
    } catch { /* ignore */ }
  }, 30000)
}

function stopAutosave() {
  if (autosaveTimer) {
    clearInterval(autosaveTimer)
    autosaveTimer = null
  }
}

function onBeforeUnload(e) {
  if (recording.value && !savingCase.value) {
    e.preventDefault()
    e.returnValue = '录制进行中，关闭页面可能丢失未保存内容'
  }
}

async function startRecording() {
  promptStartRecording()
}

async function pauseRecording() {
  await recordApi.pause(recordSessionId.value)
  videoRecorder.pause()
  recordingPaused.value = true
  operationRecordingState.paused = true
  stopUiRefreshInterval()
}

async function resumeRecording() {
  await recordApi.resume(recordSessionId.value)
  videoRecorder.resume()
  recordingPaused.value = false
  operationRecordingState.paused = false
  startUiRefreshInterval()
}

async function markSegment() {
  const { value: label } = await ElMessageBox.prompt('切点名称（可选）', '标记切点', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: `场景 ${segmentCount.value + 1}`
  }).catch(() => ({ value: null }))
  if (label === null) return
  await recordApi.markSegment(recordSessionId.value, { label: label || '' })
  segmentCount.value++
  operationRecordingState.segmentCount = segmentCount.value
  ElMessage.success('切点已标记')
}

async function finishRecording() {
  if (savingCase.value) return
  try {
    const res = await recordApi.previewDenoise(recordSessionId.value)
    denoisePreview.value = res.data
    applyDenoise.value = res.data.removed_count > 0
    showDenoiseDialog.value = true
  } catch {
    await confirmFinishRecordingDirect(true)
  }
}

function stepLabel(s) {
  const target = formatStepTarget(s)
  const loc = formatStepLocator(s)
  if (target && loc) return `${target} · ${loc}`
  return target || loc || s.element_name || ''
}

async function confirmFinishRecording() {
  showDenoiseDialog.value = false
  await confirmFinishRecordingDirect(applyDenoise.value)
}

async function confirmFinishRecordingDirect(denoise) {
  if (savingCase.value || !recordSessionId.value) return
  savingCase.value = true
  const loading = ElLoading.service({
    lock: true,
    text: '正在保存录制并分析步骤，请稍候...',
    background: 'rgba(255,255,255,0.6)'
  })
  let finished = false
  let blob = null
  const sid = recordSessionId.value
  try {
    blob = await videoRecorder.stop()
    stopStatusSync()
    stopAutosave()
    await recordApi.finish(sid, denoise)
    finished = true
    if (blob) {
      const operator = userStore.user?.display_name || userStore.user?.username || ''
      let thumbBlob = null
      const thumb = captureCanvasThumb(canvasRef.value)
      if (thumb) thumbBlob = dataUrlToBlob(thumb)
      await recordApi.uploadVideo(
        sid,
        blob,
        videoRecorder.getDurationSeconds(),
        operator,
        thumbBlob,
        recordTags()
      )
    }
    await clearDraft(sid)
    stopUiRefreshInterval()
    recording.value = false
    recordingPaused.value = false
    resetRecordingState()
    denoisePreview.value = null
    showDenoiseDialog.value = false
    ElMessage.success('录制已保存，进入审阅页')
    recordSessionId.value = null
    router.push(`/recordings/review/${sid}`)
  } catch (e) {
    const msg = e?.message || '保存录制失败，请重试'
    operationRecordingState.error = msg
    if (finished && blob && sid) {
      await saveDraftBlob(sid, blob)
      persistEmergencyDraft({
        sessionId: sid,
        deviceId,
        stepCount: recordStepCount.value,
        durationMs: videoRecorder.durationMs.value,
        reason: 'upload_failed',
        tags: recordTags()
      })
      stopUiRefreshInterval()
      recording.value = false
      recordingPaused.value = false
      resetRecordingState()
      recordSessionId.value = null
      recoveryDraft.value = loadDraftMeta()
      showRecoveryDialog.value = true
      ElMessage.error('视频上传失败，草稿已保留，可重试上传')
    } else {
      ElMessage.error(msg)
    }
  } finally {
    loading.close()
    savingCase.value = false
  }
}

async function emergencyPersist(reason) {
  if (!recording.value || !recordSessionId.value || savingCase.value || emergencyHandling) return
  emergencyHandling = true
  try {
    const blob = await videoRecorder.stop()
    stopStatusSync()
    stopAutosave()
    await recordApi.emergencySave(recordSessionId.value, reason)
    if (blob) {
      await saveDraftBlob(recordSessionId.value, blob)
      const operator = userStore.user?.display_name || userStore.user?.username || ''
      let thumbBlob = null
      const thumb = captureCanvasThumb(canvasRef.value)
      if (thumb) thumbBlob = dataUrlToBlob(thumb)
      await recordApi.uploadVideo(
        recordSessionId.value,
        blob,
        videoRecorder.getDurationSeconds(),
        operator,
        thumbBlob,
        recordTags()
      )
    }
    persistEmergencyDraft({
      sessionId: recordSessionId.value,
      deviceId,
      stepCount: recordStepCount.value,
      durationMs: videoRecorder.durationMs.value,
      reason
    })
    ElMessage.warning('录制已应急保存，可在审阅页恢复')
    const sid = recordSessionId.value
    recording.value = false
    recordingPaused.value = false
    resetRecordingState()
    recordSessionId.value = null
    router.push(`/recordings/review/${sid}`)
  } catch (e) {
    ElMessage.error(e?.message || '应急保存失败')
  } finally {
    emergencyHandling = false
  }
}

async function checkRecoveryDraft() {
  const draft = loadDraftMeta()
  if (!draft?.session_id) return
  const blob = await loadDraftBlob(draft.session_id)
  if (!blob) {
    await clearDraft(draft.session_id)
    return
  }
  recoveryDraft.value = draft
  showRecoveryDialog.value = true
}

async function recoverDraft() {
  if (!recoveryDraft.value?.session_id) return
  recovering.value = true
  try {
    const draft = recoveryDraft.value
    if (draft.reason === 'upload_failed') {
      const operator = userStore.user?.display_name || userStore.user?.username || ''
      await uploadDraftVideo(draft, recordApi, operator)
      ElMessage.success('视频上传成功')
    }
    showRecoveryDialog.value = false
    router.push(`/recordings/review/${draft.session_id}`)
  } catch (e) {
    ElMessage.error(e?.message || '恢复失败')
  } finally {
    recovering.value = false
  }
}

async function discardRecovery() {
  if (recoveryDraft.value?.session_id) {
    await clearDraft(recoveryDraft.value.session_id)
  }
  recoveryDraft.value = null
  showRecoveryDialog.value = false
}

async function cancelRecording() {
  if (recordSessionId.value) await recordApi.cancel(recordSessionId.value)
  await videoRecorder.stop()
  videoRecorder.destroy()
  stopStatusSync()
  stopAutosave()
  recording.value = false
  recordingPaused.value = false
  recordSessionId.value = null
  recordStepCount.value = 0
  segmentCount.value = 0
  liveRecentSteps.value = []
  resetRecordingState()
}

async function runInlineDebug() {
  if (!createdCaseId.value) return
  debugRunning.value = true
  try {
    await loadDebugCaseSteps()
    const payload = { device_id: deviceId }
    if (debugCaseAppPackage.value?.trim()) {
      payload.app_package = debugCaseAppPackage.value.trim()
    }
    const res = await caseApi.run(createdCaseId.value, payload)
    debugTask.value = { id: res.data.id }
    await loadDebugCaseSteps()
    await loadDebugTaskData()
    showDebugPanel.value = true
    startDebugPolling()
    ElMessage.success(`调试任务 #${res.data.id} 已提交，投屏保持连接`)
  } finally {
    debugRunning.value = false
  }
}

function goEditor() {
  if (createdCaseId.value) {
    router.push(`/cases/editor/${createdCaseId.value}?asset=1`)
  }
}

onMounted(async () => {
  loadRecordingFeatures()
  setScreenDeviceInfo(deviceId, { name: `设备 #${deviceId}` })
  localStorage.setItem(LAST_RECORD_DEVICE_KEY, String(deviceId))
  window.addEventListener('atp-task-deleted', onTaskDeleted)
  window.addEventListener('beforeunload', onBeforeUnload)
  await loadDevice()
  initManualPickFromRoute()
  bindCanvasPipeline()
  bindRecordingActions()
  resumeIfAlive()
  window.addEventListener('resize', updateLayoutSize)
  bindScreenWrapObserver()
  if (route.query.auto_record === '1' && connected.value) {
    nextTick(() => maybeAutoStartRecording())
  }
  await checkRecoveryDraft()
})

onActivated(() => {
  bindRecordingActions()
  restoreStreamView()
})

onDeactivated(() => {
  stopDebugPolling()
})

watch(connected, (v) => {
  if (v && route.query.auto_record === '1' && !recording.value) {
    nextTick(() => maybeAutoStartRecording())
  }
  if (!v && recording.value) {
    emergencyPersist('stream_disconnect')
    return
  }
  if (!v) {
    hasFrame.value = false
    triedJpegFallback = false
    scrcpyRetryCount = 0
    clearNoFrameTimer()
    clearJpegUpgradeTimer()
    if (renderer) {
      renderer.destroy()
      renderer = null
    }
  } else {
    nextTick(() => {
      if (!renderer) bindCanvasPipeline()
      scheduleNoFrameCheck()
      maybeUpgradeFromJpeg()
    })
  }
})

watch([streamMode, fps], () => {
  maybeUpgradeFromJpeg()
})

watch(() => videoRecorder.error.value, (err) => {
  if (!err) return
  ElMessageBox.alert(err, '录屏异常', { type: 'error', confirmButtonText: '知道了' })
    .catch(() => {})
})

onUnmounted(() => {
  window.removeEventListener('atp-task-deleted', onTaskDeleted)
  window.removeEventListener('beforeunload', onBeforeUnload)
  unregisterRecordingActions()
  stopUiRefreshInterval()
  stopAutosave()
  stopDebugPolling()
  window.removeEventListener('resize', updateLayoutSize)
  unbindScreenWrapObserver()
  clearNoFrameTimer()
  clearJpegUpgradeTimer()
  if (snapshotTimer) {
    clearTimeout(snapshotTimer)
    snapshotTimer = null
  }
  stopStatusSync()
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()
})
</script>

<style scoped>
.screen-page {
  max-width: none;
  width: 100%;
}
.screen-layout {
  display: flex;
  gap: 20px;
  align-items: stretch;
  width: 100%;
  justify-content: flex-start;
  height: calc(100vh - 140px);
  min-height: 520px;
}
.screen-panel {
  flex: 0 0 auto;
  width: min(420px, 38vw);
  min-width: 260px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}
.screen-wrap {
  line-height: 0;
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  overscroll-behavior: none;
  display: flex;
  justify-content: center;
  align-items: center;
}
.screen-device {
  display: flex;
  flex-direction: column;
  border-radius: var(--atp-radius-md, 12px);
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.15);
  max-width: 100%;
  max-height: 100%;
}
.screen-frame {
  position: relative;
  background: var(--atp-screen-bg);
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
  border: 2px solid var(--atp-dark-border, #334155);
}
.crop-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  cursor: crosshair;
  background: rgba(15, 23, 42, 0.15);
}
.crop-box {
  position: absolute;
  border: 2px dashed #f59e0b;
  background: rgba(245, 158, 11, 0.12);
  pointer-events: none;
  box-sizing: border-box;
}
.record-mode-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.mode-label {
  font-size: 13px;
  color: var(--atp-text-secondary);
}
.crop-hint {
  color: #c8875e;
}
.screen-placeholder {
  position: absolute;
  inset: 0;
  z-index: 2;
  color: var(--atp-screen-text-muted);
  text-align: center;
  padding: 48px 24px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--atp-screen-bg);

  &.connected {
    background: transparent;
    pointer-events: none;
  }
}
.frame-dim {
  font-size: 13px;
  color: var(--atp-screen-text-muted);
  font-family: ui-monospace, Consolas, monospace;
  margin: 0;
}
.screen-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
  user-select: none;
  touch-action: none;
  vertical-align: top;
}
.screen-loading {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--atp-screen-text-muted);
  background: rgba(45, 42, 62, 0.75);
  padding: 5px 12px;
  border-radius: 14px;
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ops-panel {
  flex: 1;
  min-width: 420px;
  min-height: 0;
  height: 100%;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ops-row-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: stretch;
}

.ops-row-item {
  margin-bottom: 0;
  height: 100%;
}

.ops-row-item :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.send-btn {
  margin-top: 4px;
  align-self: flex-start;
}

@media (max-width: 1100px) {
  .ops-row-pair {
    grid-template-columns: 1fr;
  }
}
.screen-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  margin-left: auto;
  color: var(--atp-text-muted, #999);
  font-size: 12px;
}
.record-hint {
  font-size: 13px;
  color: var(--atp-text-secondary);
  margin-bottom: 12px;
}
.record-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.record-live {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.record-stat {
  font-size: 12px;
  color: var(--atp-text-muted);
}
.record-count {
  margin-top: 10px;
  font-size: 13px;
  color: var(--atp-primary);
}
.live-steps {
  margin-top: 8px;
  max-height: 140px;
  overflow-y: auto;
  border-top: 1px dashed var(--atp-border);
  padding-top: 6px;
}
.live-step-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  padding: 4px 0;
}
.live-step-row.warn {
  color: #b45309;
}
.live-step-row.ok .live-step-locator {
  color: var(--el-color-success);
}
.live-step-no {
  color: var(--atp-text-muted);
  min-width: 18px;
  line-height: 1.4;
}
.live-step-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.live-step-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.live-step-locator {
  font-size: 11px;
  color: var(--el-color-info);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.live-step-locator.pending {
  color: var(--atp-text-muted);
  font-style: italic;
}
.input-hint {
  font-size: 12px;
  color: var(--atp-text-muted);
  margin: 0 0 8px;
}
.recovery-meta {
  font-size: 12px;
  color: var(--atp-text-muted);
  margin-top: 8px;
}
.denoise-summary { margin-bottom: 4px; }
.denoise-col-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--atp-text-secondary); }
.denoise-step { font-size: 12px; padding: 4px 0; border-bottom: 1px solid var(--atp-border-light, #eee); font-family: monospace; }
.denoise-hint { margin-left: 8px; font-size: 12px; color: var(--atp-text-secondary); }
.debug-tip { font-size: 13px; color: var(--atp-text-secondary); line-height: 1.6; padding: 0 8px; }
.debug-hint { font-size: 12px; color: var(--atp-text-secondary); margin: 0 0 10px; }
.debug-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.debug-split { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.debug-subhead { font-size: 12px; font-weight: 600; color: var(--atp-text-secondary); margin-bottom: 8px; }
.debug-step-row {
  display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; margin-bottom: 4px; font-size: 12px;
  &.active { background: var(--atp-primary-bg); }
  &.passed { opacity: 0.55; }
  &.failed { background: var(--atp-danger-bg); }
}
.debug-step-row .step-no {
  width: 20px; height: 20px; border-radius: 50%; background: var(--atp-primary); color: #fff;
  font-size: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.debug-step-row .step-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.debug-log-line { font-size: 11px; font-family: Consolas, monospace; padding: 3px 0; border-bottom: 1px solid var(--atp-brand-50); line-height: 1.4; word-break: break-all; }
.debug-log-line.error { color: var(--atp-danger); }
.log-time { color: var(--atp-text-muted); margin-right: 6px; }

.pick-target-hint {
  margin: 8px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.pick-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.pick-loading {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.pick-dialog-body .pick-coord {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pick-locator-list {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  max-height: 180px;
  overflow: auto;
}
</style>
