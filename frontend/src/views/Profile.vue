<template>
  <div class="page-container">
    <PageHeader title="个人中心" subtitle="账户信息与安全设置" />

    <el-row :gutter="20">
      <el-col :span="12">
        <AppCard title="基本信息" :hover="false">
          <div v-if="userStore.user" class="profile-hero">
            <el-avatar :size="64" class="profile-avatar">
              {{ avatarLetter }}
            </el-avatar>
            <div>
              <div class="profile-name">{{ userStore.user.display_name || userStore.user.username }}</div>
              <el-tag size="small" effect="light" :class="roleTagClass">{{ roleLabel }}</el-tag>
            </div>
          </div>

          <el-descriptions :column="1" border style="margin-top:20px">
            <el-descriptions-item label="用户名">{{ userStore.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ fmtTime(userStore.user?.last_login_at) }}</el-descriptions-item>
          </el-descriptions>

          <el-form style="margin-top:24px" label-width="100px">
            <el-form-item label="显示名称">
              <el-input v-model="profileForm.display_name" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="updateProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>

      <el-col :span="12">
        <AppCard title="修改密码" :hover="false">
          <el-form :model="passwordForm" label-width="100px">
            <el-form-item label="原密码">
              <el-input v-model="passwordForm.old_password" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.new_password" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { roleLabels, formatTime as fmtTime } from '@/utils/status'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

const roleLabel = computed(() => roleLabels[userStore.role] || userStore.role)
const roleTagClass = computed(() => {
  const adminRoles = ['super_admin', 'test_admin']
  return adminRoles.includes(userStore.role) ? 'profile-role-admin' : 'profile-role-user'
})
const avatarLetter = computed(() => {
  const name = userStore.user?.display_name || userStore.user?.username || '?'
  return name.charAt(0).toUpperCase()
})

const profileForm = reactive({
  display_name: userStore.user?.display_name || ''
})

const passwordForm = reactive({
  old_password: '',
  new_password: ''
})

async function updateProfile() {
  await authApi.updateProfile(profileForm)
  await userStore.fetchProfile()
  ElMessage.success('信息已更新')
}

async function changePassword() {
  await authApi.changePassword(passwordForm)
  ElMessage.success('密码已修改')
  passwordForm.old_password = ''
  passwordForm.new_password = ''
}
</script>

<style scoped>
.profile-hero {
  display: flex;
  align-items: center;
  gap: 16px;
}
.profile-avatar {
  background: var(--atp-brand-400);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
}
.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--atp-text);
  margin-bottom: 6px;
}
.profile-role-admin :deep(.el-tag) {
  background: var(--atp-badge-accent-bg);
  border-color: var(--atp-badge-accent-bg);
  color: var(--atp-badge-accent-text);
}
.profile-role-user :deep(.el-tag) {
  background: var(--atp-badge-default-bg);
  border-color: var(--atp-badge-default-bg);
  color: var(--atp-badge-default-text);
}
</style>
