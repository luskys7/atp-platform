<template>
  <div class="page-container">
    <PageHeader title="录制用例审阅" subtitle="视频与步骤双向联动，在线编辑后生成用例">
      <template #actions>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="manual">手工测试视图</el-radio-button>
          <el-radio-button value="auto">自动化视图</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="saving" @click="saveAndGenerate">生成手工用例</el-button>
        <el-button type="warning" :loading="automationSaving" @click="saveAsAutomation">转为自动化用例</el-button>
        <el-button type="success" plain :loading="regressionSaving" @click="submitRegression">提交回归</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="14">
        <AppCard title="录制视频" :hover="false">
          <RecordingPlayer
            v-if="videoUrl"
            ref="playerRef"
            :src="videoUrl"
            :markers="videoMarkers"
            :active-marker-index="activeStepIdx"
            :annotations="videoAnnotations"
            editable-annotations
            @timeupdate="onVideoTime"
            @seek="onVideoSeek"
            @marker-click="selectStep"
            @marker-active="idx => activeStepIdx = idx"
            @annotations-change="onAnnotationsChange"
          />
          <el-empty v-else description="暂无关联视频" />
        </AppCard>
      </el-col>
      <el-col :span="10">
        <AppCard title="标准用例字段" :hover="false">
          <el-alert v-if="meta.emergency" type="warning" :closable="false" show-icon title="应急保存录制" description="该录制因断连/异常中断，步骤可能不完整，请人工核对。" style="margin-bottom:12px" />
          <el-form label-width="88px" size="small">
            <el-form-item label="用例名称">
              <el-input v-model="caseMeta.name" />
            </el-form-item>
            <el-form-item label="前置条件">
              <el-input v-model="meta.precondition" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="预期结果">
              <el-input v-model="meta.expected_result" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="操作环境">
              <el-input v-model="meta.environment" />
            </el-form-item>
            <el-form-item label="真人模拟">
              <el-switch v-model="caseMeta.human_delay" />
            </el-form-item>
            <el-form-item label="分段生成">
              <el-switch v-model="splitBySegments" />
              <span class="hint">按切点拆分为多条用例</span>
            </el-form-item>
            <el-form-item v-if="meta.recognition_rate != null" label="识别率">
              <el-tag type="success">{{ meta.recognition_rate }}%</el-tag>
              <span v-if="meta.locator_hit_rate != null" class="hint" style="margin-left:8px">定位命中 {{ meta.locator_hit_rate }}%</span>
            </el-form-item>
            <el-form-item v-if="meta.startup_ms != null" label="启动耗时">
              <el-tag :type="meta.startup_ms <= 3000 ? 'success' : 'warning'">{{ meta.startup_ms }} ms</el-tag>
            </el-form-item>
            <el-form-item v-if="meta.record_fps_avg != null" label="录制帧率">
              <el-tag type="info">{{ meta.record_fps_avg }} fps</el-tag>
              <span v-if="meta.paint_ms_avg != null" class="hint" style="margin-left:8px">绘制 {{ meta.paint_ms_avg }}ms/帧</span>
            </el-form-item>
            <el-form-item v-if="meta.performance_grade" label="录制性能">
              <el-tag :type="perfGradeType(meta.performance_grade)">{{ perfGradeLabel(meta.performance_grade) }}</el-tag>
              <el-tag v-if="meta.cpu_ok != null" size="small" :type="meta.cpu_ok ? 'success' : 'warning'" style="margin-left:8px">
                CPU {{ meta.cpu_ok ? '达标' : '偏高' }}
              </el-tag>
              <span v-if="meta.long_task_count != null" class="hint" style="margin-left:8px">长任务 {{ meta.long_task_count }} 次</span>
            </el-form-item>
            <el-form-item v-if="meta.task_id" label="关联任务">
              <router-link :to="`/tasks/${meta.task_id}`">任务 #{{ meta.task_id }}</router-link>
            </el-form-item>
            <el-form-item label="定位校验">
              <el-button size="small" :loading="validatingLocators" @click="runValidateLocators">
                二次回放定位校验（需设备在线）
              </el-button>
              <div v-if="locatorValidation" class="locator-validation">
                <el-tag :type="locatorValidation.passed ? 'success' : 'warning'" size="small">
                  命中率 {{ locatorValidation.locator_hit_rate }}% / 阈值 {{ locatorValidation.threshold }}%
                </el-tag>
                <span class="hint">{{ locatorValidation.hits }}/{{ locatorValidation.checkable }} 步命中</span>
              </div>
            </el-form-item>
            <el-form-item v-if="validationElements.length" label="校验元素">
              <div class="validation-tags">
                <el-tag v-for="el in validationElements" :key="el" size="small" type="info">{{ el }}</el-tag>
              </div>
            </el-form-item>
          </el-form>
        </AppCard>

        <AppCard title="操作步骤" :hover="false" style="margin-top:16px">
          <div class="step-toolbar">
            <el-button size="small" @click="addStep">新增步骤</el-button>
            <el-button size="small" :disabled="selectedIdx < 0" @click="splitStep">拆分步骤</el-button>
            <el-button size="small" :disabled="selectedIdx <= 0" @click="mergeUp">向上合并</el-button>
          </div>
          <el-scrollbar max-height="420px">
            <div
              v-for="(step, idx) in steps"
              :key="idx"
              class="review-step"
              :class="{ active: activeStepIdx === idx }"
              @click="selectStep(idx)"
            >
              <div class="step-head">
                <span class="step-no">{{ idx + 1 }}</span>
                <el-tag size="small">{{ stepSummary(step) }}</el-tag>
                <el-tag v-if="stepLocatorLine(step)" size="small" type="info">{{ stepLocatorLine(step) }}</el-tag>
                <el-tag v-if="step.video_offset_ms == null" size="small" type="warning">无视频对应</el-tag>
                <el-tag v-if="step.needs_manual_fix" size="small" type="danger">需人工补定位</el-tag>
                <el-tag v-else-if="viewMode === 'auto' && step.locator_valid === false" size="small" type="warning">定位未校验</el-tag>
                <el-button
                  v-if="needsManualPick(step)"
                  size="small"
                  type="primary"
                  link
                  @click.stop="openDevicePick(idx)"
                >
                  设备补定位
                </el-button>
                <el-button
                  v-if="needsManualPick(step)"
                  size="small"
                  link
                  @click.stop="openManualLocatorDialog(idx)"
                >
                  手动填写
                </el-button>
                <el-tag v-if="step.desensitized" size="small" type="info">已脱敏</el-tag>
                <el-button size="small" type="danger" link @click.stop="removeStep(idx)">删除</el-button>
              </div>
              <img v-if="step.snapshot_thumb" :src="step.snapshot_thumb" class="step-snap" alt="快照" />
              <div class="step-body">
                <template v-if="viewMode === 'manual'">
                  <el-input v-model="step.display_name" placeholder="业务描述" size="small" />
                  <div v-if="stepLocatorLine(step)" class="locator-detail">{{ stepLocatorLine(step) }}</div>
                  <div v-if="allLocators(step).length" class="locator-detail">
                    <div v-for="item in allLocators(step)" :key="item.type + item.value">
                      {{ locatorTypeLabel(item.type) }}: {{ item.value }}
                    </div>
                  </div>
                  <el-input v-if="step.type === 'input'" v-model="step.text" size="small" style="margin-top:6px" />
                </template>
                <template v-else>
                  <div class="locator-line">{{ step.element_name || step.type }}</div>
                  <div v-if="allLocators(step).length" class="locator-detail">
                    <div v-for="item in allLocators(step)" :key="item.type + item.value">
                      {{ locatorTypeLabel(item.type) }}: {{ item.value }}
                    </div>
                  </div>
                  <div v-else-if="stepLocatorLine(step)" class="locator-detail">{{ stepLocatorLine(step) }}</div>
                </template>
                <div v-if="step.video_offset_ms != null" class="offset-tag">
                  {{ formatOffset(step.video_offset_ms) }}
                </div>
              </div>
            </div>
          </el-scrollbar>
        </AppCard>
      </el-col>
    </el-row>

    <el-dialog v-model="showManualLocatorDialog" title="手动填写定位" width="480px" destroy-on-close>
      <el-form v-if="manualLocatorStepIdx >= 0" label-width="96px" size="small">
        <el-form-item label="步骤">
          #{{ manualLocatorStepIdx + 1 }} · {{ stepSummary(steps[manualLocatorStepIdx]) }}
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="manualLocatorForm.display_name" placeholder="业务描述，如：提交按钮" />
        </el-form-item>
        <el-form-item label="元素名">
          <el-input v-model="manualLocatorForm.element_name" placeholder="脚本变量名" />
        </el-form-item>
        <el-form-item label="定位方式">
          <el-select v-model="manualLocatorForm.locator_type" style="width:100%">
            <el-option label="Resource ID" value="id" />
            <el-option label="文本" value="text" />
            <el-option label="Content-Desc" value="content_desc" />
            <el-option label="XPath" value="xpath" />
            <el-option label="OCR 文本" value="ocr" />
          </el-select>
        </el-form-item>
        <el-form-item label="定位值">
          <el-input v-model="manualLocatorForm.locator_value" placeholder="Resource ID / 文本 / XPath" />
        </el-form-item>
        <el-form-item v-if="steps[manualLocatorStepIdx]?.x != null" label="坐标">
          <span class="hint">({{ steps[manualLocatorStepIdx].x }}, {{ steps[manualLocatorStepIdx].y }}) 保留作兜底</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showManualLocatorDialog = false">取消</el-button>
        <el-button type="primary" :loading="manualLocatorSaving" @click="saveManualLocator">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { recordApi, recordingApi, caseApi } from '@/api'
import RecordingPlayer from '@/components/RecordingPlayer.vue'
import { formatStepTarget, formatStepLocator, formatAllLocators, formatLocatorType, pickPrimaryLocator } from '@/utils/stepDisplay'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const recordId = route.params.id
const playerRef = ref(null)
const videoUrl = ref('')
const videoAnnotations = ref([])
const steps = ref([])
const meta = reactive({
  precondition: '',
  expected_result: '',
  environment: 'android',
  validation_elements: []
})
const caseMeta = reactive({ name: '录制用例', platform: 'android', human_delay: false })
const viewMode = ref('auto')
const activeStepIdx = ref(-1)
const selectedIdx = ref(-1)
const splitBySegments = ref(false)
const saving = ref(false)
const automationSaving = ref(false)
const regressionSaving = ref(false)
const validatingLocators = ref(false)
const locatorValidation = ref(null)
const recordDeviceId = ref(null)
const recordVersionLabel = ref('')
const showManualLocatorDialog = ref(false)
const manualLocatorStepIdx = ref(-1)
const manualLocatorSaving = ref(false)
const manualLocatorForm = reactive({
  display_name: '',
  element_name: '',
  locator_type: 'id',
  locator_value: ''
})

const STEP_TYPE_LABELS = {
  press_key: '系统键',
  dismiss_popup: '关弹窗(自动)',
  check_anomaly: '异常检测(自动)',
  tap_xy: '坐标点击',
  click: '点击',
  select: '下拉选择',
  confirm_dialog: '弹窗确认',
  upload: '文件上传',
  hover: '鼠标悬浮',
  rich_text: '富文本输入',
  swipe: '滑动',
  input: '输入',
  launch: '启动应用',
  long_press: '长按',
  switch_context: '切换上下文'
}

const KEY_LABELS = { home: '主屏幕', back: '返回', recent: '多任务', menu: '菜单' }

function stepSummary(step) {
  const target = formatStepTarget(step)
  if (target) return target
  if (step.type === 'press_key') {
    const key = step.key || step.key_name || 'back'
    return `${STEP_TYPE_LABELS.press_key} · ${KEY_LABELS[key] || key}`
  }
  return STEP_TYPE_LABELS[step.type] || step.type || '步骤'
}

function stepLocatorLine(step) {
  return formatStepLocator(step)
}

function allLocators(step) {
  return formatAllLocators(step)
}

function locatorTypeLabel(type) {
  return formatLocatorType(type)
}

function needsManualPick(step) {
  if (!step) return false
  if (step.needs_manual_fix) return true
  if (step.type === 'tap_xy') return true
  if (['click', 'select', 'input'].includes(step.type) && step.locator_valid === false && !pickPrimaryLocator(step)) {
    return true
  }
  return false
}

function openDevicePick(idx) {
  if (!recordDeviceId.value) {
    ElMessage.warning('未找到录制设备，请确认设备在线')
    return
  }
  router.push({
    path: `/element-picker/${recordDeviceId.value}`,
    query: {
      pickRecord: String(recordId),
      pickStep: String(idx),
      returnTo: route.fullPath,
      versionTag: meta.version_label || recordVersionLabel.value || '',
      envTag: meta.environment || ''
    }
  })
}

function openManualLocatorDialog(idx) {
  const step = steps.value[idx]
  if (!step) return
  manualLocatorStepIdx.value = idx
  manualLocatorForm.display_name = step.display_name || formatStepTarget(step) || ''
  manualLocatorForm.element_name = step.element_name || ''
  const primary = pickPrimaryLocator(step)
  manualLocatorForm.locator_type = primary?.type === 'resource_id' ? 'id' : (primary?.type || 'id')
  manualLocatorForm.locator_value = primary?.value || ''
  showManualLocatorDialog.value = true
}

async function saveManualLocator() {
  if (!manualLocatorForm.locator_value?.trim()) {
    ElMessage.warning('请填写定位值')
    return
  }
  const idx = manualLocatorStepIdx.value
  const step = steps.value[idx]
  if (!step) return
  manualLocatorSaving.value = true
  try {
    const lt = manualLocatorForm.locator_type
    const val = manualLocatorForm.locator_value.trim()
    const locators = { ...(step.locators || {}), [lt]: val }
    if (lt === 'id') locators.resource_id = val.includes(':id/') ? val : val
    const patch = {
      x: step.x,
      y: step.y,
      display_name: manualLocatorForm.display_name || undefined,
      element_name: manualLocatorForm.element_name || undefined,
      locator_type: lt,
      locator_value: val,
      locators,
      locator_valid: true,
      suggested_step_type: lt === 'ocr' ? 'tap_ocr' : 'click'
    }
    await recordApi.patchStepLocator(recordId, idx, patch)
    Object.assign(step, {
      type: lt === 'ocr' ? 'tap_ocr' : 'click',
      display_name: patch.display_name || step.display_name,
      element_name: patch.element_name || step.element_name,
      locator_type: lt,
      locator_value: val,
      locators,
      locator_valid: true,
      needs_manual_fix: false
    })
    showManualLocatorDialog.value = false
    ElMessage.success('定位已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    manualLocatorSaving.value = false
  }
}

const videoMarkers = computed(() =>
  steps.value
    .map((step, idx) => ({
      index: idx,
      offsetMs: step.video_offset_ms,
      label: `${idx + 1}. ${stepSummary(step)}`
    }))
    .filter(m => m.offsetMs != null && m.offsetMs >= 0)
)

const validationElements = computed(() => {
  const fromMeta = meta.validation_elements
  if (Array.isArray(fromMeta) && fromMeta.length) return fromMeta
  const set = new Set()
  steps.value.forEach(step => {
    if (step.element_name) set.add(step.element_name)
  })
  return [...set]
})

function formatOffset(ms) {
  const s = Math.floor(ms / 1000)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function perfGradeLabel(g) {
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
}

function perfGradeType(g) {
  return { good: 'success', fair: 'warning', heavy: 'danger' }[g] || 'info'
}

async function runValidateLocators() {
  validatingLocators.value = true
  try {
    const res = await recordApi.validateLocators(recordId)
    locatorValidation.value = res.data || null
    const details = res.data?.details || []
    details.forEach(row => {
      const step = steps.value[row.step_index]
      if (step) step.locator_valid = row.valid
    })
    if (res.data?.passed) {
      ElMessage.success(`定位校验通过：${res.data.locator_hit_rate}%`)
    } else {
      ElMessage.warning(`定位命中率 ${res.data?.locator_hit_rate ?? 0}%，低于阈值 ${res.data?.threshold ?? 98}%`)
    }
  } catch (e) {
    ElMessage.error(e?.message || '定位校验失败，请确认设备在线')
  } finally {
    validatingLocators.value = false
  }
}

function stripLegacyAutoSteps(stepsArr) {
  return stepsArr.filter(step => {
    if (step._auto_generated) return false
    if (step.video_offset_ms == null
        && (step.type === 'dismiss_popup' || step.type === 'check_anomaly')) {
      return false
    }
    return true
  })
}

async function load() {
  const res = await recordApi.get(recordId)
  recordDeviceId.value = res.data.device_id || res.data.deviceId || null
  recordVersionLabel.value = res.data.version_label || res.data.versionLabel || ''
  const parsed = JSON.parse(res.data.steps_json || '{}')
  steps.value = stripLegacyAutoSteps((parsed.steps || []).map(s => ({ ...s })))
  Object.assign(meta, parsed.meta || {})
  videoAnnotations.value = Array.isArray(parsed.meta?.video_annotations)
    ? parsed.meta.video_annotations.map(a => ({ ...a }))
    : []
  caseMeta.platform = meta.platform || 'android'
  if (meta.recording_id) {
    const playRes = await recordingApi.playback(meta.recording_id)
    const url = playRes.data.url
    videoUrl.value = url.startsWith('/api/')
      ? await recordingApi.playbackStream(meta.recording_id)
      : url
  }
}

function onVideoTime(sec) {
  const ms = sec * 1000
  let nearest = -1
  let minDiff = Infinity
  steps.value.forEach((step, idx) => {
    const off = step.video_offset_ms
    if (off == null) return
    const diff = Math.abs(off - ms)
    if (diff < minDiff && off <= ms + 800) {
      minDiff = diff
      nearest = idx
    }
  })
  if (nearest >= 0) activeStepIdx.value = nearest
}

function onVideoSeek(sec) {
  onVideoTime(sec)
}

function selectStep(idx) {
  selectedIdx.value = idx
  activeStepIdx.value = idx
  const step = steps.value[idx]
  if (step?.video_offset_ms != null && playerRef.value) {
    playerRef.value.seekToMs(step.video_offset_ms)
  }
}

function addStep() {
  steps.value.push({ type: 'wait', seconds: 1, enabled: true, display_name: '等待' })
}

function removeStep(idx) {
  steps.value.splice(idx, 1)
}

function mergeUp() {
  if (selectedIdx.value <= 0) return
  const prev = steps.value[selectedIdx.value - 1]
  const cur = steps.value[selectedIdx.value]
  prev.display_name = `${prev.display_name || stepSummary(prev)} → ${cur.display_name || stepSummary(cur)}`
  steps.value.splice(selectedIdx.value, 1)
  selectedIdx.value = selectedIdx.value - 1
}

function splitStep() {
  if (selectedIdx.value < 0) return
  const cur = steps.value[selectedIdx.value]
  const waitStep = {
    type: 'wait',
    seconds: 1,
    enabled: true,
    display_name: '等待',
    video_offset_ms: cur.video_offset_ms != null ? cur.video_offset_ms + 500 : null
  }
  steps.value.splice(selectedIdx.value + 1, 0, waitStep)
  selectedIdx.value = selectedIdx.value + 1
  ElMessage.success('已在当前步骤后插入等待步骤，可继续编辑拆分')
}

let annotationSaveTimer = null
async function onAnnotationsChange(list) {
  videoAnnotations.value = list
  meta.video_annotations = list
  clearTimeout(annotationSaveTimer)
  annotationSaveTimer = setTimeout(async () => {
    try {
      await recordApi.updateSteps(recordId, { meta: { ...meta, video_annotations: list } })
    } catch {
      ElMessage.warning('标注保存失败，请稍后重试')
    }
  }, 600)
}

async function saveAndGenerate() {
  saving.value = true
  try {
    await recordApi.updateSteps(recordId, { steps: steps.value, meta })
    const payload = buildPayload()
    if (splitBySegments.value) {
      const res = await recordApi.toCasesBySegments(recordId, payload)
      ElMessage.success(`已生成 ${res.data.length} 条用例`)
      router.push('/cases')
    } else {
      const res = await recordApi.toCase(recordId, payload)
      ElMessage.success('用例已生成')
      router.push(`/cases/editor/${res.data.id}?asset=1`)
    }
  } finally {
    saving.value = false
  }
}

function buildPayload() {
  return {
    name: caseMeta.name,
    platform: caseMeta.platform,
    precondition: meta.precondition,
    expected_result: meta.expected_result,
    environment: meta.environment,
    human_delay: caseMeta.human_delay
  }
}

async function saveAsAutomation() {
  automationSaving.value = true
  try {
    await recordApi.updateSteps(recordId, { steps: steps.value, meta })
    const res = await recordApi.toAutomationCase(recordId, buildPayload())
    ElMessage.success('已转为自动化用例，可直接提交回归')
    router.push(`/cases/editor/${res.data.id}?asset=1`)
  } finally {
    automationSaving.value = false
  }
}

async function submitRegression() {
  regressionSaving.value = true
  try {
    await recordApi.updateSteps(recordId, { steps: steps.value, meta })
    const caseRes = await recordApi.toAutomationCase(recordId, buildPayload())
    const taskRes = await caseApi.run(caseRes.data.id, {})
    ElMessage.success(`回归任务 #${taskRes.data.id} 已创建并提交调度`)
    router.push(`/tasks/${taskRes.data.id}`)
  } catch (e) {
    ElMessage.error(e?.message || '提交回归失败')
  } finally {
    regressionSaving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.step-toolbar {
  margin-bottom: 10px;
  display: flex;
  gap: 8px;
}
.review-step {
  border: 1px solid var(--atp-border);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.review-step.active {
  border-color: var(--el-color-primary);
  background: rgba(2, 132, 199, 0.06);
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.step-no {
  font-weight: 600;
  color: var(--atp-text-muted);
  min-width: 20px;
}
.locator-line {
  font-size: 13px;
  font-weight: 500;
}
.locator-detail {
  font-size: 11px;
  color: var(--atp-text-muted);
  margin-top: 4px;
  word-break: break-all;
}
.offset-tag {
  font-size: 11px;
  color: var(--el-color-primary);
  margin-top: 4px;
}
.step-snap {
  width: 100%;
  max-width: 200px;
  border-radius: 6px;
  margin: 6px 0;
  border: 1px solid var(--atp-border);
}
.hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--atp-text-muted);
}
.validation-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.locator-validation {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
