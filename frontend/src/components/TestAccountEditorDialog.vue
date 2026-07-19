<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="5vh"
    class="test-account-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑测试账号' : '新建测试账号' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingOptions">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">账号基础核心信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="自动化用例、造数模板可通过标签 / 用户名读取账号">用户名</span>
          </div>
          <el-input
            v-model="form.username"
            placeholder="例：test_smoke_01、regression_user02"
            :class="{ 'is-error-input': !!errors.username }"
            @input="onUsernameInput"
          />
          <div v-if="errors.username" class="field-error">{{ errors.username }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">密码</div>
          <el-input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            :placeholder="isEdit ? '编辑账号留空则保留原有密码不修改' : '新建账号必须填写'"
            :class="{ 'is-error-input': !!errors.password }"
            @input="errors.password = ''"
          >
            <template #suffix>
              <el-button
                text
                class="pwd-toggle"
                :title="showPassword ? '隐藏明文' : '显示明文'"
                @click="showPassword = !showPassword"
              >
                <el-icon :size="16"><Hide v-if="showPassword" /><View v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="field-hint">账号密码加密存储，执行日志不会明文展示</div>
          <div v-if="errors.password" class="field-error">{{ errors.password }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">手机号</div>
          <el-input
            v-model="form.phone"
            placeholder="填写账号绑定手机号，用于验证码登录场景"
            maxlength="11"
            :class="{ 'is-error-input': !!errors.phone }"
            @input="onPhoneInput"
          />
          <div v-if="errors.phone" class="field-error">{{ errors.phone }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">启用开关</div>
          <div class="enable-row">
            <el-switch v-model="form.enabled" />
            <span class="enable-desc">开启 = 自动化可正常读取账号；关闭 = 账号冻结，无法用于执行用例</span>
          </div>
          <div v-if="form.enabled" class="field-ok">账号已启用，可在自动化流程调用</div>
          <div v-else class="field-muted">账号已冻结，所有回归任务不可读取该账号</div>
        </div>
      </section>

      <!-- 分区 2：分类归属 -->
      <section class="section-card">
        <div class="section-title">分类与归属配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="用例、套件可按标签筛选匹配对应测试账号">标签</span>
          </div>
          <div class="tag-quick">
            <el-button
              v-for="t in QUICK_TAGS"
              :key="t"
              size="small"
              :type="hasTag(t) ? 'primary' : 'default'"
              plain
              @click="toggleTag(t)"
            >{{ t }}</el-button>
          </div>
          <el-input
            v-model="form.tags"
            placeholder="多标签使用英文逗号分隔，例：冒烟,回归,支付"
          />
        </div>

        <div class="field-block">
          <div class="field-label">所属项目</div>
          <el-select
            v-model="form.project_key"
            filterable
            clearable
            placeholder="下拉选择当前业务项目，实现多项目账号资源隔离"
            style="width:100%"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label">所属团队</div>
          <el-select
            v-model="form.team_id"
            filterable
            clearable
            placeholder="下拉选择业务团队，仅本团队成员可见、可使用该账号"
            style="width:100%"
          >
            <el-option
              v-for="t in teams"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </div>
      </section>

      <!-- 分区 3：备注 -->
      <section class="section-card">
        <div class="section-title">补充备注说明</div>
        <div class="field-block">
          <div class="field-label">备注</div>
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="填写账号业务用途、使用限制，例：仅用于每日冒烟回归，不可线上压测"
          />
        </div>
      </section>

      <!-- 分区 4：说明 -->
      <section class="guide-panel">
        <div class="guide-title">测试账号独立使用说明</div>
        <div class="guide-block">
          <div class="guide-h">1. 账号隔离规则</div>
          <p>账号绑定「项目 + 团队」，仅对应团队成员可查看、在自动化用例中调用；跨项目 / 团队无法读取，保障业务账号隔离。</p>
        </div>
        <div class="guide-block">
          <div class="guide-h">2. 标签使用场景</div>
          <ul>
            <li>自动化执行时可配置标签匹配，自动分配对应账号，无需手动切换</li>
            <li>支持多标签组合筛选，例如同时带「冒烟 + 支付」标签的账号仅用于支付模块冒烟用例</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">3. 密码安全规范</div>
          <ul>
            <li>账号密码加密入库，执行日志、测试报告全程隐藏明文密码</li>
            <li>编辑账号无需重复填写密码，留空自动保留原始密码</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">4. 启用冻结功能</div>
          <p>废弃、过期账号无需删除，关闭启用开关即可全局冻结，后续需要可直接恢复使用。</p>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button v-if="isEdit && !cloneMode" class="btn-aux" @click="cloneAsNew">复制账号模板</el-button>
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
import { Close, View, Hide } from '@element-plus/icons-vue'
import { accountApi, teamApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const USERNAME_RE = /^[a-zA-Z][a-zA-Z0-9_.-]*$/
const PHONE_RE = /^1\d{10}$/
const QUICK_TAGS = ['冒烟', '回归', '灰度', '线上', '支付模块']
const PROJECT_LIST_KEY = 'atp_project_list'
const PROJECT_KEY = 'atp_current_project'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const loadingOptions = ref(false)
const cloneMode = ref(false)
const showPassword = ref(false)
const snapshot = ref('')
const teams = ref([])
const localProjects = ref([])

const form = reactive(blankForm())
const errors = reactive({ username: '', password: '', phone: '' })

const isEdit = computed(() => !!form.id && !cloneMode.value)

const projectOptions = computed(() => {
  const base = [{ id: 'default', name: '默认业务项目（全局）' }]
  const fromTeams = (teams.value || []).map(t => ({
    id: `team-${t.id}`,
    name: t.name
  }))
  const custom = (localProjects.value || []).map(p => ({ id: p.id, name: p.name }))
  const map = new Map()
  ;[...base, ...fromTeams, ...custom].forEach(p => map.set(p.id, p))
  return [...map.values()]
})

function blankForm() {
  return {
    id: null,
    username: '',
    password: '',
    phone: '',
    tags: '',
    project_key: 'default',
    team_id: null,
    remark: '',
    enabled: true
  }
}

function clearErrors() {
  errors.username = ''
  errors.password = ''
  errors.phone = ''
}

function takeSnapshot() {
  return JSON.stringify({ ...form, cloneMode: cloneMode.value })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function sanitizeUsername(raw) {
  return String(raw || '').replace(/[^\w.-]/g, '')
}

function onUsernameInput(val) {
  const raw = String(val ?? '')
  const hadIllegal = /[^\w.-]/.test(raw) || /[\u4e00-\u9fff]/.test(raw)
  const cleaned = sanitizeUsername(raw)
  form.username = cleaned
  if (!cleaned) {
    errors.username = hadIllegal ? '用户名仅允许字母、数字、下划线、点与横杠，已拦截违规字符' : '请填写用户名'
  } else if (hadIllegal) {
    errors.username = '用户名仅允许字母、数字、下划线、点与横杠，已拦截违规字符'
  } else if (!USERNAME_RE.test(cleaned)) {
    errors.username = '用户名需以字母开头，仅含字母、数字、下划线、点与横杠'
  } else {
    errors.username = ''
  }
}

function onPhoneInput() {
  const v = String(form.phone || '').replace(/\D/g, '').slice(0, 11)
  form.phone = v
  if (!v) {
    errors.phone = ''
    return
  }
  errors.phone = PHONE_RE.test(v) ? '' : '手机号须为 11 位数字，且以 1 开头'
}

function parseTags(str) {
  return String(str || '')
    .split(/[,，]/)
    .map(s => s.trim())
    .filter(Boolean)
}

function hasTag(t) {
  return parseTags(form.tags).includes(t)
}

function toggleTag(t) {
  const parts = parseTags(form.tags)
  const idx = parts.indexOf(t)
  if (idx >= 0) parts.splice(idx, 1)
  else parts.push(t)
  form.tags = parts.join(',')
}

async function loadOptions() {
  loadingOptions.value = true
  try {
    teams.value = (await teamApi.list()).data || []
  } catch {
    teams.value = []
  }
  try {
    localProjects.value = JSON.parse(localStorage.getItem(PROJECT_LIST_KEY) || '[]')
  } catch {
    localProjects.value = []
  }
  loadingOptions.value = false
}

function resolveCurrentProject() {
  try {
    const raw = localStorage.getItem(PROJECT_KEY)
    if (!raw) return 'default'
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && parsed.id) return String(parsed.id)
    return String(raw)
  } catch {
    return localStorage.getItem(PROJECT_KEY) || 'default'
  }
}

function applyRow(row) {
  clearErrors()
  cloneMode.value = false
  showPassword.value = false
  const currentProject = resolveCurrentProject()
  if (row) {
    Object.assign(form, {
      id: row.id,
      username: row.username || '',
      password: '',
      phone: row.phone || '',
      tags: row.tags || '',
      project_key: row.project_key || currentProject,
      team_id: row.team_id != null ? Number(row.team_id) : null,
      remark: row.remark || '',
      enabled: row.enabled !== false && row.status !== 'archived'
    })
  } else {
    Object.assign(form, blankForm(), { project_key: currentProject })
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
    await ElMessageBox.confirm('当前账号信息未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前账号信息未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空用户名、密码、标签等全部填写内容，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = cloneMode.value ? null : form.id
  const keepProject = form.project_key
  Object.assign(form, blankForm(), { id: keepId, project_key: keepProject })
  clearErrors()
  ElMessage.success('已重置')
}

function cloneAsNew() {
  cloneMode.value = true
  form.id = null
  if (form.username && !form.username.includes('_copy')) {
    form.username = `${form.username}_copy`
  }
  form.password = ''
  form.enabled = true
  ElMessage.success('已切换为新建模式，请修改用户名与密码后保存')
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.username?.trim()) {
    errors.username = '请填写用户名'
    ok = false
  } else if (!USERNAME_RE.test(form.username)) {
    errors.username = '用户名需以字母开头，仅含字母、数字、下划线、点与横杠'
    ok = false
  }
  if (!isEdit.value && !form.password?.trim()) {
    errors.password = '新建账号必须填写密码'
    ok = false
  }
  if (form.phone?.trim() && !PHONE_RE.test(form.phone.trim())) {
    errors.phone = '手机号须为 11 位数字，且以 1 开头'
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
      username: form.username.trim(),
      phone: form.phone || '',
      tags: form.tags || '',
      remark: form.remark || '',
      project_key: form.project_key || 'default',
      team_id: form.team_id || null,
      enabled: !!form.enabled
    }
    if (form.password) payload.password = form.password
    if (isEdit.value) {
      await accountApi.update(form.id, payload)
      ElMessage.success('测试账号已保存')
    } else {
      await accountApi.create(payload)
      ElMessage.success('测试账号已创建')
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
.field-ok { margin-top: 8px; font-size: 12px; color: #16a34a; }
.field-muted { margin-top: 8px; font-size: 12px; color: #94a3b8; }
:deep(.is-error-input .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.pwd-toggle {
  color: #3b82f6;
  padding: 0 2px;
  height: auto;
  min-height: 0;
}

.enable-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.enable-desc {
  flex: 1;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  padding-top: 2px;
}

.tag-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
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
}
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
</style>
