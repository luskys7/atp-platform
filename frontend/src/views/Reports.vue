<template>
  <div class="page-container">
    <PageHeader title="测试报告" subtitle="查看测试执行结果与通过率" />

    <AppCard :hover="false">
      <el-table :data="reports" v-loading="loading" stripe>
        <el-table-column prop="title" label="报告标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="task_id" label="任务" width="80" />
        <el-table-column prop="total_executions" label="执行" width="70" align="center" />
        <el-table-column prop="success_count" label="成功" width="70" align="center">
          <template #default="{ row }">
            <span class="text-success">{{ row.success_count }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failed_count" label="失败" width="70" align="center">
          <template #default="{ row }">
            <span class="text-danger">{{ row.failed_count }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pass_rate" label="通过率" width="110">
          <template #default="{ row }">
            <el-progress
              :percentage="row.pass_rate || 0"
              :color="passRateColor(row.pass_rate)"
              :stroke-width="8"
              :show-text="true"
              :format="(p) => p.toFixed(1) + '%'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="生成时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="$router.push(`/reports/${row.task_id}`)">查看</el-button>
            <el-button size="small" type="success" plain @click="exportPDF(row)">PDF</el-button>
            <el-button size="small" type="success" plain @click="exportExcel(row)">Excel</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:20px;justify-content:flex-end"
        @change="loadReports"
      />
    </AppCard>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { reportApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { passRateColor as chartPassRateColor } from '@/utils/chartTheme'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const reports = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function passRateColor(rate) {
  return chartPassRateColor(rate, 99)
}

async function loadReports() {
  loading.value = true
  try {
    const res = await reportApi.list({ page: page.value, page_size: pageSize.value })
    reports.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function exportPDF(row) {
  await reportApi.exportPDF(row.task_id)
  ElMessage.success('PDF 报告已下载')
}

async function exportExcel(row) {
  await reportApi.exportExcel(row.task_id)
  ElMessage.success('Excel 报告已下载')
}

onMounted(loadReports)
</script>

<style scoped>
.text-success { color: var(--atp-success); font-weight: 600; }
.text-danger { color: var(--atp-danger); font-weight: 600; }
</style>
