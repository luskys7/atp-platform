<template>
  <div class="devices-page">
    <section class="hero">
      <div class="hero-text">
        <h1>设备管理</h1>
        <p>USB 接入自动入池 · 远程投屏 · 自动化调度</p>
      </div>
      <div class="hero-actions">
        <el-button :loading="downloadingLauncher" @click="downloadLauncher">
          <el-icon><Download /></el-icon> 下载启动器
        </el-button>
        <el-button type="primary" :loading="syncingUsb" @click="syncUsbDevices">
          <el-icon><Refresh /></el-icon> 同步 USB
        </el-button>
        <el-button v-if="userStore.isAdmin" @click="showWhitelistDialog = true">
          <el-icon><Plus /></el-icon> 白名单
        </el-button>
      </div>
    </section>

    <section class="stats">
      <div v-for="s in statItems" :key="s.key" class="stat-card" :class="s.key">
        <div class="stat-icon"><el-icon :size="22"><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="filters">
        <el-select v-model="filters.platform" placeholder="平台" clearable style="width:110px" @change="loadDevices">
          <el-option label="Android" value="android" />
          <el-option label="iOS" value="ios" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:110px" @change="loadDevices">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
          <el-option label="忙碌" value="busy" />
          <el-option label="维护" value="maintenance" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索设备" clearable prefix-icon="Search" style="width:200px" @change="loadDevices" />
      </div>
      <div class="toolbar-right">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="card">卡片</el-radio-button>
          <el-radio-button value="table">列表</el-radio-button>
        </el-radio-group>
        <el-button circle @click="loadDevices"><el-icon><Refresh /></el-icon></el-button>
      </div>
    </section>

    <section v-if="viewMode === 'card'" class="device-grid" v-loading="loading">
      <article
        v-for="row in devices"
        :key="row.id"
        class="device-card-v"
        :class="[`st-${row.status}`, { streaming: isStreaming(row.id) }]"
      >
        <div class="card-preview">
          <div class="phone-frame" :class="row.platform">
            <div class="phone-inner" :style="devicePreviewStyle(row)">
              <template v-if="isStreaming(row.id) && getSessionSnapshot(row.id)">
                <span class="live-tag">LIVE</span>
              </template>
              <template v-else-if="isStreaming(row.id)">
                <span class="live-tag">连接中</span>
              </template>
              <template v-else>
                <span class="phone-res">{{ row.screen_width || '?' }}×{{ row.screen_height || '?' }}</span>
              </template>
            </div>
          </div>
          <el-tag :type="deviceStatusMap[row.status]?.type" size="small" round effect="light" class="status-tag">
            {{ deviceStatusMap[row.status]?.label || row.status }}
          </el-tag>
        </div>

        <div class="card-body">
          <h3 class="device-name">{{ row.name || row.serial_number }}</h3>
          <p class="device-model">{{ row.model || row.platform || '-' }}</p>
          <div class="meta-row">
            <span>{{ androidVer(row) }}</span>
            <span class="dot">·</span>
            <span>{{ row.screen_width && row.screen_height ? `${row.screen_width}×${row.screen_height}` : '-' }}</span>
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
          <p class="serial mono">{{ row.serial_number }}</p>
          <p v-if="row.executor_url" class="executor mono" :title="row.executor_url">执行器 {{ shortExecutor(row.executor_url) }}</p>
        </div>

        <div class="card-actions">
          <el-button
            type="primary"
            class="screen-btn"
            :disabled="row.platform !== 'android' || row.status === 'offline'"
            @click="openScreen(row)"
          >
            <el-icon><Monitor /></el-icon>
            {{ isStreaming(row.id) ? '回到投屏' : '开始投屏' }}
          </el-button>
          <el-button
            size="small"
            plain
            :disabled="row.platform !== 'android' || row.status === 'offline'"
            @click="openPicker(row)"
          >
            控件拾取
          </el-button>
          <el-dropdown v-if="userStore.isAdmin" trigger="click" @command="cmd => adminAction(cmd, row)">
            <el-button size="small" class="manage-btn">管理</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="tags">标签</el-dropdown-item>
                <el-dropdown-item command="calibration">校准</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'error'" command="reset">恢复</el-dropdown-item>
                <el-dropdown-item command="maintenance">维护</el-dropdown-item>
                <el-dropdown-item v-if="row.platform === 'ios'" command="wda">WDA</el-dropdown-item>
                <el-dropdown-item command="delete" divided>下线</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div v-if="isStreaming(row.id)" class="streaming-badge"><i />投屏中</div>
      </article>
      <el-empty v-if="!loading && !devices.length" description="暂无设备，请连接 USB 后同步" class="grid-empty" />
    </section>

    <section v-else class="table-card" v-loading="loading">
      <el-table :data="devices" stripe>
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="serial_number" label="序列号" min-width="150" />
        <el-table-column prop="platform" label="平台" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.platform === 'android' ? 'success' : 'primary'">{{ row.platform }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" min-width="120" />
        <el-table-column label="系统" width="100">
          <template #default="{ row }">{{ androidVer(row) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="deviceStatusMap[row.status]?.type">{{ deviceStatusMap[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="battery_level" label="电量" width="80">
          <template #default="{ row }">{{ row.battery_level }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.platform !== 'android' || row.status === 'offline'" @click="openScreen(row)">投屏</el-button>
            <el-button size="small" plain :disabled="row.platform !== 'android' || row.status === 'offline'" @click="openPicker(row)">控件拾取</el-button>
            <el-button v-if="userStore.isAdmin" size="small" plain @click="openTags(row)">标签</el-button>
            <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="handleDelete(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @change="loadDevices" />
    </div>

    <!-- dialogs unchanged -->
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

    <el-dialog v-model="showTagsDialog" title="设备标签" width="420px">
      <el-form label-width="80px">
        <el-form-item label="标签"><el-input v-model="tagsForm.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTagsDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTags">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { deviceStatusMap } from '@/utils/status'
import { activeScreenSessions, syncActiveScreenSessions, getSessionSnapshot } from '@/composables/useScreenStream'
import { androidVersionLabel } from '@/composables/screenFrameStyle'
import { normalizeDevice } from '@/utils/device'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const devices = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const viewMode = ref('card')
const filters = reactive({ platform: '', status: '', keyword: '' })
const showWhitelistDialog = ref(false)
const whitelistForm = reactive({ serial_number: '', platform: 'android', remark: '' })
const showCalibrationDialog = ref(false)
const calibrationDeviceId = ref(null)
const calibrationForm = reactive({ offset_x: 0, offset_y: 0, scale_x: 1, scale_y: 1 })
const showTagsDialog = ref(false)
const tagsDeviceId = ref(null)
const tagsForm = reactive({ tags: '' })
const syncingUsb = ref(false)
const downloadingLauncher = ref(false)
let snapshotTimer = null
const snapshotTick = ref(0)

function devicePreviewStyle(row) {
  snapshotTick.value // reactive dependency
  if (!isStreaming(row.id)) return {}
  const url = getSessionSnapshot(row.id)
  if (!url) return {}
  return { backgroundImage: `url(${url})`, backgroundSize: 'cover', backgroundPosition: 'center top' }
}

const streamingCount = computed(() => activeScreenSessions.value.length)

const statItems = computed(() => {
  const list = devices.value
  return [
    { key: 'online', label: '在线设备', value: list.filter(d => d.status === 'online').length, icon: 'CircleCheck' },
    { key: 'busy', label: '执行中', value: list.filter(d => d.status === 'busy').length, icon: 'Loading' },
    { key: 'streaming', label: '投屏中', value: streamingCount.value, icon: 'Monitor' },
    { key: 'total', label: '设备总数', value: total.value, icon: 'Iphone' }
  ]
})

function androidVer(row) {
  return androidVersionLabel(row)
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
function isStreaming(deviceId) {
  return activeScreenSessions.value.some(s => String(s.deviceId) === String(deviceId))
}

async function syncUsbDevices() {
  syncingUsb.value = true
  try {
    const res = await deviceApi.syncUsb()
    ElMessage.success(res.data?.message || 'USB 设备已同步')
    loadDevices()
  } finally { syncingUsb.value = false }
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
    const params = { page: page.value, page_size: pageSize.value }
    if (filters.platform) params.platform = filters.platform
    if (filters.status) params.status = filters.status
    const res = await deviceApi.list(params)
    let list = res.data.list || []
    if (filters.keyword) {
      const k = filters.keyword.toLowerCase()
      list = list.filter(d => (d.name || '').toLowerCase().includes(k) || (d.serial_number || '').toLowerCase().includes(k))
    }
    devices.value = list.map(normalizeDevice)
    total.value = res.data.total
  } finally { loading.value = false }
}

async function addWhitelist() {
  await deviceApi.addWhitelist(whitelistForm)
  ElMessage.success('白名单添加成功')
  showWhitelistDialog.value = false
  Object.assign(whitelistForm, { serial_number: '', platform: 'android', remark: '' })
  loadDevices()
}

function openScreen(row) { router.push(`/devices/${row.id}/screen`) }
function openPicker(row) { router.push(`/element-picker/${row.id}`) }

function adminAction(cmd, row) {
  if (cmd === 'tags') openTags(row)
  else if (cmd === 'calibration') openCalibration(row)
  else if (cmd === 'reset') resetHealth(row)
  else if (cmd === 'maintenance') setMaintenance(row)
  else if (cmd === 'wda') deployWda(row)
  else if (cmd === 'delete') handleDelete(row)
}

async function setMaintenance(row) {
  await deviceApi.updateStatus(row.id, { status: 'maintenance' })
  ElMessage.success('已设为维护')
  loadDevices()
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

function openTags(row) { tagsDeviceId.value = row.id; tagsForm.tags = row.tags || ''; showTagsDialog.value = true }

async function saveTags() {
  await deviceApi.updateTags(tagsDeviceId.value, { tags: tagsForm.tags })
  ElMessage.success('标签已保存')
  showTagsDialog.value = false
  loadDevices()
}

async function resetHealth(row) {
  await deviceApi.resetHealth(row.id)
  ElMessage.success('已恢复')
  loadDevices()
}

async function deployWda(row) {
  try {
    const res = await deviceApi.deployWda(row.id)
    ElMessage.success(res.data?.message || 'WDA 部署完成')
  } catch (e) { ElMessage.error(e?.response?.data?.message || 'WDA 失败') }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定下线该设备？', '确认', { type: 'warning' })
  await deviceApi.delete(row.id)
  ElMessage.success('已下线')
  loadDevices()
}

onMounted(() => {
  loadDevices()
  syncActiveScreenSessions()
  snapshotTimer = setInterval(() => {
    if (streamingCount.value > 0) snapshotTick.value++
    syncActiveScreenSessions()
  }, 2000)
})

onUnmounted(() => {
  if (snapshotTimer) clearInterval(snapshotTimer)
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

.hero-actions {
  display: flex;
  gap: 10px;

  .el-button--primary {
    background: #fff;
    color: var(--atp-brand-600);
    border: none;
    font-weight: 600;
    &:hover { background: var(--atp-brand-50); }
  }
  .el-button:not(.el-button--primary) {
    background: rgba(255, 255, 255, 0.12);
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.2);
    &:hover { background: rgba(255, 255, 255, 0.2); }
  }
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--atp-bg-elevated);
  border-radius: 16px;
  padding: 18px 20px;
  border: 1px solid var(--atp-brand-100);
  display: flex;
  align-items: center;
  gap: 14px;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--atp-shadow);
  }

  &.online .stat-icon { background: var(--atp-success-bg); color: var(--atp-success); }
  &.busy .stat-icon { background: var(--atp-warning-bg); color: #c8875e; }
  &.streaming .stat-icon { background: rgba(142, 181, 217, 0.18); color: var(--atp-info); }
  &.total .stat-icon { background: var(--atp-primary-bg); color: var(--atp-primary); }
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

.stat-value { font-size: 26px; font-weight: 700; line-height: 1.2; color: var(--atp-text); }
.stat-label { font-size: 13px; color: var(--atp-text-secondary); margin-top: 2px; }

.toolbar-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border: 1px solid var(--atp-brand-100);
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
  box-shadow: var(--atp-shadow);
}

.filters { display: flex; gap: 10px; flex-wrap: wrap; }
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

.device-card-v {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--atp-bg-elevated);
  border: 1px solid var(--atp-brand-100);
  border-radius: 16px;
  padding: 16px;
  transition: all 0.22s ease;

  &:hover {
    border-color: var(--atp-brand-300);
    box-shadow: var(--atp-shadow-lg);
    transform: translateY(-2px);
  }

  &.streaming {
    border-color: var(--atp-success);
    background: linear-gradient(180deg, rgba(108, 212, 178, 0.12) 0%, var(--atp-bg-elevated) 120px);
  }

  &.st-offline { opacity: 0.78; }
}

.card-preview {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.status-tag { flex-shrink: 0; }

.card-body {
  flex: 1;
  min-width: 0;
}

.device-name {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: var(--atp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-model {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--atp-text-secondary);
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--atp-text-secondary);
  margin-bottom: 10px;

  .dot { color: var(--atp-brand-200); }
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

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--atp-brand-50);

  .screen-btn {
    flex: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;

    :deep(.el-icon) {
      margin-right: 2px;
      font-size: 16px;
    }
  }

  .manage-btn {
    flex-shrink: 0;
    min-width: 64px;
  }
}

.streaming-badge {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  font-weight: 700;
  color: var(--atp-success);
  background: rgba(255, 255, 255, 0.92);
  padding: 2px 8px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(108, 212, 178, 0.25);

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--atp-success);
    animation: pulse 1.2s ease infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.phone-frame {
  width: 72px;
  height: 128px;
  border-radius: 14px;
  padding: 5px;
  background: linear-gradient(145deg, #3d3858, var(--atp-sidebar));
  box-shadow: 0 6px 16px rgba(45, 42, 62, 0.2);

  &.android { background: linear-gradient(145deg, #4a5568, var(--atp-sidebar)); }
  &.ios { background: linear-gradient(145deg, #1E3A8A, var(--atp-brand-600)); }
}

.phone-inner {
  height: 100%;
  border-radius: 10px;
  background: linear-gradient(180deg, var(--atp-brand-100), var(--atp-brand-200));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--atp-text-secondary);
  overflow: hidden;
}

.phone-res {
  font-size: 9px;
  font-weight: 600;
  font-family: ui-monospace, Consolas, monospace;
  text-align: center;
  line-height: 1.3;
  padding: 4px;
}

.live-tag {
  font-size: 10px;
  font-weight: 800;
  color: #fff;
  background: rgba(108, 212, 178, 0.85);
  padding: 2px 8px;
  border-radius: 10px;
  letter-spacing: 0.08em;
  z-index: 1;
}

.streaming-dot {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--atp-success);

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--atp-success);
    animation: blink 1.2s infinite;
  }
}

.card-center {
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: var(--atp-text);
  }
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 24px;
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .label {
    font-size: 11px;
    color: var(--atp-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .value {
    font-size: 13px;
    color: var(--atp-text);
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.mono {
      font-family: ui-monospace, Consolas, monospace;
      font-size: 12px;
      color: var(--atp-text-secondary);
    }
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
  background: var(--atp-brand-50);
  border-radius: 3px;
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 3px;
    transition: width 0.3s;
    &.high { background: linear-gradient(90deg, var(--atp-success), #8edfc4); }
    &.mid { background: linear-gradient(90deg, var(--atp-gold), #f7d488); }
    &.low { background: linear-gradient(90deg, var(--atp-danger), #ffa8a8); }
  }
}

.battery-text {
  min-width: 36px;
  font-weight: 600;
  &.high { color: var(--atp-success); }
  &.mid { color: var(--atp-gold); }
  &.low { color: var(--atp-danger); }
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.card-right {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: stretch;
  min-width: 130px;
}

.screen-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-weight: 600;
  padding: 12px 20px;

  .btn-icon {
    font-size: 18px;
    margin-right: 2px;
  }
}

.manage-btn {
  width: 100%;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.table-card {
  background: var(--atp-bg-elevated);
  border: 1px solid var(--atp-brand-100);
  border-radius: 16px;
  padding: 16px;
  overflow: hidden;
  box-shadow: var(--atp-shadow);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .devices-page { padding: 20px 16px 32px; }
  .hero { padding: 20px; }
  .device-card-h {
    grid-template-columns: 90px 1fr;
    .card-right {
      grid-column: 1 / -1;
      flex-direction: row;
    }
    .screen-btn { flex: 1; }
  }
}
</style>
