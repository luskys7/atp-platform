<template>
  <div class="page-container">
    <PageHeader title="CI/CD 联动" subtitle="Jenkins Webhook 与自动化触发配置" />

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="14">
        <AppCard title="Jenkins Webhook 配置" :hover="false">
          <el-form :model="config" label-width="140px">
            <el-form-item label="启用 CI 集成">
              <el-switch v-model="config.enabled" />
            </el-form-item>
            <el-form-item label="Webhook Token">
              <el-input v-model="config.webhook_token" readonly>
                <template #append>
                  <el-button @click="regenerateToken">重新生成</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="Webhook URL">
              <el-input :model-value="webhookUrl" readonly>
                <template #append>
                  <el-button @click="copyWebhook">复制</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="自动提交任务">
              <el-switch v-model="config.auto_submit" />
            </el-form-item>
            <el-divider>默认任务模板</el-divider>
            <el-form-item label="默认平台">
              <el-select v-model="config.default_platform" style="width:100%">
                <el-option label="Android" value="android" />
                <el-option label="iOS" value="ios" />
                <el-option label="双端" value="both" />
              </el-select>
            </el-form-item>
            <el-form-item label="应用包名">
              <el-input v-model="config.default_app_package" />
            </el-form-item>
            <el-form-item label="默认脚本">
              <el-input v-model="config.default_script_content" type="textarea" :rows="6" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig">保存配置</el-button>
              <el-button class="btn-accent-entry" @click="showAdvanced = !showAdvanced">
                {{ showAdvanced ? '收起高级配置' : '高级配置' }}
              </el-button>
            </el-form-item>
            <el-collapse-transition>
              <div v-show="showAdvanced" class="ci-advanced">
                <div class="section-title">流水线高级选项</div>
                <p class="ci-advanced-hint">Webhook Token 与默认任务模板可在 Jenkins Pipeline 中引用，用于触发自动化回归。</p>
              </div>
            </el-collapse-transition>
          </el-form>
        </AppCard>

        <AppCard title="Jenkins 调用示例" :hover="false" style="margin-top:20px">
          <pre class="code-block">{{ curlExample }}</pre>
        </AppCard>
      </el-col>

      <el-col :span="10">
        <AppCard title="最近 CI 触发记录" :hover="false">
          <el-table :data="recentJobs" size="small" empty-text="暂无记录">
            <el-table-column prop="job_name" label="Job" min-width="120" show-overflow-tooltip />
            <el-table-column prop="build_number" label="Build" width="70" />
            <el-table-column prop="task_id" label="任务" width="70" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <span :class="{ 'tag-running-pulse': row.status === 'running' || row.status === 'building' }">
                  <el-tag size="small" effect="light" :type="ciStatusType(row.status)">{{ ciStatusLabel(row.status) }}</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ciApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const showAdvanced = ref(false)
const config = ref({
  enabled: true,
  webhook_token: '',
  default_platform: 'android',
  default_app_package: '',
  default_script_content: '',
  auto_submit: true
})
const recentJobs = ref([])

const webhookUrl = computed(() =>
  `${window.location.origin}/api/v1/ci/jenkins/webhook`)

const curlExample = computed(() => `curl -X POST ${webhookUrl.value} \\
  -H "Content-Type: application/json" \\
  -H "X-ATP-Webhook-Token: ${config.value.webhook_token || '<token>'}" \\
  -d '{
    "job_name": "app-ui-test",
    "build_number": "42",
    "callback_url": "http://jenkins:8080/callback",
    "task": {
      "case_id": 1,
      "env_id": 2,
      "wait_template": "smoke",
      "device_tags": "gray-a"
    }
  }'

# 触发整套套件：
# "task": { "suite_id": 3, "env_id": 2 }`)

async function loadData() {
  loading.value = true
  try {
    const [cfgRes, jobsRes] = await Promise.all([ciApi.getConfig(), ciApi.recentJobs()])
    config.value = cfgRes.data
    recentJobs.value = jobsRes.data
  } finally {
    loading.value = false
  }
}

function regenerateToken() {
  config.value.webhook_token = crypto.randomUUID().replace(/-/g, '')
}

async function saveConfig() {
  await ciApi.updateConfig(config.value)
  ElMessage.success('CI 配置已保存')
  loadData()
}

function copyWebhook() {
  navigator.clipboard.writeText(webhookUrl.value)
  ElMessage.success('Webhook URL 已复制')
}

const ciStatusLabels = {
  success: '成功',
  failed: '失败',
  running: '构建中',
  building: '构建中',
  pending: '等待中',
  connected: '已连接',
  unconfigured: '未配置'
}

function ciStatusLabel(s) {
  return ciStatusLabels[s] || s || '-'
}

function ciStatusType(s) {
  if (s === 'success' || s === 'connected') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'running' || s === 'building') return 'primary'
  if (s === 'unconfigured') return 'info'
  return 'info'
}

onMounted(loadData)
</script>

<style scoped>
.code-block {
  background: var(--atp-code-bg);
  color: var(--atp-screen-text);
  padding: 20px;
  border-radius: var(--atp-radius);
  font-size: 12px;
  overflow-x: auto;
  line-height: 1.6;
  margin: 0;
}
.ci-advanced {
  margin-top: 8px;
  padding: 12px 0 4px;
  border-top: 1px dashed var(--atp-border-neutral);
}
.ci-advanced-hint {
  margin: 0;
  font-size: 13px;
  color: var(--atp-gray-purple);
  line-height: 1.6;
}
</style>
