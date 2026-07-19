<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="5vh"
    class="global-param-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑全局参数' : '新建全局参数' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：基础配置 -->
      <section class="section-card">
        <div class="section-title">参数基础配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="脚本内固定引用标识，自动化执行时自动替换对应值">参数键</span>
          </div>
          <el-input
            v-model="form.param_key"
            placeholder="仅大写英文、数字、下划线，例：API_REQUEST_HOST"
            :disabled="isEdit"
            :class="{ 'is-error-input': !!errors.param_key }"
            @input="onKeyInput"
          />
          <div class="field-hint">命名仅支持大写字母、数字、下划线，不可使用中文 / 小写 / 特殊符号</div>
          <div v-if="errors.param_key" class="field-error">{{ errors.param_key }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" :title="scopeTip">作用域</span>
          </div>
          <el-select v-model="form.scope" style="width:100%" placeholder="请选择作用域">
            <el-option label="平台全局" value="platform" />
            <el-option label="项目专属" value="project" />
            <el-option label="团队私有" value="team" />
          </el-select>
          <div class="field-hint">{{ scopeHint }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">参数值</div>
          <el-input
            v-model="form.param_value"
            :type="form.sensitive ? 'password' : 'text'"
            :show-password="form.sensitive"
            :placeholder="isEdit ? '留空则保持原值不变；填写域名 / 账号 / 密钥等' : '填写参数对应内容，域名 / 账号 / 密钥等'"
          />
        </div>

        <div class="field-block">
          <div class="field-label">敏感开关</div>
          <div class="sensitive-row">
            <el-switch v-model="form.sensitive" />
            <span class="sensitive-desc">开启后参数值加密存储，执行日志、测试报告不展示明文，适用于账号、密钥、token</span>
          </div>
          <div v-if="form.sensitive" class="field-warn">已标记敏感参数，所有执行记录将隐藏明文内容</div>
        </div>

        <div class="field-block">
          <div class="field-label">描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="填写参数业务用途，例：全局接口请求域名、登录测试账号"
          />
        </div>
      </section>

      <!-- 分区 2：使用说明 -->
      <section class="guide-panel">
        <div class="guide-title">全局参数使用说明</div>

        <div class="guide-block">
          <div class="guide-h">1. 参数引用方式（用例 / 公共步骤 / 套件钩子通用）</div>
          <p>自动化步骤中统一引用格式：<code v-pre>{{参数键名}}</code></p>
          <p>示例：配置参数键 <code>API_REQUEST_HOST</code>，脚本内填写 <code v-pre>{{API_REQUEST_HOST}}</code>，执行时自动替换为填写的参数值。</p>
        </div>

        <div class="guide-block">
          <div class="guide-h">2. 三层作用域权限规则（优先级从高到低）</div>
          <ul>
            <li><strong>团队私有：</strong>仅本团队资源可读取，其他团队完全不可见</li>
            <li><strong>项目专属：</strong>当前项目下所有团队可用，跨项目无法读取</li>
            <li><strong>平台全局：</strong>全平台所有项目、团队均可读取</li>
          </ul>
          <p>同名参数优先级：团队私有 &gt; 项目专属 &gt; 平台全局，多层级重名会优先读取范围更小的参数。</p>
        </div>

        <div class="guide-block">
          <div class="guide-h">3. 敏感参数使用规范</div>
          <ul>
            <li>账号、密码、token、密钥、签名凭证必须勾选「敏感」；</li>
            <li>敏感参数不会明文打印在执行日志、测试报告、录屏信息中，保障数据安全；</li>
            <li>仅管理员账号可查看、修改敏感参数明文，普通测试人员仅可引用无法查看原值。</li>
          </ul>
        </div>

        <div class="guide-block">
          <div class="guide-h">4. 适用场景</div>
          <p>全局参数可在：可视化测试用例、公共复用步骤、套件前置 / 后置钩子、数据集脚本中直接引用，无需重复填写固定域名、账号信息。</p>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-dropdown trigger="click" @command="applyTemplate">
            <el-button class="btn-aux">
              填充常用模板
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="host">接口域名</el-dropdown-item>
                <el-dropdown-item command="account">测试账号</el-dropdown-item>
                <el-dropdown-item command="token">登录 Token</el-dropdown-item>
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
import { Close, ArrowDown } from '@element-plus/icons-vue'
import { globalParamApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const KEY_RE = /^[A-Z][A-Z0-9_]*$/

const TEMPLATES = {
  host: {
    param_key: 'API_REQUEST_HOST',
    scope: 'platform',
    param_value: 'https://test-api.example.com',
    sensitive: false,
    description: '全局接口请求域名'
  },
  account: {
    param_key: 'TEST_LOGIN_ACCOUNT',
    scope: 'team',
    param_value: 'test_user_01',
    sensitive: true,
    description: '登录测试账号（敏感）'
  },
  token: {
    param_key: 'LOGIN_AUTH_TOKEN',
    scope: 'project',
    param_value: '请替换为真实 Token',
    sensitive: true,
    description: '登录鉴权 Token（敏感）'
  }
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const snapshot = ref('')
const form = reactive(blankForm())
const errors = reactive({ param_key: '' })

const isEdit = computed(() => !!form.id)

const scopeTip = '平台全局：全部项目、全部团队均可读取；项目专属：仅当前业务项目内资源可见；团队私有：仅本团队用例、套件可引用'

const scopeHint = computed(() => {
  if (form.scope === 'platform') return '平台全局：全部项目、全部团队均可读取'
  if (form.scope === 'project') return '项目专属：仅当前业务项目内资源可见'
  if (form.scope === 'team') return '团队私有：仅本团队用例、套件可引用'
  return ''
})

function blankForm() {
  return {
    id: null,
    param_key: '',
    scope: 'platform',
    param_value: '',
    sensitive: false,
    description: ''
  }
}

function clearErrors() {
  errors.param_key = ''
}

function normalizeScope(s) {
  if (s === 'env') return 'project'
  if (['platform', 'project', 'team'].includes(s)) return s
  return 'platform'
}

function takeSnapshot() {
  return JSON.stringify({ ...form })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function sanitizeKey(raw) {
  return String(raw || '')
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '')
}

function onKeyInput(val) {
  if (isEdit.value) return
  const raw = String(val ?? '')
  const hadIllegal = /[^A-Z0-9_]/.test(raw) || /[a-z]/.test(raw) || /[\u4e00-\u9fff]/.test(raw)
  const cleaned = sanitizeKey(raw)
  form.param_key = cleaned
  if (!cleaned) {
    errors.param_key = hadIllegal ? '仅允许大写字母、数字与下划线，已拦截违规字符' : '请填写参数键'
  } else if (hadIllegal) {
    errors.param_key = '仅允许大写字母、数字与下划线，已拦截违规字符'
  } else if (!KEY_RE.test(cleaned)) {
    errors.param_key = '参数键需以大写字母开头，仅含大写字母、数字、下划线'
  } else {
    errors.param_key = ''
  }
}

function applyRow(row) {
  clearErrors()
  if (row) {
    Object.assign(form, {
      id: row.id,
      param_key: row.param_key || '',
      scope: normalizeScope(row.scope),
      param_value: '',
      sensitive: !!row.sensitive,
      description: row.description || ''
    })
  } else {
    Object.assign(form, blankForm())
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前参数配置未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前参数配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空参数键、值与所有配置，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId, param_key: keepId ? form.param_key : '' })
  clearErrors()
  ElMessage.success('已重置')
}

function applyTemplate(cmd) {
  const tpl = TEMPLATES[cmd]
  if (!tpl) return
  if (!isEdit.value) form.param_key = tpl.param_key
  form.scope = tpl.scope
  form.param_value = tpl.param_value
  form.sensitive = tpl.sensitive
  if (!form.description?.trim()) form.description = tpl.description
  errors.param_key = ''
  const names = { host: '接口域名', account: '测试账号', token: '登录 Token' }
  ElMessage.success(`已填充「${names[cmd] || ''}」模板`)
}

function validateAll() {
  clearErrors()
  if (!form.param_key?.trim()) {
    errors.param_key = '请填写参数键'
    return false
  }
  if (!KEY_RE.test(form.param_key)) {
    errors.param_key = '参数键不符合命名规范：仅大写字母、数字、下划线，且需以字母开头'
    return false
  }
  return true
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请修正标红字段后再保存')
    return
  }
  if (!isEdit.value && !form.param_value?.trim()) {
    try {
      await ElMessageBox.confirm('当前参数值为空，确认保存空值参数吗？', '空值确认', {
        type: 'warning',
        confirmButtonText: '仍要保存',
        cancelButtonText: '返回填写'
      })
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      param_key: form.param_key.trim(),
      scope: form.scope,
      env_id: null,
      sensitive: form.sensitive,
      description: form.description || '',
      enabled: true
    }
    if (form.param_value) payload.param_value = form.param_value
    if (isEdit.value) {
      payload.change_note = '更新'
      await globalParamApi.update(form.id, payload)
      ElMessage.success('全局参数已保存')
    } else {
      if (!payload.param_value) payload.param_value = ''
      await globalParamApi.create(payload)
      ElMessage.success('全局参数已创建')
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
.field-warn {
  margin-top: 8px;
  font-size: 12px;
  color: #ea580c;
  line-height: 1.45;
}
:deep(.is-error-input .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.sensitive-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.sensitive-desc {
  flex: 1;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  padding-top: 2px;
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
.guide-h {
  font-weight: 700;
  margin-bottom: 4px;
  color: #1e3a8a;
}
.guide-block p { margin: 4px 0; }
.guide-block ul {
  margin: 4px 0;
  padding-left: 18px;
}
.guide-block code {
  background: #fff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  padding: 1px 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11.5px;
  color: #1d4ed8;
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
