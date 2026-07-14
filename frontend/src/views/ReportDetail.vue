<template>
  <div class="page-container">
    <PageHeader :title="detail?.report?.title || '报告详情'" subtitle="测试执行结果与关联资源">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" @click="exportPDF">导出 PDF</el-button>
        <el-button type="success" @click="exportExcel">导出 Excel</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading">
      <el-row :gutter="20" v-if="detail">
        <el-col :span="6">
          <StatCard label="执行次数" :value="detail.report?.total_executions || 0" icon="DataLine" variant="primary" />
        </el-col>
        <el-col :span="6">
          <StatCard label="成功" :value="detail.report?.success_count || 0" icon="CircleCheck" variant="success" />
        </el-col>
        <el-col :span="6">
          <StatCard label="失败" :value="detail.report?.failed_count || 0" icon="CircleClose" variant="danger" />
        </el-col>
        <el-col :span="6">
          <StatCard label="通过率" icon="TrendCharts" :variant="passRateVariant">
            <span :class="passRateClass">{{ (detail.report?.pass_rate || 0).toFixed(1) }}%</span>
          </StatCard>
        </el-col>

        <el-col :span="24" style="margin-top:20px" v-if="detail.report?.summary">
          <AppCard :hover="false">
            <p class="report-summary">{{ detail.report.summary }}</p>
          </AppCard>
        </el-col>

        <el-col :span="12" style="margin-top:20px">
          <AppCard title="执行实例" :hover="false">
            <el-table :data="detail.executions" size="small" stripe>
              <el-table-column prop="device_id" label="设备" width="80" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small" effect="light">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="result_summary" label="结果" show-overflow-tooltip />
            </el-table>
          </AppCard>
        </el-col>

        <el-col :span="12" style="margin-top:20px">
          <AppCard title="关联录屏" :hover="false">
            <el-table :data="detail.recordings" size="small" stripe>
              <el-table-column prop="file_name" label="文件名" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="light">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </AppCard>
        </el-col>

        <el-col :span="24" style="margin-top:20px">
          <AppCard title="执行日志" :hover="false">
            <el-timeline>
              <el-timeline-item
                v-for="log in detail.logs"
                :key="log.id"
                :type="log.level === 'error' ? 'danger' : 'primary'"
                :timestamp="fmtTime(log.created_at)"
              >
                <template v-if="log.level === 'error'">
                  <div class="log-error-block" v-html="formatErrorLog(log.message)" />
                </template>
                <template v-else>
                  [{{ log.log_type }}/{{ log.level }}] {{ log.message }}
                </template>
              </el-timeline-item>
            </el-timeline>
          </AppCard>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { reportApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage } from 'element-plus'

const route = useRoute()
const taskId = route.params.taskId
const loading = ref(false)
const detail = ref(null)

const passRateVariant = computed(() => {
  const rate = detail.value?.report?.pass_rate || 0
  if (rate >= 99) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
})

const passRateClass = computed(() => {
  const rate = detail.value?.report?.pass_rate || 0
  return rate >= 99 ? 'pass-rate-lg' : 'pass-rate-fail'
})

function formatErrorLog(message) {
  if (!message) return ''
  const escaped = message
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/\b(ERROR|FAIL|Failed|Exception|失败|错误)\b/gi, '<span class="fail-keyword">$1</span>')
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await reportApi.detail(taskId)
    detail.value = res.data
  } finally {
    loading.value = false
  }
}

async function exportPDF() {
  await reportApi.exportPDF(taskId)
  ElMessage.success('PDF 报告已下载')
}

async function exportExcel() {
  await reportApi.exportExcel(taskId)
  ElMessage.success('Excel 报告已下载')
}

onMounted(loadDetail)
</script>

<style scoped>
.report-summary {
  margin: 0;
  color: var(--atp-text-secondary);
  line-height: 1.7;
}
</style>
