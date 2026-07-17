<template>
  <div class="page-container">
    <PageHeader title="项目管理" subtitle="多业务隔离：环境、应用包、团队与数据集（沿用现有能力）" />
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
    { title: '环境配置', desc: '测试/预发/生产环境与 Base URL', icon: 'Monitor', path: '/platform-config', query: { tab: 'env' }, tone: 'primary' },
    { title: '应用包版本', desc: 'APP 包仓库与版本管理', icon: 'Box', path: '/app-packages', tone: 'success' },
    { title: '团队空间', desc: '团队隔离与成员归属', icon: 'UserFilled', path: '/platform-config', query: { tab: 'teams' }, tone: 'warning', adminOnly: true },
    { title: '数据集', desc: '参数化数据与造数模板', icon: 'Coin', path: '/platform-config', query: { tab: 'dataset' }, tone: 'info' },
    { title: '版本基线', desc: '套件/环境/包版本基线绑定', icon: 'Flag', path: '/platform-config', query: { tab: 'baseline' }, tone: 'primary' }
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
