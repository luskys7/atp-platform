<template>
  <div class="page-container config-page">
    <PageHeader title="APP 包仓库" subtitle="上传 APK/IPA、签名校验与批量设备安装">
      <template #actions>
        <el-button type="primary" @click="showUpload = true"><el-icon><Upload /></el-icon> 上传安装包</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <el-table :data="packages" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="package_name" label="包名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="version_name" label="版本" width="120">
          <template #default="{ row }">
            <span :class="versionClass(row)">{{ row.version_name || '-' }}</span>
            <el-tag v-if="isLatestVersion(row)" size="small" class="version-badge-latest" style="margin-left:6px">最新</el-tag>
            <el-tag v-else-if="isExpiredVersion(row)" size="small" class="version-badge-expired" style="margin-left:6px">过期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platform" label="平台" width="90" />
        <el-table-column prop="file_size" label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.file_size) }}</template>
        </el-table-column>
        <el-table-column prop="md5_hash" label="MD5" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.md5_hash?.slice(0, 8) }}...</span>
          </template>
        </el-table-column>
        <el-table-column prop="branch" label="分支" width="100" />
        <el-table-column prop="package_channel" label="渠道" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="channelTagType(row.package_channel)">{{ channelLabel(row.package_channel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openBatchInstall(row)">批量安装</el-button>
            <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deletePkg(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>

    <el-dialog v-model="showUpload" title="上传安装包" width="520px">
      <el-form label-width="90px">
        <el-form-item label="文件" required>
          <input type="file" accept=".apk,.ipa" @change="onFileChange" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="uploadForm.name" /></el-form-item>
        <el-form-item label="包名"><el-input v-model="uploadForm.package_name" placeholder="com.example.app" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="uploadForm.version_name" /></el-form-item>
        <el-form-item label="分支"><el-input v-model="uploadForm.branch" placeholder="main" /></el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="uploadForm.package_channel" style="width:100%">
            <el-option label="测试 test" value="test" />
            <el-option label="内测 internal" value="internal" />
            <el-option label="灰度 beta" value="beta" />
            <el-option label="生产 production（默认禁止自动化）" value="production" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="uploadForm.platform" style="width:100%">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showUpload = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBatch" title="批量安装到设备" width="560px">
      <p class="hint">已选包：{{ batchPkg?.name }}</p>
      <el-select v-model="selectedDevices" multiple filterable placeholder="选择目标设备" style="width:100%">
        <el-option v-for="d in devices" :key="d.id" :label="`${d.name || d.serial_number} (${d.status})`" :value="d.id" />
      </el-select>
      <template #footer>
        <el-button class="btn-cancel" @click="showBatch = false">取消</el-button>
        <el-button type="primary" :loading="installing" @click="doBatchInstall">开始安装</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { appPackageApi, deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const packages = ref([])
const devices = ref([])
const showUpload = ref(false)
const showBatch = ref(false)
const uploading = ref(false)
const installing = ref(false)
const uploadFile = ref(null)
const batchPkg = ref(null)
const selectedDevices = ref([])
const uploadForm = reactive({ name: '', package_name: '', version_name: '', branch: '', platform: 'android', package_channel: 'test' })

const channelLabels = { test: '测试', internal: '内测', beta: '灰度', production: '生产' }
function channelLabel(c) { return channelLabels[c] || c || '测试' }
function channelTagType(c) {
  return { test: 'success', internal: '', beta: 'warning', production: 'danger' }[c] || 'info'
}

const latestPkgMap = computed(() => {
  const map = new Map()
  for (const p of packages.value) {
    const key = `${p.package_name || p.name}|${p.platform}`
    const prev = map.get(key)
    if (!prev || new Date(p.created_at) > new Date(prev.created_at)) {
      map.set(key, p)
    }
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

function isExpiredVersion(row) {
  return row.status === 'deprecated' || !isLatestVersion(row)
}

function versionClass(row) {
  if (isLatestVersion(row)) return 'version-latest'
  if (isExpiredVersion(row)) return 'version-expired'
  return ''
}

function formatSize(bytes) {
  if (!bytes) return '-'
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? mb.toFixed(1) + ' MB' : (bytes / 1024).toFixed(0) + ' KB'
}

async function loadPackages() {
  loading.value = true
  try {
    packages.value = (await appPackageApi.list()).data
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  const res = await deviceApi.list({ page: 1, page_size: 200, platform: 'android' })
  devices.value = res.data.list
}

function onFileChange(e) {
  uploadFile.value = e.target.files?.[0] || null
  if (uploadFile.value && !uploadForm.name) uploadForm.name = uploadFile.value.name
}

async function doUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadFile.value)
    fd.append('name', uploadForm.name)
    fd.append('package_name', uploadForm.package_name)
    fd.append('version_name', uploadForm.version_name)
    fd.append('branch', uploadForm.branch)
    fd.append('platform', uploadForm.platform)
    fd.append('package_channel', uploadForm.package_channel)
    await appPackageApi.upload(fd)
    ElMessage.success('上传成功')
    showUpload.value = false
    loadPackages()
  } finally {
    uploading.value = false
  }
}

function openBatchInstall(row) {
  batchPkg.value = row
  selectedDevices.value = []
  showBatch.value = true
}

async function doBatchInstall() {
  if (!selectedDevices.value.length) {
    ElMessage.warning('请选择设备')
    return
  }
  installing.value = true
  try {
    const res = await appPackageApi.batchInstall(batchPkg.value.id, selectedDevices.value)
    ElMessage.success(`安装完成：成功 ${res.data.success_count}，失败 ${res.data.failed_count}`)
    showBatch.value = false
  } finally {
    installing.value = false
  }
}

async function deletePkg(row) {
  await ElMessageBox.confirm('确定删除该安装包？', '确认', { type: 'warning' })
  await appPackageApi.delete(row.id)
  ElMessage.success('已删除')
  loadPackages()
}

onMounted(() => { loadPackages(); loadDevices() })
</script>

<style scoped>
.mono { font-family: monospace; font-size: 12px; color: var(--atp-text-secondary); }
.hint { margin-bottom: 12px; color: var(--atp-text-secondary); }
</style>
