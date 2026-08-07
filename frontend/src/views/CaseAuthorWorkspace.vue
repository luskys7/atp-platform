<template>
  <div class="author-workbench">
    <header class="author-header">
      <div class="author-title">
        <h1>同屏编写</h1>
        <span class="case-name">{{ caseMeta.name || '未命名用例' }}</span>
        <el-tag size="small" effect="plain" type="info">{{ steps.length }} 步</el-tag>
        <el-tag v-if="dirty" size="small" type="warning" effect="light">未保存</el-tag>
      </div>
      <div class="author-actions">
        <el-button size="small" type="primary" :loading="saving" @click="saveCase">保存步骤</el-button>
        <el-button size="small" @click="goEditor">返回编辑器</el-button>
      </div>
    </header>

    <div class="author-body" v-loading="loading">
      <!-- 左：投屏控机 -->
      <main class="col-screen">
        <ScreenCanvasPanel
          v-if="selectedDeviceId"
          ref="screenRef"
          :key="selectedDeviceId"
          :device-id="selectedDeviceId"
          :auto-connect="true"
          :controllable="true"
          :max-height="'calc(100vh - 168px)'"
          @control-tap="onControlTap"
          @control-swipe="onControlSwipe"
          @control-longpress="onControlLongPress"
          @control-nav="onControlNav"
        />
        <div v-else class="no-device">
          <el-icon :size="48"><Iphone /></el-icon>
          <p>请选择在线 Android 设备</p>
        </div>
      </main>

      <!-- 右：编写控制 + 步骤 -->
      <aside class="col-ops">
        <div class="ops-block">
          <div class="ops-head">编写控制</div>
          <div class="ops-controls">
            <el-select
              v-model="selectedDeviceId"
              placeholder="选择设备"
              style="width:100%"
              @change="markDirty"
            >
              <el-option
                v-for="d in onlineDevices"
                :key="d.id"
                :label="`${d.name || d.serial_number} (${d.status})`"
                :value="d.id"
                :disabled="d.platform !== 'android' || d.status === 'offline'"
              />
            </el-select>
            <el-input
              v-model="appPackage"
              placeholder="目标 App 包名"
              clearable
              @change="markDirty"
            />
            <div class="switch-row">
              <span>自动捕获</span>
              <el-switch v-model="autoCapture" />
            </div>
            <div class="switch-row">
              <span>识别控件</span>
              <el-switch v-model="inspectEnabled" />
            </div>
            <el-alert
              v-if="!autoCapture"
              type="info"
              :closable="false"
              show-icon
              title="自动捕获已关：操作只控机。点「加入上一步」写入步骤。"
            />
            <el-button
              v-if="!autoCapture"
              type="primary"
              plain
              style="width:100%"
              :disabled="!pendingManual"
              @click="commitManual"
            >
              加入上一步操作
            </el-button>
            <el-button type="success" plain style="width:100%" :disabled="!selectedDeviceId || !appPackage" @click="launchApp">
              启动应用
            </el-button>
          </div>
        </div>

        <div class="ops-block">
          <div class="ops-head">快捷工具</div>
          <div class="tool-grid">
            <el-button size="small" @click="addWait">等待 2s</el-button>
            <el-button size="small" @click="addRelativeTime">当前时间+5分钟</el-button>
            <el-button size="small" @click="promptAssert">断言文本</el-button>
            <el-button size="small" @click="promptInput">输入文本</el-button>
          </div>
        </div>

        <div class="ops-block flex-grow">
          <div class="ops-head">
            <span>步骤列表 ({{ steps.length }})</span>
            <el-button link type="danger" size="small" :disabled="!steps.length" @click="clearSteps">清空</el-button>
          </div>
          <el-scrollbar class="steps-scroll">
            <div
              v-for="(step, idx) in steps"
              :key="step.id"
              class="step-row"
              :class="{ selected: selectedId === step.id, disabled: step.enabled === false }"
              @click="selectedId = step.id"
            >
              <span class="step-no">{{ idx + 1 }}</span>
              <div class="step-body">
                <div class="step-type">
                  {{ stepTypeLabel(step.type) }}
                  <el-tag v-if="step.locator_valid" size="small" type="success" effect="plain">已定位</el-tag>
                  <el-tag v-else-if="step.type === 'tap_xy' || step.type === 'click'" size="small" type="info" effect="plain">坐标</el-tag>
                </div>
                <div class="step-desc">{{ stepSummary(step) }}</div>
                <div v-if="stepLocator(step)" class="step-locator">{{ stepLocator(step) }}</div>
              </div>
              <div class="step-ops" @click.stop>
                <el-switch v-model="step.enabled" size="small" @change="markDirty" />
                <el-button link size="small" :disabled="idx === 0" @click="moveStep(step.id, -1)">↑</el-button>
                <el-button link size="small" :disabled="idx === steps.length - 1" @click="moveStep(step.id, 1)">↓</el-button>
                <el-button link type="danger" size="small" @click="removeStep(step.id)">删</el-button>
              </div>
            </div>
            <el-empty v-if="!steps.length" description="在左侧投屏操作，步骤将自动出现在此" :image-size="56" />
          </el-scrollbar>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { caseApi, deviceApi } from '@/api'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import ScreenCanvasPanel from '@/components/ScreenCanvasPanel.vue'
import { useCaseAuthorCapture, createEmptyStep, resetStepSeq } from '@/composables/useCaseAuthorCapture'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const caseId = computed(() => route.params.id ? Number(route.params.id) : null)
const deviceIdFromQuery = route.query.deviceId

const loading = ref(false)
const saving = ref(false)
const dirty = ref(false)
const caseMeta = reactive({
  name: '',
  platform: 'android',
  app_package: '',
  timeout_seconds: 3600,
  enable_recording: true,
  human_delay: false,
  case_status: 'draft',
  folder_id: null
})
const steps = ref([])
const onlineDevices = ref([])
const selectedDeviceId = ref(deviceIdFromQuery ? Number(deviceIdFromQuery) : null)
const appPackage = ref('')
const autoCapture = ref(true)
const inspectEnabled = ref(true)
const selectedId = ref(null)
const screenRef = ref(null)

const typeLabels = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', launch: '启动', swipe: '滑动',
  long_press: '长按', assert_text: '断言文本', assert_exists: '存在', tap_ocr: 'OCR点击',
  set_relative_time: '设置相对时间', press_key: '按键',
  custom_script: '自定义脚本'
}

function stepTypeLabel(t) { return typeLabels[t] || t }
function stepLocator(step) { return formatStepLocator(step) }
function stepSummary(step) {
  const target = formatStepTarget(step)
  if (target) return target
  if (step.type === 'wait') return `${step.seconds || 0}s`
  return step.element_name || step.expected || step.display_name || ''
}

function markDirty() { dirty.value = true }

const capture = useCaseAuthorCapture(steps, {
  autoCapture,
  inspectEnabled,
  getDeviceId: () => selectedDeviceId.value,
  getNativeSize: () => {
    const panel = screenRef.value
    if (!panel) return { w: 0, h: 0 }
    const w = typeof panel.nativeW === 'object' ? panel.nativeW?.value : panel.nativeW
    const h = typeof panel.nativeH === 'object' ? panel.nativeH?.value : panel.nativeH
    return { w: Number(w) || 0, h: Number(h) || 0 }
  }
})

const pendingManual = capture.pendingManual

function onControlTap(p) {
  capture.handleTap(p)
  markDirty()
}
function onControlSwipe(p) {
  capture.handleSwipe(p)
  markDirty()
}
function onControlLongPress(p) {
  capture.handleLongPress(p)
  markDirty()
}
function onControlNav({ key }) {
  if (!autoCapture.value) return
  capture.addToolStep('press_key', { key })
  markDirty()
}
function commitManual() {
  const id = capture.commitPendingManual()
  if (id) {
    markDirty()
    ElMessage.success('已加入步骤')
  } else {
    ElMessage.warning('暂无待加入的操作')
  }
}
function removeStep(id) {
  capture.removeStep(id)
  if (selectedId.value === id) selectedId.value = null
  markDirty()
}
function moveStep(id, dir) {
  capture.moveStep(id, dir)
  markDirty()
}
async function clearSteps() {
  try {
    await ElMessageBox.confirm('确认清空全部步骤？', '提示', { type: 'warning' })
    steps.value = []
    markDirty()
  } catch { /* cancel */ }
}

function addWait() {
  capture.addToolStep('wait', { seconds: 2 })
  markDirty()
}
function addRelativeTime() {
  capture.addToolStep('set_relative_time', { offset_minutes: 5, confirm: false })
  markDirty()
  ElMessage.success('已添加「当前时间+5分钟」步骤')
}
async function promptAssert() {
  try {
    const { value } = await ElMessageBox.prompt('请输入期望文本', '断言文本', {
      inputPlaceholder: '如：登录成功',
      confirmButtonText: '加入步骤'
    })
    if (!value?.trim()) return
    capture.addToolStep('assert_text', { expected: value.trim() })
    markDirty()
  } catch { /* cancel */ }
}
async function promptInput() {
  try {
    const { value } = await ElMessageBox.prompt('请输入要发送到设备的文本', '输入文本', {
      inputPlaceholder: '文本内容',
      confirmButtonText: '发送并记步骤'
    })
    if (!value?.trim()) return
    if (selectedDeviceId.value) {
      try {
        await deviceApi.screenInput(selectedDeviceId.value, { text: value.trim() })
      } catch (e) {
        ElMessage.warning(e?.message || '文本已记步骤，但发送设备失败')
      }
    }
    capture.addToolStep('input', { text: value.trim() })
    markDirty()
  } catch { /* cancel */ }
}

async function launchApp() {
  const pkg = (appPackage.value || '').trim()
  if (!pkg || !selectedDeviceId.value) return
  try {
    // 通过 adb monkey / am start 由后端 key/input 通道不易启动；用 launch 步骤 + shell 风格：先记步骤并尝试 warm
    await deviceApi.screenWarmUi(selectedDeviceId.value, { blocking: false }).catch(() => {})
    capture.addToolStep('launch', { app_package: pkg })
    caseMeta.app_package = pkg
    markDirty()
    ElMessage.success('已添加启动应用步骤（保存后调试时会真正拉起 App）')
  } catch (e) {
    ElMessage.error(e?.message || '启动失败')
  }
}

function buildVisualJson() {
  return JSON.stringify({
    version: 1,
    human_delay: !!caseMeta.human_delay,
    steps: steps.value.map(({ id, ...rest }) => rest)
  })
}

function buildCasePayload() {
  return {
    name: caseMeta.name || '同屏编写用例',
    platform: caseMeta.platform || 'android',
    script_type: 'visual',
    steps_content: buildVisualJson(),
    app_package: appPackage.value || caseMeta.app_package || '',
    timeout_seconds: caseMeta.timeout_seconds || 3600,
    enable_recording: caseMeta.enable_recording !== false,
    case_status: caseMeta.case_status || 'draft',
    folder_id: caseMeta.folder_id || null
  }
}

async function saveCase() {
  if (!caseMeta.name?.trim()) {
    try {
      const { value } = await ElMessageBox.prompt('请填写用例名称', '保存用例', {
        inputValue: caseMeta.name || '同屏编写用例',
        confirmButtonText: '保存'
      })
      if (!value?.trim()) return
      caseMeta.name = value.trim()
    } catch {
      return
    }
  }
  saving.value = true
  try {
    caseMeta.app_package = appPackage.value || caseMeta.app_package
    if (caseId.value) {
      await caseApi.update(caseId.value, buildCasePayload())
      ElMessage.success('步骤已保存')
    } else {
      const res = await caseApi.create(buildCasePayload())
      ElMessage.success('用例已创建并保存')
      await router.replace({
        path: `/cases/editor/${res.data.id}/author`,
        query: selectedDeviceId.value ? { deviceId: selectedDeviceId.value } : {}
      })
    }
    dirty.value = false
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goEditor() {
  if (caseId.value) router.push(`/cases/editor/${caseId.value}?asset=1`)
  else router.push('/cases/editor?asset=1')
}

async function loadCase() {
  if (!caseId.value) {
    caseMeta.name = '同屏编写用例'
    return
  }
  const res = await caseApi.get(caseId.value)
  const c = res.data
  caseMeta.name = c.name || ''
  caseMeta.platform = c.platform || 'android'
  caseMeta.app_package = c.app_package || ''
  caseMeta.timeout_seconds = c.timeout_seconds || 3600
  caseMeta.enable_recording = c.enable_recording !== false
  caseMeta.case_status = c.case_status || 'draft'
  caseMeta.folder_id = c.folder_id || null
  caseMeta.human_delay = false
  appPackage.value = c.app_package || ''
  try {
    const parsed = JSON.parse(c.steps_content || '{}')
    caseMeta.human_delay = !!parsed.human_delay
    steps.value = (parsed.steps || []).map(s => createEmptyStep({
      ...s,
      enabled: s.enabled !== false
    }))
    const maxId = steps.value.reduce((m, s) => Math.max(m, Number(s.id) || 0), 0)
    resetStepSeq(maxId + 1)
  } catch {
    steps.value = []
  }
}

async function loadDevices() {
  const res = await deviceApi.list({ page: 1, page_size: 50, platform: 'android' })
  onlineDevices.value = (res.data.list || []).filter(d => d.status !== 'offline')
  if (!selectedDeviceId.value && onlineDevices.value.length) {
    const online = onlineDevices.value.find(d => d.status === 'online')
    selectedDeviceId.value = online?.id || onlineDevices.value[0].id
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadCase(), loadDevices()])
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.author-workbench {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: var(--atp-bg);
  overflow: hidden;
}

.author-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--atp-bg-elevated);
  border-bottom: 1px solid var(--atp-border-light);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.author-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;

  h1 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
}

.case-name {
  font-size: 14px;
  color: var(--atp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.author-body {
  flex: 1;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0;
  min-height: 0;
}

.col-screen {
  padding: 12px 16px;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  background: var(--atp-brand-50);
  border-right: 1px solid var(--atp-border-light);

  :deep(.screen-panel) {
    max-height: calc(100vh - 168px);
  }
}

.col-ops {
  background: var(--atp-bg-elevated);
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.ops-block {
  border-bottom: 1px solid var(--atp-border-light);
  flex-shrink: 0;

  &.flex-grow {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    flex-shrink: 1;
  }
}

.ops-head {
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--atp-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.ops-controls {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.tool-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px 14px;
}

.steps-scroll {
  flex: 1;
  min-height: 0;
  padding: 8px;
}

.step-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: background 0.15s;
  border: 1px solid transparent;

  &:hover { background: var(--atp-primary-bg); }

  &.selected {
    background: var(--atp-primary-bg);
    border-color: rgba(8, 145, 178, 0.35);
  }
  &.disabled { opacity: 0.55; }
}

.step-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--atp-primary);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.step-body { flex: 1; min-width: 0; }
.step-type {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}
.step-desc {
  font-size: 11px;
  color: var(--atp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-locator {
  font-size: 10px;
  color: var(--el-color-info);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.no-device {
  flex: 1;
  width: 280px;
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--atp-text-secondary);
  gap: 12px;
}

@media (max-width: 960px) {
  .author-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }
  .col-screen {
    border-right: none;
    border-bottom: 1px solid var(--atp-border-light);
    max-height: 50vh;
  }
  .col-ops {
    min-height: 40vh;
  }
}
</style>
