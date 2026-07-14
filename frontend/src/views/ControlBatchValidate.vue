<template>
  <div class="page-container">
    <PageHeader title="控件批量校验" subtitle="按应用/页面/版本/环境批量校验控件池，输出失效与高风险清单">
      <template #actions>
        <el-button @click="$router.push('/controls')">返回控件池</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <el-form :model="form" inline size="small" class="filter-form">
        <el-form-item label="设备序列号" required>
          <el-input v-model="form.serial_number" placeholder="adb devices 中的序列号" style="width:200px" />
        </el-form-item>
        <el-form-item label="应用包名">
          <el-input v-model="form.app_package" placeholder="com.example.app" style="width:200px" clearable />
        </el-form-item>
        <el-form-item label="页面">
          <el-input v-model="form.page_name" style="width:140px" clearable />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version_tag" style="width:120px" clearable />
        </el-form-item>
        <el-form-item label="环境">
          <el-input v-model="form.env_tag" style="width:120px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="running" @click="runBatch">开始校验</el-button>
        </el-form-item>
      </el-form>

      <div v-if="unstableTop.length" class="unstable-block">
        <div class="block-title">近 30 天高频变更控件（置顶）</div>
        <el-table :data="unstableTop" size="small" stripe>
          <el-table-column prop="element_name" label="控件" min-width="140" />
          <el-table-column prop="app_package" label="包名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="change_count" label="变更次数" width="100" />
          <el-table-column prop="version_tag" label="版本" width="100" />
          <el-table-column prop="env_tag" label="环境" width="100" />
        </el-table>
      </div>

      <div v-if="summary" class="summary-row">
        <el-tag type="info">合计 {{ summary.total }}</el-tag>
        <el-tag type="success">通过 {{ summary.passed }}</el-tag>
        <el-tag type="danger">失败 {{ summary.failed }}</el-tag>
        <el-tag v-if="summary.archived" type="warning">自动归档 {{ summary.archived }}</el-tag>
      </div>

      <el-table v-if="results.length" :data="results" v-loading="running" stripe style="margin-top:12px">
        <el-table-column prop="element_name" label="控件" min-width="140" />
        <el-table-column prop="page_name" label="页面" width="120" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.valid ? 'success' : 'danger'" size="small">{{ row.valid ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matched_by" label="命中" width="100" />
        <el-table-column prop="error" label="原因" min-width="120" show-overflow-tooltip />
        <el-table-column prop="fail_streak" label="连续失败" width="90" />
        <el-table-column label="归档" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.archived" type="warning" size="small">已归档</el-tag>
            <el-tag v-else-if="row.suggest_archive" type="danger" size="small" effect="plain">建议归档</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="risk_level" label="风险" width="80" />
      </el-table>
      <el-empty v-else-if="!running" description="填写设备序列号与筛选条件后执行批量校验" />
    </AppCard>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { controlApi } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const running = ref(false)
const results = ref([])
const unstableTop = ref([])
const summary = ref(null)

const form = reactive({
  serial_number: '',
  app_package: '',
  page_name: '',
  version_tag: '',
  env_tag: '',
  platform: 'android'
})

async function loadUnstable() {
  try {
    const res = await controlApi.unstableStats({ days: 30, limit: 10 })
    unstableTop.value = res.data || []
  } catch {
    unstableTop.value = []
  }
}

async function runBatch() {
  if (!form.serial_number?.trim()) {
    ElMessage.warning('请填写设备序列号')
    return
  }
  running.value = true
  results.value = []
  summary.value = null
  try {
    const res = await controlApi.batchValidate({ ...form })
    const data = res.data || {}
    results.value = data.results || []
    unstableTop.value = data.unstable_top || unstableTop.value
    summary.value = {
      total: data.total || 0,
      passed: data.passed || 0,
      failed: data.failed || 0,
      archived: data.archived || 0
    }
    ElMessage.success(`校验完成：${data.passed || 0}/${data.total || 0} 通过`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '批量校验失败')
  } finally {
    running.value = false
  }
}

onMounted(() => {
  if (route.query.app_package) form.app_package = String(route.query.app_package)
  if (route.query.version_tag) form.version_tag = String(route.query.version_tag)
  if (route.query.env_tag) form.env_tag = String(route.query.env_tag)
  loadUnstable()
})
</script>

<style scoped>
.filter-form { margin-bottom: 12px; }
.unstable-block { margin: 12px 0; }
.block-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.summary-row { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
</style>
