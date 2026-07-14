<template>
  <div class="page-container page-overview">
    <PageHeader title="首页概览" subtitle="平台运行状态与测试质量一览" />

    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="设备总数" :value="stats.devices?.total || 0" icon="Iphone" variant="primary" />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="在线设备" :value="stats.devices?.online || 0" icon="CircleCheck" variant="success" />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="运行中任务" :value="stats.tasks?.running || 0" icon="Loading" variant="warning" />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard :label="'通过率'" icon="TrendCharts" :variant="passRateVariant">
          {{ (stats.pass_rate || 0).toFixed(1) }}%
          <template #extra>
            <el-tag v-if="stats.pass_rate_ok === false" type="danger" size="small" style="margin-left:6px">
              &lt; {{ stats.min_pass_rate }}%
            </el-tag>
          </template>
        </StatCard>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:8px" v-if="coverage.total_cases != null">
      <el-col :xs="24" :sm="8" :lg="6">
        <StatCard label="用例总数" :value="coverage.total_cases || 0" icon="DocumentCopy" variant="primary" />
      </el-col>
      <el-col :xs="24" :sm="8" :lg="6">
        <StatCard label="已生效用例" :value="coverage.active_cases || 0" icon="CircleCheck" variant="success" />
      </el-col>
      <el-col :xs="24" :sm="8" :lg="6">
        <StatCard label="自动化覆盖率" :value="`${(coverage.automation_rate || 0).toFixed(1)}%`" icon="PieChart" variant="warning" />
      </el-col>
      <el-col :xs="24" :sm="8" :lg="6">
        <StatCard label="待评审" :value="coverage.review_cases || 0" icon="Clock" />
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:8px">
      <el-col :xs="24" :lg="12">
        <AppCard title="继续录制">
          <div v-if="recordingActive" class="continue-rec">
            <p>设备 <strong>{{ recordingDeviceLabel }}</strong> 正在录制中</p>
            <el-button type="primary" @click="goContinueRecording">返回投屏继续</el-button>
          </div>
          <div v-else-if="lastRecordDeviceId" class="continue-rec">
            <p>上次录制设备 ID：<strong>{{ lastRecordDeviceId }}</strong></p>
            <el-button type="warning" @click="goQuickRecord">一键继续录制</el-button>
          </div>
          <el-empty v-else description="暂无录制记录，请从设备投屏页开始录制" />
        </AppCard>
      </el-col>
      <el-col :xs="24" :lg="12">
        <AppCard title="录制质量审计" v-loading="auditLoading">
          <div v-if="audit.sample_count" class="audit-summary">
            <div class="audit-row">
              <span>样本数</span><strong>{{ audit.sample_count }}</strong>
            </div>
            <div v-if="audit.avg_recognition_rate != null" class="audit-row">
              <span>平均识别率</span>
              <el-tag :type="audit.avg_recognition_rate >= 95 ? 'success' : 'warning'">{{ audit.avg_recognition_rate }}%</el-tag>
            </div>
            <div v-if="audit.avg_startup_ms != null" class="audit-row">
              <span>平均启动耗时</span>
              <el-tag :type="audit.avg_startup_ms <= 3000 ? 'success' : 'warning'">{{ audit.avg_startup_ms }} ms</el-tag>
            </div>
            <div v-if="audit.cpu_ok_rate != null" class="audit-row">
              <span>CPU 达标率</span>
              <el-tag :type="audit.cpu_ok_rate >= 80 ? 'success' : 'warning'">{{ audit.cpu_ok_rate }}%</el-tag>
            </div>
            <div v-if="audit.sessions_below_threshold?.length" class="audit-warn">
              {{ audit.sessions_below_threshold.length }} 个会话识别率低于 95%
            </div>
            <el-button size="small" type="primary" link @click="$router.push('/recording-quality')">查看完整质量报告</el-button>
          </div>
          <el-empty v-else description="暂无已完成录制会话" />
        </AppCard>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:8px">
      <el-col :xs="24" :lg="12">
        <AppCard title="设备状态分布">
          <div ref="deviceChartRef" class="chart-box"></div>
        </AppCard>
      </el-col>
      <el-col :xs="24" :lg="12">
        <AppCard title="任务执行统计">
          <div ref="taskChartRef" class="chart-box"></div>
        </AppCard>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="24">
        <AppCard title="通过率趋势">
          <div ref="passRateChartRef" class="chart-box chart-box--wide"></div>
        </AppCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi, recordApi } from '@/api'
import { operationRecordingState } from '@/composables/useOperationRecording'
import { markRecordingBoot } from '@/composables/useRecordingStartup'
import * as echarts from 'echarts'
import { CHART } from '@/utils/chartTheme'

const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'
const router = useRouter()

const loading = ref(false)
const auditLoading = ref(false)
const stats = ref({})
const coverage = ref({})
const audit = ref({})
const deviceChartRef = ref()
const taskChartRef = ref()
const passRateChartRef = ref()
let deviceChart, taskChart, passRateChart

const recordingActive = computed(() => operationRecordingState.active)
const recordingDeviceLabel = computed(() =>
  operationRecordingState.deviceName || `ID ${operationRecordingState.deviceId}`
)
const lastRecordDeviceId = ref(localStorage.getItem(LAST_RECORD_DEVICE_KEY) || '')

const passRateVariant = computed(() => {
  const rate = stats.value.pass_rate || 0
  const min = stats.value.min_pass_rate || 99
  if (rate >= min) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
})

const chartColors = CHART.device

async function loadStats() {
  loading.value = true
  auditLoading.value = true
  try {
    const [dash, cov, auditRes] = await Promise.all([
      reportApi.dashboard(),
      reportApi.coverage().catch(() => ({ data: {} })),
      recordApi.auditSummary(10).catch(() => ({ data: {} }))
    ])
    stats.value = dash.data
    coverage.value = cov.data || {}
    audit.value = auditRes.data || {}
    lastRecordDeviceId.value = localStorage.getItem(LAST_RECORD_DEVICE_KEY) || ''
    renderCharts()
  } finally {
    loading.value = false
    auditLoading.value = false
  }
}

function goContinueRecording() {
  if (operationRecordingState.deviceId) {
    router.push(`/devices/${operationRecordingState.deviceId}/screen`)
  }
}

function goQuickRecord() {
  markRecordingBoot()
  if (lastRecordDeviceId.value) {
    router.push(`/devices/${lastRecordDeviceId.value}/screen?auto_record=1`)
  }
}

function renderCharts() {
  if (deviceChartRef.value) {
    deviceChart?.dispose()
    deviceChart = echarts.init(deviceChartRef.value)
    deviceChart.setOption({
      color: chartColors,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c}' },
        data: [
          { name: '在线', value: stats.value.devices?.online || 0 },
          { name: '忙碌', value: stats.value.devices?.busy || 0 },
          { name: '离线', value: Math.max(0, (stats.value.devices?.total || 0) - (stats.value.devices?.online || 0) - (stats.value.devices?.busy || 0)) }
        ]
      }]
    })
  }

  if (taskChartRef.value) {
    taskChart?.dispose()
    taskChart = echarts.init(taskChartRef.value)
    taskChart.setOption({
      color: CHART.task,
      tooltip: { trigger: 'axis' },
      grid: { left: 48, right: 24, top: 24, bottom: 32 },
      xAxis: { type: 'category', data: ['成功', '失败', '运行中', '总计'], axisLine: { lineStyle: { color: CHART.grid } } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: CHART.grid } } },
      series: [{
        type: 'bar',
        barWidth: 40,
        itemStyle: { borderRadius: [6, 6, 0, 0] },
        data: [
          stats.value.tasks?.success || 0,
          stats.value.tasks?.failed || 0,
          stats.value.tasks?.running || 0,
          stats.value.tasks?.total || 0
        ]
      }]
    })
  }

  if (passRateChartRef.value) {
    const trend = stats.value.pass_rate_trend || []
    const minRate = stats.value.min_pass_rate || 99
    passRateChart?.dispose()
    passRateChart = echarts.init(passRateChartRef.value)
    passRateChart.setOption({
      color: [CHART.passRateLine],
      tooltip: { trigger: 'axis', formatter: '{b}<br/>通过率: {c}%' },
      grid: { left: 48, right: 24, top: 32, bottom: 48 },
      xAxis: {
        type: 'category',
        data: trend.map(t => `#${t.task_id}`),
        axisLabel: { rotate: 30, color: CHART.axis }
      },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
          { offset: 0, color: CHART.passRateArea[0] },
          { offset: 1, color: CHART.passRateArea[1] }
        ]}},
        data: trend.map(t => t.pass_rate),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: CHART.threshold, type: 'dashed' },
          data: [{ yAxis: minRate, label: { formatter: '阈值 {c}%', position: 'end' } }]
        }
      }]
    })
  }
}

function handleResize() {
  deviceChart?.resize()
  taskChart?.resize()
  passRateChart?.resize()
}

onMounted(() => {
  loadStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  deviceChart?.dispose()
  taskChart?.dispose()
  passRateChart?.dispose()
})
</script>

<style scoped>
.chart-box {
  height: 300px;
}
.chart-box--wide {
  height: 280px;
}
.continue-rec {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}
.continue-rec p {
  margin: 0;
  color: var(--atp-text-muted);
}
.audit-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.audit-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}
.audit-warn {
  margin-top: 4px;
  font-size: 13px;
  color: #d97706;
}
</style>
