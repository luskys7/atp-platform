<template>
  <div class="page-container ci-page">
    <PageHeader
      title="CI/CD 联动"
      subtitle="WebHook 持续集成联动，支持 Jenkins 构建完成后自动触发自动化回归任务"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">刷新触发记录</el-button>
        <el-button :loading="exporting" @click="exportLogs">导出触发日志</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="20" v-loading="loading">
      <!-- 模块 2：WebHook 集成配置 -->
      <el-col :xs="24" :lg="14">
        <AppCard title="WebHook 集成配置" :hover="false">
          <el-form :model="config" label-width="130px" class="ci-form">
            <el-form-item label="启用 CI 集成">
              <div class="field-inline">
                <el-switch v-model="config.enabled" />
                <span class="field-hint">关闭后拒绝所有外部 CI 平台推送请求</span>
              </div>
            </el-form-item>

            <el-form-item label="访问密钥">
              <div class="field-stack">
                <div class="input-with-actions">
                  <el-input
                    v-model="config.webhook_token"
                    type="password"
                    show-password
                    readonly
                    placeholder="访问密钥"
                  />
                  <el-button @click="copyToken">复制密钥</el-button>
                  <el-button @click="regenerateToken">重新生成</el-button>
                </div>
                <p class="field-tip">重新生成密钥后，Jenkins 内旧配置立即失效，请同步更新</p>
              </div>
            </el-form-item>

            <el-form-item label="回调地址">
              <div class="field-stack">
                <div class="input-with-actions">
                  <el-input :model-value="webhookUrl" readonly />
                  <el-button @click="copyWebhook">复制</el-button>
                </div>
                <p class="field-tip">在 Jenkins 构建后置脚本中调用该地址推送构建信息</p>
              </div>
            </el-form-item>

            <el-form-item label="自动提交任务">
              <div class="field-inline">
                <el-switch v-model="config.auto_submit" />
                <span class="field-hint">收到 CI 推送后自动创建自动化任务；关闭仅记录日志、不执行回归</span>
              </div>
            </el-form-item>

            <el-divider content-position="left">默认任务模板</el-divider>

            <el-form-item label="目标平台">
              <el-select v-model="config.default_platform" style="width:100%" placeholder="选择平台">
                <el-option label="Android" value="android" />
                <el-option label="iOS" value="ios" />
              </el-select>
            </el-form-item>

            <el-form-item label="应用包名">
              <el-input
                v-model="config.default_app_package"
                placeholder="填写被测应用唯一包标识，如 com.example.app"
              />
            </el-form-item>

            <el-form-item label="绑定测试套件">
              <el-select
                v-model="config.default_suite_id"
                clearable
                filterable
                style="width:100%"
                placeholder="CI 构建后自动执行的回归套件"
              >
                <el-option
                  v-for="s in suites"
                  :key="s.id"
                  :label="s.name"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="绑定版本基线">
              <el-select
                v-model="config.default_baseline_id"
                clearable
                filterable
                style="width:100%"
                placeholder="统一环境、参数配置的版本基线"
              >
                <el-option
                  v-for="b in baselines"
                  :key="b.id"
                  :label="baselineLabel(b)"
                  :value="b.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="执行环境">
              <el-select
                v-model="config.default_env_id"
                clearable
                filterable
                style="width:100%"
                placeholder="指定自动化运行环境"
              >
                <el-option
                  v-for="e in environments"
                  :key="e.id"
                  :label="e.name"
                  :value="e.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="自定义扩展脚本">
              <el-input
                v-model="config.default_script_content"
                type="textarea"
                :rows="5"
                placeholder="接收 CI 推送参数，支持自定义前置处理逻辑"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
              <el-button class="btn-accent-entry" @click="openAdvanced">高级配置</el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>

      <!-- 模块 3：最近 CI 触发记录 -->
      <el-col :xs="24" :lg="10">
        <AppCard title="最近 CI 触发记录" :hover="false">
          <el-table
            :data="recentJobs"
            size="small"
            empty-text="暂无 CI 触发记录，请完成 Jenkins 接入配置并发起代码构建"
          >
            <el-table-column prop="job_name" label="构建任务名称 (Job)" min-width="120" show-overflow-tooltip />
            <el-table-column prop="build_number" label="构建编号 (Build)" width="100" />
            <el-table-column label="绑定自动化任务" width="110">
              <template #default="{ row }">
                {{ boundTaskLabel(row) }}
              </template>
            </el-table-column>
            <el-table-column label="触发状态" width="120">
              <template #default="{ row }">
                <span :class="{ 'tag-running-pulse': isRunning(row.status) }">
                  <el-tag size="small" effect="light" :type="ciStatusType(row.status)">
                    {{ ciStatusLabel(row.status) }}
                  </el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="branch" label="代码分支" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.branch || '-' }}</template>
            </el-table-column>
            <el-table-column label="触发时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLogDialog(row)">查看日志</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 4：Jenkins 调用示例 -->
    <AppCard title="Jenkins 调用参考示例（Shell 脚本）" :hover="false" class="ci-section">
      <div class="code-toolbar">
        <el-button size="small" @click="copyCurl">复制示例</el-button>
      </div>
      <pre class="code-block">{{ curlExample }}</pre>
      <div class="param-note">
        <div class="param-note__title">必填参数说明</div>
        <ul class="param-list">
          <li><code>job_name</code>：Jenkins 构建任务名称（Job 名）</li>
          <li><code>build_num</code>：本次构建编号（Build Number）</li>
          <li><code>branch</code>：触发构建的代码分支（如 main、develop）</li>
          <li><code>download_url</code>：安装包下载地址，供自动化任务拉取被测包</li>
        </ul>
      </div>
    </AppCard>

    <!-- 模块 5：接入说明 -->
    <div class="ci-guide">
      <h3>CI/CD 接入完整使用说明</h3>

      <h4>1. 完整接入流程</h4>
      <ol>
        <li>开启「启用 CI 集成」，复制【回调地址】和【访问密钥】</li>
        <li>在 Jenkins 项目 → 构建后操作 / 后置 Shell 脚本，粘贴示例代码</li>
        <li>填入密钥、配置包地址、分支信息</li>
        <li>保存配置，推送代码触发构建，查看右侧【最近 CI 触发记录】验证连通性</li>
      </ol>

      <h4>2. 参数传递规则</h4>
      <p>Jenkins 推送的 <code>branch</code>、安装包地址可在自动化用例、全局脚本中通过变量直接读取。</p>

      <h4>3. 安全规范</h4>
      <ul>
        <li>访问密钥请勿对外泄露，泄露请立即点击「重新生成」；</li>
        <li>建议配置 IP 白名单，仅 Jenkins 服务器可调用接口；</li>
        <li>测试环境与生产环境使用两套独立密钥，避免跨环境误触发任务。</li>
      </ul>

      <h4>4. 常见故障排查</h4>
      <ul>
        <li>Jenkins 调用返回 403：密钥错误 / CI 集成开关关闭 / IP 不在白名单</li>
        <li>收到请求但未创建任务：「自动提交任务」开关关闭</li>
        <li>任务执行失败：检查默认模板套件、平台、包名配置是否有效</li>
      </ul>
    </div>

    <!-- 高级配置弹窗 -->
    <el-dialog v-model="showAdvanced" title="高级配置" width="560px" destroy-on-close>
      <el-form :model="advanced" label-width="140px">
        <el-form-item label="请求 IP 白名单">
          <el-input
            v-model="advanced.ip_whitelist"
            type="textarea"
            :rows="3"
            placeholder="仅允许指定 Jenkins 服务器 IP 发起调用，多个用逗号或换行分隔；留空不限制"
          />
        </el-form-item>
        <el-form-item label="分支允许规则">
          <el-input
            v-model="advanced.branch_allow"
            type="textarea"
            :rows="2"
            placeholder="仅允许触发的分支，如 main, release/*；留空不限制"
          />
        </el-form-item>
        <el-form-item label="分支禁止规则">
          <el-input
            v-model="advanced.branch_deny"
            type="textarea"
            :rows="2"
            placeholder="禁止触发的分支，如 feature/*, hotfix/*"
          />
        </el-form-item>
        <el-form-item label="任务并发上限">
          <el-input-number v-model="advanced.max_concurrent_tasks" :min="1" :max="100" />
          <span class="field-hint dialog-hint">同一 CI 链路最多同时运行几条自动化任务，防止设备池占满</span>
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="advanced.receive_retry_count" :min="0" :max="10" />
          <span class="field-hint dialog-hint">接收失败时平台重试次数</span>
        </el-form-item>
        <el-form-item label="消息接收超时">
          <el-input-number v-model="advanced.receive_timeout_seconds" :min="5" :max="600" />
          <span class="field-hint dialog-hint">秒</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdvanced = false">取消</el-button>
        <el-button type="primary" @click="applyAdvanced">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看日志弹窗 -->
    <el-dialog v-model="showLogDialog" title="触发日志详情" width="640px" destroy-on-close>
      <template v-if="logRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="构建任务">{{ logRow.job_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="构建编号">{{ logRow.build_number || '-' }}</el-descriptions-item>
          <el-descriptions-item label="代码分支">{{ logRow.branch || '-' }}</el-descriptions-item>
          <el-descriptions-item label="触发状态">{{ ciStatusLabel(logRow.status) }}</el-descriptions-item>
          <el-descriptions-item label="自动化任务">{{ boundTaskLabel(logRow) }}</el-descriptions-item>
          <el-descriptions-item label="安装包地址">{{ logRow.download_url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">
            <span :class="{ 'error-text': logRow.error_message }">{{ logRow.error_message || '无' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="触发时间">{{ fmtTime(logRow.created_at) }}</el-descriptions-item>
        </el-descriptions>
        <div class="log-payload">
          <div class="log-payload__title">完整入参</div>
          <pre class="code-block compact">{{ formatPayload(logRow.request_payload) }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ciApi, suiteApi, envApi, baselineApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const showAdvanced = ref(false)
const showLogDialog = ref(false)
const logRow = ref(null)

const config = ref({
  enabled: true,
  webhook_token: '',
  default_platform: 'android',
  default_app_package: '',
  default_script_content: '',
  auto_submit: true,
  ip_whitelist: '',
  branch_allow: '',
  branch_deny: '',
  max_concurrent_tasks: 3,
  receive_retry_count: 0,
  receive_timeout_seconds: 30,
  default_suite_id: null,
  default_baseline_id: null,
  default_env_id: null
})

const advanced = reactive({
  ip_whitelist: '',
  branch_allow: '',
  branch_deny: '',
  max_concurrent_tasks: 3,
  receive_retry_count: 0,
  receive_timeout_seconds: 30
})

const recentJobs = ref([])
const suites = ref([])
const environments = ref([])
const baselines = ref([])

const webhookUrl = computed(() =>
  `${window.location.origin}/api/v1/ci/jenkins/webhook`)

const curlExample = computed(() => `# TestFlow CI联动WebHook调用示例
curl --location --request POST '${webhookUrl.value}' \\
--header 'Token: 【此处粘贴你的访问密钥】' \\
--header 'Content-Type: application/json' \\
--data '{
    "job_name": "\${JOB_NAME}",
    "build_num": "\${BUILD_NUMBER}",
    "branch": "\${GIT_BRANCH}",
    "download_url": "安装包下载地址"
}'`)

function baselineLabel(b) {
  return b.name || b.version || b.title || `基线 #${b.id}`
}

function boundTaskLabel(row) {
  if (row.task_id) return `#${row.task_id}`
  if (row.suite_run_id) return `套件运行 #${row.suite_run_id}`
  return '-'
}

function isRunning(s) {
  return s === 'running' || s === 'building' || s === 'triggered'
}

const ciStatusLabels = {
  success: '✅执行成功',
  failed: '❌触发失败',
  callback_failed: '❌触发失败',
  running: '⏳任务运行中',
  building: '⏳任务运行中',
  triggered: '⏳任务运行中',
  pending: '⏳任务运行中',
  logged: '仅记录日志',
  connected: '✅执行成功',
  unconfigured: '❌触发失败'
}

function ciStatusLabel(s) {
  return ciStatusLabels[s] || s || '-'
}

function ciStatusType(s) {
  if (s === 'success' || s === 'connected') return 'success'
  if (s === 'failed' || s === 'callback_failed' || s === 'unconfigured') return 'danger'
  if (s === 'logged') return 'info'
  if (isRunning(s)) return 'warning'
  return 'info'
}

function formatPayload(raw) {
  if (!raw) return '（无入参记录）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function loadData() {
  loading.value = true
  try {
    const [cfgRes, jobsRes, suiteRes, envRes, baselineRes] = await Promise.all([
      ciApi.getConfig(),
      ciApi.recentJobs(),
      suiteApi.list().catch(() => ({ data: [] })),
      envApi.list().catch(() => ({ data: [] })),
      baselineApi.list().catch(() => ({ data: [] }))
    ])
    config.value = {
      ...config.value,
      ...cfgRes.data,
      default_suite_id: cfgRes.data?.default_suite_id ?? null,
      default_baseline_id: cfgRes.data?.default_baseline_id ?? null,
      default_env_id: cfgRes.data?.default_env_id ?? null
    }
    recentJobs.value = jobsRes.data || []
    suites.value = suiteRes.data || []
    const envData = envRes.data
    environments.value = Array.isArray(envData) ? envData : (envData?.list || [])
    const blData = baselineRes.data
    baselines.value = Array.isArray(blData) ? blData : (blData?.list || [])
  } finally {
    loading.value = false
  }
}

function syncAdvancedFromConfig() {
  advanced.ip_whitelist = config.value.ip_whitelist || ''
  advanced.branch_allow = config.value.branch_allow || ''
  advanced.branch_deny = config.value.branch_deny || ''
  advanced.max_concurrent_tasks = config.value.max_concurrent_tasks ?? 3
  advanced.receive_retry_count = config.value.receive_retry_count ?? 0
  advanced.receive_timeout_seconds = config.value.receive_timeout_seconds ?? 30
}

function openAdvanced() {
  syncAdvancedFromConfig()
  showAdvanced.value = true
}

function applyAdvanced() {
  config.value.ip_whitelist = advanced.ip_whitelist
  config.value.branch_allow = advanced.branch_allow
  config.value.branch_deny = advanced.branch_deny
  config.value.max_concurrent_tasks = advanced.max_concurrent_tasks
  config.value.receive_retry_count = advanced.receive_retry_count
  config.value.receive_timeout_seconds = advanced.receive_timeout_seconds
  showAdvanced.value = false
  ElMessage.success('高级配置已应用，请点击保存配置生效')
}

function regenerateToken() {
  config.value.webhook_token = crypto.randomUUID().replace(/-/g, '')
  ElMessage.warning('密钥已重新生成，保存后 Jenkins 旧配置将立即失效')
}

async function saveConfig() {
  saving.value = true
  try {
    await ciApi.updateConfig(config.value)
    ElMessage.success('CI 配置已保存')
    await loadData()
  } finally {
    saving.value = false
  }
}

async function copyText(text, okMsg) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(okMsg)
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function copyToken() {
  if (!config.value.webhook_token) {
    ElMessage.warning('暂无访问密钥')
    return
  }
  copyText(config.value.webhook_token, '访问密钥已复制')
}

function copyWebhook() {
  copyText(webhookUrl.value, '回调地址已复制')
}

function copyCurl() {
  const filled = curlExample.value.replace(
    '【此处粘贴你的访问密钥】',
    config.value.webhook_token || '【此处粘贴你的访问密钥】'
  )
  copyText(filled, '调用示例已复制')
}

async function exportLogs() {
  exporting.value = true
  try {
    const blob = await ciApi.exportJobs()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `ci-trigger-logs-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('触发日志已导出')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function openLogDialog(row) {
  logRow.value = row
  showLogDialog.value = true
}

onMounted(async () => {
  await loadData()
  syncAdvancedFromConfig()
})

watch(showAdvanced, (v) => {
  if (v) syncAdvancedFromConfig()
})
</script>

<style scoped>
.ci-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.field-inline {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.field-stack {
  width: 100%;
}

.input-with-actions {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}

.input-with-actions .el-input {
  flex: 1;
}

.field-hint {
  font-size: 12px;
  color: var(--atp-gray-purple, #8b8fa3);
  line-height: 1.5;
}

.dialog-hint {
  margin-left: 12px;
}

.field-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
  line-height: 1.5;
}

.ci-section {
  margin-top: 20px;
}

.code-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

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

.code-block.compact {
  padding: 12px 14px;
  max-height: 280px;
}

.param-note {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--atp-gray-purple, #8b8fa3);
  line-height: 1.6;
}

.param-note__title {
  font-weight: 600;
  color: #1f2a37;
  margin-bottom: 6px;
}

.param-list {
  margin: 0;
  padding-left: 1.2em;
}

.param-list li + li {
  margin-top: 4px;
}

.param-note code {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.04);
  font-size: 12px;
  color: #1f2a37;
}

.ci-guide {
  margin-top: 20px;
  padding: 20px 24px;
  border-radius: var(--atp-radius, 8px);
  background: #e8f3ff;
  color: #1f2a37;
  line-height: 1.7;
}

.ci-guide h3 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.ci-guide h4 {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.ci-guide ol,
.ci-guide ul {
  margin: 0;
  padding-left: 1.4em;
}

.ci-guide li + li {
  margin-top: 4px;
}

.ci-guide p {
  margin: 0;
}

.ci-guide code {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}

.log-payload {
  margin-top: 16px;
}

.log-payload__title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.error-text {
  color: var(--el-color-danger);
}
</style>
