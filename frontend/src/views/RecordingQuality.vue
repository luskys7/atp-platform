<template>
  <div class="page-container">
    <PageHeader title="录制质量审计" subtitle="识别率、定位命中率与 CPU 性能采样报告">
      <template #actions>
        <el-select v-model="sampleLimit" style="width:120px" @change="loadAudit">
          <el-option :value="10" label="10 样本" />
          <el-option :value="20" label="20 样本" />
          <el-option :value="50" label="50 样本" />
        </el-select>
        <el-button @click="exportReport"><el-icon><Download /></el-icon> 导出报告</el-button>
        <el-button :loading="loading" @click="loadAudit"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </template>
    </PageHeader>

    <AppCard v-if="p0Acceptance" title="P0 验收门禁" :hover="false" style="margin-bottom:16px">
      <el-result
        :icon="p0Acceptance.overall_passed ? 'success' : 'warning'"
        :title="p0Acceptance.overall_passed ? 'P0 指标全部达标' : 'P0 指标存在未达标项'"
      >
        <template #sub-title>
          <el-space wrap>
            <el-tag :type="audit.recognition_passed ? 'success' : 'danger'">识别率 {{ audit.recognition_passed ? '达标' : '未达标' }}</el-tag>
            <el-tag :type="p0Acceptance.startup_passed ? 'success' : 'danger'">启动 ≤3s {{ p0Acceptance.startup_passed ? '达标' : '未达标' }}</el-tag>
            <el-tag :type="p0Acceptance.cpu_passed ? 'success' : 'danger'">CPU 采样 {{ p0Acceptance.cpu_passed ? '达标' : '未达标' }}</el-tag>
          </el-space>
        </template>
        <template #extra>
          <el-descriptions :column="2" border size="small" style="max-width:720px;margin:0 auto">
            <el-descriptions-item label="最大启动耗时">{{ p0Acceptance.max_startup_ms != null ? `${p0Acceptance.max_startup_ms} ms` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="超 3s 会话">{{ audit.sessions_over_startup?.length || 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="≥5min 长录制">{{ audit.long_session_count || 0 }} 个</el-descriptions-item>
            <el-descriptions-item label="未通过项">{{ (p0Acceptance.failed_checks || []).join(', ') || '无' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
    </AppCard>

    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="审计样本" :value="audit.sample_count || 0" icon="Document" variant="primary" />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="平均识别率" icon="TrendCharts" :variant="recognitionVariant">
          {{ audit.avg_recognition_rate != null ? `${audit.avg_recognition_rate}%` : '-' }}
        </StatCard>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="平均启动耗时" icon="Timer" :variant="startupVariant">
          {{ audit.avg_startup_ms != null ? `${audit.avg_startup_ms} ms` : '-' }}
        </StatCard>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatCard label="CPU 达标率" icon="Cpu" :variant="cpuVariant">
          {{ audit.cpu_ok_rate != null ? `${audit.cpu_ok_rate}%` : '-' }}
        </StatCard>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :xs="24" :lg="10">
        <AppCard title="性能等级分布" :hover="false">
          <div ref="gradeChartRef" class="chart-box" />
          <el-empty v-if="!gradeChartData.length" description="暂无性能采样数据" />
        </AppCard>
      </el-col>
      <el-col :xs="24" :lg="14">
        <AppCard title="验收阈值" :hover="false">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="识别率阈值">
              ≥ {{ audit.min_recognition_rate ?? 95 }}%
              <el-tag size="small" :type="audit.recognition_passed ? 'success' : 'warning'" style="margin-left:8px">
                {{ audit.recognition_passed ? '达标' : '未达标' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="定位命中率阈值">≥ {{ audit.min_locator_hit_rate ?? 98 }}%（审阅页可抽检）</el-descriptions-item>
            <el-descriptions-item label="CPU 性能">longtask 采样，good/fair/heavy 三级；CPU 达标率 ≥ 80%</el-descriptions-item>
            <el-descriptions-item label="启动耗时">P0 要求 ≤ 3000ms（FAB→开录）</el-descriptions-item>
            <el-descriptions-item label="低于识别率阈值">
              {{ audit.sessions_below_threshold?.length || 0 }} 个会话
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="audit.sessions_below_threshold?.length"
            type="warning"
            :closable="false"
            show-icon
            style="margin-top:12px"
            title="以下会话识别率低于阈值"
          >
            <span v-for="s in audit.sessions_below_threshold" :key="s.id" class="warn-link" @click="goReview(s.id)">
              #{{ s.id }} ({{ s.recognition_rate }}%)
            </span>
          </el-alert>
        </AppCard>
      </el-col>
    </el-row>

    <AppCard title="会话明细" :hover="false" style="margin-top:16px">
      <el-table :data="audit.sessions || []" stripe size="small">
        <el-table-column prop="id" label="会话" width="80" />
        <el-table-column prop="module_name" label="模块" min-width="120" />
        <el-table-column prop="task_id" label="任务" width="80">
          <template #default="{ row }">
            <router-link v-if="row.task_id" :to="`/tasks/${row.task_id}`">{{ row.task_id }}</router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="step_count" label="步骤" width="70" />
        <el-table-column label="识别率" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.recognition_rate != null" size="small" :type="row.recognition_rate >= (audit.min_recognition_rate || 95) ? 'success' : 'warning'">
              {{ row.recognition_rate }}%
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="定位命中" width="100">
          <template #default="{ row }">
            {{ row.locator_hit_rate != null ? `${row.locator_hit_rate}%` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="性能" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.performance_grade" size="small" :type="gradeType(row.performance_grade)">
              {{ gradeLabel(row.performance_grade) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="启动" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.startup_ms != null" size="small" :type="row.startup_ms <= 3000 ? 'success' : 'warning'">
              {{ row.startup_ms }}ms
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="80">
          <template #default="{ row }">{{ row.duration_seconds != null ? `${row.duration_seconds}s` : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="goReview(row.id)">审阅</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { recordApi } from '@/api'
import * as echarts from 'echarts'
import { CHART } from '@/utils/chartTheme'

const router = useRouter()
const loading = ref(false)
const sampleLimit = ref(10)
const audit = ref({})
const gradeChartRef = ref()
let gradeChart = null

const p0Acceptance = computed(() => audit.value.p0_acceptance || null)

const gradeChartData = computed(() => {
  const counts = audit.value.performance_grade_counts || {}
  return Object.entries(counts).filter(([, v]) => v > 0).map(([k, v]) => ({ name: gradeLabel(k), value: v }))
})

const recognitionVariant = computed(() => {
  const rate = audit.value.avg_recognition_rate
  const min = audit.value.min_recognition_rate || 95
  if (rate == null) return 'primary'
  return rate >= min ? 'success' : 'warning'
})

const startupVariant = computed(() => {
  const ms = audit.value.avg_startup_ms
  if (ms == null) return 'primary'
  return ms <= 3000 ? 'success' : 'warning'
})

const cpuVariant = computed(() => {
  const rate = audit.value.cpu_ok_rate
  if (rate == null) return 'primary'
  return rate >= 80 ? 'success' : 'warning'
})

function gradeLabel(g) {
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
}

function gradeType(g) {
  return { good: 'success', fair: 'warning', heavy: 'danger' }[g] || 'info'
}

function goReview(id) {
  router.push(`/recordings/review/${id}`)
}

function exportReport() {
  const blob = new Blob([JSON.stringify(audit.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `recording-p0-audit-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function loadAudit() {
  loading.value = true
  try {
    const res = await recordApi.auditSummary(sampleLimit.value)
    audit.value = res.data || {}
    renderGradeChart()
  } finally {
    loading.value = false
  }
}

function renderGradeChart() {
  if (!gradeChartRef.value) return
  gradeChart?.dispose()
  gradeChart = echarts.init(gradeChartRef.value)
  const data = gradeChartData.value
  if (!data.length) return
  gradeChart.setOption({
    color: CHART.device,
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      data,
      label: { formatter: '{b}: {c}' }
    }]
  })
}

watch(gradeChartData, () => renderGradeChart())

onMounted(loadAudit)
onUnmounted(() => gradeChart?.dispose())
</script>

<style scoped>
.chart-box {
  height: 260px;
}
.warn-link {
  display: inline-block;
  margin-right: 12px;
  color: var(--el-color-warning);
  cursor: pointer;
  text-decoration: underline;
}
</style>
