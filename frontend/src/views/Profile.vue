<template>
  <div class="page-container profile-page">
    <PageHeader
      title="个人中心"
      subtitle="账户基础信息、登录安全与个性化偏好设置"
    />

    <el-row :gutter="20">
      <!-- 模块 2：基础信息 -->
      <el-col :xs="24" :lg="12">
        <AppCard title="基础信息" :hover="false">
          <div class="profile-hero">
            <div class="avatar-wrap" @click="triggerAvatarUpload" title="点击上传自定义头像">
              <el-avatar :size="72" class="profile-avatar" :src="avatarSrc">
                {{ avatarShort }}
              </el-avatar>
              <div class="avatar-mask">上传</div>
              <input
                ref="avatarInput"
                type="file"
                accept="image/*"
                class="hidden-input"
                @change="onAvatarChange"
              >
            </div>
            <div>
              <div class="profile-name">{{ profileForm.display_name || userStore.user?.username || '-' }}</div>
              <el-tag size="small" effect="light" :type="roleTagType">{{ roleLabel }}</el-tag>
              <p class="avatar-hint">点击头像可上传自定义头像</p>
            </div>
          </div>

          <div class="readonly-block">
            <div class="readonly-row">
              <span class="readonly-label">登录用户名</span>
              <el-tooltip content="用户名创建后不可修改" placement="top">
                <span class="readonly-value muted">{{ userStore.user?.username || '-' }}</span>
              </el-tooltip>
            </div>
            <div class="readonly-row">
              <span class="readonly-label">最近登录时间</span>
              <span class="readonly-value muted">{{ fmtTime(userStore.user?.last_login_at) }}</span>
            </div>
            <div class="readonly-row">
              <span class="readonly-label">最近登录 IP</span>
              <span class="readonly-value muted">{{ userStore.user?.last_login_ip || '-' }}</span>
            </div>
          </div>

          <el-form label-width="110px" class="profile-form">
            <el-form-item label="显示名称">
              <el-input v-model="profileForm.display_name" placeholder="用于平台内展示昵称" />
            </el-form-item>
            <el-form-item label="联系手机号">
              <el-input v-model="profileForm.phone" placeholder="选填，便于紧急联系" maxlength="20" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="profileForm.email" placeholder="接收任务报告、系统告警通知" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="!profileDirty" :loading="savingProfile" @click="saveProfile">
                保存信息
              </el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>

      <el-col :xs="24" :lg="12">
        <!-- 模块 3：修改密码 -->
        <AppCard title="修改登录密码" :hover="false">
          <el-form :model="passwordForm" label-width="110px" class="profile-form">
            <el-form-item label="原密码" required>
              <el-input
                v-model="passwordForm.old_password"
                type="password"
                show-password
                placeholder="请输入当前登录密码"
              />
            </el-form-item>
            <el-form-item label="新密码" required>
              <el-input
                v-model="passwordForm.new_password"
                type="password"
                show-password
                placeholder="设置新密码"
              />
              <p class="field-tip">长度 8~32 位，建议包含大小写、数字、符号</p>
            </el-form-item>
            <el-form-item label="确认新密码" required>
              <el-input
                v-model="passwordForm.confirm_password"
                type="password"
                show-password
                placeholder="再次输入新密码"
              />
              <p v-if="passwordForm.confirm_password && !passwordMatch" class="field-error">两次输入的新密码不一致</p>
              <p v-else-if="passwordForm.confirm_password && passwordMatch" class="field-ok">两次输入一致</p>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPassword" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </AppCard>

        <!-- 模块 4：会话管理 -->
        <AppCard title="登录会话与登录日志" :hover="false" class="card-gap">
          <div class="section-head">
            <span class="section-title">当前在线会话</span>
            <el-button
              type="danger"
              plain
              size="small"
              :loading="revokingOthers"
              @click="revokeOthers"
            >
              退出所有其他会话
            </el-button>
          </div>
          <p class="risk-tip">执行「退出所有其他会话」后，除当前页面以外所有登录设备将被踢出</p>

          <el-table :data="sessions" size="small" empty-text="暂无在线会话，重新登录后可在此管理">
            <el-table-column prop="device_label" label="登录设备" min-width="100" />
            <el-table-column prop="browser" label="浏览器" width="100" />
            <el-table-column prop="ip" label="登录 IP" width="120" />
            <el-table-column label="登录时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.login_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  :disabled="row.jti === currentJti"
                  @click="revokeSession(row)"
                >
                  {{ row.jti === currentJti ? '当前' : '强制下线' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="session-actions">
            <el-button @click="openLoginLogs">查看全部登录记录</el-button>
          </div>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 5：扩展 -->
    <el-row :gutter="20" class="ext-row">
      <el-col :xs="24" :lg="12">
        <AppCard title="个性化偏好设置" :hover="false">
          <el-form label-width="140px" class="profile-form">
            <el-form-item label="报表默认导出格式">
              <el-radio-group v-model="preferences.report_export_format">
                <el-radio-button value="pdf">PDF</el-radio-button>
                <el-radio-button value="excel">Excel</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="大屏刷新间隔">
              <el-select v-model="preferences.dashboard_refresh_seconds" style="width:180px">
                <el-option :value="15" label="15 秒" />
                <el-option :value="30" label="30 秒" />
                <el-option :value="60" label="60 秒" />
                <el-option :value="120" label="2 分钟" />
                <el-option :value="0" label="关闭自动刷新" />
              </el-select>
            </el-form-item>
            <el-form-item label="消息通知">
              <div class="notify-switches">
                <div class="switch-row">
                  <span>任务完成通知</span>
                  <el-switch v-model="preferences.notify_task_done" />
                </div>
                <div class="switch-row">
                  <span>执行失败告警</span>
                  <el-switch v-model="preferences.notify_task_failed" />
                </div>
                <div class="switch-row">
                  <span>审计变更通知</span>
                  <el-switch v-model="preferences.notify_audit_change" />
                </div>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPrefs" @click="savePreferences">保存偏好</el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>

      <el-col :xs="24" :lg="12">
        <AppCard title="API 访问密钥" :hover="false">
          <p class="api-desc">用于 CI/CD、外部脚本调用平台开放接口；完整密钥仅在生成时展示一次。</p>
          <div class="api-toolbar">
            <el-input v-model="newKeyName" placeholder="密钥名称" style="width:200px" />
            <el-button type="primary" :loading="creatingKey" @click="createApiKey">生成密钥</el-button>
          </div>
          <el-table :data="apiKeys" size="small" empty-text="暂无 API 密钥" style="margin-top:12px">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column label="密钥前缀" width="140">
              <template #default="{ row }">{{ row.key_prefix }}••••</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.active ? 'success' : 'info'">
                  {{ row.active ? '有效' : '已作废' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.active"
                  link
                  type="danger"
                  @click="revokeApiKey(row)"
                >
                  一键作废
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 6：安全规范 -->
    <div class="security-guide">
      <h3>账户安全规范说明</h3>
      <ul>
        <li>用户名一经创建无法修改，仅可调整显示昵称；</li>
        <li>修改登录密码成功后，所有终端会话自动失效，需要重新登录；</li>
        <li>定期查看登录日志，发现陌生 IP 登录请立即修改密码，并下线所有会话；</li>
        <li>超级管理员账号建议至少 90 天更新一次登录密码，避免长期使用固定密码；</li>
        <li>API 密钥请勿对外泄露，一旦泄露立即作废并重新生成。</li>
      </ul>
    </div>

    <!-- 登录日志弹窗 -->
    <el-dialog v-model="showLogs" title="近 90 天登录记录" width="780px" destroy-on-close>
      <el-table :data="loginLogs" size="small" max-height="420" empty-text="暂无登录记录">
        <el-table-column label="登录时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.login_at) }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="登录 IP" width="130" />
        <el-table-column prop="device_label" label="设备" width="110" />
        <el-table-column prop="browser" label="浏览器" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异地" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.remote_login" size="small" type="warning">异地</el-tag>
            <span v-else class="muted">否</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const avatarInput = ref(null)

const profileForm = reactive({
  display_name: '',
  phone: '',
  email: ''
})
const profileSnapshot = ref('')

const passwordForm = reactive({
  old_password: '',
  new_password: '',
  confirm_password: ''
})

const preferences = reactive({
  report_export_format: 'pdf',
  dashboard_refresh_seconds: 30,
  notify_task_done: true,
  notify_task_failed: true,
  notify_audit_change: false
})

const sessions = ref([])
const loginLogs = ref([])
const apiKeys = ref([])
const showLogs = ref(false)
const newKeyName = ref('')
const currentJti = ref('')

const savingProfile = ref(false)
const savingPassword = ref(false)
const savingPrefs = ref(false)
const revokingOthers = ref(false)
const creatingKey = ref(false)

const profileRoleLabels = {
  super_admin: '超级管理员',
  test_admin: '项目管理员',
  tester: '测试人员',
  developer_readonly: '研发只读'
}

const avatarShortMap = {
  super_admin: '超',
  test_admin: '管',
  tester: '测',
  developer_readonly: '研'
}

const roleLabel = computed(() => profileRoleLabels[userStore.role] || userStore.role || '-')
const roleTagType = computed(() => {
  if (userStore.role === 'super_admin') return 'danger'
  if (userStore.role === 'test_admin') return 'warning'
  return 'primary'
})
const avatarShort = computed(() => avatarShortMap[userStore.role] || '用')
const avatarSrc = computed(() => userStore.user?.avatar_url || '')

const profileDirty = computed(() => {
  const cur = JSON.stringify({
    display_name: profileForm.display_name || '',
    phone: profileForm.phone || '',
    email: profileForm.email || ''
  })
  return cur !== profileSnapshot.value
})

const passwordMatch = computed(() =>
  passwordForm.new_password
  && passwordForm.confirm_password
  && passwordForm.new_password === passwordForm.confirm_password
)

function syncProfileForm(user) {
  profileForm.display_name = user?.display_name || ''
  profileForm.phone = user?.phone || ''
  profileForm.email = user?.email || ''
  profileSnapshot.value = JSON.stringify({
    display_name: profileForm.display_name || '',
    phone: profileForm.phone || '',
    email: profileForm.email || ''
  })
}

function syncPreferences(prefs) {
  const p = prefs || {}
  preferences.report_export_format = p.report_export_format || 'pdf'
  preferences.dashboard_refresh_seconds = p.dashboard_refresh_seconds ?? 30
  preferences.notify_task_done = p.notify_task_done !== false
  preferences.notify_task_failed = p.notify_task_failed !== false
  preferences.notify_audit_change = !!p.notify_audit_change
}

function parseJtiFromToken(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.jti || ''
  } catch {
    return ''
  }
}

async function loadAll() {
  const payload = await userStore.fetchProfile()
  syncProfileForm(userStore.user)
  syncPreferences(payload?.preferences || userStore.preferences)
  currentJti.value = parseJtiFromToken(userStore.token)
  const [sessRes, keyRes] = await Promise.all([
    authApi.sessions().catch(() => ({ data: [] })),
    authApi.apiKeys().catch(() => ({ data: [] }))
  ])
  sessions.value = sessRes.data || []
  apiKeys.value = keyRes.data || []
}

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const res = await authApi.uploadAvatar(file)
    await userStore.fetchProfile()
    ElMessage.success('头像已更新')
    if (res?.data?.avatar_url) {
      userStore.user = { ...userStore.user, avatar_url: res.data.avatar_url }
    }
  } catch (err) {
    ElMessage.error(err?.message || '头像上传失败')
  }
}

async function saveProfile() {
  if (!profileDirty.value) return
  savingProfile.value = true
  try {
    await authApi.updateProfile({
      display_name: profileForm.display_name,
      phone: profileForm.phone,
      email: profileForm.email
    })
    await userStore.fetchProfile()
    syncProfileForm(userStore.user)
    ElMessage.success('信息已保存')
  } finally {
    savingProfile.value = false
  }
}

function validateNewPassword(pwd) {
  if (!pwd || pwd.length < 8 || pwd.length > 32) {
    return '密码长度需为 8~32 位'
  }
  let score = 0
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  if (score < 3) return '密码需至少包含大小写字母、数字、符号中的三类'
  return ''
}

async function changePassword() {
  if (!passwordForm.old_password) {
    ElMessage.warning('原密码不能为空')
    return
  }
  const ruleErr = validateNewPassword(passwordForm.new_password)
  if (ruleErr) {
    ElMessage.warning(ruleErr)
    return
  }
  if (!passwordMatch.value) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await authApi.changePassword({
      old_password: passwordForm.old_password,
      new_password: passwordForm.new_password,
      confirm_password: passwordForm.confirm_password
    })
    await ElMessageBox.alert(
      '密码修改成功，所有终端会话将被强制下线，请重新登录',
      '修改成功',
      { type: 'success', confirmButtonText: '重新登录' }
    )
    userStore.logout()
    router.push('/login')
  } catch {
    // 错误已由拦截器提示
  } finally {
    savingPassword.value = false
  }
}

async function revokeSession(row) {
  await ElMessageBox.confirm(`确认强制下线该会话（${row.device_label} / ${row.ip}）？`, '强制下线', {
    type: 'warning'
  })
  await authApi.revokeSession(row.id)
  ElMessage.success('已强制下线')
  sessions.value = (await authApi.sessions()).data || []
}

async function revokeOthers() {
  await ElMessageBox.confirm(
    '执行后除当前页面以外所有登录设备全部踢出，是否继续？',
    '退出所有其他会话',
    { type: 'warning' }
  )
  revokingOthers.value = true
  try {
    const res = await authApi.revokeOtherSessions()
    ElMessage.success(`已下线 ${res.data?.revoked ?? 0} 个其他会话`)
    sessions.value = (await authApi.sessions()).data || []
  } finally {
    revokingOthers.value = false
  }
}

async function openLoginLogs() {
  loginLogs.value = (await authApi.loginLogs()).data || []
  showLogs.value = true
}

async function savePreferences() {
  savingPrefs.value = true
  try {
    await authApi.updateProfile({
      display_name: profileForm.display_name,
      phone: profileForm.phone,
      email: profileForm.email,
      preferences: { ...preferences }
    })
    userStore.preferences = { ...preferences }
    ElMessage.success('偏好已保存')
  } finally {
    savingPrefs.value = false
  }
}

async function createApiKey() {
  creatingKey.value = true
  try {
    const res = await authApi.createApiKey({ name: newKeyName.value || '默认密钥' })
    const key = res.data?.api_key
    apiKeys.value = (await authApi.apiKeys()).data || []
    newKeyName.value = ''
    if (key) {
      await ElMessageBox.alert(
        `请立即复制保存，关闭后将无法再次查看完整密钥：\n\n${key}`,
        '密钥已生成',
        { confirmButtonText: '我已保存' }
      )
    }
  } finally {
    creatingKey.value = false
  }
}

async function revokeApiKey(row) {
  await ElMessageBox.confirm(`确认作废密钥「${row.name}」？作废后不可恢复。`, '作废密钥', { type: 'warning' })
  await authApi.revokeApiKey(row.id)
  ElMessage.success('密钥已作废')
  apiKeys.value = (await authApi.apiKeys()).data || []
}

onMounted(loadAll)
</script>

<style scoped>
.profile-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s;
}

.profile-avatar {
  background: var(--atp-brand-400, #4c7cf0);
  color: #fff;
  font-size: 26px;
  font-weight: 600;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--atp-text, #1f2a37);
  margin-bottom: 6px;
}

.avatar-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--atp-gray-purple, #8b8fa3);
}

.hidden-input {
  display: none;
}

.readonly-block {
  margin: 20px 0 8px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f6f8;
  border: 1px solid #e8eaef;
}

.readonly-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
}

.readonly-row + .readonly-row {
  border-top: 1px dashed #e0e3ea;
}

.readonly-label {
  color: #6b7280;
  flex-shrink: 0;
}

.readonly-value {
  text-align: right;
  word-break: break-all;
}

.muted {
  color: #9aa3b2;
}

.profile-form {
  margin-top: 12px;
}

.field-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--atp-gray-purple, #8b8fa3);
  line-height: 1.5;
}

.field-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

.field-ok {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-success);
}

.card-gap {
  margin-top: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 14px;
}

.risk-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-color-warning);
  line-height: 1.5;
}

.session-actions {
  margin-top: 14px;
}

.ext-row {
  margin-top: 20px;
}

.notify-switches {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 320px;
}

.api-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--atp-gray-purple, #8b8fa3);
  line-height: 1.6;
}

.api-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.security-guide {
  margin-top: 20px;
  padding: 20px 24px;
  border-radius: var(--atp-radius, 8px);
  background: #e8f3ff;
  color: #1f2a37;
  line-height: 1.7;
}

.security-guide h3 {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 600;
}

.security-guide ul {
  margin: 0;
  padding-left: 1.4em;
}

.security-guide li + li {
  margin-top: 4px;
}
</style>
