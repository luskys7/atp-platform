<template>
  <div class="page-container">
    <PageHeader title="定位失败报表" subtitle="按偶发时序 / 设备兼容 / 永久失效分层统计（§11）">
      <template #actions>
        <el-button @click="$router.push('/controls')">返回控件池</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <div class="toolbar">
        <span>统计周期</span>
        <el-input-number v-model="days" :min="7" :max="90" size="small" @change="loadStats" />
        <span>天</span>
        <el-button size="small" @click="loadStats">刷新</el-button>
      </div>

      <div v-if="stats" class="summary-grid">
        <div class="stat-card">
          <div class="stat-label">合计</div>
          <div class="stat-value">{{ stats.total || 0 }}</div>
        </div>
        <div class="stat-card timing">
          <div class="stat-label">偶发时序</div>
          <div class="stat-value">{{ stats.timing || 0 }}</div>
        </div>
        <div class="stat-card device">
          <div class="stat-label">设备兼容</div>
          <div class="stat-value">{{ stats.device || 0 }}</div>
        </div>
        <div class="stat-card permanent">
          <div class="stat-label">永久失效</div>
          <div class="stat-value">{{ stats.permanent || 0 }}</div>
        </div>
      </div>

      <el-divider content-position="left">高频失败控件</el-divider>
      <el-table :data="stats?.top_elements || []" stripe size="small" v-loading="loading">
        <el-table-column prop="element_name" label="控件" min-width="180" />
        <el-table-column prop="count" label="失败次数" width="120" />
      </el-table>
    </AppCard>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { controlApi } from '@/api'

const days = ref(30)
const stats = ref(null)
const loading = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const res = await controlApi.locatorFailureStats({ days: days.value })
    stats.value = res.data || null
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.stat-card { padding: 12px; border-radius: 8px; background: var(--el-fill-color-light); }
.stat-card.timing { border-left: 3px solid var(--el-color-warning); }
.stat-card.device { border-left: 3px solid var(--el-color-info); }
.stat-card.permanent { border-left: 3px solid var(--el-color-danger); }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.stat-value { font-size: 22px; font-weight: 600; margin-top: 4px; }
</style>
