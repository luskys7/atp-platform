<template>
  <el-dialog
    :model-value="modelValue"
    width="640px"
    top="6vh"
    class="pkg-upload-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">上传安装包</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：文件上传 -->
      <section class="section-card">
        <div class="section-title">
          <span class="req">*</span> 文件上传
        </div>

        <div
          v-if="!fileMeta"
          class="drop-zone"
          :class="{ dragging, 'is-error': !!errors.file }"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
          @click="triggerPick"
        >
          <el-icon :size="36" class="drop-icon"><UploadFilled /></el-icon>
          <p class="drop-main">拖拽 APK/IPA 安装包到此处，或点击选择文件</p>
          <el-button type="primary" plain size="small" @click.stop="triggerPick">选择文件</el-button>
          <input
            ref="fileInput"
            type="file"
            accept=".apk,.ipa,application/vnd.android.package-archive"
            class="hidden-input"
            @change="onFileInput"
          />
        </div>

        <div v-else class="file-panel">
          <div class="file-info">
            <el-icon :size="28" class="file-icon"><Document /></el-icon>
            <div class="file-meta">
              <div class="file-name" :title="fileMeta.name">{{ fileMeta.name }}</div>
              <div class="file-sub">{{ fileMeta.sizeText }} · {{ fileMeta.typeLabel }}</div>
            </div>
            <el-button size="small" @click="reselectFile">重新选择</el-button>
          </div>
          <el-progress
            v-if="reading"
            :percentage="readProgress"
            :stroke-width="10"
            status=""
          />
          <div v-if="readDone" class="read-ok">文件读取成功，已自动解析包信息</div>
          <div v-if="parseHint" class="parse-hint">{{ parseHint }}</div>
        </div>
        <div v-if="errors.file" class="field-error">{{ errors.file }}</div>
      </section>

      <!-- 分区 2：基础信息 -->
      <section class="section-card">
        <div class="section-title">安装包基础信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span>名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="填写安装包业务名称，例：V2.10 测试迭代包"
            :class="{ 'is-error-input': !!errors.name }"
            @input="errors.name = ''"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="区分不同应用，用于任务执行匹配安装包">应用包名</span>
          </div>
          <el-input
            v-model="form.package_name"
            placeholder="应用唯一包标识，选择文件后自动解析回填"
          />
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span>版本号</span>
          </div>
          <el-input
            v-model="form.version_name"
            placeholder="填写迭代版本，例：2.10.0"
            :class="{ 'is-error-input': !!errors.version_name }"
            @input="errors.version_name = ''"
          />
          <div v-if="errors.version_name" class="field-error">{{ errors.version_name }}</div>
        </div>

        <div class="field-row">
          <div class="field-block half">
            <div class="field-label">
              <span class="tip-label" title="匹配对应测试设备系统，安卓支持 APK、苹果支持 IPA">平台</span>
            </div>
            <el-select v-model="form.platform" style="width:100%">
              <el-option label="安卓" value="android" />
              <el-option label="iOS" value="ios" />
            </el-select>
          </div>
          <div class="field-block half">
            <div class="field-label">
              <span class="tip-label" title="区分内测灰度包与线上正式发布包">分发渠道</span>
            </div>
            <el-select v-model="form.package_channel" style="width:100%">
              <el-option label="测试渠道" value="test" />
              <el-option label="预发渠道" value="beta" />
              <el-option label="生产渠道" value="production" />
            </el-select>
          </div>
        </div>

        <div class="field-block">
          <div class="field-label">开发分支</div>
          <el-input v-model="form.branch" placeholder="例：main / release/2.10" />
        </div>
      </section>

      <!-- 分区 4：辅助说明 -->
      <section class="hint-box">
        <ul>
          <li>支持文件格式：安卓 APK、苹果 IPA 安装包</li>
          <li>便捷提示：选中文件后将自动解析包名、版本、平台，无需手动填写</li>
          <li>渠道说明：生产渠道安装包仅允许线上正式回归任务使用</li>
        </ul>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submit">上传</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, nextTick } from 'vue'
import { Close, UploadFilled, Document } from '@element-plus/icons-vue'
import { appPackageApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const fileInput = ref(null)
const file = ref(null)
const fileMeta = ref(null)
const dragging = ref(false)
const reading = ref(false)
const readDone = ref(false)
const readProgress = ref(0)
const parseHint = ref('')
const uploading = ref(false)
const snapshot = ref('')

const form = reactive(blankForm())
const errors = reactive({ file: '', name: '', version_name: '' })

function blankForm() {
  return {
    name: '',
    package_name: '',
    version_name: '',
    platform: 'android',
    package_channel: 'test',
    branch: 'main'
  }
}

function clearErrors() {
  errors.file = ''
  errors.name = ''
  errors.version_name = ''
}

function takeSnapshot() {
  return JSON.stringify({
    form: { ...form },
    fileName: file.value?.name || '',
    fileSize: file.value?.size || 0
  })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function formatSize(bytes) {
  if (!bytes) return '0 KB'
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(2)} MB` : `${(bytes / 1024).toFixed(1)} KB`
}

function isValidPackageFile(f) {
  const name = (f?.name || '').toLowerCase()
  return name.endsWith('.apk') || name.endsWith('.ipa')
}

function detectPlatform(f) {
  const name = (f?.name || '').toLowerCase()
  if (name.endsWith('.ipa')) return 'ios'
  return 'android'
}

function extractVersion(filename) {
  const base = filename.replace(/\.(apk|ipa)$/i, '')
  const m = base.match(/v?(\d+\.\d+(\.\d+){0,2}([-_][A-Za-z0-9.]+)?)/i)
  return m ? m[1] : ''
}

function extractPackageName(filename) {
  const base = filename.replace(/\.(apk|ipa)$/i, '')
  const m = base.match(/([a-zA-Z][\w]*(\.[a-zA-Z][\w]*){1,})/)
  if (m && m[1].includes('.')) return m[1]
  return ''
}

function extractName(filename) {
  return filename.replace(/\.(apk|ipa)$/i, '')
}

function triggerPick() {
  fileInput.value?.click()
}

function reselectFile() {
  clearFileState()
  nextTick(() => triggerPick())
}

function clearFileState() {
  file.value = null
  fileMeta.value = null
  reading.value = false
  readDone.value = false
  readProgress.value = 0
  parseHint.value = ''
  errors.file = ''
  if (fileInput.value) fileInput.value.value = ''
}

function onFileInput(e) {
  const f = e.target.files?.[0]
  if (f) acceptFile(f)
}

function onDrop(e) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) acceptFile(f)
}

async function acceptFile(f) {
  if (!isValidPackageFile(f)) {
    ElMessage.warning('仅支持 APK、IPA 安装包文件')
    errors.file = '仅支持 APK、IPA 安装包文件'
    return
  }
  errors.file = ''
  file.value = f
  const platform = detectPlatform(f)
  fileMeta.value = {
    name: f.name,
    sizeText: formatSize(f.size),
    typeLabel: platform === 'ios' ? '苹果 IPA' : '安卓 APK'
  }
  await simulateReadAndParse(f, platform)
}

function simulateReadAndParse(f, platform) {
  return new Promise((resolve) => {
    reading.value = true
    readDone.value = false
    readProgress.value = 0
    parseHint.value = ''

    const timer = setInterval(() => {
      readProgress.value = Math.min(96, readProgress.value + 12 + Math.floor(Math.random() * 8))
    }, 80)

    // 读取文件头部校验 ZIP/APK 魔数
    const reader = new FileReader()
    reader.onload = () => {
      clearInterval(timer)
      readProgress.value = 100
      reading.value = false
      readDone.value = true

      form.platform = platform
      if (!form.name) form.name = extractName(f.name)
      const ver = extractVersion(f.name)
      if (ver) form.version_name = ver
      const pkg = extractPackageName(f.name)
      if (pkg) form.package_name = pkg
      else parseHint.value = '未能从文件名自动解析应用包名，请手动填写'

      // 简单校验文件头 PK
      try {
        const buf = new Uint8Array(reader.result)
        if (buf.length < 2 || buf[0] !== 0x50 || buf[1] !== 0x4b) {
          parseHint.value = '文件头校验异常，请确认安装包未损坏后继续上传'
        }
      } catch { /* ignore */ }

      resolve()
    }
    reader.onerror = () => {
      clearInterval(timer)
      reading.value = false
      readProgress.value = 0
      errors.file = '文件读取失败，请重新选择'
      resolve()
    }
    reader.readAsArrayBuffer(f.slice(0, 64))
  })
}

function validateVersion(v) {
  const s = (v || '').trim()
  if (!s) return '版本号不能为空'
  // 允许 1.0 / 2.10.0 / 2.10.0-beta
  if (!/^\d+(\.\d+){0,3}([-_.][A-Za-z0-9]+)?$/.test(s)) {
    return '版本号格式不规范，示例：2.10.0'
  }
  return ''
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!file.value || !isValidPackageFile(file.value)) {
    errors.file = '请选择合法的 APK 或 IPA 安装包文件'
    ok = false
  }
  if (!form.name?.trim()) {
    errors.name = '包名称不能为空'
    ok = false
  }
  const verErr = validateVersion(form.version_name)
  if (verErr) {
    errors.version_name = verErr
    ok = false
  }
  return ok
}

function resetToBlank() {
  Object.assign(form, blankForm())
  clearFileState()
  clearErrors()
}

async function resetForm() {
  await ElMessageBox.confirm('将清空文件选择与全部输入内容，是否继续？', '重置表单', { type: 'warning' })
  resetToBlank()
  ElMessage.success('已重置')
}

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前已填写内容未提交，确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    done()
  } catch { /* stay */ }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前已填写内容未提交，确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('name', form.name.trim())
    fd.append('package_name', form.package_name.trim())
    fd.append('version_name', form.version_name.trim())
    fd.append('branch', form.branch.trim() || 'main')
    fd.append('platform', form.platform)
    fd.append('package_channel', form.package_channel)
    await appPackageApi.upload(fd)
    ElMessage.success('安装包上传成功')
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  resetToBlank()
  await nextTick()
  snapshot.value = takeSnapshot()
})
</script>

<style scoped>
.dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.dlg-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.dlg-close { margin-right: -6px; }

.dlg-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 68vh;
  overflow: auto;
}

.section-card {
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}
.req { color: #ef4444; margin-right: 2px; }

.drop-zone {
  border: 1.5px dashed #94a3b8;
  border-radius: 12px;
  background: #fff;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.drop-zone:hover,
.drop-zone.dragging {
  border-color: var(--atp-primary);
  background: #eff6ff;
}
.drop-zone.is-error { border-color: #f97316; }
.drop-icon { color: var(--atp-primary); margin-bottom: 8px; }
.drop-main {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
}
.hidden-input { display: none; }

.file-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
}
.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.file-icon { color: var(--atp-primary); flex-shrink: 0; }
.file-meta { flex: 1; min-width: 0; }
.file-name {
  font-weight: 600;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-sub { font-size: 12px; color: #64748b; margin-top: 2px; }
.read-ok {
  margin-top: 8px;
  font-size: 12px;
  color: #059669;
  font-weight: 600;
}
.parse-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}

.field-block { margin-bottom: 12px; }
.field-block:last-child { margin-bottom: 0; }
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.tip-label {
  border-bottom: 1px dashed #94a3b8;
  cursor: help;
}
.field-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}
:deep(.is-error-input .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.hint-box {
  padding: 12px 14px;
  background: #f1f5f9;
  border-radius: 10px;
}
.hint-box ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.75;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}
.footer-right { display: flex; gap: 8px; }
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}

@media (max-width: 560px) {
  .field-row { grid-template-columns: 1fr; }
}
</style>
