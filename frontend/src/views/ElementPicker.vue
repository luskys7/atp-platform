<template>
  <div class="page-container picker-page">
    <!-- 设备选择（无 deviceId 时） -->
    <template v-if="!deviceId">
      <PageHeader title="控件获取" subtitle="选择在线安卓设备，在投屏画面点击控件即可获取定位信息，无需新建录制用例">
        <template #actions>
          <el-button :loading="devicesLoading" @click="loadDevices">刷新设备</el-button>
          <el-button type="warning" plain :loading="rebootingOffline" @click="rebootAllOffline">重启离线设备</el-button>
          <el-button type="primary" plain @click="$router.push('/controls')">进入控件库</el-button>
        </template>
      </PageHeader>

      <div class="status-banner" :class="hubBanner.tone">
        <el-icon><component :is="hubBanner.icon" /></el-icon>
        <span class="status-banner__text">{{ hubBanner.text }}</span>
        <div v-if="hubBanner.tone === 'danger'" class="status-banner__actions">
          <el-button size="small" type="warning" :loading="rebootingOffline" @click="rebootAllOffline">一键重启全部设备</el-button>
          <el-button size="small" type="primary" :loading="syncingUsb" @click="syncUsbFromHub">同步 USB 接入新设备</el-button>
        </div>
      </div>

      <AppCard title="可用设备" :hover="false">
        <el-table v-loading="devicesLoading" :data="androidDevices" stripe empty-text="">
          <el-table-column label="设备名称" min-width="140">
            <template #default="{ row }">
              <span class="dev-name">{{ row.name || row.model || row.serial_number || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="设备序列号" min-width="160">
            <template #default="{ row }">
              <span class="dev-serial">{{ row.serial_number || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="屏幕分辨率" width="130">
            <template #default="{ row }">
              {{ row.screen_width && row.screen_height ? `${row.screen_width}×${row.screen_height}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="设备状态" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                round
                effect="plain"
                class="status-tag"
                :class="isDeviceOnline(row) ? 'is-online' : 'is-offline'"
              >
                {{ isDeviceOnline(row) ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-tooltip
                :disabled="canStartPick(row)"
                content="设备处于离线状态，无法开启控件拾取，请重启设备后重试"
                placement="top"
              >
                <span>
                  <el-button
                    type="primary"
                    size="small"
                    :disabled="!canStartPick(row)"
                    @click="openPicker(row.id)"
                  >
                    开始拾取
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!devicesLoading && !androidDevices.length" class="hub-empty">
          <p>暂无已接入的测试设备</p>
          <div class="hub-empty__actions">
            <el-button type="primary" :loading="syncingUsb" @click="syncUsbFromHub">同步 USB 设备</el-button>
            <el-button v-if="userStore.isAdmin" @click="showWhitelistDialog = true">添加设备白名单</el-button>
          </div>
        </div>
      </AppCard>

      <AppCard class="hub-history-card" :hover="false">
        <template #header>
          <div class="hub-history-head">
            <div>
              <div class="hub-history-title">历史拾取记录（本机）</div>
              <div class="hub-history-desc">共 {{ pickHistory.length }} 条，保存在当前浏览器；可直接查看定位参数或批量写入控件库</div>
            </div>
            <div class="hub-history-actions">
              <el-button
                size="small"
                type="primary"
                :disabled="!pickHistory.length"
                @click="restoreHistoryToPool"
              >批量入库控件库</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="!pickHistory.length"
                @click="clearPickHistory"
              >清空历史</el-button>
            </div>
          </div>
        </template>
        <el-table
          :data="pickHistory"
          stripe
          max-height="420"
          empty-text="暂无本机拾取历史"
        >
          <el-table-column type="index" label="#" width="52" :index="hubHistoryIndex" />
          <el-table-column label="控件名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.display_name || row.element_name || `控件 (${row.x ?? '-'},${row.y ?? '-'})` }}
            </template>
          </el-table-column>
          <el-table-column label="定位类型" width="110">
            <template #default="{ row }">{{ hubHistoryLocator(row).type || '-' }}</template>
          </el-table-column>
          <el-table-column label="定位值" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="hub-loc-code">{{ hubHistoryLocator(row).value || '-' }}</code>
            </template>
          </el-table-column>
          <el-table-column label="设备" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.device_label || '-' }}</template>
          </el-table-column>
          <el-table-column label="拾取时间" width="170">
            <template #default="{ row }">{{ formatHubPickTime(row.picked_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="restoreSingleHistoryToPool(row)">入库</el-button>
            </template>
          </el-table-column>
        </el-table>
      </AppCard>

      <el-dialog v-model="showWhitelistDialog" title="添加设备白名单" width="480px" destroy-on-close>
        <el-form :model="whitelistForm" label-width="100px">
          <el-form-item label="序列号" required><el-input v-model="whitelistForm.serial_number" /></el-form-item>
          <el-form-item label="平台" required>
            <el-select v-model="whitelistForm.platform" style="width:100%">
              <el-option label="Android" value="android" /><el-option label="iOS" value="ios" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="whitelistForm.remark" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showWhitelistDialog = false">取消</el-button>
          <el-button type="primary" @click="addWhitelistFromHub">确认</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- 控件拾取工作台 -->
    <template v-else>
      <PageHeader
        class="picker-workbench-header"
        :title="`控件获取 · ${device?.model || device?.name || device?.serial_number || `#${deviceId}`}`"
      >
        <template #actions>
          <el-tag v-if="connected" type="success" size="small">已连接</el-tag>
          <el-tag v-else-if="connecting" type="warning" size="small">连接中</el-tag>
          <ScreenFpsTag v-if="deviceId" :device-id="deviceId" />
          <el-button style="margin-left:12px" @click="goHub">切换设备</el-button>
          <el-button @click="$router.push('/controls')">进入控件库</el-button>
          <el-button v-if="pickReturnTo" type="primary" @click="goReturn">返回审阅</el-button>
        </template>
      </PageHeader>

      <div v-if="pickReviewHint" class="review-banner">
        正在为审阅步骤 <strong>#{{ pickStepIndex + 1 }}</strong> 补定位 — 点击目标控件后点「应用到审阅步骤」
      </div>

      <div class="picker-workbench inspector-3pane">
        <aside class="screen-panel">
          <AppCard :hover="false" class="screen-card">
            <template #header>
              <div class="screen-card-title">
                <span class="screen-title-text">设备投屏</span>
                <span class="screen-device-name">{{ device?.model || device?.name || device?.serial_number || '未知设备' }}</span>
                <span class="resolution-tag">{{ resolutionLabel }}</span>
                <el-tag v-if="freezePickActive" type="warning" size="small" effect="dark">截图点选中</el-tag>
                <el-tag v-if="uiTreeReady" type="success" size="small" effect="plain">树已就绪</el-tag>
                <el-tag v-else-if="connected" type="info" size="small" effect="plain">未刷新</el-tag>
                <el-tag v-if="dumpSource" size="small" :type="dumpSource === 'u2' ? 'success' : 'warning'" effect="plain">{{ dumpSourceLabel }}</el-tag>
              </div>
            </template>
            <div class="screen-body" ref="screenBodyRef">
              <div class="screen-wrap" ref="screenWrapRef">
                <div class="screen-device">
                  <div class="screen-frame" :style="screenFrameStyle">
                    <canvas
                      ref="canvasRef"
                      class="screen-canvas"
                      :class="{
                        picking: interactionMode === 'pick',
                        operating: interactionMode === 'operate',
                        frozen: freezePickActive
                      }"
                      @mousedown.prevent="onMouseDown"
                      @mousemove="onPickCursorMove"
                      @mouseleave="onPickCursorLeave"
                    />
                    <div v-if="freezePickActive" class="freeze-banner">
                      画面已冻结 · 点击截图上的控件进行拾取
                      <button type="button" class="freeze-banner-btn" @click.stop="exitFreezePick">解除</button>
                      <button type="button" class="freeze-banner-btn" @click.stop="enterFreezePick">重新截取</button>
                    </div>
                    <div
                      v-if="interactionMode === 'pick' && pickCursor.visible"
                      class="pick-cursor-ring"
                      :style="{ left: `${pickCursor.x}px`, top: `${pickCursor.y}px` }"
                    />
                    <div
                      v-for="r in pickClickRipples"
                      :key="r.id"
                      class="pick-click-ripple"
                      :style="{ left: `${r.x}px`, top: `${r.y}px` }"
                    />
                    <template v-if="interactionMode === 'pick' && resultPanelMode === 'current' && showPickHighlight && currentHighlightBox">
                      <div
                        :key="'cur-' + highlightFlashKey"
                        class="pick-highlight pick-highlight--current"
                        :class="{ 'is-flashing': highlightFlash }"
                        :style="currentHighlightBox.style"
                      />
                    </template>
                    <div v-if="!hasFrame" class="screen-placeholder">
                      <el-icon :size="40"><Monitor /></el-icon>
                      <p>{{ statusText }}</p>
                      <p class="frame-dim">{{ resolutionLabel }}</p>
                      <el-button v-if="!connecting && !connected" type="primary" @click="connectStream">连接投屏</el-button>
                    </div>
                  </div>
                  <ScreenNavBar :disabled="!connected" @key="pressNavKey" />
                </div>
              </div>
              <aside class="screen-side-toolbar">
                <el-button type="primary" size="small" :disabled="connected" :loading="connecting" @click="connectStream">连接</el-button>
                <el-button size="small" :disabled="!connected" @click="stopStream">断开</el-button>
                <el-button type="primary" size="small" :loading="warming || hierarchyLoading" :disabled="!connected" @click="refreshUiTree">刷新树</el-button>
                <el-button
                  size="small"
                  :type="interactionMode === 'operate' ? 'primary' : 'default'"
                  :disabled="freezePickActive"
                  @click="interactionMode = 'operate'"
                >操控</el-button>
                <el-button
                  size="small"
                  :type="interactionMode === 'pick' ? 'primary' : 'default'"
                  @click="interactionMode = 'pick'"
                >识别</el-button>
                <el-button
                  size="small"
                  :type="freezePickActive ? 'warning' : 'default'"
                  class="btn-outline-blue"
                  :loading="freezing"
                  :disabled="!hasFrame || !connected"
                  @click="freezePickActive ? exitFreezePick() : enterFreezePick()"
                >{{ freezePickActive ? '解除冻结' : '冻结点选' }}</el-button>
                <span class="toolbar-sep" />
                <el-button size="small" type="danger" class="btn-end-pick" @click="confirmEndPick">结束</el-button>
                <el-button size="small" class="btn-outline-blue" :disabled="!hasFrame" @click="captureScreenshot">截图</el-button>
                <el-button size="small" class="btn-outline-blue" :disabled="!currentPick && !pickHistory.length" @click="confirmClearPickInfo">清空</el-button>
              </aside>
            </div>
          </AppCard>
        </aside>

        <aside class="tree-panel">
          <UiHierarchyTree
            ref="hierarchyTreeRef"
            :root="hierarchyRoot"
            :loading="hierarchyLoading"
            :ready="connected"
            :node-count="hierarchyNodeCount"
            :truncated="hierarchyTruncated"
            :current-id="hierarchyCurrentId"
            :select-bounds="currentPick?.bounds || ''"
            @select="onHierarchyNodeSelect"
            @refresh="refreshUiTree"
          />
        </aside>

        <section class="detail-panel">
          <AppCard :hover="false" class="pick-result-card">
            <PickResultPanel
              ref="pickResultPanelRef"
              :current-pick="currentPick"
              :locator-chain="locatorChain"
              :relative-form="relativeForm"
              :manual-form="manualForm"
              :pick-history="pickHistory"
              :inspecting="inspecting"
              :validating="validating"
              :applying="applying"
              :connected="connected"
              :validate-result="validateResult"
              :validate-attempts="validateAttempts"
              :pick-record-id="pickRecordId"
              :pick-step-index="pickStepIndex"
              :device-label="device?.model || device?.name || ''"
              :format-validate-meta="formatValidateMeta"
              @copy-all="copyAllLocators"
              @validate="validateCurrentLocator"
              @save-pool="openSavePool"
              @create-case="goCreateCaseFromPick"
              @apply-review="applyToReviewStep"
              @copy-text="copyText"
              @set-primary="setChainPrimary"
              @move-chain="moveChainItem"
              @toggle-enabled="toggleChainEnabled"
              @add-relative="addRelativeToChain"
              @apply-manual="applyManualToCurrent"
              @select-history="selectHistory"
              @clear-history="clearPickHistory"
              @restore-history-pool="restoreHistoryToPool"
              @goto-pool="$router.push('/controls')"
              @panel-mode-change="onResultPanelModeChange"
            />
          </AppCard>
        </section>
      </div>

      <el-dialog v-model="showSavePool" title="保存至公共控件库" width="480px" destroy-on-close>
        <el-form label-width="96px" size="small">
          <el-form-item label="控件名称" required>
            <el-input v-model="savePoolForm.element_name" placeholder="如：登录按钮" />
          </el-form-item>
          <el-form-item label="业务分类">
            <el-input v-model="savePoolForm.page_name" placeholder="如：登录页 / 首页" />
          </el-form-item>
          <el-form-item label="版本标签">
            <el-input v-model="savePoolForm.version_tag" placeholder="v1.0.0 / test" />
          </el-form-item>
          <el-form-item label="环境标签">
            <el-input v-model="savePoolForm.env_tag" placeholder="test / staging" />
          </el-form-item>
          <el-form-item label="控件分级">
            <el-select v-model="savePoolForm.control_tag" style="width:100%">
              <el-option v-for="t in CONTROL_TAGS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="等待规则">
            <div class="wait-rule-row">
              <el-select v-model="savePoolForm.wait_condition" size="small" style="width:110px">
                <el-option v-for="c in WAIT_CONDITIONS" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
              <el-input-number v-model="savePoolForm.wait_timeout_ms" size="small" :min="0" :max="60000" :step="500" />
              <span class="hint">ms（0=不等待）</span>
            </div>
          </el-form-item>
          <el-form-item label="主定位">
            <code class="save-loc-preview">{{ savePoolPreview }}</code>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showSavePool = false">取消</el-button>
          <el-button type="primary" :loading="savingPool" @click="saveToControlPool">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, onActivated, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deviceApi, recordApi, controlApi } from '@/api'
import { useScreenStream, setScreenDeviceInfo } from '@/composables/useScreenStream'
import { createScreenCanvasRenderer } from '@/composables/useScreenCanvas'
import { fixedScreenFrameStyle, fixedScreenFrameStyleInBox, frameSizeFromDevice, frameMaxHeight, NAV_BAR_HEIGHT } from '@/composables/screenFrameStyle'
import { formatStepLocator, formatLocatorType } from '@/utils/stepDisplay'
import {
  buildLocatorChainFromPick, chainToLocators, primaryFromChain, isWeakPick,
  sortLocatorChainByPassRate,
  riskLevelLabel, riskTagType, mapLocatorTypeForPool, mapLocatorValueForPool,
  ANCHOR_DIRECTIONS, parseLocatorKv,
  buildParentIndexValue, buildAnchorAdjacentValue, buildRegionLocatorValue,
  computeStabilityScore, stabilityScoreLabel, stabilityScoreType,
  CONTROL_TAGS, WAIT_CONDITIONS, validateElementName, recommendReasonLabel
} from '@/utils/locatorAssist'
import { normalizeDevice } from '@/utils/device'
import { useUserStore } from '@/stores/user'
import ScreenNavBar from '@/components/ScreenNavBar.vue'
import PickResultPanel from '@/components/element-picker/PickResultPanel.vue'
import UiHierarchyTree from '@/components/element-picker/UiHierarchyTree.vue'
import ScreenFpsTag from '@/components/ScreenFpsTag.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Loading } from '@element-plus/icons-vue'

defineOptions({ name: 'ElementPicker' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const deviceId = computed(() => route.params.id ? String(route.params.id) : '')
const device = ref(null)
const canvasRef = ref(null)
const screenWrapRef = ref(null)
const screenBodyRef = ref(null)
const hasFrame = ref(false)
const frameW = ref(1080)
const frameH = ref(1920)

const devicesLoading = ref(false)
const androidDevices = ref([])
const rebootingOffline = ref(false)
const syncingUsb = ref(false)
const showWhitelistDialog = ref(false)
const whitelistForm = reactive({ serial_number: '', platform: 'android', remark: '' })

const interactionMode = ref('pick')
/** 冻结当前投屏帧后点选（截图点选） */
const freezePickActive = ref(false)
const freezing = ref(false)
const inspecting = ref(false)
const pickResultPanelRef = ref(null)
const warming = ref(false)
const uiTreeReady = ref(false)
const hierarchyRoot = ref(null)
const hierarchyLoading = ref(false)
const hierarchyNodeCount = ref(0)
const hierarchyTruncated = ref(false)
const hierarchyCurrentId = ref('')
const hierarchyTreeRef = ref(null)
const pageContext = ref('native')
const appProfile = ref(null)
const dumpSource = ref('')
const applying = ref(false)
const validating = ref(false)
const savingPool = ref(false)
const currentPick = ref(null)
const PICK_HISTORY_KEY = 'atp_element_pick_history'

function loadPersistedPickHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(PICK_HISTORY_KEY) || '[]')
    return Array.isArray(raw) ? raw.slice(0, 100) : []
  } catch {
    return []
  }
}

function persistPickHistory(list) {
  try {
    const payload = JSON.stringify((list || []).slice(0, 100))
    const save = () => {
      try { localStorage.setItem(PICK_HISTORY_KEY, payload) } catch { /* ignore */ }
    }
    if (typeof requestIdleCallback === 'function') requestIdleCallback(save, { timeout: 1500 })
    else setTimeout(save, 0)
  } catch { /* ignore */ }
}

const pickHistory = ref(loadPersistedPickHistory())
const lastPickMarker = ref(null)
/** 仅在用户点击拾取/回看历史后展示高亮；清空后关闭 */
const showPickHighlight = ref(false)
/** 右侧面板：仅在「当前控件」页展示投屏高亮 */
const resultPanelMode = ref('current')
const highlightFlash = ref(false)
const highlightFlashKey = ref(0)
let highlightFlashTimer = null
const PICK_TO_CASE_KEY = 'atp_pick_to_case'
const locatorChain = ref([])
const validateResult = ref(null)
const validateAttempts = ref([])
const showValidateDetail = ref(false)
const showSavePool = ref(false)
let pickSeq = 0

const savePoolForm = reactive({
  page_name: '',
  element_name: '',
  version_tag: '',
  env_tag: '',
  control_tag: 'static',
  wait_condition: 'clickable',
  wait_timeout_ms: 0
})
const pickContextVersion = ref('')
const pickContextEnv = ref('')

const relativeForm = reactive({
  container: '',
  index: 0,
  anchor: '',
  direction: 'right',
  region_bounds: '',
  inner_type: 'content_desc',
  inner_value: ''
})

const pickRecordId = ref(null)
const pickStepIndex = ref(null)
const pickReturnTo = ref('')
const pickReviewHint = computed(() => pickRecordId.value != null && pickStepIndex.value != null)

const manualForm = reactive({
  display_name: '',
  element_name: '',
  locator_type: 'id',
  locator_value: ''
})

let dragStart = null
let dragStartClient = null
const SWIPE_THRESHOLD = 12

const pickCursor = reactive({ visible: false, x: 0, y: 0 })
const pickClickRipples = ref([])
let pickRippleSeq = 0
const pickRippleTimers = new Set()

function canvasLocalPoint(event) {
  const el = canvasRef.value
  if (!el) return null
  const rect = el.getBoundingClientRect()
  return {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
    inside:
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom
  }
}

function onPickCursorMove(event) {
  if (interactionMode.value !== 'pick') {
    pickCursor.visible = false
    return
  }
  const pt = canvasLocalPoint(event)
  if (!pt?.inside) {
    pickCursor.visible = false
    return
  }
  pickCursor.x = pt.x
  pickCursor.y = pt.y
  pickCursor.visible = true
}

function onPickCursorLeave() {
  pickCursor.visible = false
}

function spawnPickClickEffect(clientX, clientY) {
  const el = canvasRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const id = ++pickRippleSeq
  pickClickRipples.value.push({
    id,
    x: clientX - rect.left,
    y: clientY - rect.top
  })
  const timer = setTimeout(() => {
    pickClickRipples.value = pickClickRipples.value.filter(r => r.id !== id)
    pickRippleTimers.delete(timer)
  }, 480)
  pickRippleTimers.add(timer)
}

const {
  connected, connecting, statusText, nativeW, nativeH, streamW, streamH,
  lastFrame, startStream, stopStream, resumeIfAlive,
  attachFrameListener, attachMetaListener
} = useScreenStream(deviceId)

let renderer = null
let detachFrame = null
let detachMeta = null

const layoutStyle = ref(fixedScreenFrameStyle(1080, 1920, frameMaxHeight('calc(100vh - 220px)', NAV_BAR_HEIGHT)))
const screenFrameStyle = computed(() => layoutStyle.value)

const deviceCoordW = computed(() => device.value?.screen_width || frameW.value || 1080)
const deviceCoordH = computed(() => device.value?.screen_height || frameH.value || 1920)
const resolutionLabel = computed(() => `${deviceCoordW.value} × ${deviceCoordH.value}`)

function parseBoundsStyle(bounds, dw, dh) {
  const m = String(bounds || '').match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/)
  if (!m || !dw || !dh) return null
  const x1 = Number(m[1])
  const y1 = Number(m[2])
  const x2 = Number(m[3])
  const y2 = Number(m[4])
  if (!(x2 > x1 && y2 > y1)) return null
  return {
    left: `${(x1 / dw) * 100}%`,
    top: `${(y1 / dh) * 100}%`,
    width: `${((x2 - x1) / dw) * 100}%`,
    height: `${((y2 - y1) / dh) * 100}%`
  }
}

function pointFallbackStyle(x, y, dw, dh) {
  const px = Number(x)
  const py = Number(y)
  if (!Number.isFinite(px) || !Number.isFinite(py) || !dw || !dh) return null
  const size = 28
  return {
    left: `${(px / dw) * 100}%`,
    top: `${(py / dh) * 100}%`,
    width: `${(size / dw) * 100}%`,
    height: `${(size / dh) * 100}%`,
    transform: 'translate(-50%, -50%)'
  }
}

function triggerHighlightFlash() {
  showPickHighlight.value = true
  highlightFlash.value = false
  highlightFlashKey.value += 1
  nextTick(() => {
    highlightFlash.value = true
    if (highlightFlashTimer) clearTimeout(highlightFlashTimer)
    highlightFlashTimer = setTimeout(() => {
      highlightFlash.value = false
    }, 900)
  })
}

function clearPickHighlight() {
  showPickHighlight.value = false
  highlightFlash.value = false
  lastPickMarker.value = null
  darkFrameStreak = 0
  if (highlightFlashTimer) {
    clearTimeout(highlightFlashTimer)
    highlightFlashTimer = null
  }
}

/** 采样投屏亮度：锁屏/灭屏多为近黑画面，用于收起无效高亮 */
let lastDarkCheckAt = 0
let darkFrameStreak = 0
let darkSampleCanvas = null

function isCanvasMostlyDark(sourceCanvas) {
  if (!sourceCanvas?.width || !sourceCanvas?.height) return false
  try {
    if (!darkSampleCanvas) darkSampleCanvas = document.createElement('canvas')
    const sw = 24
    const sh = 40
    darkSampleCanvas.width = sw
    darkSampleCanvas.height = sh
    const sctx = darkSampleCanvas.getContext('2d', { willReadFrequently: true })
    if (!sctx) return false
    sctx.drawImage(sourceCanvas, 0, 0, sw, sh)
    const { data } = sctx.getImageData(0, 0, sw, sh)
    let sum = 0
    let bright = 0
    const n = sw * sh
    for (let i = 0; i < data.length; i += 4) {
      const lum = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]
      sum += lum
      if (lum > 40) bright += 1
    }
    const avg = sum / n
    // 锁屏黑屏 / AOD：整体极暗，亮点极少
    return avg < 16 && bright / n < 0.06
  } catch {
    return false
  }
}

function maybeClearHighlightOnDarkScreen() {
  if (freezePickActive.value) return
  if (!showPickHighlight.value) {
    darkFrameStreak = 0
    return
  }
  const now = Date.now()
  if (now - lastDarkCheckAt < 400) return
  lastDarkCheckAt = now
  const canvas = canvasRef.value
  if (!canvas) return
  if (isCanvasMostlyDark(canvas)) {
    darkFrameStreak += 1
    if (darkFrameStreak >= 2) clearPickHighlight()
  } else {
    darkFrameStreak = 0
  }
}

function onResultPanelModeChange(mode) {
  resultPanelMode.value = mode === 'history' ? 'history' : 'current'
}

watch(interactionMode, (mode) => {
  // 切到操控设备通常会离开当前页，收起高亮
  if (mode === 'operate') {
    clearPickHighlight()
    pickCursor.visible = false
    if (freezePickActive.value) exitFreezePick({ silent: true })
  }
})

function setStreamPaused(paused) {
  try { renderer?.setPaused?.(!!paused) } catch { /* ignore */ }
}

async function enterFreezePick() {
  if (!connected.value || !hasFrame.value || !canvasRef.value) {
    ElMessage.warning('请先连接投屏并等待画面出现')
    return
  }
  if (freezing.value) return
  freezing.value = true
  try {
    interactionMode.value = 'pick'
    // 重新截取时先短暂恢复投屏，拿最新一帧再冻结
    if (freezePickActive.value) {
      setStreamPaused(false)
      await new Promise((r) => setTimeout(r, 280))
    }
    setStreamPaused(true)
    freezePickActive.value = true
    clearPickHighlight()
    // 同步抓取 UI dump，尽量与冻结画面同帧绑定
    warming.value = true
    uiTreeReady.value = false
    try {
      let res
      try {
        res = await deviceApi.screenPrepareUi(deviceId.value)
      } catch {
        res = await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
      }
      const data = res.data || {}
      uiTreeReady.value = !!data.ok
      pageContext.value = data.page_context || data.warm?.page_context || 'native'
      applyAppProfile(data.app_profile || data.warm?.app_profile)
      applyDumpSource(data.dump_source || data.source || data.warm?.dump_source)
      if (data.ok) {
        await loadUiHierarchy({ force: true })
        ElMessage.success('画面已冻结，点击截图上的控件即可拾取')
      } else {
        ElMessage.warning(data.message || data.error || 'UI 树同步失败，仍可尝试点选')
      }
    } finally {
      warming.value = false
    }
  } finally {
    freezing.value = false
  }
}

function exitFreezePick({ silent = false } = {}) {
  freezePickActive.value = false
  freezing.value = false
  setStreamPaused(false)
  if (!silent) ElMessage.info('已解除冻结，恢复实时投屏')
}

const currentHighlightBox = computed(() => {
  if (!showPickHighlight.value) return null
  const pick = currentPick.value
  if (!pick) return null
  const dw = deviceCoordW.value || 1
  const dh = deviceCoordH.value || 1
  const style = parseBoundsStyle(pick.bounds, dw, dh)
    || pointFallbackStyle(pick.inspect_x ?? pick.x, pick.inspect_y ?? pick.y, dw, dh)
  if (!style) return null
  return { id: pick.id, style }
})

const savePoolPreview = computed(() => {
  const p = primaryFromChain(locatorChain.value)
  if (!p.locator_value) return '-'
  return `${formatLocatorType(p.locator_type)} · ${p.locator_value}`
})

const pickDisplayName = computed(() =>
  currentPick.value?.display_name || currentPick.value?.element_name || '未命名控件'
)

const recognizeStatus = computed(() => {
  const pick = currentPick.value
  if (!pick) return { label: '未拾取', type: 'info' }
  if (pick.valid && !isWeakPick(pick)) return { label: '已识别', type: 'success' }
  if (isWeakPick(pick)) return { label: '高风险', type: 'danger' }
  return { label: '需补充', type: 'warning' }
})

const hasScreenRatioLocator = computed(() => {
  const pick = currentPick.value
  if (!pick) return false
  if (pick.locators?.screen_ratio) return true
  return (locatorChain.value || []).some(i => i.type === 'screen_ratio' && i.value)
})

const pickWidgetTypeDisplay = computed(() => {
  const pick = currentPick.value
  if (!pick) return '-'
  const step = String(pick.suggested_step_type || '').toLowerCase()
  if (step === 'tap' || step === 'click' || pick.clickable === true) return 'click 点击控件'
  if (step === 'input' || step === 'send_keys') return 'input 输入控件'
  if (step === 'tap_ocr') return 'ocr 点击控件'
  return widgetTypeLabel(pick.widget_type || pick.class)
})

const recommendedLocatorItem = computed(() => {
  return locatorChain.value.find(i => i.recommended)
    || locatorChain.value[0]
    || null
})

const enabledChainCount = computed(() => locatorChain.value.filter(i => i.enabled !== false).length)

const relativePreview = computed(() => ({
  parent_index: buildParentIndexValue(relativeForm.container, relativeForm.index),
  anchor_adjacent: buildAnchorAdjacentValue(relativeForm.anchor, relativeForm.direction),
  region_locator: buildRegionLocatorValue(relativeForm.region_bounds, relativeForm.inner_type, relativeForm.inner_value)
}))

const stabilityScore = computed(() => computeStabilityScore(currentPick.value))

const pageContextLabel = computed(() => {
  const map = { native: '原生', webview: 'WebView', hybrid: '混合' }
  return map[pageContext.value] || pageContext.value
})

const appProfileTagType = computed(() => {
  const t = appProfile.value?.app_type
  if (t === 'native' || t === 'compose') return 'success'
  if (t === 'flutter' || t === 'webview' || t === 'hybrid' || t === 'react_native') return 'warning'
  return 'danger'
})

const appProfileBannerClass = computed(() => {
  if (!appProfile.value) return ''
  if (appProfile.value.ui_tree_suitable) return 'is-ok'
  if (appProfile.value.recommended_strategy === 'ocr') return 'is-ocr'
  return 'is-warn'
})

const dumpSourceLabel = computed(() => {
  const map = {
    u2: 'uiautomator2 完整层级',
    shell: '系统 shell dump（回退）',
    cache: '缓存',
    fail: '失败',
    '': '未知'
  }
  return map[dumpSource.value] || dumpSource.value || '未知'
})

function applyDumpSource(src) {
  if (src) dumpSource.value = src
}

function applyAppProfile(profile) {
  if (!profile || typeof profile !== 'object') return
  appProfile.value = profile
  if (profile.app_type === 'webview') pageContext.value = 'webview'
  else if (profile.app_type === 'hybrid') pageContext.value = 'hybrid'
}

const RECOMMEND_DESC = {
  content_desc: '依托页面文本描述，适配多分辨率，抗页面布局变动，稳定性最优',
  text: '依托可见文本，适配多分辨率，抗布局微调，稳定性较高',
  id: '依托唯一 resource-id，跨版本稳定，优先推荐',
  resource_id: '依托唯一 resource-id，跨版本稳定，优先推荐',
  xpath: '通用路径定位，适配结构相似页面，稳定性中等',
  xpath_desc: '基于描述的相对路径，兼顾语义与结构',
  relative_xpath: '短结构路径，比绝对路径更耐布局微调',
  absolute_xpath: '从根节点写死的完整路径，结构一变易失效',
  class_name: '仅依赖类名，易受布局复用影响，稳定性偏低',
  bounds: '固定坐标区域，分辨率变化时需校准',
  screen_ratio: '屏幕比例坐标，适配多分辨率兜底',
  ocr: 'OCR 文本识别，适合自绘/无障碍稀疏界面'
}

const recommendReasonText = computed(() => {
  const pick = currentPick.value
  if (pick?.recommend_reason) return pick.recommend_reason
  const rec = recommendedLocatorItem.value
  if (!rec) return ''
  return rec.recommend_reason || RECOMMEND_DESC[rec.type] || recommendReasonLabel(rec.type)
})

/** 备选方案：与定位链一致，按通过率从高到低 */
const alternativeSchemes = computed(() => {
  const chain = locatorChain.value || []
  if (chain.length) {
    return chain.map(item => ({
      key: item.type,
      label: formatLocatorType(item.type === 'resource_id' ? 'id' : item.type),
      value: item.value || '',
      pass_rate: item.pass_rate,
      recommended: !!item.recommended
    }))
  }
  return []
})

function normalizeLocatorType(type) {
  return type === 'resource_id' ? 'id' : type
}

const onlineDeviceCount = computed(() => androidDevices.value.filter(d => isDeviceOnline(d)).length)
const offlineDeviceCount = computed(() => androidDevices.value.filter(d => !isDeviceOnline(d)).length)
const hubBanner = computed(() => {
  const total = androidDevices.value.length
  const online = onlineDeviceCount.value
  const offline = offlineDeviceCount.value
  if (!total || online === 0) {
    return { tone: 'danger', icon: 'WarningFilled', text: '当前无可用在线设备，离线设备无法启动控件拾取' }
  }
  if (offline === 0) {
    return { tone: 'success', icon: 'CircleCheck', text: `当前共 ${total} 台设备，其中 ${online} 台在线，可正常拾取控件` }
  }
  return { tone: 'warning', icon: 'WarningFilled', text: `${online} 台设备在线可用，${offline} 台设备离线（离线设备拾取功能已禁用）` }
})

function isDeviceOnline(row) {
  return row?.status === 'online' || row?.status === 'busy'
}

function canStartPick(row) {
  return (row.platform === 'android' || row.platform === 'ios') && isDeviceOnline(row)
}

function widgetTypeLabel(raw) {
  const s = String(raw || '').toLowerCase()
  if (!s || s === 'unknown') return '未知类型'
  if (s.includes('button') || s.includes('btn')) return '按钮'
  if (s.includes('edit') || s.includes('input') || s.includes('textfield')) return '文本框'
  if (s.includes('image') || s.includes('img') || s.includes('imageview')) return '图片'
  if (s.includes('text') || s.includes('label') || s.includes('textview')) return '文本'
  if (s.includes('check')) return '复选框'
  if (s.includes('switch')) return '开关'
  if (s.includes('list') || s.includes('recycler')) return '列表'
  if (s.includes('web')) return '网页视图'
  return raw || '未知类型'
}

function formatPickTime(ts) {
  if (!ts) return '-'
  try {
    const d = new Date(ts)
    if (Number.isNaN(d.getTime())) return String(ts)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  } catch {
    return String(ts)
  }
}

const validateResultText = computed(() => {
  const r = validateResult.value
  if (!r) return ''
  if (r.valid) return `校验通过 · 可点击 · ${formatLocatorType(r.matched_by)}`
  if (r.error === 'not_clickable') return `存在但不可点击 · ${formatLocatorType(r.matched_by || '')}`
  return r.error || '校验未通过'
})

function validateAttemptLabel(att) {
  if (att.clickable) return '可点击'
  if (att.found && att.visible) return '不可点'
  if (att.found) return '不可见'
  return att.reason === 'not_found' ? '未命中' : (att.reason || '未命中')
}

function hasDuplicateRisk(pick) {
  return (pick?.risk_tags || []).includes('duplicate')
}

function duplicateRiskDetail(pick) {
  const reasons = (pick?.risk_reasons || []).filter(r => String(r).includes('匹配') || String(r).includes('重复'))
  if (reasons.length) return reasons.join('；')
  return '该定位在当前页面存在多处匹配，保存前请在定位链中改选更唯一的主定位（如带 resource-id / 更长 content-desc 的项）。'
}

async function focusLocatorChainPanel() {
  await nextTick()
  pickResultPanelRef.value?.focusChainSection?.()
}

async function confirmDuplicateIfNeeded(actionLabel = '继续') {
  if (!hasDuplicateRisk(currentPick.value)) return true
  try {
    await ElMessageBox.confirm(
      duplicateRiskDetail(currentPick.value),
      '重复匹配预警',
      {
        type: 'warning',
        confirmButtonText: actionLabel,
        cancelButtonText: '去调整定位链',
        distinguishCancelAndClose: true
      }
    )
    return true
  } catch (action) {
    if (action === 'cancel') {
      await focusLocatorChainPanel()
      ElMessage.info('已定位到定位链，请点选更唯一的方案设为主定位')
    }
    return false
  }
}

async function notifyDuplicateRisk(pick) {
  if (!hasDuplicateRisk(pick)) return
  try {
    await ElMessageBox.confirm(
      duplicateRiskDetail(pick),
      '重复匹配预警',
      {
        type: 'warning',
        confirmButtonText: '去调整定位链',
        cancelButtonText: '我知道了',
        distinguishCancelAndClose: true
      }
    )
    await focusLocatorChainPanel()
    ElMessage.info('请在定位链中点选更唯一的方案设为主定位，再保存或应用')
  } catch {
    // 我知道了 / 关闭
  }
}

function shortClass(clazz) {
  return String(clazz || '').split('.').pop() || clazz
}

function truncateLocator(val, max = 48) {
  const s = String(val || '')
  return s.length <= max ? s : `${s.slice(0, max - 3)}...`
}

function syncRelativeFormFromPick(pick) {
  const locs = pick?.locators || {}
  const pi = parseLocatorKv(locs.parent_index || '')
  const aa = parseLocatorKv(locs.anchor_adjacent || '')
  const rl = parseLocatorKv(locs.region_locator || '')
  relativeForm.container = pi.container || ''
  relativeForm.index = Number.isFinite(Number(pi.index)) ? Number(pi.index) : 0
  relativeForm.anchor = aa.anchor || ''
  relativeForm.direction = aa.dir || 'right'
  relativeForm.region_bounds = rl.region || pick?.bounds || ''
  relativeForm.inner_type = rl.type || 'content_desc'
  relativeForm.inner_value = rl.value || ''
}

function formatValidateMeta(vr) {
  if (!vr) return '-'
  const at = vr.validated_at ? new Date(vr.validated_at).toLocaleString() : '未知时间'
  const status = vr.valid ? '通过' : (vr.error === 'not_clickable' ? '不可点' : '未通过')
  const by = vr.matched_by ? ` · ${formatLocatorType(vr.matched_by)}` : ''
  return `${at} · ${status}${by}`
}

function addRelativeToChain(type) {
  const value = relativePreview.value[type]
  if (!value) return
  if (locatorChain.value.some(i => i.type === type && i.value === value)) {
    ElMessage.info('该相对定位已在链中')
    return
  }
  const isFirst = !locatorChain.value.length
  locatorChain.value = [
    ...locatorChain.value,
    {
      type,
      value,
      enabled: true,
      priority: locatorChain.value.length + 1,
      recommended: isFirst,
      primary: isFirst
    }
  ]
  syncChainToPick()
  ElMessage.success('已加入定位链')
}

function syncChainToPick() {
  if (!currentPick.value) return
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  currentPick.value = {
    ...currentPick.value,
    locators,
    locator_chain: locatorChain.value.map((item, idx) => ({ ...item, priority: idx + 1 })),
    locator_type: primary.locator_type,
    locator_value: primary.locator_value
  }
  fillManualForm(currentPick.value)
}

function updateLayoutSize() {
  const { w, h } = frameSizeFromDevice(device.value || { screen_width: frameW.value, screen_height: frameH.value })
  // 以投屏区高度驱动等比缩放，宽度跟随比例，避免左右大块留白
  const body = screenBodyRef.value
  const boxH = body?.clientHeight || 0
  if (boxH > 40) {
    layoutStyle.value = fixedScreenFrameStyleInBox(w, h, 10000, boxH - 2, NAV_BAR_HEIGHT)
    return
  }
  layoutStyle.value = fixedScreenFrameStyle(
    w,
    h,
    frameMaxHeight('calc(100vh - 160px)', NAV_BAR_HEIGHT),
    Math.min(window.innerWidth * 0.42, 520)
  )
}

function redrawLastFrame() {
  nextTick(() => {
    if (renderer && lastFrame.value) {
      renderer.onFrame(lastFrame.value)
    }
  })
}

function moveChainItem(index, delta) {
  const next = index + delta
  if (next < 0 || next >= locatorChain.value.length) return
  const arr = [...locatorChain.value]
  const tmp = arr[index]
  arr[index] = arr[next]
  arr[next] = tmp
  locatorChain.value = arr.map((item, idx) => ({ ...item, priority: idx + 1 }))
  syncChainToPick()
}

function setChainPrimary(index) {
  if (index < 0 || index >= locatorChain.value.length) return
  if (locatorChain.value[index].enabled === false) return
  locatorChain.value = locatorChain.value.map((item, idx) => ({
    ...item,
    primary: idx === index
  }))
  syncChainToPick()
}

function toggleChainEnabled(index, enabled) {
  if (index < 0 || index >= locatorChain.value.length) return
  locatorChain.value[index].enabled = !!enabled
  syncChainToPick()
}

function resolveAppPackage() {
  return device.value?.app_package?.trim()
    || device.value?.current_package?.trim()
    || 'com.atp.unscoped'
}

function maybeFillControlForm(pick) {
  if (route.query.return_fill !== '1' && sessionStorage.getItem('atp_fill_control_form') !== '1') return
  syncChainToPick()
  const fromChain = primaryFromChain(locatorChain.value)
  const primary = (fromChain.locator_value)
    ? fromChain
    : ((pick.locator_type && pick.locator_value)
      ? { locator_type: pick.locator_type, locator_value: pick.locator_value, raw_type: pick.locator_type }
      : (pick.locators && Object.keys(pick.locators).length
        ? {
            locator_type: Object.keys(pick.locators)[0],
            locator_value: pick.locators[Object.keys(pick.locators)[0]],
            raw_type: Object.keys(pick.locators)[0]
          }
        : null))
  const rawType = primary?.raw_type || primary?.locator_type || 'id'
  const rawValue = primary?.locator_value || ''
  const payload = {
    app_package: device.value?.app_package || pick.app_package || '',
    page_name: pick.page_name || '',
    element_name: pick.element_name || pick.text || pick.content_desc || '',
    platform: (device.value?.platform || 'android').toLowerCase().includes('ios') ? 'ios' : 'android',
    locator_type: mapLocatorTypeForPool(rawType, rawValue),
    locator_value: mapLocatorValueForPool(rawType, rawValue),
    version_tag: '',
    env_tag: '',
    control_tag: 'static',
    is_core: false
  }
  if (payload.element_name && !/[\u4e00-\u9fff]/.test(payload.element_name)) {
    const zh = pick.text || pick.content_desc || pick.display_name
    if (zh && /[\u4e00-\u9fff]/.test(zh)) payload.element_name = zh
  }
  sessionStorage.setItem('atp_control_pool_form_fill', JSON.stringify(payload))
  localStorage.setItem('atp_control_pool_form_fill', JSON.stringify(payload))
  sessionStorage.removeItem('atp_fill_control_form')
  ElMessage.success('定位信息已回填，正在返回控件池表单')
  router.push({ path: '/controls', query: { open_form: '1' } })
}

function goHub() {
  clearPickHighlight()
  router.push('/element-picker')
}

function goReturn() {
  if (pickReturnTo.value) router.push(pickReturnTo.value)
}

function openPicker(id) {
  // 保留来源 query（如 return_fill），避免跳转设备页后丢上下文；路由名变化会重挂载一次工作台属预期
  router.push({
    path: `/element-picker/${id}`,
    query: { ...route.query }
  })
}

async function loadDevices() {
  devicesLoading.value = true
  try {
    const res = await deviceApi.list({ page: 1, page_size: 100 })
    androidDevices.value = (res.data?.list || [])
      .filter(d => d.platform === 'android' || d.platform === 'ios')
      .map(normalizeDevice)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '加载设备列表失败'))
  } finally {
    devicesLoading.value = false
  }
}

async function syncUsbFromHub() {
  syncingUsb.value = true
  try {
    const res = await deviceApi.syncUsb()
    ElMessage.success(res.data?.message || 'USB 设备已同步')
    await loadDevices()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '同步失败'))
  } finally {
    syncingUsb.value = false
  }
}

async function rebootAllOffline() {
  const offline = androidDevices.value.filter(d => !isDeviceOnline(d))
  if (!offline.length) {
    ElMessage.info('当前没有离线设备')
    return
  }
  rebootingOffline.value = true
  try {
    let ok = 0
    for (const d of offline) {
      try {
        if (typeof deviceApi.resetHealth === 'function') await deviceApi.resetHealth(d.id)
        else await deviceApi.updateStatus(d.id, { status: 'online' })
        ok++
      } catch { /* continue */ }
    }
    ElMessage.success(`已向 ${ok}/${offline.length} 台离线设备下发重启指令`)
    await loadDevices()
  } finally {
    rebootingOffline.value = false
  }
}

async function addWhitelistFromHub() {
  if (!whitelistForm.serial_number?.trim()) {
    ElMessage.warning('请填写序列号')
    return
  }
  await deviceApi.addWhitelist(whitelistForm)
  ElMessage.success('白名单添加成功')
  showWhitelistDialog.value = false
  Object.assign(whitelistForm, { serial_number: '', platform: 'android', remark: '' })
  await loadDevices()
}

async function confirmEndPick() {
  try {
    await ElMessageBox.confirm(
      '结束拾取将断开投屏并返回设备列表，当前画面上的高亮会消失。历史拾取记录仍保留在本机，可再次进入查看。',
      '确认结束拾取？',
      {
        type: 'warning',
        confirmButtonText: '结束拾取',
        cancelButtonText: '继续拾取',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  endPickSession()
}

function endPickSession() {
  try { exitFreezePick({ silent: true }) } catch { /* ignore */ }
  try { stopStream() } catch { /* ignore */ }
  currentPick.value = null
  clearPickHighlight()
  goHub()
}

function captureScreenshot() {
  const canvas = canvasRef.value
  if (!canvas) {
    ElMessage.warning('暂无投屏画面可截取')
    return
  }
  try {
    canvas.toBlob((blob) => {
      if (!blob) {
        ElMessage.error('截图失败')
        return
      }
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `picker-${deviceId.value || 'screen'}-${Date.now()}.png`
      a.rel = 'noopener'
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      ElMessage.success('截图已下载')
    }, 'image/png')
  } catch (e) {
    ElMessage.error('截图失败')
  }
}

async function confirmClearPickInfo() {
  try {
    await ElMessageBox.confirm(
      '将清空当前已展示的控件定位信息与画面高亮。拾取历史仍可在「拾取历史」中回看。',
      '确认清空拾取信息？',
      {
        type: 'warning',
        confirmButtonText: '确认清空',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  clearCurrentPick()
}

function clearCurrentPick() {
  currentPick.value = null
  locatorChain.value = []
  validateResult.value = null
  validateAttempts.value = []
  hierarchyCurrentId.value = ''
  clearPickHighlight()
  ElMessage.success('已清空当前拾取数据')
}

function goCreateCaseFromPick() {
  if (!currentPick.value) {
    ElMessage.warning('请先拾取控件')
    return
  }
  const patch = buildPatchFromPick(currentPick.value)
  if (!patch.locator_value && !Object.keys(patch.locators || {}).length) {
    ElMessage.warning('当前控件缺少定位信息，请先识别或手动补充')
    return
  }
  try {
    sessionStorage.setItem(PICK_TO_CASE_KEY, JSON.stringify({
      ...patch,
      bounds: currentPick.value.bounds || '',
      text: currentPick.value.text || '',
      class: currentPick.value.class || '',
      device_id: deviceId.value,
      device_label: device.value?.model || device.value?.name || '',
      picked_at: currentPick.value.picked_at || Date.now()
    }))
  } catch {
    ElMessage.error('无法暂存拾取数据')
    return
  }
  router.push({ path: '/cases/editor', query: { asset: '1', from_pick: '1' } })
}

function copyAllLocators() {
  if (!currentPick.value) return
  const lines = []
  const rec = recommendedLocatorItem.value
  if (rec?.value) {
    lines.push(`推荐·${formatLocatorType(normalizeLocatorType(rec.type))}: ${rec.value}`)
  }
  for (const s of alternativeSchemes.value) {
    if (!s.value || s.recommended) continue
    lines.push(`${s.label}: ${s.value}`)
  }
  for (const item of locatorChain.value) {
    if (!item?.value || item.enabled === false) continue
    const label = formatLocatorType(normalizeLocatorType(item.type))
    const row = `${label}: ${item.value}`
    if (!lines.includes(row) && !(rec?.value && item.value === rec.value)) {
      lines.push(`链·${row}`)
    }
  }
  if (!lines.length) {
    copyJson()
    return
  }
  copyText(lines.join('\n'))
}

function apiErrorMessage(e, fallback = '请求失败') {
  return e?.response?.data?.message
    || e?.response?.data?.error?.message
    || (e?.message && !String(e.message).includes('status code') ? e.message : fallback)
}

function initReviewContext() {
  const q = route.query
  if (q.pickRecord) pickRecordId.value = Number(q.pickRecord)
  if (q.pickStep != null && q.pickStep !== '') pickStepIndex.value = Number(q.pickStep)
  if (typeof q.returnTo === 'string') pickReturnTo.value = q.returnTo
  pickContextVersion.value = typeof q.versionTag === 'string' ? q.versionTag : ''
  pickContextEnv.value = typeof q.envTag === 'string' ? q.envTag : ''
}

async function loadDevice() {
  if (!deviceId.value) return
  const res = await deviceApi.get(deviceId.value)
  device.value = normalizeDevice(res.data)
  const size = frameSizeFromDevice(device.value)
  frameW.value = size.w
  frameH.value = size.h
  updateLayoutSize()
  setScreenDeviceInfo(deviceId.value, {
    name: device.value?.name || device.value?.serial_number
  })
}

function bindCanvasPipeline() {
  if (!canvasRef.value) return
  if (renderer) renderer.destroy()
  renderer = createScreenCanvasRenderer(canvasRef, {
    onFrameDrawn: () => {
      if (!hasFrame.value) hasFrame.value = true
      maybeClearHighlightOnDarkScreen()
    }
  })
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  detachFrame = attachFrameListener(buffer => {
    if (!renderer || freezePickActive.value) return
    renderer.onFrame(buffer)
  })
  detachMeta = attachMetaListener(meta => {
    renderer?.onMeta(meta)
  })
  if (freezePickActive.value) setStreamPaused(true)
}

function mapCoords(event) {
  const canvas = canvasRef.value
  if (!canvas || !canvas.width || !canvas.height) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  const cx = (event.clientX - rect.left) * (canvas.width / rect.width)
  const cy = (event.clientY - rect.top) * (canvas.height / rect.height)
  const sx = Math.max(0, Math.min(cx, canvas.width))
  const sy = Math.max(0, Math.min(cy, canvas.height))
  const dw = deviceCoordW.value
  const dh = deviceCoordH.value
  return {
    x: Math.round(sx * dw / canvas.width),
    y: Math.round(sy * dh / canvas.height)
  }
}

async function loadUiHierarchy({ force = false } = {}) {
  if (!deviceId.value || !connected.value) return
  hierarchyLoading.value = true
  try {
    const res = await deviceApi.screenUiHierarchy(deviceId.value, { force: !!force })
    const data = res.data || {}
    if (data.ok && data.root) {
      hierarchyRoot.value = data.root
      hierarchyNodeCount.value = Number(data.nodeCount || 0)
      hierarchyTruncated.value = !!data.truncated
      if (data.dump_source) applyDumpSource(data.dump_source)
      uiTreeReady.value = true
    } else {
      hierarchyRoot.value = null
      hierarchyNodeCount.value = 0
      ElMessage.warning(data.error || data.message || '获取 UI 树失败')
    }
  } catch (e) {
    hierarchyRoot.value = null
    ElMessage.warning(apiErrorMessage(e, '获取 UI 树失败'))
  } finally {
    hierarchyLoading.value = false
  }
}

async function refreshUiTree() {
  if (!connected.value) return
  // 刷新 UI 树通常表示页面已变化，收起旧高亮
  clearPickHighlight()
  hierarchyCurrentId.value = ''
  warming.value = true
  uiTreeReady.value = false
  try {
    // 优先 prepare：首次安装 ATX + u2 hierarchy 验证
    let res
    try {
      res = await deviceApi.screenPrepareUi(deviceId.value)
    } catch {
      res = await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
    }
    const data = res.data || {}
    uiTreeReady.value = !!data.ok
    pageContext.value = data.page_context || data.warm?.page_context || 'native'
    applyAppProfile(data.app_profile || data.warm?.app_profile)
    applyDumpSource(data.dump_source || data.source || data.warm?.dump_source)
    if (data.need_user_action) {
      ElMessage.warning(data.message || '请在手机上确认安装/授权 ATX 组件后再次刷新')
    } else if (data.ok) {
      await loadUiHierarchy({ force: false })
      const strategy = appProfile.value?.strategy_label
      const src = dumpSourceLabel.value
      const ctxHint = pageContext.value !== 'native' ? `（${pageContextLabel.value}）` : ''
      const strategyHint = strategy ? `，推荐：${strategy}` : ''
      ElMessage.success(`UI 树已刷新 · ${src}${ctxHint}${strategyHint}`)
    } else {
      ElMessage.warning(data.message || data.error || 'UI 树刷新失败')
    }
  } catch (e) {
    ElMessage.warning(apiErrorMessage(e, 'UI 树刷新失败'))
  } finally {
    warming.value = false
  }
}

async function onHierarchyNodeSelect(node) {
  if (!node?.bounds || !connected.value) return
  if (inspecting.value) return
  interactionMode.value = 'pick'
  inspecting.value = true
  hierarchyCurrentId.value = node.id || ''
  try {
    const res = await deviceApi.screenInspectBounds(deviceId.value, { bounds: node.bounds })
    const data = res.data || {}
    if (!data.bounds && node.bounds) data.bounds = node.bounds
    if (node.contentDesc && !data.content_desc) data.content_desc = node.contentDesc
    if (node.package && !data.package) data.package = node.package
    if (node.index != null && data.index == null) data.index = node.index
    applyPickResult(data)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '选中控件失败'))
  } finally {
    inspecting.value = false
  }
}

async function inspectAt(x, y) {
  if (inspecting.value) return null
  inspecting.value = true
  lastPickMarker.value = { x, y }
  try {
    // 仅在尚未预热时同步 dump；已就绪则直接用缓存，避免每次拾取都 uiautomator dump 导致画面闪烁
    if (!uiTreeReady.value) {
      warming.value = true
      try {
        let warmRes
        try {
          warmRes = await deviceApi.screenPrepareUi(deviceId.value)
        } catch {
          warmRes = await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
        }
        applyAppProfile(warmRes.data?.app_profile || warmRes.data?.warm?.app_profile)
        applyDumpSource(warmRes.data?.dump_source || warmRes.data?.source || warmRes.data?.warm?.dump_source)
        if (warmRes.data?.page_context || warmRes.data?.warm?.page_context) {
          pageContext.value = warmRes.data.page_context || warmRes.data.warm.page_context
        }
        uiTreeReady.value = !!warmRes.data?.ok
      } finally {
        warming.value = false
      }
    }

    const dw = deviceCoordW.value
    const dh = deviceCoordH.value
    let res = await deviceApi.screenInspect(deviceId.value, {
      x, y, display_width: dw, display_height: dh, blocking: true
    })
    let data = { ...(res.data || {}), x, y, id: ++pickSeq }
    applyAppProfile(data.app_profile)
    applyDumpSource(data.dump_source)

    if (data.inspect_error === 'cache_miss' || data.inspect_error === 'ui_dump_failed') {
      uiTreeReady.value = false
      warming.value = true
      try {
        let warmRes
        try {
          warmRes = await deviceApi.screenPrepareUi(deviceId.value)
        } catch {
          warmRes = await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
        }
        applyAppProfile(warmRes.data?.app_profile || warmRes.data?.warm?.app_profile)
        applyDumpSource(warmRes.data?.dump_source || warmRes.data?.source)
        uiTreeReady.value = !!warmRes.data?.ok
      } finally {
        warming.value = false
      }
      res = await deviceApi.screenInspect(deviceId.value, {
        x, y, display_width: dw, display_height: dh, blocking: true
      })
      data = { ...(res.data || {}), x, y, id: data.id }
      applyAppProfile(data.app_profile)
    }

    // 弱命中重试已在执行器端完成，前端不再二次 inspect，避免重复 dump 闪屏
    applyPickResult(data)
    return data
  } catch (e) {
    const fallback = { x, y, valid: false, id: ++pickSeq }
    applyPickResult(fallback)
    ElMessage.warning(apiErrorMessage(e, '控件识别失败，请先刷新 UI 树'))
    return null
  } finally {
    inspecting.value = false
  }
}

function applyPickResult(data) {
  locatorChain.value = buildLocatorChainFromPick(data)
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  const merged = {
    ...data,
    locators,
    locator_chain: locatorChain.value.map((item, idx) => ({ ...item, priority: idx + 1 })),
    locator_type: primary.locator_type || data.locator_type,
    locator_value: primary.locator_value || data.locator_value,
    picked_at: Date.now(),
    device_label: device.value?.model || device.value?.name || device.value?.serial_number || `#${deviceId.value}`
  }
  fillManualForm(merged)
  currentPick.value = merged
  syncRelativeFormFromPick(merged)
  pickHistory.value.unshift(merged)
  if (pickHistory.value.length > 100) pickHistory.value.length = 100
  persistPickHistory(pickHistory.value)
  lastPickMarker.value = { x: merged.inspect_x ?? merged.x, y: merged.inspect_y ?? merged.y }
  triggerHighlightFlash()
  // 点屏结果同步到中间 UI 树
  syncTreeSelectionByBounds(merged.bounds)
  maybeFillControlForm(merged)
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  applyAppProfile(merged.app_profile)
  if (merged.auto_context_switched) {
    ElMessage.success(merged.context_hint || '已自动切换 WebView 上下文')
  } else if (merged.context) {
    pageContext.value = merged.context
  }
  if (merged.source === 'ocr_screen' && merged.valid) {
    ElMessage.success(merged.strategy_applied === 'ocr_first'
      ? '已按 App 类型优先使用 OCR 识别'
      : 'OCR 识别成功')
  } else if (!merged.valid || isWeakPick(merged)) {
    const preferOcr = merged.prefer_ocr || appProfile.value?.recommended_strategy === 'ocr'
    ElMessage.warning(
      preferOcr
        ? (merged.strategy_hint || '当前 App 不适合纯 UI 树，请用 OCR 兜底或屏幕坐标')
        : (isWeakPick(merged) ? '识别到高风险控件，请检查定位链或手动补充' : '未识别到稳定控件，可手动填写定位')
    )
  }
  if (hasDuplicateRisk(merged)) {
    notifyDuplicateRisk(merged)
  }
}

function syncTreeSelectionByBounds(bounds) {
  const b = String(bounds || '').trim()
  if (!b || !hierarchyRoot.value) {
    hierarchyCurrentId.value = ''
    return
  }
  const hit = hierarchyTreeRef.value?.findByBounds?.(b)
  hierarchyCurrentId.value = hit?.id || ''
}

async function validateCurrentLocator() {
  if (!connected.value || !locatorChain.value.length) return
  validating.value = true
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  try {
    const locators = chainToLocators(locatorChain.value)
    const res = await deviceApi.screenValidateLocator(deviceId.value, {
      locators,
      locator_chain: locatorChain.value.filter(i => i.enabled !== false)
    })
    validateResult.value = res.data || {}
    validateAttempts.value = res.data?.attempts || []
    // 校验后按实际命中结果重排优先级（可点击 > 存在 > 未命中）
    locatorChain.value = sortLocatorChainByPassRate(
      locatorChain.value,
      currentPick.value?.platform || device.value?.platform || 'android',
      validateAttempts.value
    )
    const validatedAt = new Date().toISOString()
    const vr = {
      valid: !!res.data?.valid,
      matched_by: res.data?.matched_by || '',
      error: res.data?.error || '',
      attempts_count: validateAttempts.value.length,
      validated_at: validatedAt
    }
    if (currentPick.value) {
      currentPick.value = {
        ...currentPick.value,
        validate_result: vr,
        validated_at: validatedAt,
        locator_chain: locatorChain.value
      }
    }
    if (res.data?.valid) {
      ElMessage.success(`定位有效且可点击，命中 ${formatLocatorType(res.data.matched_by)}`)
    } else if (res.data?.error === 'not_clickable') {
      ElMessage.warning(`定位存在但不可点击（${formatLocatorType(res.data.matched_by)}），请换备用定位`)
    } else {
      ElMessage.warning('当前定位链未命中，请刷新 UI 树或调整备用定位')
    }
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '定位校验失败'))
  } finally {
    validating.value = false
  }
}

async function switchWebViewContext() {
  if (!deviceId.value || !connected.value) return
  try {
    await deviceApi.screenSwitchContext(deviceId.value, { target: 'webview' })
    await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
    uiTreeReady.value = true
    ElMessage.success('已切换 WebView 上下文，请重新识别控件')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '切换 WebView 失败'))
  }
}

async function ocrFallbackPick() {
  const pick = currentPick.value
  if (!pick || !connected.value) return
  try {
    const data = await inspectAt(pick.x, pick.y)
    if (data?.valid && (data.source === 'ocr_screen' || data.locators?.ocr)) {
      ElMessage.success('OCR 兜底识别成功')
    } else {
      ElMessage.warning('OCR 未识别到有效文本，请手动填写 OCR 定位')
    }
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, 'OCR 兜底失败'))
  }
}

function openSavePool() {
  if (!currentPick.value) return
  savePoolForm.page_name = ''
  savePoolForm.element_name = currentPick.value.element_name || manualForm.element_name || ''
  savePoolForm.version_tag = pickContextVersion.value || ''
  savePoolForm.env_tag = pickContextEnv.value || ''
  showSavePool.value = true
}

async function saveToControlPool() {
  if (!savePoolForm.element_name?.trim()) {
    ElMessage.warning('请填写元素名')
    return
  }
  const nameErr = validateElementName(savePoolForm.element_name.trim())
  if (nameErr) {
    ElMessage.warning(nameErr)
    return
  }
  if (!(await confirmDuplicateIfNeeded('仍要保存'))) return
  syncChainToPick()
  const pick = currentPick.value
  const primary = primaryFromChain(locatorChain.value)
  if (!primary.locator_value) {
    ElMessage.warning('请至少保留一条有效主定位')
    return
  }
  savingPool.value = true
  try {
    await controlApi.createPool({
      app_package: resolveAppPackage(),
      page_name: savePoolForm.page_name?.trim() || '',
      element_name: savePoolForm.element_name.trim(),
      platform: device.value?.platform || 'android',
      locator_type: mapLocatorTypeForPool(primary.raw_type || primary.locator_type, primary.locator_value),
      locator_value: mapLocatorValueForPool(primary.raw_type || primary.locator_type, primary.locator_value),
      version_tag: savePoolForm.version_tag?.trim() || '',
      env_tag: savePoolForm.env_tag?.trim() || '',
      control_tag: savePoolForm.control_tag || 'static',
      wait_rule: savePoolForm.wait_timeout_ms > 0 ? {
        condition: savePoolForm.wait_condition,
        timeout_ms: savePoolForm.wait_timeout_ms,
        interval_ms: 500
      } : undefined,
      display_name: pick.display_name || manualForm.display_name,
      widget_type: pick.widget_type,
      risk_level: pick.risk_level,
      risk_tags: pick.risk_tags || [],
      risk_reasons: pick.risk_reasons || [],
      locators: pick.locators || {},
      locator_chain: locatorChain.value,
      validate_result: pick.validate_result || null,
      validated_at: pick.validated_at || pick.validate_result?.validated_at || null,
      tap_x: pick.x,
      tap_y: pick.y
    })
    ElMessage.success('已保存到控件池')
    showSavePool.value = false
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存控件池失败'))
  } finally {
    savingPool.value = false
  }
}

function fillManualForm(data) {
  manualForm.display_name = data?.display_name || ''
  manualForm.element_name = data?.element_name || ''
  const primary = primaryFromChain(locatorChain.value.length ? locatorChain.value : buildLocatorChainFromPick(data || {}))
  manualForm.locator_type = primary.locator_type || data?.locator_type || 'id'
  manualForm.locator_value = primary.locator_value || data?.locator_value || ''
}

function buildPatchFromPick(pick) {
  syncChainToPick()
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  const patch = {
    x: pick.x,
    y: pick.y,
    display_name: pick.display_name || manualForm.display_name || undefined,
    element_name: pick.element_name || manualForm.element_name || undefined,
    locator_type: primary.locator_type || pick.locator_type,
    locator_value: primary.locator_value || pick.locator_value,
    locators,
    locator_chain: locatorChain.value,
    risk_level: pick.risk_level,
    risk_tags: pick.risk_tags,
    risk_reasons: pick.risk_reasons,
    widget_type: pick.widget_type,
    suggested_step_type: pick.suggested_step_type || 'click',
    locator_valid: !!pick.valid && !isWeakPick(pick)
  }
  if (pick.validate_result) {
    patch.validate_result = pick.validate_result
    patch.validated_at = pick.validated_at || pick.validate_result.validated_at
    patch.last_validation = pick.validate_result
  }
  if (manualForm.locator_value?.trim()) {
    const lt = manualForm.locator_type
    patch.locator_type = lt
    patch.locator_value = manualForm.locator_value.trim()
    patch.locators[lt] = patch.locator_value
    if (lt === 'ocr') patch.suggested_step_type = 'tap_ocr'
    patch.locator_valid = true
  }
  if (Object.keys(patch.locators).length) patch.locator_valid = true
  return patch
}

async function applyToReviewStep() {
  if (!currentPick.value || pickRecordId.value == null || pickStepIndex.value == null) return
  if (!(await confirmDuplicateIfNeeded('仍要应用'))) return
  applying.value = true
  try {
    const patch = buildPatchFromPick(currentPick.value)
    if (!patch.locator_valid) {
      ElMessage.warning('请先填写或识别至少一种有效定位方式')
      return
    }
    await recordApi.patchStepLocator(pickRecordId.value, pickStepIndex.value, patch)
    ElMessage.success('已应用到审阅步骤')
    goReturn()
  } catch (e) {
    ElMessage.error(e?.message || '应用失败')
  } finally {
    applying.value = false
  }
}

function applyManualToCurrent() {
  if (!manualForm.locator_value?.trim()) return
  const base = currentPick.value || { x: 0, y: 0, id: ++pickSeq, locators: {} }
  const lt = manualForm.locator_type
  const val = manualForm.locator_value.trim()
  const updated = {
    ...base,
    display_name: manualForm.display_name || base.display_name,
    element_name: manualForm.element_name || base.element_name,
    locator_type: lt,
    locator_value: val,
    locators: { ...(base.locators || {}), [lt]: val },
    valid: true,
    suggested_step_type: lt === 'ocr' ? 'tap_ocr' : 'click'
  }
  currentPick.value = updated
  const idx = pickHistory.value.findIndex(h => h.id === updated.id)
  if (idx >= 0) pickHistory.value[idx] = updated
  else pickHistory.value.unshift(updated)
  persistPickHistory(pickHistory.value)
  ElMessage.success('已更新当前控件')
}

async function clearPickHistory() {
  if (!pickHistory.value.length) return
  try {
    await ElMessageBox.confirm(
      `将清空本轮全部 ${pickHistory.value.length} 条拾取历史，清空后无法从本页回看。`,
      '确认清空拾取历史？',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  pickHistory.value = []
  persistPickHistory([])
  ElMessage.success('已清空拾取历史')
}

/** 从浏览器本地拾取历史批量写回控件库（清库后恢复用） */
async function restoreHistoryToPool() {
  const list = (pickHistory.value || []).filter(Boolean)
  if (!list.length) {
    ElMessage.warning('当前浏览器没有可恢复的拾取历史')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将把本机浏览器中的 ${list.length} 条拾取历史写入控件库（按控件名+定位去重）。是否继续？`,
      '从拾取历史恢复控件',
      { type: 'info', confirmButtonText: '开始入库', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const result = await writeHistoryItemsToPool(list)
  ElMessage.success(`拾取历史入库完成：成功 ${result.ok}，跳过 ${result.skip}，失败 ${result.fail}`)
  if (result.ok > 0) {
    try {
      await ElMessageBox.confirm('是否前往控件库查看？', '入库完成', {
        confirmButtonText: '查看控件库',
        cancelButtonText: '留在本页',
        type: 'success'
      })
      router.push('/controls')
    } catch { /* stay */ }
  }
}

async function restoreSingleHistoryToPool(pick) {
  if (!pick) return
  const result = await writeHistoryItemsToPool([pick])
  if (result.ok) ElMessage.success('已写入控件库')
  else if (result.skip) ElMessage.warning('已存在或缺少定位信息，已跳过')
  else ElMessage.error('入库失败')
}

function hubHistoryIndex(index) {
  return (pickHistory.value?.length || 0) - index
}

function hubHistoryLocator(pick) {
  if (!pick) return { type: '', value: '' }
  const chain = Array.isArray(pick.locator_chain) && pick.locator_chain.length
    ? pick.locator_chain
    : buildLocatorChainFromPick(pick)
  const primary = primaryFromChain(chain)
  const type = primary.locator_type || pick.locator_type || ''
  const value = primary.locator_value || pick.locator_value || ''
  if (type || value) return { type, value }
  const locs = pick.locators
  if (locs && typeof locs === 'object') {
    const first = Object.entries(locs).find(([, v]) => v != null && String(v).trim())
    if (first) return { type: first[0], value: String(first[1]) }
  }
  return { type: '', value: '' }
}

function formatHubPickTime(ts) {
  if (!ts) return '-'
  try {
    const d = new Date(ts)
    if (Number.isNaN(d.getTime())) return String(ts)
    const p = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  } catch {
    return String(ts)
  }
}

async function writeHistoryItemsToPool(list) {
  let ok = 0
  let skip = 0
  let fail = 0
  const seen = new Set()
  for (const pick of list) {
    const chain = Array.isArray(pick.locator_chain) && pick.locator_chain.length
      ? pick.locator_chain
      : buildLocatorChainFromPick(pick)
    const primary = primaryFromChain(chain)
    const elementName = String(pick.element_name || pick.display_name || '').trim()
      || `控件_${pick.x ?? 0}_${pick.y ?? 0}`
    const rawType = primary.raw_type || primary.locator_type || pick.locator_type || ''
    const rawValue = primary.locator_value || pick.locator_value || ''
    let locatorValue = mapLocatorValueForPool(rawType, rawValue)
    let locatorType = mapLocatorTypeForPool(rawType, rawValue)
    // 兜底：从 locators 对象取第一条有效定位
    if (!locatorValue && pick.locators && typeof pick.locators === 'object') {
      const first = Object.entries(pick.locators).find(([, v]) => v != null && String(v).trim())
      if (first) {
        locatorType = mapLocatorTypeForPool(first[0], first[1])
        locatorValue = mapLocatorValueForPool(first[0], first[1])
      }
    }
    if (!locatorType) locatorType = 'id'
    if (!locatorValue) {
      skip += 1
      continue
    }
    const dedupeKey = `${elementName}::${locatorType}::${locatorValue}`
    if (seen.has(dedupeKey)) {
      skip += 1
      continue
    }
    seen.add(dedupeKey)
    try {
      // 后端 Jackson SNAKE_CASE：必须传 snake_case，否则 @Valid 报「参数错误」
      await controlApi.createPool({
        app_package: pick.app_package || resolveAppPackage(),
        page_name: pick.page_name || '',
        element_name: elementName,
        platform: pick.platform || device.value?.platform || 'android',
        locator_type: locatorType,
        locator_value: locatorValue,
        version_tag: pick.version_tag || '',
        env_tag: pick.env_tag || '',
        control_tag: pick.control_tag || 'static',
        display_name: pick.display_name || elementName,
        widget_type: pick.widget_type,
        risk_level: pick.risk_level,
        risk_tags: pick.risk_tags || [],
        risk_reasons: pick.risk_reasons || [],
        locators: pick.locators || chainToLocators(chain),
        locator_chain: chain,
        validate_result: pick.validate_result || null,
        validated_at: pick.validated_at || null,
        tap_x: pick.x,
        tap_y: pick.y,
        device_element_value: pick.device_element_value || pick.text || pick.content_desc || undefined
      }, { silent: true })
      ok += 1
    } catch (e) {
      const msg = String(e?.message || e || '')
      if (/已存在|DUPLICATE|duplicate|unique|冲突/i.test(msg)) skip += 1
      else fail += 1
    }
  }
  return { ok, skip, fail }
}

function selectHistory(item) {
  currentPick.value = item
  locatorChain.value = buildLocatorChainFromPick(item)
  fillManualForm(item)
  lastPickMarker.value = { x: item.x, y: item.y }
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  triggerHighlightFlash()
  pickResultPanelRef.value?.switchPanelMode?.('current')
}

function copyText(text) {
  navigator.clipboard.writeText(String(text)).then(() => ElMessage.success('已复制')).catch(() => {})
}

function copyJson() {
  if (!currentPick.value) return
  const payload = buildPatchFromPick(currentPick.value)
  copyText(JSON.stringify(payload, null, 2))
}

function onMouseDown(event) {
  if (!connected.value) return
  dragStart = mapCoords(event)
  dragStartClient = { x: event.clientX, y: event.clientY }
  window.addEventListener('mouseup', onMouseUp, { once: true })
}

async function onMouseUp(event) {
  if (!dragStart || !dragStartClient || !connected.value) {
    dragStart = null
    dragStartClient = null
    return
  }
  const start = dragStart
  const startClient = dragStartClient
  const end = mapCoords(event)
  const dx = Math.abs(event.clientX - startClient.x)
  const dy = Math.abs(event.clientY - startClient.y)
  dragStart = null
  dragStartClient = null

  // 冻结点选：禁止滑动设备（避免画面与 dump 脱节），只做控件拾取
  if (freezePickActive.value) {
    if (dx > SWIPE_THRESHOLD || dy > SWIPE_THRESHOLD) {
      ElMessage.info('冻结点选中不支持滑动，请先解除冻结或重新截取')
      return
    }
    spawnPickClickEffect(event.clientX, event.clientY)
    await inspectAt(end.x, end.y)
    return
  }

  // 操控模式：拖拽=滑动，点击=点击；识别模式：点击=拾取，拖拽仍滑动设备以便翻页
  if (dx > SWIPE_THRESHOLD || dy > SWIPE_THRESHOLD) {
    clearPickHighlight()
    uiTreeReady.value = false
    try {
      await deviceApi.screenSwipe(deviceId.value, {
        x1: start.x, y1: start.y, x2: end.x, y2: end.y, duration_ms: 300
      })
    } catch (e) {
      ElMessage.error(apiErrorMessage(e, '滑动失败'))
    }
    return
  }

  if (interactionMode.value === 'pick') {
    spawnPickClickEffect(event.clientX, event.clientY)
    await inspectAt(end.x, end.y)
    return
  }

  try {
    clearPickHighlight()
    await deviceApi.screenTap(deviceId.value, { x: end.x, y: end.y })
    uiTreeReady.value = false
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '点击设备失败'))
  }
}

async function pressNavKey(key) {
  if (!connected.value) return
  if (freezePickActive.value) {
    ElMessage.info('冻结点选中请先解除冻结，再操作导航键')
    return
  }
  clearPickHighlight()
  uiTreeReady.value = false
  try {
    await deviceApi.screenKey(deviceId.value, { key })
  } catch {
    ElMessage.error('按键发送失败')
  }
}

async function connectStream() {
  await nextTick()
  updateLayoutSize()
  bindCanvasPipeline()
  await startStream()
  redrawLastFrame()
  uiTreeReady.value = false
}

function onWindowResize() {
  updateLayoutSize()
  redrawLastFrame()
}

let screenWrapObserver = null

function bindScreenWrapObserver() {
  if (screenWrapObserver || typeof ResizeObserver === 'undefined') return
  nextTick(() => {
    const target = screenBodyRef.value || screenWrapRef.value
    if (!target) return
    screenWrapObserver = new ResizeObserver(() => {
      updateLayoutSize()
      redrawLastFrame()
    })
    screenWrapObserver.observe(target)
    updateLayoutSize()
  })
}

function unbindScreenWrapObserver() {
  if (screenWrapObserver) {
    screenWrapObserver.disconnect()
    screenWrapObserver = null
  }
}

watch(deviceId, async (id) => {
  if (!id) {
    loadDevices()
    return
  }
  initReviewContext()
  await loadDevice()
  nextTick(() => {
    bindCanvasPipeline()
    resumeIfAlive()
  })
}, { immediate: false })

onMounted(async () => {
  if (!deviceId.value) {
    loadDevices()
    return
  }
  initReviewContext()
  try {
    await deviceApi.syncUsb()
  } catch { /* ignore */ }
  await loadDevice()
  window.addEventListener('resize', onWindowResize)
  nextTick(() => {
    bindCanvasPipeline()
    bindScreenWrapObserver()
    resumeIfAlive()
    redrawLastFrame()
  })
})

onActivated(() => {
  if (deviceId.value) {
    updateLayoutSize()
    resumeIfAlive()
    nextTick(() => {
      bindCanvasPipeline()
      bindScreenWrapObserver()
      redrawLastFrame()
    })
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  unbindScreenWrapObserver()
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()
  for (const t of pickRippleTimers) clearTimeout(t)
  pickRippleTimers.clear()
  pickClickRipples.value = []
})
</script>

<style scoped>
.picker-page {
  max-width: none;
}
.picker-page :deep(.picker-workbench-header) {
  margin-bottom: 10px;
  padding-bottom: 8px;
}
.picker-page :deep(.picker-workbench-header .page-header__title) {
  font-size: 18px;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 13px;
}
.status-banner.success {
  background: #ecfdf5;
  color: #047857;
}
.status-banner.warning {
  background: #fffbeb;
  color: #92400e;
}
.status-banner.danger {
  background: #fff7ed;
  color: #9a3412;
}
.status-banner__text { flex: 1; min-width: 200px; }
.status-banner__actions { display: flex; gap: 8px; flex-wrap: wrap; }

.dev-name {
  font-weight: 700;
  color: var(--atp-text, #0f172a);
}
.dev-serial {
  font-size: 12px;
  color: #94a3b8;
}

.status-tag {
  font-weight: 700 !important;
  border-width: 1px !important;
}
.status-tag.is-online {
  background: #ecfdf5 !important;
  border-color: #6ee7b7 !important;
  color: #047857 !important;
}
.status-tag.is-online :deep(.el-tag__content) {
  color: #047857 !important;
}
.status-tag.is-offline {
  background: #fff7ed !important;
  border-color: #fdba74 !important;
  color: #9a3412 !important;
}
.status-tag.is-offline :deep(.el-tag__content) {
  color: #9a3412 !important;
}

.hub-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--atp-text-secondary);
}
.hub-empty p { margin: 0 0 16px; }
.hub-empty__actions { display: flex; justify-content: center; gap: 12px; flex-wrap: wrap; }
.hub-history-card {
  margin-top: 16px;
}
.hub-history-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.hub-history-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
}
.hub-history-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #86909c;
}
.hub-history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.hub-loc-code {
  font-size: 12px;
  color: #4e5969;
  word-break: break-all;
}

.screen-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
  line-height: 1.2;
  width: 100%;
}
.screen-title-text {
  font-weight: 700;
  font-size: 13px;
  color: #0f172a;
  white-space: nowrap;
}
.screen-device-name {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.screen-body {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 8px;
  flex: 1;
  min-height: 0;
  width: fit-content;
  max-width: 100%;
}
.screen-wrap {
  line-height: 0;
  flex: 0 0 auto;
  align-self: flex-start;
  overflow: hidden;
  overscroll-behavior: none;
}
.screen-device {
  display: flex;
  flex-direction: column;
  border-radius: var(--atp-radius-md, 12px);
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.15);
}
.screen-side-toolbar {
  flex: 0 0 76px;
  width: 76px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 2px 0;
  overflow: auto;
}
.screen-side-toolbar .toolbar-sep {
  height: 1px;
  width: 100%;
  background: #e2e8f0;
  margin: 2px 0;
}
.screen-side-toolbar :deep(.el-button) {
  width: 100%;
  margin: 0;
  padding: 7px 4px;
  border-radius: 8px;
  font-size: 12px;
}

.hist-name { font-weight: 600; color: var(--atp-text); }
.hist-meta {
  font-size: 11px;
  color: #94a3b8;
}
.history-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  gap: 8px;
}

.picker-workbench {
  display: grid;
  /* 投屏列随内容收缩，减少左右留白 */
  grid-template-columns: max-content minmax(200px, 260px) minmax(300px, 1fr);
  gap: 12px;
  align-items: stretch;
}
.picker-workbench.inspector-3pane {
  height: calc(100vh - 128px);
  min-height: 560px;
}
@media (max-width: 1440px) {
  .picker-workbench {
    grid-template-columns: max-content minmax(190px, 240px) minmax(280px, 1fr);
  }
}
@media (max-width: 1280px) {
  .picker-workbench {
    grid-template-columns: max-content minmax(180px, 220px) minmax(260px, 1fr);
  }
}
@media (max-width: 1100px) {
  .picker-workbench {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }
  .screen-panel, .tree-panel, .detail-panel {
    height: auto;
    max-height: none;
  }
  .screen-panel {
    min-height: 70vh;
  }
  .screen-body {
    width: 100%;
  }
  .tree-panel {
    max-height: 360px;
  }
}
.screen-panel,
.tree-panel,
.detail-panel {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.screen-panel {
  width: max-content;
  max-width: 100%;
}
.screen-panel > .screen-card,
.tree-panel :deep(.ui-hierarchy-tree),
.detail-panel > .pick-result-card {
  flex: 1;
  min-height: 0;
  height: 100%;
}
.screen-card,
.pick-result-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}
.screen-card {
  width: max-content;
  max-width: 100%;
}
.screen-card :deep(.el-card__header),
.pick-result-card :deep(.el-card__header) {
  flex-shrink: 0;
  padding: 8px 10px;
}
.screen-card :deep(.el-card__body),
.pick-result-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  padding: 6px 8px 8px;
}
.screen-card :deep(.el-card__body) {
  padding-top: 4px;
  overflow: hidden;
}
.detail-panel {
  gap: 0;
}
.resolution-tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.screen-frame {
  position: relative;
  background: var(--atp-screen-bg, #0f172a);
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
  border: 2px solid var(--atp-dark-border, #334155);
}
.screen-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  vertical-align: top;
}
.screen-canvas.picking {
  cursor: none;
}
.screen-canvas.frozen {
  cursor: crosshair;
  box-shadow: inset 0 0 0 2px rgba(245, 158, 11, 0.85);
}
.screen-canvas.operating {
  cursor: grab;
}
.screen-canvas.operating:active {
  cursor: grabbing;
}
.freeze-banner {
  position: absolute;
  left: 8px;
  right: 8px;
  top: 8px;
  z-index: 5;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.78);
  color: #fef3c7;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: auto;
}
.freeze-banner-btn {
  border: 1px solid rgba(253, 230, 138, 0.55);
  background: rgba(245, 158, 11, 0.18);
  color: #fde68a;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
}
.freeze-banner-btn:hover {
  background: rgba(245, 158, 11, 0.32);
}
.pick-cursor-ring {
  position: absolute;
  width: 24px;
  height: 24px;
  margin-left: -12px;
  margin-top: -12px;
  border: 2px solid #8b6cf0;
  border-radius: 50%;
  box-sizing: border-box;
  background: rgba(139, 108, 240, 0.14);
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.9),
    0 0 10px rgba(139, 108, 240, 0.45);
  pointer-events: none;
  z-index: 6;
  transform: translateZ(0);
}
.pick-cursor-ring::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 5px;
  height: 5px;
  margin: -2.5px 0 0 -2.5px;
  border-radius: 50%;
  background: #8b6cf0;
  box-shadow: 0 0 4px rgba(139, 108, 240, 0.8);
}
.pick-click-ripple {
  position: absolute;
  width: 14px;
  height: 14px;
  margin-left: -7px;
  margin-top: -7px;
  border-radius: 50%;
  border: 2px solid rgba(139, 108, 240, 0.95);
  background: rgba(139, 108, 240, 0.28);
  pointer-events: none;
  z-index: 7;
  animation: pick-click-ripple 0.45s ease-out forwards;
}
@keyframes pick-click-ripple {
  0% {
    transform: scale(0.55);
    opacity: 1;
  }
  70% {
    opacity: 0.55;
  }
  100% {
    transform: scale(3.4);
    opacity: 0;
  }
}
.pick-highlight {
  position: absolute;
  box-sizing: border-box;
  pointer-events: none;
  border-radius: 3px;
  z-index: 4;
}
.pick-highlight--current {
  border: 2px solid #2563eb;
  background: rgba(21, 82, 214, 0.12);
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.25);
}
.pick-highlight--current.is-flashing {
  animation: pick-flash 0.85s ease-out 1;
}
@keyframes pick-flash {
  0% {
    background: rgba(37, 99, 235, 0.42);
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.55), 0 0 18px rgba(59, 130, 246, 0.65);
    border-color: #60a5fa;
  }
  55% {
    background: rgba(37, 99, 235, 0.18);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.35);
  }
  100% {
    background: rgba(37, 99, 235, 0.12);
    box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.25);
    border-color: #2563eb;
  }
}
.btn-end-pick {
  --el-button-bg-color: #ef4444;
  --el-button-border-color: #ef4444;
  --el-button-text-color: #fff;
  --el-button-hover-bg-color: #dc2626;
  --el-button-hover-border-color: #dc2626;
  --el-button-hover-text-color: #fff;
}
.btn-outline-blue {
  --el-button-bg-color: #fff;
  --el-button-border-color: #3b82f6;
  --el-button-text-color: #2563eb;
  --el-button-hover-bg-color: #eff6ff;
  --el-button-hover-border-color: #2563eb;
  --el-button-hover-text-color: #1d4ed8;
  --el-button-disabled-bg-color: #fff;
  --el-button-disabled-border-color: #bfdbfe;
  --el-button-disabled-text-color: #93c5fd;
}
.btn-record-case {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
  font-weight: 600;
}
.screen-placeholder {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--atp-screen-text-muted, #94a3b8);
  gap: 8px;
  font-size: 13px;
  background: var(--atp-screen-bg, #0f172a);
}
.frame-dim {
  font-size: 12px;
  opacity: 0.85;
}
.pick-mode-row {
  margin-top: 12px;
}
.panel-block {
  margin-top: 0;
}
.review-banner {
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
  font-size: 13px;
}
.action-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 0;
  line-height: 1.5;
}
.app-profile-banner {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.55;
}
.app-profile-banner.is-ok {
  border-color: color-mix(in srgb, var(--el-color-success) 35%, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--el-color-success) 8%, var(--el-fill-color-blank));
}
.app-profile-banner.is-warn {
  border-color: color-mix(in srgb, var(--el-color-warning) 40%, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--el-color-warning) 10%, var(--el-fill-color-blank));
}
.app-profile-banner.is-ocr {
  border-color: color-mix(in srgb, var(--el-color-danger) 30%, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--el-color-danger) 8%, var(--el-fill-color-blank));
}
.app-profile-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.app-profile-pkg {
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.app-profile-reason {
  margin-top: 4px;
  color: var(--el-text-color-regular);
}
.app-profile-meta {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}
.app-profile-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-color-warning-dark-2, #b45309);
}
.inspecting-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--el-color-primary);
  font-size: 13px;
  border-radius: inherit;
  pointer-events: none;
}
.spin {
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.pick-result-card {
  position: relative;
  min-height: 0;
}
.pick-result-card :deep(.pick-result-panel) {
  flex: 1;
  min-height: 0;
}
.pick-result-body {
  display: flex;
  flex-direction: column;
  gap: var(--pick-card-gap);
}
.pick-result-body.dimmed {
  opacity: 0.45;
  transition: opacity 0.15s;
}
.pick-result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.pick-result-title {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}
.pick-result-title-sep {
  margin: 0 6px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.pick-result-title-name {
  color: var(--el-color-primary);
}
.pick-status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.pick-alert {
  margin: 0;
}
.pick-alert :deep(.el-alert) {
  padding: 6px 10px;
}
.result-block {
  border: 1px solid var(--pick-border);
  border-radius: var(--pick-card-radius);
  background: var(--pick-surface-muted);
  padding: var(--pick-card-pad);
}
.result-block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.result-block-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.basic-info-card {
  background: color-mix(in srgb, var(--el-fill-color-light) 70%, var(--el-fill-color-blank));
}
.info-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
}
.info-table th,
.info-table td {
  border: 1px solid var(--pick-border);
  padding: 8px 10px;
  vertical-align: middle;
  word-break: break-all;
}
.info-table th {
  width: 18%;
  background: color-mix(in srgb, var(--el-fill-color) 80%, transparent);
  color: var(--el-text-color-secondary);
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
}
.info-table td {
  width: 32%;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
}
.recommend-card {
  background: var(--pick-rec-bg);
  border-color: var(--pick-rec-border);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--el-color-success) 12%, transparent);
}
.recommend-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.recommend-card-title {
  font-size: 14px;
  font-weight: 650;
  color: var(--el-color-success);
}
.recommend-card-value {
  display: block;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
  padding: 8px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--el-fill-color-blank) 88%, var(--el-color-success));
  border: 1px solid color-mix(in srgb, var(--el-color-success) 18%, var(--el-border-color-lighter));
  color: var(--el-text-color-primary);
}
.recommend-card-desc {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.alt-schemes-card {
  background: var(--el-fill-color-blank);
}
.alt-scheme-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.alt-scheme-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.alt-scheme-row:first-of-type {
  padding-top: 0;
}
.alt-scheme-row.empty {
  opacity: 0.45;
}
.alt-scheme-name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
.alt-scheme-value {
  font-size: 12px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alt-scheme-row.empty .alt-scheme-value {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}
.relative-config-card {
  background: var(--el-fill-color-blank);
}
.relative-cols {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.relative-col {
  border: 1px solid var(--pick-border);
  border-radius: 6px;
  padding: 10px;
  background: var(--pick-surface-muted);
  min-width: 0;
}
.relative-col-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}
.relative-col-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.region-inner {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 6px;
}
.chain-manage-card {
  background: var(--el-fill-color-blank);
}
.chain-table {
  border: 1px solid var(--pick-border);
  border-radius: 6px;
  overflow: hidden;
}
.chain-table-head,
.chain-table-row {
  display: grid;
  grid-template-columns: 64px 150px minmax(0, 1fr) 64px 72px;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}
.chain-table-head {
  background: var(--el-fill-color-light);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--pick-border);
}
.chain-table-row {
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer;
  transition: background 0.12s;
}
.chain-table-row:last-child {
  border-bottom: none;
}
.chain-table-row:hover {
  background: var(--el-fill-color-lighter);
}
.chain-table-row.recommend {
  background: color-mix(in srgb, var(--el-color-success) 8%, var(--el-fill-color-blank));
}
.chain-table-row.off {
  opacity: 0.5;
}
.chain-table-row .col-pri {
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.chain-table-row .col-name {
  color: var(--el-text-color-primary);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.chain-table-row .col-name em {
  font-style: normal;
  font-size: 11px;
  color: var(--el-color-success);
  background: color-mix(in srgb, var(--el-color-success) 14%, transparent);
  padding: 0 6px;
  border-radius: 999px;
}
.chain-table-row .col-expr {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-regular);
}
.chain-table-row .col-enable,
.chain-table-row .col-sort {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
@media (max-width: 1100px) {
  .relative-cols {
    grid-template-columns: 1fr;
  }
  .alt-scheme-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .chain-table-head,
  .chain-table-row {
    grid-template-columns: 48px minmax(0, 1fr) 56px 64px;
  }
  .chain-table-head .col-name,
  .chain-table-row .col-name {
    display: none;
  }
}
.validate-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.validate-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--pick-card-radius);
  font-size: 12px;
}
.validate-banner.ok {
  background: var(--pick-validate-ok);
  color: var(--el-color-success);
}
.validate-banner.fail {
  background: var(--pick-validate-fail);
  color: var(--el-color-danger);
}
.validate-detail {
  margin-top: 4px;
  padding: 6px 8px;
  border-radius: var(--pick-card-radius);
  background: var(--pick-detail-bg);
  font-size: 11px;
}
.validate-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 4px;
}
.validate-line .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  margin-top: 4px;
  flex-shrink: 0;
}
.validate-line .dot.ok {
  background: var(--el-color-success);
}
.validate-line .dot.warn {
  background: var(--el-color-warning);
}
.validate-line .validate-status {
  flex: 0 0 52px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}
.validate-line .vt {
  flex: 0 0 72px;
  color: var(--el-text-color-secondary);
}
.validate-line code {
  flex: 1;
  word-break: break-all;
}
.pick-actions-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--pick-border);
}
.save-loc-preview {
  display: block;
  word-break: break-all;
  font-size: 11px;
  background: var(--pick-surface-muted);
  padding: 5px 7px;
  border-radius: 4px;
}
.history-row {
  display: flex;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  margin-bottom: 4px;
}
.history-row:hover,
.history-row.active {
  background: var(--el-fill-color-light);
}
.hist-no {
  flex: 0 0 20px;
  color: var(--el-text-color-secondary);
}
.hist-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.hist-loc {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}


@media (prefers-color-scheme: dark) {
  .pick-result-card {
    --pick-surface: #161b22;
    --pick-surface-muted: #1c2430;
    --pick-border: #2f3a4a;
    --pick-rec-bg: color-mix(in srgb, var(--el-color-success) 14%, #161b22);
    --pick-rec-border: color-mix(in srgb, var(--el-color-success) 32%, #2f3a4a);
    --pick-validate-ok: color-mix(in srgb, var(--el-color-success) 16%, #161b22);
    --pick-validate-fail: color-mix(in srgb, var(--el-color-danger) 16%, #161b22);
  }
}

:global(html.dark) .pick-result-card {
  --pick-surface: var(--el-bg-color-overlay, #1d1e1f);
  --pick-surface-muted: var(--el-fill-color-light, #262727);
  --pick-border: var(--el-border-color-lighter, #414243);
  --pick-rec-bg: color-mix(in srgb, var(--el-color-success) 14%, var(--el-bg-color, #141414));
  --pick-rec-border: color-mix(in srgb, var(--el-color-success) 30%, var(--el-border-color-lighter, #414243));
  --pick-validate-ok: color-mix(in srgb, var(--el-color-success) 16%, var(--el-bg-color, #141414));
  --pick-validate-fail: color-mix(in srgb, var(--el-color-danger) 16%, var(--el-bg-color, #141414));
}

</style>
