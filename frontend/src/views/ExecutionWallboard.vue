<template>
  <div class="wallboard">
    <header class="wb-header">
      <div>
        <h1>TestFlow 全局执行大屏</h1>
        <p class="wb-sub">设备 · 任务 · 通过率实时概览</p>
      </div>
      <div class="wb-meta">
        <span>{{ checkedAt }}</span>
        <el-button size="small" @click="$router.push('/dashboard')">退出大屏</el-button>
      </div>
    </header>

    <div class="wb-grid" v-loading="loading">
      <div class="wb-card wb-card--hero">
        <div class="wb-label">通过率</div>
        <div class="wb-value" :class="passClass">{{ (data.pass_rate || 0).toFixed(1) }}%</div>
        <div class="wb-foot">治理线 {{ data.min_pass_rate || 99 }}%</div>
      </div>
      <div class="wb-card">
        <div class="wb-label">在线设备</div>
        <div class="wb-value text-green">{{ data.devices?.online || 0 }}</div>
        <div class="wb-foot">总计 {{ data.devices?.total || 0 }}</div>
      </div>
      <div class="wb-card">
        <div class="wb-label">忙碌设备</div>
        <div class="wb-value text-amber">{{ data.devices?.busy || 0 }}</div>
        <div class="wb-foot">离线 {{ data.devices?.offline || 0 }}</div>
      </div>
      <div class="wb-card">
        <div class="wb-label">运行中任务</div>
        <div class="wb-value text-blue">{{ data.tasks?.running || 0 }}</div>
        <div class="wb-foot">排队 {{ data.tasks?.queued || 0 }}</div>
      </div>
      <div class="wb-card">
        <div class="wb-label">队列深度</div>
        <div class="wb-value">{{ data.queue_depth || 0 }}</div>
        <div class="wb-foot">待提交 {{ data.tasks?.pending || 0 }}</div>
      </div>
      <div class="wb-card">
        <div class="wb-label">成功 / 失败</div>
        <div class="wb-value"><span class="text-green">{{ data.tasks?.success || 0 }}</span> / <span class="text-red">{{ data.tasks?.failed || 0 }}</span></div>
        <div class="wb-foot">异常设备 {{ data.devices?.error || 0 }}</div>
      </div>
    </div>

    <el-row :gutter="20" class="wb-charts">
      <el-col :span="14">
        <div class="wb-panel">
          <h3>通过率趋势</h3>
          <div ref="trendRef" class="chart-area"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="wb-panel">
          <h3>运行中任务</h3>
          <el-table :data="data.running_tasks || []" size="small" stripe empty-text="暂无运行任务" class="wb-table">
            <el-table-column prop="id" label="#" width="60" />
            <el-table-column prop="name" label="任务" show-overflow-tooltip />
            <el-table-column prop="platform" label="平台" width="80" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { reportApi } from '@/api'
import * as echarts from 'echarts'
import { CHART } from '@/utils/chartTheme'

const loading = ref(false)
const data = ref({})
const checkedAt = ref('-')
const trendRef = ref()
let trendChart
let timer

const passClass = computed(() => {
  const ok = data.value.pass_rate_ok !== false
  return ok ? 'text-green' : 'text-red'
})

async function load() {
  loading.value = true
  try {
    const res = await reportApi.wallboard()
    data.value = res.data
    checkedAt.value = new Date().toLocaleString('zh-CN')
    renderTrend()
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendRef.value) return
  trendChart?.dispose()
  trendChart = echarts.init(trendRef.value)
  const trend = data.value.pass_rate_trend || []
  const dark = CHART.dark
  trendChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: trend.map(t => `#${t.task_id}`),
      axisLabel: { color: dark.axis },
      axisLine: { lineStyle: { color: dark.grid } }
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      axisLabel: { color: dark.axis, formatter: '{value}%' },
      splitLine: { lineStyle: { color: dark.grid } }
    },
    series: [{
      type: 'line', smooth: true, data: trend.map(t => t.pass_rate),
      areaStyle: { color: dark.passRateArea[0] },
      lineStyle: { color: CHART.passRateLine, width: 3 },
      itemStyle: { color: CHART.brand[1] }
    }]
  })
}

onMounted(() => {
  load()
  timer = setInterval(load, 5000)
  window.addEventListener('resize', () => trendChart?.resize())
})

onUnmounted(() => {
  clearInterval(timer)
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.wallboard {
  min-height: 100vh;
  background: var(--atp-dark-bg);
  color: var(--atp-screen-text);
  padding: 24px 28px 32px;
}

.wb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;

  h1 {
    font-size: 28px;
    font-weight: 700;
    margin: 0 0 6px;
    letter-spacing: 0.02em;
    color: var(--atp-screen-text);
  }
}

.wb-sub { color: var(--atp-screen-text-muted); margin: 0; font-size: 14px; }
.wb-meta { display: flex; align-items: center; gap: 12px; color: var(--atp-screen-text-muted); font-size: 13px; }

.wb-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.wb-card {
  background: rgba(45, 42, 74, 0.55);
  border: 1px solid var(--atp-dark-border);
  border-radius: var(--atp-radius);
  padding: 18px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, var(--atp-brand-400), var(--atp-primary));
  }
}

.wb-card--hero {
  grid-column: span 2;
  background: linear-gradient(135deg, rgba(45, 42, 74, 0.8) 0%, rgba(28, 25, 50, 0.95) 100%);
  border-color: var(--atp-brand-600);

  &::before {
    height: 4px;
    background: linear-gradient(90deg, var(--atp-brand-400), var(--atp-primary), var(--atp-brand-400));
  }
}

.wb-label { font-size: 13px; color: var(--atp-screen-text-muted); margin-bottom: 8px; }
.wb-value { font-size: 36px; font-weight: 700; line-height: 1.1; color: var(--atp-screen-text); }
.wb-foot { margin-top: 8px; font-size: 12px; color: var(--atp-screen-text-muted); }

.text-green { color: var(--atp-success); }
.text-amber { color: var(--atp-warning); }
.text-blue { color: var(--atp-info); }
.text-red { color: var(--atp-danger); }

.wb-panel {
  background: rgba(45, 42, 74, 0.55);
  border: 1px solid var(--atp-dark-border);
  border-radius: var(--atp-radius);
  padding: 16px 18px;
  min-height: 320px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);

  h3 { margin: 0 0 12px; font-size: 15px; color: var(--atp-screen-text); }
}

.chart-area { height: 260px; }

.wb-table {
  background: transparent;
  :deep(.el-table) {
    --el-table-bg-color: transparent;
    --el-table-tr-bg-color: transparent;
    --el-table-header-bg-color: rgba(28, 25, 50, 0.6);
    --el-table-row-hover-bg-color: rgba(56, 189, 248, 0.12);
    --el-table-border-color: var(--atp-dark-border);
    --el-table-text-color: var(--atp-screen-text);
    --el-table-header-text-color: var(--atp-screen-text-muted);
  }
  :deep(.el-table__row:nth-child(even) td.el-table__cell) { background: rgba(28, 25, 50, 0.35); }
  :deep(.el-table__row:nth-child(odd) td.el-table__cell) { background: transparent; }
}

@media (max-width: 1200px) {
  .wb-grid { grid-template-columns: repeat(3, 1fr); }
  .wb-card--hero { grid-column: span 3; }
}
</style>
