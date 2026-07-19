<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="6vh"
    class="baseline-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑版本基线' : '新建版本基线' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingOptions">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础基线信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="用于固定一套版本回归完整配置，发起任务时一键选用">基线名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="例：V2.3 版本全量回归基线 / 登录模块迭代基线"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="标记基线适配的 App 迭代版本，方便筛选历史基线">版本标签</span>
          </div>
          <el-input
            v-model="form.version_label"
            placeholder="填写迭代版本号，例：2.3.0"
          />
        </div>

        <div class="field-block">
          <div class="field-label">描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="填写基线适配范围、回归范围说明，例：2.3 版本全模块冒烟回归"
          />
        </div>
      </section>

      <!-- 分区 2：资源绑定 -->
      <section class="section-card">
        <div class="section-title">回归资源绑定</div>

        <div class="field-block">
          <div class="field-label-row">
            <div class="field-label" style="margin:0">
              <span class="tip-label" title="基线执行时自动注入该套环境域名与变量">关联环境</span>
            </div>
            <el-button type="primary" link size="small" @click="goEnvConfig">前往环境配置</el-button>
          </div>
          <el-select
            v-model="form.env_id"
            clearable
            filterable
            placeholder="下拉选择已创建的环境配置"
            style="width:100%"
          >
            <el-option
              v-for="e in envs"
              :key="e.id"
              :label="envLabel(e)"
              :value="e.id"
            />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label-row">
            <div class="field-label" style="margin:0">
              <span class="tip-label" title="执行基线任务时自动下发该版本安装包至测试设备">关联安装包</span>
            </div>
            <el-button type="primary" link size="small" @click="goAppPackages">前往 APP 包仓库</el-button>
          </div>
          <el-select
            v-model="form.app_package_id"
            clearable
            filterable
            placeholder="下拉选择仓库内安装包"
            style="width:100%"
          >
            <el-option
              v-for="p in packages"
              :key="p.id"
              :label="pkgLabel(p)"
              :value="p.id"
            />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="基线内置固定回归套件，一键发起全版本自动化测试">绑定测试套件</span>
          </div>
          <el-select
            v-model="form.suite_ids"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="多选需要固定回归的业务套件"
            style="width:100%"
          >
            <el-option
              v-for="s in suites"
              :key="s.id"
              :label="suiteLabel(s)"
              :value="s.id"
            />
          </el-select>
          <div class="field-hint">可多选套件；保存后将作为本基线固定回归范围</div>
        </div>
      </section>

      <!-- 分区 4：说明 -->
      <section class="hint-box">
        版本基线作用：统一绑定环境、安装包、回归套件，版本迭代时无需重复配置，一键发起完整版本回归任务，保障每轮迭代回归配置统一。
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button v-if="isEdit && !cloneMode" class="btn-aux" @click="cloneAsNew">复制基线模板</el-button>
        </div>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">
            {{ isEdit && !cloneMode ? '保存' : '创建' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { baselineApi, envApi, appPackageApi, suiteApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const router = useRouter()
const saving = ref(false)
const loadingOptions = ref(false)
const cloneMode = ref(false)
const snapshot = ref('')
const envs = ref([])
const packages = ref([])
const suites = ref([])

const form = reactive(blankForm())
const errors = reactive({ name: '' })

const isEdit = computed(() => !!form.id && !cloneMode.value)

function blankForm() {
  return {
    id: null,
    name: '',
    version_label: '',
    description: '',
    env_id: null,
    app_package_id: null,
    suite_ids: [],
    config_json: '{}'
  }
}

function clearErrors() {
  errors.name = ''
}

function takeSnapshot() {
  return JSON.stringify({ ...form, cloneMode: cloneMode.value })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  errors.name = form.name?.trim() ? '' : '请填写基线业务名称'
}

function envLabel(e) {
  const typeMap = { test: '测试', staging: '预发', gray: '预发', prod: '生产' }
  const t = typeMap[e.env_type] || e.env_type || ''
  return t ? `${e.name}（${t}）` : e.name
}

function pkgLabel(p) {
  const ver = p.version_name || p.version_code || '-'
  const plat = p.platform || p.os_type || ''
  return plat ? `${p.name} · ${ver} · ${plat}` : `${p.name} · ${ver}`
}

function suiteLabel(s) {
  return s.name || `套件 #${s.id}`
}

function parseConfig(json) {
  try {
    const obj = JSON.parse(json || '{}')
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? obj : {}
  } catch {
    return {}
  }
}

function extractSuiteIds(row) {
  const cfg = parseConfig(row?.config_json)
  const fromCfg = Array.isArray(cfg.suite_ids)
    ? cfg.suite_ids.map(Number).filter(n => !Number.isNaN(n))
    : []
  if (fromCfg.length) return [...new Set(fromCfg)]
  if (row?.suite_id) return [Number(row.suite_id)]
  return []
}

function buildConfigJson() {
  const cfg = parseConfig(form.config_json)
  cfg.suite_ids = [...form.suite_ids]
  return JSON.stringify(cfg)
}

async function loadOptions() {
  loadingOptions.value = true
  try {
    const [envRes, pkgRes, suiteRes] = await Promise.all([
      envApi.list().catch(() => ({ data: [] })),
      appPackageApi.list().catch(() => ({ data: [] })),
      suiteApi.list().catch(() => ({ data: [] }))
    ])
    envs.value = envRes.data || []
    packages.value = pkgRes.data || []
    suites.value = suiteRes.data || []
  } finally {
    loadingOptions.value = false
  }
}

function applyRow(row) {
  clearErrors()
  cloneMode.value = false
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      version_label: row.version_label || '',
      description: row.description || '',
      env_id: row.env_id != null ? Number(row.env_id) : null,
      app_package_id: row.app_package_id != null ? Number(row.app_package_id) : null,
      suite_ids: extractSuiteIds(row),
      config_json: row.config_json || '{}'
    })
  } else {
    Object.assign(form, blankForm())
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  await loadOptions()
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前填写内容未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前填写内容未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空当前所有输入与绑定资源，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = cloneMode.value ? null : form.id
  Object.assign(form, blankForm(), { id: keepId })
  clearErrors()
  ElMessage.success('已重置')
}

function cloneAsNew() {
  cloneMode.value = true
  form.id = null
  if (form.name && !form.name.includes('副本')) form.name = `${form.name}_副本`
  ElMessage.success('已切换为新建模式，请修改版本标签后创建')
}

async function leaveTo(path, query) {
  if (isDirty()) {
    try {
      await ElMessageBox.confirm('当前填写内容未保存，是否确认关闭弹窗并前往？', '未保存确认', {
        type: 'warning',
        confirmButtonText: '仍要前往',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
  router.push(query ? { path, query } : path)
}

function goEnvConfig() {
  leaveTo('/platform-config', { tab: 'env' })
}

function goAppPackages() {
  leaveTo('/app-packages')
}

function validateAll() {
  clearErrors()
  if (!form.name?.trim()) {
    errors.name = '请填写基线业务名称'
    return false
  }
  return true
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项后再提交')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      version_label: form.version_label || '',
      description: form.description || '',
      env_id: form.env_id || null,
      app_package_id: form.app_package_id || null,
      suite_id: form.suite_ids.length ? form.suite_ids[0] : null,
      config_json: buildConfigJson()
    }
    if (isEdit) {
      await baselineApi.update(form.id, payload)
      ElMessage.success('版本基线已保存')
    } else {
      await baselineApi.create(payload)
      ElMessage.success('版本基线已创建')
    }
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
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

.field-block { margin-bottom: 12px; }
.field-block:last-child { margin-bottom: 0; }
.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.req { color: #ef4444; }
.tip-label {
  border-bottom: 1px dashed #94a3b8;
  cursor: help;
}
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
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
  font-size: 12px;
  color: #64748b;
  line-height: 1.65;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
</style>
