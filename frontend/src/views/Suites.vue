<template>
  <div class="page-container">
    <PageHeader title="测试套件" subtitle="编排用例、执行策略与一键回归">
      <template #actions>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon> 新建套件</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <el-table :data="suites" v-loading="loading" stripe>
        <el-table-column prop="name" label="套件名称" min-width="200" />
        <el-table-column prop="tags" label="标签" width="140" show-overflow-tooltip />
        <el-table-column prop="exec_mode" label="执行模式" width="100">
          <template #default="{ row }">{{ row.exec_mode === 'parallel' ? '并行' : '串行' }}</template>
        </el-table-column>
        <el-table-column prop="fail_policy" label="失败策略" width="100">
          <template #default="{ row }">{{ row.fail_policy === 'stop' ? '终止' : '继续' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="success" plain @click="runSuite(row)">执行</el-button>
            <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteSuite(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>

    <AppCard v-if="suiteRuns.length" title="执行批次" :hover="false" style="margin-top:20px">
      <el-table :data="suiteRuns" size="small" stripe>
        <el-table-column prop="id" label="批次ID" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="runStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="success_count" label="成功" width="70" align="center" />
        <el-table-column prop="failed_count" label="失败" width="70" align="center" />
        <el-table-column prop="total_items" label="总数" width="70" align="center" />
        <el-table-column prop="started_at" label="开始时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="viewRun(row)">详情</el-button>
            <el-button v-if="row.status === 'running'" size="small" type="warning" plain @click="pauseRun(row)">暂停</el-button>
            <el-button v-if="row.status === 'failed' || row.status === 'paused'" size="small" type="warning" plain @click="resumeRun(row)">断点续跑</el-button>
            <el-button size="small" plain @click="restoreConfig(row)">还原配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </AppCard>

    <el-dialog v-model="showDialog" :title="form.id ? '编辑套件' : '新建套件'" width="820px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="冒烟,回归" /></el-form-item>
        <el-form-item label="执行模式">
          <el-radio-group v-model="form.exec_mode">
            <el-radio value="serial">串行</el-radio>
            <el-radio value="parallel">并行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="失败策略">
          <el-radio-group v-model="form.fail_policy">
            <el-radio value="continue_on_fail">失败继续</el-radio>
            <el-radio value="stop">失败终止</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="失败策略">
          <el-radio-group v-model="form.fail_policy">
            <el-radio value="continue_on_fail">失败继续</el-radio>
            <el-radio value="stop">失败终止</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-collapse v-model="hookHelpOpen" class="hook-help">
          <el-collapse-item title="套件钩子使用说明" name="help">
            <div class="hook-help-body">
              <p><strong>前置钩子</strong>：套件开始执行前运行，适用于环境初始化、登录、授权等。</p>
              <p><strong>后置钩子</strong>：全部用例执行完毕后运行，适用于退出登录、杀进程、清理缓存等。</p>
              <p>格式与可视化用例步骤相同，填写 JSON 对象，包含 <code>steps</code> 数组。留空表示不配置。</p>
              <p class="hook-types">支持步骤类型：<code>wait</code> 等待 · <code>click</code> 点击控件 · <code>tap_xy</code> 坐标点击 · <code>input</code> 输入 · <code>launch</code> 启动应用（自动异常检测） · <code>swipe</code> 滑动 · <code>assert_text</code> / <code>assert_exists</code> 断言 · <code>check_anomaly</code> 页面异常检测 · <code>assert_process</code> 进程存活 · <code>invoke_common</code> 调用公共步骤</p>
              <div class="hook-examples">
                <span>快速插入示例：</span>
                <el-button size="small" @click="fillHookExample('before', 'wait')">前置-等待</el-button>
                <el-button size="small" @click="fillHookExample('before', 'launch')">前置-启动应用</el-button>
                <el-button size="small" @click="fillHookExample('before', 'common')">前置-公共步骤</el-button>
                <el-button size="small" @click="fillHookExample('after', 'wait')">后置-等待</el-button>
                <el-button size="small" @click="fillHookExample('after', 'swipe')">后置-回到桌面</el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-form-item label="前置钩子">
          <el-input
            v-model="form.hook_before"
            type="textarea"
            :rows="4"
            placeholder='{"steps":[{"type":"wait","seconds":2},{"type":"launch","app_package":"com.example.app"}]}'
          />
        </el-form-item>
        <el-form-item label="后置钩子">
          <el-input
            v-model="form.hook_after"
            type="textarea"
            :rows="4"
            placeholder='{"steps":[{"type":"wait","seconds":1},{"type":"swipe","x1":500,"y1":800,"x2":500,"y2":400}]}'
          />
        </el-form-item>
        <el-form-item label="关联用例">
          <div style="width:100%">
            <el-select v-model="pickerCaseId" filterable placeholder="添加用例" style="width:100%;margin-bottom:8px" @change="addCase">
              <el-option v-for="c in availableCases" :key="c.id" :label="`${c.name} (${c.case_status})`" :value="c.id" />
            </el-select>
            <el-table :data="suiteItems" size="small" stripe>
              <el-table-column label="顺序" width="60" align="center">
                <template #default="{ $index }">
                  <el-button size="small" plain :disabled="$index === 0" @click="moveItem($index, -1)">↑</el-button>
                  <el-button size="small" plain :disabled="$index === suiteItems.length - 1" @click="moveItem($index, 1)">↓</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="case_name" label="用例" min-width="180" />
              <el-table-column prop="case_status" label="状态" width="90" />
              <el-table-column label="启用" width="80" align="center">
                <template #default="{ row }">
                  <el-switch v-model="row.enabled" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <el-button size="small" type="danger" plain @click="suiteItems.splice($index, 1)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSuite">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRunDialog" title="批次详情" width="720px">
      <el-table v-if="runDetail.items" :data="runDetail.items" size="small">
        <el-table-column prop="case_name" label="用例" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="failed_step_index" label="失败步骤" width="90" />
        <el-table-column prop="task_id" label="任务ID" width="80" />
        <el-table-column prop="error_message" label="错误" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { suiteApi, caseApi, checkpointApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const suites = ref([])
const allCases = ref([])
const suiteRuns = ref([])
const currentSuiteId = ref(null)
const showRunDialog = ref(false)
const runDetail = ref({ items: [] })
const showDialog = ref(false)
const suiteItems = ref([])
const pickerCaseId = ref(null)
const form = reactive({
  id: null, name: '', tags: '', exec_mode: 'serial', fail_policy: 'continue_on_fail',
  hook_before: '', hook_after: ''
})
const hookHelpOpen = ref(['help'])

const hookExamples = {
  before: {
    wait: {
      steps: [{ type: 'wait', seconds: 3, enabled: true }]
    },
    launch: {
      steps: [
        { type: 'wait', seconds: 1, enabled: true },
        { type: 'launch', app_package: 'com.example.app', enabled: true }
      ]
    },
    common: {
      steps: [{ type: 'invoke_common', common_step: '登录流程', enabled: true }]
    }
  },
  after: {
    wait: {
      steps: [{ type: 'wait', seconds: 2, enabled: true }]
    },
    swipe: {
      steps: [
        { type: 'swipe', x1: 500, y1: 800, x2: 500, y2: 400, enabled: true },
        { type: 'wait', seconds: 1, enabled: true }
      ]
    }
  }
}

const availableCases = computed(() =>
  allCases.value.filter(c => !suiteItems.value.some(i => i.case_id === c.id))
)

async function loadSuites() {
  loading.value = true
  try {
    suites.value = (await suiteApi.list()).data
  } finally {
    loading.value = false
  }
}

async function loadAllCases() {
  const res = await caseApi.list({ page: 1, page_size: 200 })
  allCases.value = res.data.list
}

function openDialog(row) {
  if (row) {
    form.id = row.id
    form.name = row.name
    form.tags = row.tags || ''
    form.exec_mode = row.exec_mode
    form.fail_policy = row.fail_policy
    form.hook_before = row.hook_before || ''
    form.hook_after = row.hook_after || ''
    suiteApi.get(row.id).then(res => {
      suiteItems.value = res.data.items.map(i => ({
        case_id: i.case_id,
        case_name: i.case_name,
        case_status: i.case_status,
        enabled: i.enabled !== false
      }))
    })
  } else {
    form.id = null
    form.name = ''
    form.tags = ''
    form.exec_mode = 'serial'
    form.fail_policy = 'continue_on_fail'
    form.hook_before = ''
    form.hook_after = ''
    suiteItems.value = []
  }
  pickerCaseId.value = null
  showDialog.value = true
}

function addCase(caseId) {
  if (!caseId) return
  const c = allCases.value.find(x => x.id === caseId)
  if (c) {
    suiteItems.value.push({ case_id: c.id, case_name: c.name, case_status: c.case_status, enabled: true })
  }
  pickerCaseId.value = null
}

function moveItem(index, delta) {
  const target = index + delta
  if (target < 0 || target >= suiteItems.value.length) return
  const arr = [...suiteItems.value]
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  suiteItems.value = arr
}

function fillHookExample(phase, exampleKey) {
  const example = hookExamples[phase]?.[exampleKey]
  if (!example) return
  const json = JSON.stringify(example, null, 2)
  if (phase === 'before') form.hook_before = json
  else form.hook_after = json
}

async function saveSuite() {
  const payload = {
    name: form.name,
    tags: form.tags,
    exec_mode: form.exec_mode,
    fail_policy: form.fail_policy,
    hook_before: form.hook_before || null,
    hook_after: form.hook_after || null,
    items: suiteItems.value.map((item, idx) => ({
      case_id: item.case_id,
      sort_order: idx,
      enabled: item.enabled !== false
    }))
  }
  if (form.id) {
    await suiteApi.update(form.id, payload)
  } else {
    await suiteApi.create(payload)
  }
  ElMessage.success('套件已保存')
  showDialog.value = false
  loadSuites()
}

async function runSuite(row) {
  const res = await suiteApi.run(row.id)
  const skipped = res.data.skipped || 0
  ElMessage.success(`套件已启动，批次 #${res.data.suite_run_id}，执行 ${res.data.total} 个用例${skipped ? `，跳过 ${skipped} 个` : ''}`)
  currentSuiteId.value = row.id
  loadSuiteRuns()
}

function runStatusType(s) {
  return { completed: 'success', failed: 'danger', running: 'warning', paused: 'info' }[s] || 'info'
}

async function loadSuiteRuns() {
  if (!currentSuiteId.value) return
  suiteRuns.value = (await checkpointApi.listSuiteRuns(currentSuiteId.value)).data
}

async function viewRun(row) {
  const res = await checkpointApi.getRun(row.id)
  runDetail.value = res.data
  showRunDialog.value = true
}

async function pauseRun(row) {
  await checkpointApi.pauseRun(row.id)
  ElMessage.success('套件批次已暂停')
  loadSuiteRuns()
}

async function resumeRun(row) {
  const res = await checkpointApi.resumeRun(row.id)
  ElMessage.success(`已续跑 ${res.data.resumed_count} 个用例/步骤`)
  loadSuiteRuns()
}

async function restoreConfig(row) {
  await ElMessageBox.confirm('将把套件配置还原为该批次执行时的快照，是否继续？', '还原配置', { type: 'warning' })
  await suiteApi.restoreConfig(row.id)
  ElMessage.success('套件配置已还原')
  loadSuites()
}

async function deleteSuite(row) {
  await ElMessageBox.confirm('套件将移入回收站', '确认', { type: 'warning' })
  await suiteApi.delete(row.id)
  ElMessage.success('已删除')
  loadSuites()
}

onMounted(() => { loadSuites(); loadAllCases() })
</script>

<style scoped>
.hook-help {
  margin: 0 0 16px 100px;
  border: none;
}
.hook-help :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: var(--el-color-primary);
  border: none;
  height: 36px;
}
.hook-help :deep(.el-collapse-item__wrap) {
  border: none;
}
.hook-help-body {
  font-size: 13px;
  line-height: 1.7;
  color: var(--atp-text-secondary, #606266);
}
.hook-help-body p {
  margin: 0 0 8px;
}
.hook-help-body code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color-light, #f5f7fa);
  font-size: 12px;
}
.hook-types {
  font-size: 12px;
}
.hook-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.hook-examples > span {
  font-size: 12px;
  color: var(--atp-text-secondary, #909399);
}
</style>
