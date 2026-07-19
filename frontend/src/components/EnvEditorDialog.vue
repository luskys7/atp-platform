<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="6vh"
    class="env-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑环境配置' : '环境配置' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础环境信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="用于区分多套业务环境，执行任务时一键切换">环境名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="填写业务环境名称，例：测试环境、预发灰度、线上生产环境"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="errors.name = ''"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="不同类型环境会限制自动化执行权限，生产环境需开启自动化标识">环境类型</span>
          </div>
          <el-select v-model="form.env_type" style="width:100%" placeholder="请选择环境类型">
            <el-option label="测试环境" value="test" />
            <el-option label="预发灰度环境" value="staging" />
            <el-option label="生产正式环境" value="prod" />
          </el-select>
        </div>
      </section>

      <!-- 分区 2：接口地址 -->
      <section class="section-card">
        <div class="section-title">接口基础地址</div>
        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span>接口基础域名地址</span>
          </div>
          <el-input
            v-model="form.base_url"
            placeholder="填写服务请求根地址，例：https://test-api.xxx.com"
            :class="{ 'is-error-input': !!errors.base_url }"
            @input="onBaseUrlInput"
          />
          <div v-if="errors.base_url" class="field-error">{{ errors.base_url }}</div>
          <div v-else-if="baseUrlWarn" class="field-warn">{{ baseUrlWarn }}</div>
        </div>
      </section>

      <!-- 分区 3：全局变量 -->
      <section class="section-card">
        <div class="section-head">
          <div class="section-title" style="margin:0">全局环境变量</div>
          <div class="mode-switch">
            <el-radio-group v-model="varMode" size="small" @change="onVarModeChange">
              <el-radio-button value="visual">可视化编辑（推荐）</el-radio-button>
              <el-radio-button value="raw">原生文本编辑</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click" @command="applyTemplate">
              <el-button size="small" class="btn-tpl">填充模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="gray">灰度设备标签模板</el-dropdown-item>
                  <el-dropdown-item command="prod">生产自动化权限开启模板</el-dropdown-item>
                  <el-dropdown-item command="token">通用请求头 token 模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div v-if="varMode === 'visual'" class="kv-editor">
          <div v-for="(row, idx) in kvRows" :key="idx" class="kv-row">
            <el-input v-model="row.key" placeholder="变量名，例：token" @input="syncJsonFromKv" />
            <el-input v-model="row.value" placeholder="变量值" @input="syncJsonFromKv" />
            <el-button type="danger" plain circle @click="removeKv(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" plain @click="addKv">
            <el-icon><Plus /></el-icon> 新增变量行
          </el-button>
          <div class="kv-preview">将自动生成 JSON：{{ form.config_json || '{}' }}</div>
        </div>

        <div v-else class="raw-editor">
          <el-input
            v-model="form.config_json"
            type="textarea"
            :rows="8"
            :class="{ 'is-error-input': !!errors.config_json }"
            placeholder='{"token":"xxx","gray_device_tags":"gray-a,gray-b","allow_automation":true}'
            @input="onRawJsonInput"
          />
          <div v-if="errors.config_json" class="field-error">{{ errors.config_json }}</div>
        </div>
      </section>

      <!-- 分区 5：规则说明 -->
      <section class="rule-box">
        <div class="rule-title">环境规则说明</div>
        <ul>
          <li><strong>灰度预发环境：</strong>可配置灰度设备标签，用于定向分流测试机型</li>
          <li><strong>生产正式环境：</strong>必须开启自动化允许标识，否则平台无法发起自动化执行任务</li>
        </ul>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button v-if="isEdit" class="btn-aux" @click="cloneAsNew">复制现有模板</el-button>
        </div>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { Close, ArrowDown, Delete, Plus } from '@element-plus/icons-vue'
import { envApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const varMode = ref('visual')
const snapshot = ref('')
const baseUrlWarn = ref('')
const kvRows = ref([{ key: '', value: '' }])
const errors = reactive({ name: '', base_url: '', config_json: '' })

const form = reactive(blankForm())

const isEdit = computed(() => !!form.id)

const TEMPLATES = {
  gray: {
    gray_device_tags: 'gray-a,gray-b',
    allow_automation: true
  },
  prod: {
    allow_automation: true,
    note: '生产环境已开启自动化允许标识'
  },
  token: {
    token: '请替换为真实 Token',
    Authorization: 'Bearer 请替换为真实 Token'
  }
}

function blankForm() {
  return {
    id: null,
    name: '',
    env_type: 'test',
    base_url: '',
    config_json: '{}'
  }
}

function clearErrors() {
  errors.name = ''
  errors.base_url = ''
  errors.config_json = ''
  baseUrlWarn.value = ''
}

function takeSnapshot() {
  return JSON.stringify({ ...form, varMode: varMode.value, kvRows: kvRows.value })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function normalizeEnvType(t) {
  if (t === 'gray') return 'staging'
  if (['test', 'staging', 'prod'].includes(t)) return t
  return 'test'
}

function parseKvFromJson(text) {
  try {
    const obj = JSON.parse(text || '{}')
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return [{ key: '', value: '' }]
    const rows = Object.entries(obj).map(([key, value]) => ({
      key,
      value: value == null ? '' : (typeof value === 'string' ? value : JSON.stringify(value))
    }))
    return rows.length ? rows : [{ key: '', value: '' }]
  } catch {
    return [{ key: '', value: '' }]
  }
}

function syncJsonFromKv() {
  const obj = {}
  for (const row of kvRows.value) {
    const k = (row.key || '').trim()
    if (!k) continue
    const raw = row.value
    // 尝试解析布尔/数字/JSON，否则当字符串
    if (raw === 'true') obj[k] = true
    else if (raw === 'false') obj[k] = false
    else if (raw !== '' && !Number.isNaN(Number(raw)) && /^-?\d+(\.\d+)?$/.test(String(raw).trim())) {
      obj[k] = Number(raw)
    } else {
      try {
        if (typeof raw === 'string' && (raw.trim().startsWith('{') || raw.trim().startsWith('['))) {
          obj[k] = JSON.parse(raw)
        } else {
          obj[k] = raw
        }
      } catch {
        obj[k] = raw
      }
    }
  }
  form.config_json = JSON.stringify(obj, null, 2)
  errors.config_json = ''
}

function addKv() {
  kvRows.value.push({ key: '', value: '' })
}

function removeKv(idx) {
  kvRows.value.splice(idx, 1)
  if (!kvRows.value.length) kvRows.value.push({ key: '', value: '' })
  syncJsonFromKv()
}

function onVarModeChange(mode) {
  if (mode === 'visual') {
    kvRows.value = parseKvFromJson(form.config_json)
    syncJsonFromKv()
  } else {
    syncJsonFromKv()
  }
}

function validateUrl(url, { soft = false } = {}) {
  const v = (url || '').trim()
  if (!v) {
    if (soft) return ''
    return '请填写接口基础域名地址'
  }
  try {
    const u = new URL(v)
    if (!['http:', 'https:'].includes(u.protocol)) {
      return '地址格式错误：仅支持 http 或 https 协议'
    }
    return ''
  } catch {
    return '地址格式错误：请输入合法的 http/https 地址'
  }
}

function onBaseUrlInput() {
  errors.base_url = ''
  const soft = validateUrl(form.base_url, { soft: true })
  baseUrlWarn.value = soft && form.base_url.trim() ? soft : ''
}

function findJsonErrorLine(text) {
  try {
    JSON.parse(text || '{}')
    return null
  } catch (e) {
    const msg = String(e.message || '')
    const m = msg.match(/position\s+(\d+)/i) || msg.match(/at position\s+(\d+)/i)
    if (m) {
      const pos = Number(m[1])
      const before = (text || '').slice(0, pos)
      const line = before.split('\n').length
      return { line, message: `JSON 语法错误，约在第 ${line} 行` }
    }
    return { line: null, message: 'JSON 语法错误，请检查格式' }
  }
}

function onRawJsonInput() {
  const err = findJsonErrorLine(form.config_json)
  errors.config_json = err ? err.message : ''
}

function applyTemplate(cmd) {
  const tpl = TEMPLATES[cmd]
  if (!tpl) return
  let current = {}
  try { current = JSON.parse(form.config_json || '{}') || {} } catch { current = {} }
  const merged = { ...current, ...tpl }
  form.config_json = JSON.stringify(merged, null, 2)
  if (varMode.value === 'visual') kvRows.value = parseKvFromJson(form.config_json)
  errors.config_json = ''
  ElMessage.success('模板已填充，可继续修改')
}

function loadFromRow(row) {
  clearErrors()
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      env_type: normalizeEnvType(row.env_type),
      base_url: row.base_url || '',
      config_json: row.config_json || '{}'
    })
  } else {
    Object.assign(form, blankForm())
  }
  // 美化 JSON
  try {
    form.config_json = JSON.stringify(JSON.parse(form.config_json || '{}'), null, 2)
  } catch { /* keep */ }
  varMode.value = 'visual'
  kvRows.value = parseKvFromJson(form.config_json)
  onBaseUrlInput()
  snapshot.value = takeSnapshot()
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  loadFromRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) {
    done()
    return
  }
  try {
    await ElMessageBox.confirm('当前内容未保存，是否确认关闭？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    done()
  } catch {
    /* stay */
  }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前内容未保存，是否确认关闭？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  await ElMessageBox.confirm('将清空当前所有输入内容，是否继续？', '重置表单', { type: 'warning' })
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  kvRows.value = [{ key: '', value: '' }]
  clearErrors()
  ElMessage.success('已重置')
}

function cloneAsNew() {
  form.id = null
  if (form.name && !form.name.includes('副本')) form.name = `${form.name}_副本`
  snapshot.value = ''
  ElMessage.success('已切换为新建模式，保存后将创建同类环境')
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '环境名称不能为空'
    ok = false
  }
  const urlErr = validateUrl(form.base_url)
  if (urlErr) {
    errors.base_url = urlErr
    baseUrlWarn.value = urlErr
    ok = false
  }
  if (varMode.value === 'visual') syncJsonFromKv()
  const jsonErr = findJsonErrorLine(form.config_json)
  if (jsonErr) {
    errors.config_json = jsonErr.message
    ok = false
    if (varMode.value === 'visual') {
      varMode.value = 'raw'
    }
  }
  return ok
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  saving.value = true
  try {
    let configJson = form.config_json
    try {
      configJson = JSON.stringify(JSON.parse(form.config_json || '{}'))
    } catch {
      /* already validated */
    }
    const payload = {
      name: form.name.trim(),
      env_type: form.env_type,
      base_url: form.base_url.trim(),
      config_json: configJson
    }
    if (form.id) await envApi.update(form.id, payload)
    else await envApi.create(payload)
    ElMessage.success(form.id ? '环境配置已更新' : '环境配置已创建')
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
  padding-right: 4px;
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
  color: var(--atp-text);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.mode-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-tpl {
  --el-button-bg-color: #fff;
  --el-button-border-color: #cbd5e1;
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
.req { color: #ef4444; }
.tip-label {
  border-bottom: 1px dashed #94a3b8;
  cursor: help;
}
.field-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}
.field-warn {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}
:deep(.is-error-input .el-input__wrapper),
:deep(.is-error-input .el-textarea__inner) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.kv-editor { display: flex; flex-direction: column; gap: 8px; }
.kv-row {
  display: grid;
  grid-template-columns: 1fr 1.2fr auto;
  gap: 8px;
  align-items: center;
}
.kv-preview {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  word-break: break-all;
  line-height: 1.45;
}

.rule-box {
  padding: 12px 14px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
}
.rule-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
  color: #92400e;
}
.rule-box ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #78350f;
  line-height: 1.7;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
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
