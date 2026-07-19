<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="5vh"
    class="credential-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑加密凭据' : '新建加密凭据' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingOptions">
      <!-- 分区 1：基础标识 -->
      <section class="section-card">
        <div class="section-title">凭据基础标识</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="自动化脚本内固定引用标识 {{凭据名称}}，执行时自动解密读取">凭据名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="仅大写英文、数字、下划线，例：API_AUTH_TOKEN"
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div class="field-hint">命名仅支持大写字母、数字、下划线，不可使用中文 / 小写 / 特殊符号</div>
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="用于凭据列表快速筛选，区分不同用途密钥">凭据分类</span>
          </div>
          <el-select v-model="form.category" clearable placeholder="请选择凭据分类" style="width:100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label">可见权限范围</div>
          <el-select v-model="form.visibility_scope" style="width:100%" placeholder="请选择可见权限">
            <el-option label="平台全局" value="platform" />
            <el-option label="项目专属" value="project" />
            <el-option label="团队私有" value="team" />
          </el-select>
          <div class="field-hint">{{ scopeHint }}</div>
        </div>
      </section>

      <!-- 分区 2：绑定环境 -->
      <section class="section-card">
        <div class="section-title">绑定环境配置</div>
        <div class="field-block">
          <div class="field-label-row">
            <div class="field-label" style="margin:0">
              <span class="tip-label" title="凭据仅在绑定的环境执行时生效，切换其他环境无法读取该密钥">绑定生效环境</span>
            </div>
            <el-button type="primary" link size="small" @click="goEnvConfig">前往环境配置</el-button>
          </div>
          <el-select
            v-model="form.env_id"
            clearable
            filterable
            placeholder="下拉选择绑定环境（名称 + ID）"
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
      </section>

      <!-- 分区 3：密钥值 -->
      <section class="section-card">
        <div class="section-title">加密密钥值</div>
        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            密钥内容
          </div>
          <el-input
            v-model="form.value"
            :type="showValue ? 'text' : 'password'"
            :placeholder="isEdit ? '编辑凭据留空则保留原有加密密钥不修改' : '新建凭据必须完整填写密钥'"
            :class="{ 'is-error-input': !!errors.value }"
            @input="onValueInput"
          >
            <template #suffix>
              <el-button
                text
                class="pwd-toggle"
                :title="showValue ? '切换为密文' : '切换为明文'"
                @click="showValue = !showValue"
              >
                <el-icon :size="16"><Hide v-if="showValue" /><View v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="field-warn">密钥全程加密存储，执行日志、测试报告、录屏永久屏蔽明文，不会泄露敏感凭证</div>
          <div v-if="errors.value" class="field-error">{{ errors.value }}</div>
        </div>
      </section>

      <!-- 分区 4：描述 -->
      <section class="section-card">
        <div class="section-title">补充业务描述</div>
        <div class="field-block">
          <div class="field-label">描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="填写凭据用途、有效期、使用限制，例：预发环境接口鉴权 Token，有效期 30 天"
          />
        </div>
      </section>

      <!-- 分区 5：说明 -->
      <section class="guide-panel">
        <div class="guide-title">加密凭据独立安全使用说明</div>
        <div class="guide-block">
          <div class="guide-h">1. 凭据引用规则</div>
          <p>自动化用例、造数模板、公共步骤、全局参数中统一引用格式：<code>{{ credRefSample }}</code>，执行时系统自动解密替换密钥内容。</p>
        </div>
        <div class="guide-block">
          <div class="guide-h">2. 三层权限隔离安全规则（优先级从高到低）</div>
          <ul>
            <li>团队私有：仅团队管理员可见明文，普通成员仅能调用无法查看密钥；跨团队完全不可读取</li>
            <li>项目专属：当前项目内所有团队可调用，仅项目管理员查看明文</li>
            <li>平台全局：全平台均可调用，仅平台超级管理员查看原始密钥</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">3. 数据加密安全机制</div>
          <ul>
            <li>所有密钥入库全程 AES 加密存储，数据库无明文留存</li>
            <li>自动化执行日志、测试报告、录屏截图、导出文件全部自动脱敏隐藏凭据明文</li>
            <li>生产环境凭据建议设置为「团队私有」，限制访问人群，杜绝密钥泄露</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">4. 环境生效逻辑</div>
          <p>凭据仅在绑定环境下生效，切换至其他测试 / 预发 / 生产环境时，脚本无法读取当前凭据，实现多环境密钥隔离。</p>
        </div>
        <div class="guide-block">
          <div class="guide-h">5. 业务适用场景</div>
          <p>接口鉴权 Token、数据库账号密码、第三方 SDK 证书、后台管理密钥、支付渠道凭证等敏感信息统一托管。</p>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-dropdown trigger="click" @command="applyTemplate">
            <el-button class="btn-aux">
              填充通用凭据模板
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="api">接口鉴权 Token</el-dropdown-item>
                <el-dropdown-item command="db">数据库账号密钥</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
import { useRouter } from 'vue-router'
import { Close, View, Hide, ArrowDown } from '@element-plus/icons-vue'
import { credentialApi, envApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const NAME_RE = /^[A-Z][A-Z0-9_]*$/
const CATEGORIES = ['接口鉴权', '数据库密钥', '测试账号凭证', '第三方证书', '其他密钥']

const TEMPLATES = {
  api: {
    name: 'API_AUTH_TOKEN',
    category: '接口鉴权',
    visibility_scope: 'project',
    description: '接口鉴权 Token，请替换为真实密钥'
  },
  db: {
    name: 'DB_ACCOUNT_SECRET',
    category: '数据库密钥',
    visibility_scope: 'team',
    description: '数据库账号密钥，请替换为真实密钥'
  }
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const router = useRouter()
const saving = ref(false)
const loadingOptions = ref(false)
const showValue = ref(false)
const snapshot = ref('')
const envs = ref([])

const form = reactive(blankForm())
const errors = reactive({ name: '', value: '' })

const isEdit = computed(() => !!form.id)
const credRefSample = '{{凭据名称}}'

const scopeHint = computed(() => {
  if (form.visibility_scope === 'team') {
    return '团队私有：仅本团队管理员可查看明文，普通成员仅可引用不可查看密钥'
  }
  if (form.visibility_scope === 'project') {
    return '项目专属：当前项目所有团队可引用，仅项目管理员查看明文'
  }
  return '平台全局：全平台可引用，仅超管查看明文'
})

function blankForm() {
  return {
    id: null,
    name: '',
    category: '',
    visibility_scope: 'platform',
    env_id: null,
    value: '',
    description: ''
  }
}

function clearErrors() {
  errors.name = ''
  errors.value = ''
}

function takeSnapshot() {
  return JSON.stringify({ ...form })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function sanitizeName(raw) {
  return String(raw || '').replace(/[^A-Za-z0-9_]/g, '').toUpperCase()
}

function onNameInput(val) {
  const raw = String(val ?? '')
  const hadIllegal = /[^A-Z0-9_]/.test(raw) || /[a-z]/.test(raw) || /[\u4e00-\u9fff]/.test(raw)
  const cleaned = sanitizeName(raw)
  form.name = cleaned
  if (!cleaned) {
    errors.name = hadIllegal
      ? '命名仅支持大写字母、数字、下划线，已拦截违规字符'
      : '请填写凭据名称'
  } else if (hadIllegal) {
    errors.name = '命名仅支持大写字母、数字、下划线，已拦截违规字符'
  } else if (!NAME_RE.test(cleaned)) {
    errors.name = '凭据名称需以大写字母开头，仅含大写字母、数字、下划线'
  } else {
    errors.name = ''
  }
}

function onValueInput() {
  if (!isEdit.value && !String(form.value || '').trim()) {
    errors.value = '请填写密钥内容'
  } else {
    errors.value = ''
  }
}

function envLabel(e) {
  const typeMap = { test: '测试', staging: '预发', gray: '预发', prod: '生产' }
  const t = typeMap[e.env_type] || e.env_type || ''
  const typePart = t ? ` · ${t}` : ''
  return `${e.name}${typePart}（ID: ${e.id}）`
}

function normalizeScope(s) {
  if (['platform', 'project', 'team'].includes(s)) return s
  return 'platform'
}

function normalizeCategory(c) {
  if (!c) return ''
  if (CATEGORIES.includes(c)) return c
  const map = {
    api: '接口鉴权',
    auth: '接口鉴权',
    db: '数据库密钥',
    database: '数据库密钥',
    account: '测试账号凭证',
    cert: '第三方证书',
    certificate: '第三方证书',
    other: '其他密钥'
  }
  const key = String(c).toLowerCase()
  return map[key] || (CATEGORIES.includes(c) ? c : '其他密钥')
}

async function loadOptions() {
  loadingOptions.value = true
  try {
    envs.value = (await envApi.list()).data || []
  } catch {
    envs.value = []
  }
  loadingOptions.value = false
}

function applyRow(row) {
  clearErrors()
  showValue.value = false
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      category: normalizeCategory(row.category),
      visibility_scope: normalizeScope(row.visibility_scope),
      env_id: row.env_id != null ? Number(row.env_id) : null,
      value: '',
      description: row.description || ''
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
  if (!isDirty()) {
    emit('update:modelValue', false)
    done()
    return
  }
  try {
    await ElMessageBox.confirm('当前凭据配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
    done()
  } catch { /* stay */ }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前凭据配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空凭据名称、密钥、绑定环境全部配置，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  clearErrors()
  ElMessage.success('已重置')
}

function applyTemplate(key) {
  const tpl = TEMPLATES[key]
  if (!tpl) return
  form.name = tpl.name
  form.category = tpl.category
  form.visibility_scope = tpl.visibility_scope
  form.description = tpl.description
  if (!isEdit.value) form.value = ''
  errors.name = NAME_RE.test(form.name) ? '' : errors.name
  ElMessage.success(`已填充「${key === 'api' ? '接口鉴权 Token' : '数据库账号密钥'}」模板`)
}

async function leaveTo(path, query) {
  if (isDirty()) {
    try {
      await ElMessageBox.confirm('当前凭据配置未保存，是否确认关闭弹窗并前往？', '未保存确认', {
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

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写凭据名称'
    ok = false
  } else if (!NAME_RE.test(form.name)) {
    errors.name = '凭据名称需以大写字母开头，仅含大写字母、数字、下划线'
    ok = false
  }
  if (!isEdit.value && !String(form.value || '').trim()) {
    errors.value = '请填写密钥内容'
    ok = false
  }
  return ok
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请修正标红字段后再保存')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      category: form.category || '',
      visibility_scope: form.visibility_scope || 'platform',
      env_id: form.env_id || null,
      description: form.description || ''
    }
    if (form.value) payload.value = form.value
    if (isEdit.value) {
      await credentialApi.update(form.id, payload)
      ElMessage.success('加密凭据已保存')
    } else {
      await credentialApi.create(payload)
      ElMessage.success('加密凭据已创建')
    }
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
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
.dlg-title { margin: 0; font-size: 18px; font-weight: 700; }
.dlg-close { margin-right: -6px; }

.dlg-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 70vh;
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
.field-warn {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
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

.pwd-toggle {
  color: #3b82f6;
  padding: 0 2px;
  height: auto;
  min-height: 0;
}

.guide-panel {
  padding: 14px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
}
.guide-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 12px;
}
.guide-block {
  margin-bottom: 12px;
  font-size: 12px;
  color: #334155;
  line-height: 1.65;
}
.guide-block:last-child { margin-bottom: 0; }
.guide-h { font-weight: 700; margin-bottom: 4px; color: #1e3a8a; }
.guide-block p { margin: 4px 0; }
.guide-block ul { margin: 4px 0; padding-left: 18px; }

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-left, .footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
</style>
