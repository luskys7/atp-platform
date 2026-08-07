<template>
  <div class="page-container">
    <PageHeader title="系统设置" subtitle="低频运维与安全配置；个人中心、CI/CD 请从左侧导航进入" />
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.title" :xs="24" :sm="12" :lg="6">
        <AppCard class="hub-card" :hover="true" @click="go(card)">
          <div class="hub-card-inner">
            <div class="hub-icon" :class="card.tone">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.desc }}</p>
            <el-button type="primary" link>进入</el-button>
          </div>
        </AppCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const cards = computed(() => {
  const list = [
    // 个人中心、CI/CD 已在左侧「系统设置」导航，此处不再重复
    { title: '录屏配置', desc: '录制开关与质量阈值', icon: 'VideoCamera', path: '/platform-config', query: { tab: 'recording' }, tone: 'info' },
    { title: '加密凭据', desc: '密钥与敏感配置', icon: 'Key', path: '/platform-config', query: { tab: 'credentials' }, tone: 'warning', adminOnly: true },
    { title: '安全审计', desc: '操作留痕与审计日志', icon: 'Document', path: '/platform-config', query: { tab: 'audit' }, tone: 'success', adminOnly: true },
    { title: '健康监控', desc: '执行器与平台健康状态', icon: 'Odometer', path: '/platform-config', query: { tab: 'monitor' }, tone: 'primary', adminOnly: true },
    { title: '灾备备份', desc: '平台数据备份与恢复', icon: 'FolderOpened', path: '/platform-config', query: { tab: 'backup' }, tone: 'info', adminOnly: true },
    { title: '测试账号池', desc: '执行账号占用与释放', icon: 'UserFilled', path: '/platform-config', query: { tab: 'accounts' }, tone: 'warning', adminOnly: true }
  ]
  return list.filter(c => !c.adminOnly || userStore.isAdmin)
})

function go(card) {
  if (card.query) router.push({ path: card.path, query: card.query })
  else router.push(card.path)
}
</script>

<style scoped>
.hub-card {
  cursor: pointer;
  margin-bottom: 16px;
  min-height: 168px;
}
.hub-card-inner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}
.hub-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 4px;
}
.hub-icon.primary { background: linear-gradient(135deg, #38bdf8, #0891b2); }
.hub-icon.success { background: linear-gradient(135deg, #34d399, #059669); }
.hub-icon.warning { background: linear-gradient(135deg, #fbbf24, #d97706); }
.hub-icon.info { background: linear-gradient(135deg, #818cf8, #4f46e5); }
.hub-card-inner h3 { margin: 0; font-size: 16px; }
.hub-card-inner p {
  margin: 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
  min-height: 40px;
}
</style>
