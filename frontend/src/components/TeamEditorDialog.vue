<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="6vh"
    class="team-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑团队' : '新建团队' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingUsers">
      <!-- 分区 1：基础必填 -->
      <section class="section-card">
        <div class="section-title">团队基础必填信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="用于区分不同业务测试小组，隔离用例、套件、设备资源访问权限">团队名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="填写业务团队中文名称，例：支付业务测试组、登录模块迭代组"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="编码为团队唯一标识，用于自动化资源权限隔离匹配">团队编码</span>
          </div>
          <el-input
            v-model="form.code"
            :disabled="isEdit && !cloneMode"
            placeholder="仅允许小写英文、横杠，例：pay-test-group"
            :class="{ 'is-error-input': !!errors.code }"
            @input="onCodeInput"
          />
          <div class="field-hint">编码仅支持小写字母与横杠，不可输入中文、大写、特殊符号</div>
          <div v-if="errors.code" class="field-error">{{ errors.code }}</div>
        </div>
      </section>

      <!-- 分区 2：描述 -->
      <section class="section-card">
        <div class="section-title">团队补充描述</div>
        <div class="field-block">
          <div class="field-label">团队描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="填写团队负责业务范围、迭代版本说明（选填）&#10;例：负责支付模块全流程冒烟、线上版本回归测试"
          />
        </div>
      </section>

      <!-- 分区 3：成员权限 -->
      <section class="section-card">
        <div class="section-title">团队成员权限配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="团队管理员拥有编辑 / 删除全部资源权限">管理员</span>
          </div>
          <el-select
            v-model="adminUserId"
            filterable
            clearable
            placeholder="下拉选择平台账号作为团队管理员"
            style="width:100%"
          >
            <el-option
              v-for="u in adminCandidates"
              :key="u.id"
              :label="userLabel(u)"
              :value="u.id"
            />
          </el-select>
          <div class="field-hint">管理员拥有编辑 / 删除团队内全部资源权限</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="仅可查看、执行团队内自动化资源，无编辑删除权限">普通测试成员</span>
          </div>
          <el-select
            v-model="memberUserIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="批量多选账号，仅可查看、执行团队内资源"
            style="width:100%"
          >
            <el-option
              v-for="u in memberCandidates"
              :key="u.id"
              :label="userLabel(u)"
              :value="u.id"
            />
          </el-select>
          <div class="member-actions">
            <el-button size="small" type="primary" plain @click="addAllTesters">批量添加成员</el-button>
            <el-button size="small" @click="clearMembers">移除选中成员</el-button>
          </div>
          <div class="field-hint">普通成员仅可查看、执行团队内自动化资源，无编辑删除权限</div>
        </div>
      </section>

      <!-- 分区 5：提示 -->
      <section class="hint-box">
        团队空间作用：不同业务团队资源完全隔离，仅本团队成员可查看、编辑对应测试用例、套件、设备资源，避免跨业务误操作。
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button v-if="isEdit && !cloneMode" class="btn-aux" @click="cloneAsNew">复制已有团队模板</el-button>
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
import { Close } from '@element-plus/icons-vue'
import { teamApi, authApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const CODE_RE = /^[a-z]+(-[a-z]+)*$/

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const loadingUsers = ref(false)
const cloneMode = ref(false)
const snapshot = ref('')
const users = ref([])
const adminUserId = ref(null)
const memberUserIds = ref([])
const form = reactive(blankForm())
const errors = reactive({ name: '', code: '' })

const isEdit = computed(() => !!form.id && !cloneMode.value)

const adminCandidates = computed(() => {
  // 优先管理员角色，也允许选择任意账号
  return users.value
})

const memberCandidates = computed(() => {
  return users.value.filter(u => u.id !== adminUserId.value)
})

function blankForm() {
  return { id: null, name: '', code: '', description: '' }
}

function clearErrors() {
  errors.name = ''
  errors.code = ''
}

function userLabel(u) {
  const role = { super_admin: '超管', test_admin: '管理员', tester: '测试', developer_readonly: '只读' }[u.role] || u.role
  return `${u.display_name || u.username}（${u.username} · ${role}）`
}

function takeSnapshot() {
  return JSON.stringify({
    form: { ...form },
    adminUserId: adminUserId.value,
    memberUserIds: [...memberUserIds.value],
    cloneMode: cloneMode.value
  })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  if (!form.name?.trim()) errors.name = '请填写团队名称'
  else errors.name = ''
}

function sanitizeCode(raw) {
  return String(raw || '')
    .toLowerCase()
    .replace(/[^a-z-]/g, '')
    .replace(/--+/g, '-')
}

function onCodeInput(val) {
  if (isEdit && !cloneMode.value) return
  const raw = String(val ?? '')
  const hadIllegal = /[^a-z-]/.test(raw)
  const cleaned = sanitizeCode(raw)
  form.code = cleaned
  if (!cleaned) {
    errors.code = hadIllegal ? '仅允许小写字母与横杠，已拦截违规字符' : '请填写团队编码'
  } else if (hadIllegal) {
    errors.code = '仅允许小写字母与横杠，已拦截违规字符'
  } else if (!CODE_RE.test(cleaned)) {
    errors.code = '编码格式不正确：仅允许小写英文与横杠，例：pay-test-group'
  } else {
    errors.code = ''
  }
}

function addAllTesters() {
  const ids = users.value
    .filter(u => u.role === 'tester' && u.id !== adminUserId.value)
    .map(u => u.id)
  memberUserIds.value = [...new Set([...memberUserIds.value, ...ids])]
  ElMessage.success(ids.length ? `已添加 ${ids.length} 名测试成员` : '暂无可添加的测试成员')
}

function clearMembers() {
  memberUserIds.value = []
}

async function loadUsers() {
  loadingUsers.value = true
  try {
    users.value = (await authApi.listUsers()).data || []
  } catch {
    users.value = []
  } finally {
    loadingUsers.value = false
  }
}

function applyRow(row) {
  clearErrors()
  cloneMode.value = false
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      code: row.code || '',
      description: row.description || ''
    })
    const inTeam = users.value.filter(u => u.team_id === row.id)
    const admins = inTeam.filter(u => ['super_admin', 'test_admin'].includes(u.role))
    adminUserId.value = admins[0]?.id || inTeam[0]?.id || null
    memberUserIds.value = inTeam.filter(u => u.id !== adminUserId.value).map(u => u.id)
  } else {
    Object.assign(form, blankForm())
    adminUserId.value = null
    memberUserIds.value = []
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  await loadUsers()
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

watch(adminUserId, (id) => {
  if (id != null) {
    memberUserIds.value = memberUserIds.value.filter(x => x !== id)
  }
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
    await ElMessageBox.confirm('将清空当前所有输入、成员配置内容，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = cloneMode.value ? null : form.id
  Object.assign(form, blankForm(), { id: keepId })
  adminUserId.value = null
  memberUserIds.value = []
  clearErrors()
  ElMessage.success('已重置')
}

function cloneAsNew() {
  cloneMode.value = true
  form.id = null
  if (form.name && !form.name.includes('副本')) form.name = `${form.name}_副本`
  form.code = form.code ? `${form.code}-copy` : ''
  onCodeInput(form.code)
  ElMessage.success('已切换为新建模式，请修改编码后创建同类业务组')
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写团队名称'
    ok = false
  }
  if (!form.code?.trim()) {
    errors.code = '请填写团队编码'
    ok = false
  } else if (!CODE_RE.test(form.code)) {
    errors.code = '编码格式不正确：仅允许小写英文与横杠，例：pay-test-group'
    ok = false
  }
  return ok
}

async function syncMembers(teamId) {
  const targetIds = new Set([
    ...(adminUserId.value ? [adminUserId.value] : []),
    ...memberUserIds.value
  ])
  // 分配选中成员到本团队
  for (const uid of targetIds) {
    try { await teamApi.assignUser(uid, teamId) } catch { /* skip */ }
  }
  // 编辑时：原属于本团队但不在目标列表的用户，解除归属（设为 null）
  if (props.editRow?.id && !cloneMode.value) {
    const prev = users.value.filter(u => u.team_id === props.editRow.id)
    for (const u of prev) {
      if (!targetIds.has(u.id)) {
        try { await teamApi.assignUser(u.id, null) } catch { /* skip */ }
      }
    }
  }
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      code: form.code.trim(),
      description: form.description || ''
    }
    let teamId = form.id
    if (isEdit) {
      await teamApi.update(form.id, payload)
      teamId = form.id
      ElMessage.success('团队已保存')
    } else {
      const res = await teamApi.create(payload)
      teamId = res.data?.id
      ElMessage.success('团队已创建')
    }
    if (teamId) await syncMembers(teamId)
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

.member-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
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
