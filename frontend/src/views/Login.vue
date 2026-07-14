<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="grid-pattern"></div>
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
    </div>

    <div class="login-content">
      <div class="login-brand">
        <div class="brand-icon">
          <el-icon :size="36"><Monitor /></el-icon>
        </div>
        <h1>{{ APP_NAME }}</h1>
        <p class="brand-desc">{{ APP_TAGLINE }}</p>
        <ul class="feature-list">
          <li><el-icon><CircleCheck /></el-icon> 多设备并行调度</li>
          <li><el-icon><CircleCheck /></el-icon> 录屏溯源与报告</li>
          <li><el-icon><CircleCheck /></el-icon> 控件池与 CI 集成</li>
        </ul>
      </div>

      <div class="login-card">
        <h2>欢迎登录</h2>
        <p class="card-sub">请输入您的账号信息</p>

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" native-type="submit" class="login-btn">
              登 录
            </el-button>
          </el-form-item>
          <el-form-item v-if="ssoEnabled">
            <el-button :loading="ssoLoading" class="login-btn sso-btn" @click="handleSsoLogin">
              {{ ssoProvider }} 登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-hint">
          <el-icon><InfoFilled /></el-icon>
          默认账号 admin / admin123
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { APP_NAME, APP_TAGLINE } from '@/config/app'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const ssoLoading = ref(false)
const ssoEnabled = ref(false)
const ssoProvider = ref('企业 SSO')
const ssoMockSecret = ref('testflow-sso-demo')

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}

async function handleSsoLogin() {
  const { value } = await ElMessageBox.prompt('演示模式：输入平台用户名', ssoProvider.value, {
    confirmButtonText: 'SSO 登录',
    inputPlaceholder: 'admin',
    inputValue: form.username || 'admin'
  }).catch(() => ({ value: null }))
  if (!value) return
  ssoLoading.value = true
  try {
    const res = await authApi.ssoLogin({ sso_token: `${ssoMockSecret.value}:${value}` })
    userStore.token = res.data.token
    userStore.user = res.data.user
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('user', JSON.stringify(res.data.user))
    ElMessage.success('SSO 登录成功')
    router.push('/dashboard')
  } finally {
    ssoLoading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await authApi.ssoConfig()
    ssoEnabled.value = !!res.data?.enabled
    ssoProvider.value = res.data?.provider_name || '企业 SSO'
  } catch {
    ssoEnabled.value = false
  }
})
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--atp-dark-bg) 0%, #0E4A6E 45%, var(--atp-primary) 100%);
}

.grid-pattern {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 48px 48px;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}
.glow-1 {
  width: 400px;
  height: 400px;
  background: var(--atp-primary);
  top: -100px;
  right: 10%;
}
.glow-2 {
  width: 300px;
  height: 300px;
  background: var(--atp-brand-400);
  bottom: -50px;
  left: 5%;
}

.login-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px;
}

.login-brand {
  max-width: 400px;
  color: #fff;

  h1 {
    font-size: 32px;
    font-weight: 700;
    margin: 20px 0 12px;
    letter-spacing: -0.02em;
  }
}

.brand-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--atp-brand-400), var(--atp-primary));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 32px rgba(8, 145, 178, 0.35);
}

.brand-desc {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.6;
}

.feature-list {
  list-style: none;
  margin-top: 32px;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.8);
    margin-bottom: 12px;

    .el-icon {
      color: var(--atp-success);
    }
  }
}

.login-card {
  width: 400px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 20px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(20px);

  h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--atp-text);
    margin-bottom: 8px;
  }
}

.card-sub {
  font-size: 14px;
  color: var(--atp-text-secondary);
  margin-bottom: 28px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 10px;
}

.sso-btn {
  background: var(--atp-brand-50);
  border-color: var(--atp-brand-200);
  color: var(--atp-text);
}

.login-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 20px;
  font-size: 12px;
  color: var(--atp-text-muted);
}

@media (max-width: 900px) {
  .login-content {
    flex-direction: column;
    gap: 40px;
  }
  .login-brand {
    text-align: center;
    .brand-icon { margin: 0 auto; }
    .feature-list { display: none; }
  }
}
</style>
