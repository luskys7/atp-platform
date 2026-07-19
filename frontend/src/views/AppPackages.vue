<template>
  <div class="page-container packages-page">
    <PageHeader title="APP 包仓库" subtitle="上传安卓 / 苹果安装包，支持签名校验、多设备批量远程安装">
      <template #actions>
        <div class="header-actions">
          <el-tooltip content="请先勾选安装包后再执行批量操作" :disabled="hasSelection" placement="bottom">
            <span class="batch-wrap">
              <el-dropdown :disabled="!hasSelection" @command="onBatchCommand">
                <el-button class="btn-muted" :disabled="!hasSelection" :loading="batchLoading">
                  批量操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="install">批量下发安装</el-dropdown-item>
                    <el-dropdown-item command="export">批量导出清单</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </el-tooltip>
          <el-button type="primary" class="btn-upload" @click="openUpload">
            <el-icon><Upload /></el-icon> 上传安装包
          </el-button>
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：统计 -->
    <div class="stats-row">
      <div class="stat-card tone-all" @click="resetFilters">
        <div class="stat-value">{{ stats.all }}</div>
        <div class="stat-label">仓库全部安装包</div>
      </div>
      <div class="stat-card tone-android" @click="quickPlatform('android')">
        <div class="stat-value is-android">{{ stats.android }}</div>
        <div class="stat-label">安卓安装包</div>
      </div>
      <div class="stat-card tone-ios" @click="quickPlatform('ios')">
        <div class="stat-value is-ios">{{ stats.ios }}</div>
        <div class="stat-label">iOS 安装包</div>
      </div>
      <div
        class="stat-card tone-bad"
        :class="{ highlight: stats.abnormal > 0 }"
        @click="quickAbnormal"
      >
        <div class="stat-value is-warn">{{ stats.abnormal }}</div>
        <div class="stat-label">待校验异常包</div>
      </div>
    </div>

    <AppCard :hover="false" class="list-card">
      <!-- 模块 3：筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索包名称、版本、分支、渠道"
          clearable
          style="width:260px"
          @clear="page = 1"
          @change="page = 1"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.platform" placeholder="运行平台" clearable style="width:120px" @change="page = 1">
          <el-option label="全部" value="" />
          <el-option label="安卓" value="android" />
          <el-option label="iOS" value="ios" />
        </el-select>
        <el-select v-model="filters.version" placeholder="版本号" clearable filterable style="width:140px" @change="page = 1">
          <el-option v-for="v in versionOptions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-select v-model="filters.branch" placeholder="开发分支" clearable filterable style="width:140px" @change="page = 1">
          <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
        </el-select>
        <el-select v-model="filters.channel" placeholder="分发渠道" clearable style="width:130px" @change="page = 1">
          <el-option label="测试" value="test" />
          <el-option label="内测" value="internal" />
          <el-option label="灰度" value="beta" />
          <el-option label="生产" value="production" />
        </el-select>
        <el-switch
          v-model="filters.onlyAbnormal"
          inline-prompt
          active-text="仅异常"
          inactive-text="全部"
          @change="page = 1"
        />
        <div class="filter-right">
          <el-button type="primary" plain :loading="loading" @click="refreshList">
            <el-icon><Refresh /></el-icon> 刷新列表
          </el-button>
          <el-button @click="resetFilters">重置全部筛选条件</el-button>
        </div>
      </div>

      <!-- 模块 4：表格 -->
      <el-table
        :data="pagedList"
        v-loading="loading"
        stripe
        empty-text=""
        :row-class-name="rowClassName"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="安装包名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="package_name" label="应用包名" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.package_name || '—' }}</template>
        </el-table-column>
        <el-table-column label="版本号" width="130">
          <template #default="{ row }">
            <span :class="versionClass(row)">{{ row.version_name || '—' }}</span>
            <el-tag v-if="isLatestVersion(row)" size="small" class="version-badge-latest" style="margin-left:6px">最新</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运行平台" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :class="platformTagClass(row.platform)">
              {{ platformLabel(row.platform) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="100">
          <template #default="{ row }">{{ formatSize(row.file_size) }}</template>
        </el-table-column>
        <el-table-column label="文件校验码" min-width="140">
          <template #default="{ row }">
            <el-popover placement="top" :width="420" trigger="hover">
              <template #reference>
                <span class="mono hash-cell">{{ shortHash(row.md5_hash) }}</span>
              </template>
              <div class="hash-pop">
                <pre>{{ row.md5_hash || '无校验码' }}</pre>
                <el-button size="small" type="primary" plain @click="copyHash(row)">一键复制</el-button>
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column prop="branch" label="开发分支" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.branch || '—' }}</template>
        </el-table-column>
        <el-table-column label="分发渠道" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="channelTagType(row.package_channel)" effect="plain">
              {{ channelLabel(row.package_channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" @click="openBatchInstall(row)">远程安装</el-button>
              <el-button size="small" type="success" :loading="downloadingId === row.id" @click="downloadPkg(row)">下载包文件</el-button>
              <el-button size="small" class="btn-muted-sm" @click="copyHash(row)">复制校验码</el-button>
              <el-button size="small" type="warning" :loading="reverifyId === row.id" @click="reverifyPkg(row)">重新校验签名</el-button>
              <el-button
                v-if="userStore.isAdmin"
                size="small"
                type="danger"
                @click="deletePkg(row)"
              >删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !filteredList.length" class="table-empty">
        <p class="empty-title">仓库暂无上传的安装包</p>
        <el-button type="primary" @click="openUpload">上传安装包</el-button>
        <p class="empty-hint">上传安装包后可在测试任务、套件中一键选用，批量下发至测试设备</p>
      </div>

      <div class="pager-bar">
        <div class="pager-stats">当前筛选结果共 <strong>{{ filteredList.length }}</strong> 个安装包</div>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredList.length"
          :page-sizes="[10, 15, 20, 50]"
          layout="sizes, prev, pager, next"
        />
      </div>
    </AppCard>

    <!-- 模块 5：底部指引 -->
    <section class="guide-bar">
      <div class="guide-left">
        <h4>包仓库操作指引</h4>
        <ul>
          <li>
            <strong>图文教程：</strong>
            <el-button type="primary" link @click="showGuide = true">点击查看「上传包、批量设备安装、签名校验」完整操作流程</el-button>
          </li>
          <li>提示说明：校验码用于核对文件完整性，避免安装包传输损坏</li>
        </ul>
      </div>
      <div class="guide-right">
        <h4>跨页面快捷跳转</h4>
        <div class="jump-btns">
          <el-button @click="$router.push('/tasks')">测试任务</el-button>
          <el-button @click="$router.push('/suites')">测试套件</el-button>
          <el-button type="primary" plain @click="$router.push('/devices')">设备管理</el-button>
        </div>
        <p class="jump-hint">执行回归选用安装包 · 套件基线关联固定迭代包 · 查看在线设备批量下发</p>
      </div>
    </section>

    <AppPackageUploadDialog v-model="showUpload" @saved="loadPackages" />

    <el-dialog v-model="showBatch" :title="batchMode === 'multi' ? '批量下发安装' : '远程安装到设备'" width="560px">
      <p class="hint" v-if="batchMode === 'single'">已选包：{{ batchPkg?.name }}</p>
      <p class="hint" v-else>已选 {{ selectedRows.length }} 个安装包，将依次下发到所选设备</p>
      <el-select v-model="selectedDevices" multiple filterable placeholder="选择目标测试设备" style="width:100%">
        <el-option
          v-for="d in devices"
          :key="d.id"
          :label="`${d.name || d.serial_number}（${platformLabel(d.platform)} · ${d.status}）`"
          :value="d.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="showBatch = false">取消</el-button>
        <el-button type="primary" :loading="installing" @click="doBatchInstall">开始安装</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGuide" title="APP 包仓库操作教程" width="640px">
      <ol class="tutorial-list">
        <li>
          <div class="tutorial-title">上传安装包</div>
          <p>点击右上角「上传安装包」，选择 APK / IPA，填写名称、版本、分支与渠道后提交。</p>
          <p>系统会自动计算文件校验码，用于后续完整性核对。</p>
        </li>
        <li>
          <div class="tutorial-title">批量设备安装</div>
          <p>在表格中勾选安装包，或点击单行「远程安装」，选择在线测试设备后一键下发。</p>
          <p>当前远程安装主要支持安卓设备；请确保设备在线且执行器可用。</p>
        </li>
        <li>
          <div class="tutorial-title">签名 / 校验码校验</div>
          <p>若传输后怀疑文件损坏，点击「重新校验签名」重新计算校验码。</p>
          <p>异常包会以浅橙色行背景提示，可在统计卡「待校验异常包」中快速筛选。</p>
        </li>
      </ol>
      <template #footer>
        <el-button type="primary" @click="showGuide = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { appPackageApi, deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppPackageUploadDialog from '@/components/AppPackageUploadDialog.vue'

const userStore = useUserStore()
const loading = ref(false)
const packages = ref([])
const devices = ref([])
const selectedRows = ref([])
const showUpload = ref(false)
const showBatch = ref(false)
const showGuide = ref(false)
const installing = ref(false)
const batchLoading = ref(false)
const batchPkg = ref(null)
const batchMode = ref('single')
const selectedDevices = ref([])
const downloadingId = ref(null)
const reverifyId = ref(null)
const page = ref(1)
const pageSize = ref(15)
const filters = reactive({
  keyword: '',
  platform: '',
  version: '',
  branch: '',
  channel: '',
  onlyAbnormal: false
})

const channelLabels = { test: '测试', internal: '内测', beta: '灰度', production: '生产' }
function channelLabel(c) { return channelLabels[c] || c || '测试' }
function channelTagType(c) {
  return { test: 'success', internal: 'info', beta: 'warning', production: 'danger' }[c] || 'info'
}
function platformLabel(p) {
  return { android: '安卓', ios: 'iOS' }[p] || p || '—'
}
function platformTagClass(p) {
  return p === 'ios' ? 'tag-ios' : 'tag-android'
}

function isAbnormal(row) {
  const log = String(row.build_log || '')
  if (log.startsWith('VERIFY_FAILED')) return true
  if (!row.md5_hash || String(row.md5_hash).length < 8) return true
  return false
}

const stats = computed(() => {
  const list = packages.value || []
  return {
    all: list.length,
    android: list.filter(p => p.platform === 'android').length,
    ios: list.filter(p => p.platform === 'ios').length,
    abnormal: list.filter(isAbnormal).length
  }
})

const versionOptions = computed(() => {
  const set = new Set()
  packages.value.forEach(p => { if (p.version_name) set.add(p.version_name) })
  return [...set]
})

const branchOptions = computed(() => {
  const set = new Set()
  packages.value.forEach(p => { if (p.branch) set.add(p.branch) })
  return [...set]
})

const filteredList = computed(() => {
  const kw = (filters.keyword || '').trim().toLowerCase()
  return (packages.value || []).filter(row => {
    if (filters.platform && row.platform !== filters.platform) return false
    if (filters.version && row.version_name !== filters.version) return false
    if (filters.branch && row.branch !== filters.branch) return false
    if (filters.channel && row.package_channel !== filters.channel) return false
    if (filters.onlyAbnormal && !isAbnormal(row)) return false
    if (!kw) return true
    const channelText = channelLabel(row.package_channel)
    return [row.name, row.package_name, row.version_name, row.branch, channelText, row.package_channel]
      .some(v => String(v || '').toLowerCase().includes(kw))
  })
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const hasSelection = computed(() => selectedRows.value.length > 0)

const latestPkgMap = computed(() => {
  const map = new Map()
  for (const p of packages.value) {
    const key = `${p.package_name || p.name}|${p.platform}`
    const prev = map.get(key)
    if (!prev || new Date(p.created_at) > new Date(prev.created_at)) map.set(key, p)
  }
  return map
})

function pkgKey(row) {
  return `${row.package_name || row.name}|${row.platform}`
}
function isLatestVersion(row) {
  const latest = latestPkgMap.value.get(pkgKey(row))
  return latest?.id === row.id && row.status !== 'deprecated'
}
function versionClass(row) {
  return isLatestVersion(row) ? 'version-latest' : ''
}
function formatSize(bytes) {
  if (!bytes) return '—'
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`
}
function shortHash(hash) {
  if (!hash) return '—'
  return hash.length > 12 ? `${hash.slice(0, 10)}…` : hash
}
function rowClassName({ row }) {
  return isAbnormal(row) ? 'row-abnormal' : ''
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function quickPlatform(p) {
  filters.platform = p
  filters.onlyAbnormal = false
  page.value = 1
}
function quickAbnormal() {
  filters.onlyAbnormal = true
  page.value = 1
}
function resetFilters() {
  filters.keyword = ''
  filters.platform = ''
  filters.version = ''
  filters.branch = ''
  filters.channel = ''
  filters.onlyAbnormal = false
  page.value = 1
}

async function loadPackages() {
  loading.value = true
  try {
    packages.value = (await appPackageApi.list()).data || []
    const maxPage = Math.max(1, Math.ceil(filteredList.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } finally {
    loading.value = false
  }
}

async function refreshList() {
  await loadPackages()
  ElMessage.success('列表已刷新')
}

async function loadDevices() {
  try {
    const res = await deviceApi.list({ page: 1, page_size: 200 })
    devices.value = res.data?.list || []
  } catch {
    devices.value = []
  }
}

function openUpload() {
  showUpload.value = true
}

function openBatchInstall(row) {
  batchMode.value = 'single'
  batchPkg.value = row
  selectedDevices.value = []
  showBatch.value = true
}

async function doBatchInstall() {
  if (!selectedDevices.value.length) { ElMessage.warning('请选择设备'); return }
  const targets = batchMode.value === 'multi' ? selectedRows.value : [batchPkg.value]
  if (!targets?.length) return
  installing.value = true
  try {
    let ok = 0
    let fail = 0
    for (const pkg of targets) {
      try {
        const res = await appPackageApi.batchInstall(pkg.id, selectedDevices.value)
        ok += res.data?.success_count || 0
        fail += res.data?.failed_count || 0
      } catch {
        fail += selectedDevices.value.length
      }
    }
    ElMessage.success(`安装完成：成功 ${ok}，失败 ${fail}`)
    showBatch.value = false
  } finally {
    installing.value = false
  }
}

async function downloadPkg(row) {
  downloadingId.value = row.id
  try {
    await appPackageApi.download(row.id, row.file_name || row.name)
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '下载失败，请确认文件仍存在')
  } finally {
    downloadingId.value = null
  }
}

async function copyHash(row) {
  const text = row.md5_hash || ''
  if (!text) { ElMessage.warning('无校验码可复制'); return }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('校验码已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

async function reverifyPkg(row) {
  reverifyId.value = row.id
  try {
    const res = await appPackageApi.reverify(row.id)
    if (res.data?.ok) ElMessage.success(res.data.message || '校验完成')
    else ElMessage.warning(res.data?.message || '校验未通过')
    await loadPackages()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重新校验失败')
  } finally {
    reverifyId.value = null
  }
}

async function deletePkg(row) {
  await ElMessageBox.confirm(
    `确定删除安装包「${row.name}」？删除后包文件不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  await appPackageApi.delete(row.id)
  ElMessage.success('已删除')
  await loadPackages()
}

async function onBatchCommand(cmd) {
  if (!selectedRows.value.length) return
  if (cmd === 'install') {
    batchMode.value = 'multi'
    batchPkg.value = null
    selectedDevices.value = []
    showBatch.value = true
    return
  }
  if (cmd === 'export') {
    batchLoading.value = true
    try {
      const rows = selectedRows.value.map(r => ({
        安装包名称: r.name,
        应用包名: r.package_name || '',
        版本号: r.version_name || '',
        运行平台: platformLabel(r.platform),
        文件大小: formatSize(r.file_size),
        文件校验码: r.md5_hash || '',
        开发分支: r.branch || '',
        分发渠道: channelLabel(r.package_channel)
      }))
      const header = Object.keys(rows[0])
      const csv = [header.join(',')]
        .concat(rows.map(r => header.map(h => `"${String(r[h]).replace(/"/g, '""')}"`).join(',')))
        .join('\n')
      const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `app_packages_${Date.now()}.csv`
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success(`已导出 ${rows.length} 条安装包清单`)
    } finally {
      batchLoading.value = false
    }
  }
}

watch(filteredList, (list) => {
  const maxPage = Math.max(1, Math.ceil(list.length / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

onMounted(() => { loadPackages(); loadDevices() })
</script>

<style scoped>
.packages-page :deep(.page-header__info h2) {
  font-size: 24px;
  font-weight: 700;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.batch-wrap { display: inline-flex; }
.btn-muted {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
.btn-muted-sm {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}
.btn-upload {
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 16px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.stat-card.tone-all { background: #f8fafc; }
.stat-card.tone-android { background: #eff6ff; }
.stat-card.tone-ios { background: #ecfdf5; }
.stat-card.tone-bad { background: #fff7ed; }
.stat-card.highlight {
  outline: 2px solid #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--atp-text);
}
.stat-value.is-android { color: #2563eb; }
.stat-value.is-ios { color: #059669; }
.stat-value.is-warn { color: #ea580c; }
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.filter-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: #64748b; }
.hash-cell { cursor: pointer; }
.hash-pop pre {
  margin: 0 0 8px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}

.tag-android { --el-tag-text-color: #2563eb; --el-tag-bg-color: #eff6ff; --el-tag-border-color: #bfdbfe; }
.tag-ios { --el-tag-text-color: #059669; --el-tag-bg-color: #ecfdf5; --el-tag-border-color: #a7f3d0; }
.version-latest { color: #047857; font-weight: 600; }
.version-badge-latest { --el-tag-bg-color: #d1fae5; --el-tag-text-color: #047857; }

:deep(.row-abnormal) > td {
  background: #fff7ed !important;
}

.table-empty {
  text-align: center;
  padding: 36px 16px 12px;
}
.empty-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
}
.empty-hint {
  margin: 12px auto 0;
  max-width: 420px;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--atp-border-neutral);
}
.pager-stats { font-size: 13px; color: var(--atp-text-secondary); }

.guide-bar {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
  margin-top: 20px;
  padding: 20px 22px;
  background: #f8fafc;
  border-radius: 14px;
  border: 1px solid var(--atp-border-neutral);
}
.guide-left h4,
.guide-right h4 { margin: 0 0 10px; font-size: 14px; }
.guide-left ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.85;
}
.jump-btns { display: flex; flex-wrap: wrap; gap: 8px; }
.jump-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}
.hint { margin-bottom: 12px; color: var(--atp-text-secondary); }

.tutorial-list { margin: 0; padding-left: 22px; }
.tutorial-list > li {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
}
.tutorial-list > li:last-child { border-bottom: none; margin-bottom: 0; }
.tutorial-title { font-weight: 700; margin-bottom: 6px; }
.tutorial-list p {
  margin: 0 0 4px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .guide-bar { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .stats-row { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
