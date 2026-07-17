<template>
  <div class="devices-page">
    <!-- 模块 1：Hero -->
    <section class="hero">
      <div class="hero-text">
        <h1>设备管理</h1>
        <p>USB 接入自动入池 · 远程投屏 · 自动化调度</p>
        <el-alert
          v-if="recordIntent"
          class="record-intent-alert"
          type="warning"
          show-icon
          :closable="true"
          title="一键录制：请选择在线设备进入投屏后开始录制"
          @close="clearRecordIntent"
        />
      </div>
      <div class="hero-actions">
        <el-button :loading="downloadingLauncher" @click="downloadLauncher">
          <el-icon><Download /></el-icon> 下载启动器
        </el-button>
        <el-button type="primary" class="btn-sync" :loading="syncingUsb" @click="syncUsbDevices">
          <el-icon><Refresh /></el-icon> 同步 USB
        </el-button>
        <el-button v-if="userStore.isAdmin" class="btn-whitelist" @click="showWhitelistDialog = true">
          <el-icon><Plus /></el-icon> 白名单
        </el-button>
      </div>
    </section>

    <!-- 模块 2：指标 4 宫格 -->
    <section class="stats">
      <div
        v-for="s in statItems"
        :key="s.key"
        class="stat-card"
        :class="[s.key, { active: filters.status === s.filterValue, 'warn-online': s.key === 'online' && s.value === 0, 'warn-busy': s.key === 'busy' && s.value >= 3 }]"
        @click="applyStatFilter(s)"
      >
        <div v-if="s.key === 'online' && s.value === 0" class="stat-alert-bar" title="当前无可用执行设备" />
        <div class="stat-icon"><el-icon :size="22"><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
          <div v-if="s.key === 'online' && s.value === 0" class="stat-hint">当前无可用执行设备</div>
        </div>
      </div>
    </section>

    <!-- 模块 4：筛选工具栏 -->
    <section class="toolbar-card">
      <div class="filters">
        <el-select v-model="filters.platform" placeholder="平台" clearable style="width:120px" @change="onFilterChange">
          <el-option label="Android" value="android" />
          <el-option label="iOS" value="ios" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:140px" @change="onFilterChange">
          <el-option label="空闲在线" value="online" />
          <el-option label="执行中" value="busy" />
          <el-option label="投屏中" value="streaming" />
          <el-option label="离线故障" value="offline" />
        </el-select>
        <el-select v-model="filters.group" placeholder="设备分组" clearable style="width:150px" @change="onFilterChange">
          <el-option v-for="g in DEVICE_GROUPS" :key="g" :label="g" :value="g" />
        </el-select>
        <el-select v-model="filters.os_version" placeholder="系统版本" clearable style="width:140px" @change="onFilterChange">
          <el-option v-for="v in osVersionOptions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="型号 / 序列号 / 设备 ID"
          clearable
          prefix-icon="Search"
          style="width:220px"
          @change="onFilterChange"
          @clear="onFilterChange"
        />
      </div>
      <div class="toolbar-right">
        <el-button circle :loading="loading" @click="loadDevices">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </section>

    <!-- 模块 5：设备卡片 -->
    <section class="device-grid" v-loading="loading">
      <article
        v-for="row in filteredDevices"
        :key="row.id"
        :id="`device-card-${row.id}`"
        class="device-card-v"
        :class="[
          cardStatusClass(row),
          {
            streaming: isStreaming(row.id),
            highlight: highlightId === row.id,
            'is-loading': cardLoadingId === row.id
          }
        ]"
        v-loading="cardLoadingId === row.id"
      >
        <div class="card-top-bar" />
        <div class="card-head">
          <el-tag size="small" round effect="dark" class="status-pill" :class="cardStatusClass(row)">
            {{ statusLabel(row) }}
          </el-tag>
          <h3 class="device-name" :title="row.model || row.name">{{ row.model || row.name || row.serial_number }}</h3>
        </div>

        <div class="card-core">
          <div class="core-row">
            <span class="core-label">占用人</span>
            <span class="core-value">{{ occupyLabel(row) }}</span>
          </div>
          <div v-if="isOccupied(row)" class="core-row">
            <span class="core-label">占用倒计时</span>
            <span class="core-value" :class="{ overtime: isOccupyOvertime(row) }">{{ occupyCountdown(row) }}</span>
          </div>
          <div class="core-row">
            <span class="core-label">系统版本</span>
            <span class="core-value">{{ androidVer(row) || '-' }}</span>
          </div>
          <div class="battery-row">
            <el-icon><Lightning /></el-icon>
            <div class="battery-track">
              <i :style="{ width: `${row.battery_level || 0}%` }" :class="batteryClass(row.battery_level)" />
            </div>
            <span class="battery-text" :class="batteryClass(row.battery_level)">{{ row.battery_level ?? '-' }}%</span>
          </div>
          <div v-if="row.tags" class="tags">
            <el-tag v-for="t in row.tags.split(',').slice(0, 3)" :key="t" size="small" round effect="plain">{{ t.trim() }}</el-tag>
          </div>
          <p v-if="row.executor_url" class="executor mono" :title="row.executor_url">执行器 {{ shortExecutor(row.executor_url) }}</p>
        </div>

        <div class="card-meta">
          <span>{{ row.screen_width && row.screen_height ? `${row.screen_width}×${row.screen_height}` : '分辨率未知' }}</span>
          <span class="mono">{{ row.serial_number || '-' }}</span>
        </div>

        <div class="card-actions">
          <el-button
            type="primary"
            class="screen-btn"
            :disabled="!canScreen(row)"
            @click="openScreen(row)"
          >
            <el-icon><Monitor /></el-icon>
            {{ isStreaming(row.id) ? '回到投屏' : '开始投屏' }}
          </el-button>
          <div class="action-row">
            <el-button size="small" :disabled="cardLoadingId === row.id" @click="rebootDevice(row)">重启设备</el-button>
            <el-button
              size="small"
              type="warning"
              plain
              :disabled="!isOccupied(row) && row.status !== 'busy'"
              @click="releaseOccupy(row)"
            >释放占用</el-button>
          </div>
          <div class="action-row secondary">
            <el-button
              size="small"
              plain
              :disabled="!canScreen(row)"
              @click="openPicker(row)"
            >控件拾取</el-button>
            <el-button size="small" class="manage-btn" @click="openManage(row)">管理</el-button>
          </div>
        </div>
      </article>

      <!-- 空状态：筛选无结果 -->
      <div v-if="!loading && allDevices.length && !filteredDevices.length" class="empty-panel">
        <p>未找到符合筛选条件的设备</p>
        <el-button type="primary" plain @click="resetFilters">重置筛选条件</el-button>
      </div>

      <el-empty
        v-else-if="!loading && !allDevices.length"
        description="暂无设备，请连接 USB 后同步"
        class="grid-empty"
      />
    </section>

    <div v-if="filteredDevices.length" class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="filteredTotal"
        layout="total, prev, pager, next"
        @current-change="page = $event"
      />
    </div>

    <!-- 白名单 -->
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
        <el-button type="primary" @click="addWhitelist">确认</el-button>
      </template>
    </el-dialog>

    <!-- 管理弹窗：低频操作收拢 -->
    <el-dialog v-model="showManageDialog" title="设备管理" width="520px" destroy-on-close>
      <el-form v-if="manageRow" label-width="100px">
        <el-form-item label="设备名称">
          <el-input :model-value="manageRow.name || manageRow.model" disabled />
        </el-form-item>
        <el-form-item label="归属分组">
          <el-select v-model="manageForm.group" clearable placeholder="选择分组" style="width:100%">
            <el-option v-for="g in DEVICE_GROUPS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="manageForm.tags" placeholder="逗号分隔，不含分组" />
        </el-form-item>
        <el-divider content-position="left">低频运维</el-divider>
        <div class="manage-ops">
          <el-button @click="openCalibration(manageRow)">手势校准</el-button>
          <el-button v-if="manageRow.status === 'error'" @click="resetHealth(manageRow)">恢复健康</el-button>
          <el-button @click="setMaintenance(manageRow)">设为维护</el-button>
          <el-button v-if="manageRow.platform === 'ios'" @click="deployWda(manageRow)">部署 WDA</el-button>
          <el-button type="danger" plain @click="handleDelete(manageRow)">移除 / 下线</el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showManageDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingManage" @click="saveManage">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCalibrationDialog" title="手势坐标校准" width="440px">
      <el-form :model="calibrationForm" label-width="90px">
        <el-form-item label="X 偏移"><el-input-number v-model="calibrationForm.offset_x" :step="1" /></el-form-item>
        <el-form-item label="Y 偏移"><el-input-number v-model="calibrationForm.offset_y" :step="1" /></el-form-item>
        <el-form-item label="X 缩放"><el-input-number v-model="calibrationForm.scale_x" :step="0.01" :min="0.5" :max="2" /></el-form-item>
        <el-form-item label="Y 缩放"><el-input-number v-model="calibrationForm.scale_y" :step="0.01" :min="0.5" :max="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCalibrationDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCalibration">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { activeScreenSessions, syncActiveScreenSessions } from '@/composables/useScreenStream'
import { androidVersionLabel } from '@/composables/screenFrameStyle'
import { normalizeDevice, mergeDeviceGroupTag } from '@/utils/device'
import { ElMessage, ElMessageBox } from 'element-plus'

const DEVICE_GROUPS = ['录制专用组', '回归测试组', '线上功能组']
const OCCUPY_WARN_MS = 30 * 60 * 1000

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const recordIntent = ref(route.query.intent === 'record')

const loading = ref(false)
const syncingUsb = ref(false)
const allDevices = ref([])
const page = ref(1)
const pageSize = ref(20)
const highlightId = ref(null)
const cardLoadingId = ref(null)
const nowTick = ref(Date.now())
let snapshotTimer = null
let clockTimer = null

const filters = reactive({
  platform: '',
  status: '',
  group: '',
  os_version: '',
  keyword: ''
})

const showWhitelistDialog = ref(false)
const whitelistForm = reactive({ serial_number: '', platform: 'android', remark: '' })
const showCalibrationDialog = ref(false)
const calibrationDeviceId = ref(null)
const calibrationForm = reactive({ offset_x: 0, offset_y: 0, scale_x: 1, scale_y: 1 })
const showTagsDialog = ref(false)
const tagsDeviceId = ref(null)
const tagsForm = reactive({ tags: '' })
const downloadingLauncher = ref(false)
const snapshotTick = ref(0)
const showManageDialog = ref(false)
const manageRow = ref(null)
const manageForm = reactive({ group: '', tags: '' })
const savingManage = ref(false)

function clearRecordIntent() {
  recordIntent.value = false
  if (route.query.intent === 'record') {
    const q = { ...route.query }
    delete q.intent
    router.replace({ path: '/devices', query: q })
  }
}

function applyQueryFilters() {
  const st = route.query.status
  if (typeof st === 'string' && st) {
    // 首页「在线设备」→ 空闲在线
    filters.status = st === 'online' ? 'online' : st
  }
  if (route.query.intent === 'record') recordIntent.value = true
}

const streamingCount = computed(() => activeScreenSessions.value.length)

function isStreaming(deviceId) {
  return activeScreenSessions.value.some(s => String(s.deviceId) === String(deviceId))
}

function visualStatus(row) {
  if (isStreaming(row.id)) return 'streaming'
  if (row.status === 'busy') return 'busy'
  if (row.status === 'online') return 'online'
  if (row.status === 'offline' || row.status === 'error') return 'offline'
  return row.status || 'offline'
}

function cardStatusClass(row) {
  const s = visualStatus(row)
  if (s === 'online') return 'st-online'
  if (s === 'busy' || s === 'streaming') return 'st-busy'
  return 'st-offline'
}

function statusLabel(row) {
  const s = visualStatus(row)
  if (s === 'online') return '空闲'
  if (s === 'busy') return '执行中'
  if (s === 'streaming') return '投屏中'
  return '离线故障'
}

const onlineIdleCount = computed(() =>
  allDevices.value.filter(d => d.status === 'online' && !isStreaming(d.id)).length
)
const busyCount = computed(() => allDevices.value.filter(d => d.status === 'busy').length)

const statItems = computed(() => [
  { key: 'online', label: '在线设备', value: onlineIdleCount.value, icon: 'CircleCheck', filterValue: 'online' },
  { key: 'busy', label: '执行中', value: busyCount.value, icon: 'Loading', filterValue: 'busy' },
  { key: 'streaming', label: '投屏中', value: streamingCount.value, icon: 'Monitor', filterValue: 'streaming' },
  { key: 'total', label: '设备总数', value: allDevices.value.length, icon: 'Iphone', filterValue: '' }
])

const osVersionOptions = computed(() => {
  const set = new Set()
  for (const d of allDevices.value) {
    const label = androidVer(d)
    if (label && label !== '-') set.add(label)
  }
  return [...set].sort()
})

function matchFilters(row) {
  if (filters.platform && row.platform !== filters.platform) return false
  if (filters.group && row.device_group !== filters.group) return false
  if (filters.os_version && androidVer(row) !== filters.os_version) return false
  if (filters.keyword) {
    const k = filters.keyword.toLowerCase()
    const hay = `${row.name} ${row.model} ${row.serial_number} ${row.id}`.toLowerCase()
    if (!hay.includes(k)) return false
  }
  if (filters.status) {
    const vs = visualStatus(row)
    if (filters.status === 'online' && vs !== 'online') return false
    if (filters.status === 'busy' && vs !== 'busy') return false
    if (filters.status === 'streaming' && vs !== 'streaming') return false
    if (filters.status === 'offline' && vs !== 'offline') return false
  }
  return true
}

const matchedDevices = computed(() => allDevices.value.filter(matchFilters))
const filteredTotal = computed(() => matchedDevices.value.length)
const filteredDevices = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return matchedDevices.value.slice(start, start + pageSize.value)
})

function applyStatFilter(s) {
  filters.status = s.filterValue || ''
  page.value = 1
  syncQuery()
}

function onFilterChange() {
  page.value = 1
  syncQuery()
}

function resetFilters() {
  filters.platform = ''
  filters.status = ''
  filters.group = ''
  filters.os_version = ''
  filters.keyword = ''
  page.value = 1
  syncQuery()
}

function syncQuery() {
  const q = {}
  if (filters.status) q.status = filters.status
  if (route.query.intent) q.intent = route.query.intent
  router.replace({ path: '/devices', query: q })
}

function androidVer(row) {
  return androidVersionLabel(row) || (row.os_version ? `Android${row.os_version}` : '-')
}

function shortExecutor(url) {
  if (!url) return ''
  try {
    return new URL(url).host
  } catch {
    return String(url).replace(/^https?:\/\//, '')
  }
}

function batteryClass(level) {
  if (level == null) return ''
  if (level < 20) return 'low'
  if (level < 50) return 'mid'
  return 'high'
}

function canScreen(row) {
  return row.platform === 'android' && row.status !== 'offline' && row.status !== 'error'
}

function isOccupied(row) {
  return !!(row.locked_by_task_id || row.status === 'busy' || row.occupied_by)
}

function occupyLabel(row) {
  if (row.occupied_by) return row.occupied_by
  if (row.locked_by_task_id) return `任务 #${row.locked_by_task_id}`
  if (row.status === 'busy') return '任务占用中'
  if (isStreaming(row.id)) return '投屏会话'
  return '空闲'
}

function occupyStartMs(row) {
  if (row.occupied_at) {
    const t = new Date(row.occupied_at).getTime()
    if (!Number.isNaN(t)) return t
  }
  if (row.lock_expires_at) {
    // 无开始时间时用 expires - 默认超时窗口估算
    const exp = new Date(row.lock_expires_at).getTime()
    if (!Number.isNaN(exp)) return exp - 60 * 60 * 1000
  }
  return null
}

function isOccupyOvertime(row) {
  if (!isOccupied(row)) return false
  const start = occupyStartMs(row)
  if (start == null) return false
  return nowTick.value - start >= OCCUPY_WARN_MS
}

function occupyCountdown(row) {
  nowTick.value // reactive
  const start = occupyStartMs(row)
  if (start == null) {
    if (row.lock_expires_at) {
      const left = new Date(row.lock_expires_at).getTime() - nowTick.value
      if (left <= 0) return '已超时'
      return formatDuration(left) + ' 后释放'
    }
    return '占用中'
  }
  const elapsed = nowTick.value - start
  if (elapsed >= OCCUPY_WARN_MS) return `已超时 ${formatDuration(elapsed - OCCUPY_WARN_MS)}`
  return `已占用 ${formatDuration(elapsed)}`
}

function formatDuration(ms) {
  const sec = Math.max(0, Math.floor(ms / 1000))
  const m = Math.floor(sec / 60)
  const s = sec % 60
  if (m >= 60) {
    const h = Math.floor(m / 60)
    return `${h}h ${m % 60}m`
  }
  return `${m}m ${String(s).padStart(2, '0')}s`
}

async function syncUsbDevices() {
  syncingUsb.value = true
  try {
    const res = await deviceApi.syncUsb()
    ElMessage.success(res.data?.message || 'USB 设备已同步')
    await loadDevices()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '同步失败')
  } finally {
    syncingUsb.value = false
  }
}

async function downloadLauncher() {
  downloadingLauncher.value = true
  try {
    const info = await deviceApi.launcherInfo()
    if (!info.data?.available) {
      ElMessage.warning(info.data?.message || '启动器尚未就绪，请联系管理员打包')
      return
    }
    const fileBlob = await deviceApi.downloadLauncher()
    if (!(fileBlob instanceof Blob)) {
      ElMessage.error('下载失败：响应格式不正确')
      return
    }
    if (fileBlob.type && fileBlob.type.includes('json')) {
      const text = await fileBlob.text()
      try {
        const j = JSON.parse(text)
        ElMessage.error(j.message || j.error?.message || '下载失败')
      } catch {
        ElMessage.error('下载失败')
      }
      return
    }
    const url = URL.createObjectURL(fileBlob)
    const a = document.createElement('a')
    a.href = url
    a.download = info.data?.filename || 'TestFlow-Executor.exe'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载。保存后双击运行，填写中心地址即可接入本机手机。')
  } catch (e) {
    ElMessage.error(e?.message || '下载启动器失败')
  } finally {
    downloadingLauncher.value = false
  }
}

async function loadDevices() {
  loading.value = true
  try {
    const res = await deviceApi.list({ page: 1, page_size: 200 })
    allDevices.value = (res.data.list || []).map(normalizeDevice)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载设备失败')
  } finally {
    loading.value = false
  }
}

async function addWhitelist() {
  await deviceApi.addWhitelist(whitelistForm)
  ElMessage.success('白名单添加成功')
  showWhitelistDialog.value = false
  Object.assign(whitelistForm, { serial_number: '', platform: 'android', remark: '' })
  loadDevices()
}

function openScreen(row) {
  ElMessage.success('正在打开投屏…')
  router.push(`/devices/${row.id}/screen`)
}

function openPicker(row) {
  router.push(`/element-picker/${row.id}`)
}

async function rebootDevice(row) {
  cardLoadingId.value = row.id
  try {
    if (typeof deviceApi.resetHealth === 'function') {
      await deviceApi.resetHealth(row.id)
    } else {
      await deviceApi.updateStatus(row.id, { status: 'online' })
    }
    ElMessage.success('重启指令已下发')
    await loadDevices()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重启失败')
  } finally {
    cardLoadingId.value = null
  }
}

async function releaseOccupy(row) {
  cardLoadingId.value = row.id
  try {
    await deviceApi.updateStatus(row.id, { status: 'online' })
    ElMessage.success('已释放占用')
    await loadDevices()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '释放失败')
  } finally {
    cardLoadingId.value = null
  }
}

function openManage(row) {
  manageRow.value = row
  manageForm.group = row.device_group || ''
  // 去掉 group: 前缀后的展示标签
  manageForm.tags = String(row.tags || '')
    .split(',')
    .map(s => s.trim())
    .filter(s => s && !/^group:/i.test(s) && !DEVICE_GROUPS.includes(s))
    .join(',')
  showManageDialog.value = true
}

async function saveManage() {
  if (!manageRow.value) return
  savingManage.value = true
  try {
    const tags = mergeDeviceGroupTag(manageForm.tags, manageForm.group)
    await deviceApi.updateTags(manageRow.value.id, { tags })
    ElMessage.success('已保存')
    showManageDialog.value = false
    await loadDevices()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingManage.value = false
  }
}

function openCalibration(row) {
  calibrationDeviceId.value = row.id
  let cal = { offset_x: 0, offset_y: 0, scale_x: 1, scale_y: 1 }
  try { if (row.calibration_json) cal = { ...cal, ...JSON.parse(row.calibration_json) } } catch { /* */ }
  Object.assign(calibrationForm, cal)
  showCalibrationDialog.value = true
}

async function saveCalibration() {
  await deviceApi.updateCalibration(calibrationDeviceId.value, { calibration_json: JSON.stringify({ ...calibrationForm }) })
  ElMessage.success('校准已保存')
  showCalibrationDialog.value = false
  loadDevices()
}

async function setMaintenance(row) {
  await deviceApi.updateStatus(row.id, { status: 'maintenance' })
  ElMessage.success('已设为维护')
  showManageDialog.value = false
  loadDevices()
}

async function resetHealth(row) {
  await deviceApi.resetHealth(row.id)
  ElMessage.success('已恢复')
  showManageDialog.value = false
  loadDevices()
}

async function deployWda(row) {
  try {
    const res = await deviceApi.deployWda(row.id)
    ElMessage.success(res.data?.message || 'WDA 部署完成')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || 'WDA 失败')
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定下线该设备？', '确认', { type: 'warning' })
  await deviceApi.delete(row.id)
  ElMessage.success('已下线')
  showManageDialog.value = false
  loadDevices()
}

watch(filteredTotal, (n) => {
  const maxPage = Math.max(1, Math.ceil(n / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

watch(() => route.query, () => {
  applyQueryFilters()
}, { deep: true })

onMounted(() => {
  applyQueryFilters()
  loadDevices()
  syncActiveScreenSessions()
  snapshotTimer = setInterval(() => syncActiveScreenSessions(), 2000)
  clockTimer = setInterval(() => { nowTick.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (snapshotTimer) clearInterval(snapshotTimer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped lang="scss">
.devices-page {
  padding: 28px 32px 40px;
  max-width: 1440px;
  margin: 0 auto;
  min-height: 100%;
  background:
    radial-gradient(ellipse at 100% 0%, rgba(56, 189, 248, 0.1) 0%, transparent 45%),
    radial-gradient(ellipse at 0% 100%, rgba(16, 185, 129, 0.06) 0%, transparent 40%),
    var(--atp-bg-page);
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  gap: 20px;
  flex-wrap: wrap;
  padding: 28px 32px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--atp-dark-bg) 0%, #0E4A6E 55%, var(--atp-brand-600) 100%);
  color: #fff;
  box-shadow: var(--atp-shadow-lg);

  h1 {
    margin: 0 0 8px;
    font-size: 28px;
    font-weight: 700;
    letter-spacing: -0.02em;
  }
  p { margin: 0; color: rgba(255, 255, 255, 0.72); font-size: 14px; }
}

.record-intent-alert {
  margin-top: 14px;
  max-width: 560px;
}

.hero-actions {
  display: flex;
  gap: 10px;

  .btn-sync {
    font-weight: 600;
    background: #2563eb;
    border-color: #2563eb;
    color: #fff;
    &:hover, &:focus {
      background: #1d4ed8;
      border-color: #1d4ed8;
      color: #fff;
    }
  }

  .btn-whitelist {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.28);
    &:hover, &:focus {
      background: rgba(255, 255, 255, 0.26);
      color: #fff;
      border-color: rgba(255, 255, 255, 0.4);
    }
  }
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  background: var(--atp-bg-elevated);
  border-radius: 16px;
  padding: 18px 20px;
  border: none;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
  }

  &.active {
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.35);
  }

  &.warn-busy {
    background: #fffbeb;
  }

  &.online .stat-icon { background: var(--atp-success-bg); color: var(--atp-success); }
  &.busy .stat-icon { background: var(--atp-warning-bg); color: #c8875e; }
  &.streaming .stat-icon { background: rgba(142, 181, 217, 0.18); color: var(--atp-info); }
  &.total .stat-icon { background: #f1f5f9; color: #64748b; }
}

.stat-alert-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #f59e0b;
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--atp-text);
}
.stat-label { font-size: 13px; color: var(--atp-text-secondary); margin-top: 2px; }
.stat-hint { font-size: 11px; color: #d97706; margin-top: 4px; }

.toolbar-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border: none;
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}

.filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }

.device-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-height: 200px;
}

@media (max-width: 1500px) {
  .device-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 960px) {
  .device-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .device-grid { grid-template-columns: 1fr; }
}

.grid-empty {
  grid-column: 1 / -1;
  padding: 48px 0;
}

.empty-panel {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  color: var(--atp-text-secondary);

  p { margin: 0 0 16px; font-size: 14px; }
}

.device-card-v {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: none;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
  overflow: hidden;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
  }

  &.highlight {
    animation: cardFlash 0.7s ease 3;
  }
}

.card-top-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #94a3b8;
}

.st-online .card-top-bar { background: #10b981; }
.st-busy .card-top-bar { background: #f59e0b; }
.st-offline .card-top-bar { background: #f97316; }

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  min-width: 0;
}

.status-pill {
  flex-shrink: 0;
  border: none !important;
  color: #fff !important;
  font-weight: 600;

  :deep(.el-tag__content) {
    color: #fff !important;
  }

  &.st-online {
    background: #059669 !important;
    --el-tag-bg-color: #059669;
    --el-tag-border-color: #059669;
    --el-tag-text-color: #fff;
  }
  &.st-busy {
    background: #d97706 !important;
    --el-tag-bg-color: #d97706;
    --el-tag-border-color: #d97706;
    --el-tag-text-color: #fff;
  }
  &.st-offline {
    background: #ea580c !important;
    --el-tag-bg-color: #ea580c;
    --el-tag-border-color: #ea580c;
    --el-tag-text-color: #fff;
  }
}

.device-name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--atp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-core {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}

.serial {
  margin: 8px 0 0;
  font-size: 10px;
  color: var(--atp-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.executor {
  margin: 4px 0 0;
  font-size: 10px;
  color: var(--atp-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.core-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.core-label { color: var(--atp-text-secondary); }
.core-value {
  color: var(--atp-text);
  font-weight: 500;
  text-align: right;
  &.overtime { color: #dc2626; font-weight: 700; }
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 12px;

  .mono {
    font-family: ui-monospace, Consolas, monospace;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.battery-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  color: var(--atp-text-secondary);
  font-size: 12px;
}

.battery-track {
  flex: 1;
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 3px;
    transition: width 0.3s;
    &.high { background: linear-gradient(90deg, var(--atp-success), #8edfc4); }
    &.mid { background: linear-gradient(90deg, #f59e0b, #f7d488); }
    &.low { background: linear-gradient(90deg, #f97316, #fdba74); }
  }
}

.battery-text {
  min-width: 36px;
  font-weight: 600;
  &.high { color: var(--atp-success); }
  &.mid { color: #d97706; }
  &.low { color: #ea580c; }
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.screen-btn {
  width: 100%;
  font-weight: 600;
}

.action-row {
  display: flex;
  gap: 8px;

  .el-button { flex: 1; }

  &.secondary .el-button {
    --el-button-text-color: #64748b;
  }
}

.manage-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

@keyframes cardFlash {
  0%, 100% { box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06); }
  50% { box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.45); }
}

@media (max-width: 900px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .devices-page { padding: 20px 16px 32px; }
  .hero { padding: 20px; }
}
</style>
