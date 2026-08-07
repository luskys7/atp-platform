<template>
  <el-dialog
    :model-value="modelValue"
    width="920px"
    top="4vh"
    class="suite-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ form.id ? '编辑套件' : '新建套件' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingDetail">
      <!-- 模块 2：基础信息 -->
      <section class="section-card">
        <div class="section-title">套件基础信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="填写中文业务名称，快速区分套件回归用途">套件名称</span>
          </div>
          <el-input
            v-model="form.name"
            maxlength="80"
            show-word-limit
            placeholder="例：版本全量回归套件 / 登录模块冒烟套件"
          />
          <div class="name-tips">
            命名建议：版本全量回归套件、登录模块冒烟套件、支付流程专项测试套件、个人中心迭代回归套件
          </div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="用于快速筛选同类套件">标签</span>
          </div>
          <el-input v-model="form.tags" placeholder="冒烟,回归（逗号分隔多标签）" />
          <div class="tag-quick">
            <span class="tag-quick-label">常用标签：</span>
            <el-button
              v-for="t in QUICK_TAGS"
              :key="t"
              size="small"
              round
              :type="hasTag(t) ? 'primary' : 'default'"
              plain
              @click="toggleQuickTag(t)"
            >{{ t }}</el-button>
          </div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="串行 = 单设备逐条跑；并行 = 多设备同时执行，提升回归效率">执行模式</span>
          </div>
          <el-radio-group v-model="form.exec_mode" class="mode-radios">
            <el-radio value="serial" class="radio-serial">
              <span class="radio-text is-serial">串行</span>
              <span class="radio-desc">所有用例按顺序依次执行</span>
            </el-radio>
            <el-radio value="parallel" class="radio-parallel">
              <span class="radio-text is-parallel">并行</span>
              <span class="radio-desc">多台设备同步执行全部用例</span>
            </el-radio>
          </el-radio-group>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="控制套件出现报错后的执行逻辑">失败处理策略</span>
          </div>
          <el-radio-group v-model="form.fail_policy" class="policy-radios">
            <el-radio value="continue_on_fail" class="radio-continue">
              <span class="radio-text is-continue">失败继续执行</span>
              <span class="radio-desc">单条用例失败，后续用例正常跑完</span>
            </el-radio>
            <el-radio value="stop" class="radio-stop">
              <span class="radio-text is-stop">失败立即停止</span>
              <span class="radio-desc">一旦用例失败，直接终止整套回归</span>
            </el-radio>
          </el-radio-group>
        </div>
      </section>

      <!-- 模块 3：钩子 -->
      <section class="section-card">
        <div class="section-title-row">
          <div class="section-title tip-label" title="前置钩子用于统一初始化环境；后置钩子用于执行后清理设备环境">
            套件执行钩子
          </div>
          <div class="section-actions">
            <el-dropdown trigger="click" @command="applyHookTemplate">
              <el-button size="small" type="primary" plain>
                填充模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="init_app">初始化 APP → 前置钩子</el-dropdown-item>
                  <el-dropdown-item command="clear_cache">清理缓存 → 前置钩子</el-dropdown-item>
                  <el-dropdown-item command="dismiss_popup">关闭弹窗 → 前置钩子</el-dropdown-item>
                  <el-dropdown-item divided command="exit_app">退出应用 → 后置钩子</el-dropdown-item>
                  <el-dropdown-item command="cleanup">清理收尾 → 后置钩子</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" @click="showHookHelp = true">套件钩子使用说明</el-button>
          </div>
        </div>

        <div class="hook-block">
          <div class="hook-subtitle">套件执行前（前置钩子）</div>
          <div class="hook-mode-bar">
            <el-radio-group v-model="beforeMode" size="small" @change="onBeforeModeChange">
              <el-radio-button value="visual">可视化步骤编辑（推荐）</el-radio-button>
              <el-radio-button value="script">原生脚本文本</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="beforeMode === 'visual'" class="visual-hook">
            <div class="visual-quick">
              <el-button size="small" @click="addVisualStep('before', { type: 'wait', seconds: 2 })">+ 等待</el-button>
              <el-button size="small" @click="addVisualStep('before', { type: 'launch', app_package: 'com.example.app' })">+ 启动应用</el-button>
              <el-button size="small" @click="addVisualStep('before', { type: 'clear_cache', mode: 'disk' })">+ 清理缓存</el-button>
              <el-button size="small" @click="addVisualStep('before', { type: 'dismiss_popup' })">+ 关闭弹窗</el-button>
            </div>
            <div v-if="!beforeSteps.length" class="visual-empty">暂无前置步骤，点击上方按钮添加</div>
            <div v-for="(s, i) in beforeSteps" :key="'b'+i" class="visual-step">
              <span class="vs-idx">{{ i + 1 }}</span>
              <el-tag size="small" type="info">{{ stepTypeLabel(s.type) }}</el-tag>
              <span class="vs-desc">{{ stepDesc(s) }}</span>
              <el-button size="small" text type="danger" @click="beforeSteps.splice(i, 1); syncBeforeFromVisual()">移除</el-button>
            </div>
          </div>
          <div v-else>
            <el-input
              v-model="form.hook_before"
              type="textarea"
              :rows="5"
              :class="{ 'is-json-error': beforeJsonError }"
              placeholder='{"steps":[{"type":"wait","seconds":2},{"type":"launch","app_package":"com.example.app"}]}'
              @input="validateBeforeJson"
            />
            <div v-if="beforeJsonError" class="json-error">语法异常：{{ beforeJsonError }}</div>
          </div>
        </div>

        <div class="hook-block">
          <div class="hook-subtitle">套件执行完成后（后置钩子）</div>
          <div class="hook-mode-bar">
            <el-radio-group v-model="afterMode" size="small" @change="onAfterModeChange">
              <el-radio-button value="visual">可视化步骤编辑（推荐）</el-radio-button>
              <el-radio-button value="script">原生脚本文本</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="afterMode === 'visual'" class="visual-hook">
            <div class="visual-quick">
              <el-button size="small" @click="addVisualStep('after', { type: 'wait', seconds: 1 })">+ 等待</el-button>
              <el-button size="small" @click="addVisualStep('after', { type: 'force_stop' })">+ 退出应用</el-button>
              <el-button size="small" @click="addVisualStep('after', { type: 'clear_cache', mode: 'all' })">+ 清理全部缓存</el-button>
              <el-button size="small" @click="addVisualStep('after', { type: 'swipe', x1: 500, y1: 800, x2: 500, y2: 400 })">+ 滑动回桌面</el-button>
            </div>
            <div v-if="!afterSteps.length" class="visual-empty">暂无后置步骤，点击上方按钮添加</div>
            <div v-for="(s, i) in afterSteps" :key="'a'+i" class="visual-step">
              <span class="vs-idx">{{ i + 1 }}</span>
              <el-tag size="small" type="warning">{{ stepTypeLabel(s.type) }}</el-tag>
              <span class="vs-desc">{{ stepDesc(s) }}</span>
              <el-button size="small" text type="danger" @click="afterSteps.splice(i, 1); syncAfterFromVisual()">移除</el-button>
            </div>
          </div>
          <div v-else>
            <el-input
              v-model="form.hook_after"
              type="textarea"
              :rows="5"
              :class="{ 'is-json-error': afterJsonError }"
              placeholder='{"steps":[{"type":"wait","seconds":1},{"type":"force_stop"}]}'
              @input="validateAfterJson"
            />
            <div v-if="afterJsonError" class="json-error">语法异常：{{ afterJsonError }}</div>
          </div>
        </div>
      </section>

      <!-- 模块 4：关联用例 -->
      <section class="section-card">
        <div class="section-title">关联测试用例</div>
        <div class="case-toolbar">
          <el-select
            v-model="pickerCaseIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="添加用例（可多选批量添加）"
            style="flex:1;min-width:220px"
          >
            <el-option
              v-for="c in availableCases"
              :key="c.id"
              :label="`${c.name}（${statusLabel(c.case_status)}）`"
              :value="c.id"
            />
          </el-select>
          <el-button type="primary" :disabled="!pickerCaseIds.length" @click="addSelectedCases">添加用例</el-button>
          <el-button @click="goCases">前往用例页面</el-button>
          <el-button type="danger" plain :disabled="!suiteItems.length" @click="clearAllCases">一键清空</el-button>
        </div>

        <el-table
          v-if="suiteItems.length"
          :data="suiteItems"
          size="small"
          stripe
          row-key="case_id"
          class="case-table"
        >
          <el-table-column label="顺序" width="120" align="center">
            <template #default="{ $index }">
              <div class="order-cell">
                <span
                  class="drag-handle"
                  draggable="true"
                  title="拖拽调整顺序"
                  @dragstart="onDragStart($index, $event)"
                  @dragover.prevent
                  @drop="onDrop($index, $event)"
                >⋮⋮</span>
                <span>{{ $index + 1 }}</span>
                <el-button size="small" text :disabled="$index === 0" @click="moveItem($index, -1)">↑</el-button>
                <el-button size="small" text :disabled="$index === suiteItems.length - 1" @click="moveItem($index, 1)">↓</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="case_name" label="用例名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="用例状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.case_status) }}</template>
          </el-table-column>
          <el-table-column label="是否启用" width="100" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ $index }">
              <el-button size="small" type="danger" plain @click="suiteItems.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-else class="cases-empty">
          <p>尚未关联任何测试用例</p>
          <div class="cases-empty-actions">
            <el-button type="primary" @click="focusCasePicker">添加用例</el-button>
            <el-button @click="goCases">前往测试用例页面新建</el-button>
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-muted" @click="resetForm">重置配置</el-button>
          <el-button class="btn-muted" @click="saveAsTemplate">保存为模板</el-button>
        </div>
        <div class="footer-right">
          <el-button class="btn-muted" @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveSuite">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>

  <el-drawer v-model="showHookHelp" title="套件钩子使用说明" size="400px" append-to-body>
    <div class="help-drawer">
      <h4>前置钩子</h4>
      <p>套件运行前统一执行的操作，适用于环境初始化、启动应用、登录授权、关闭干扰弹窗等。</p>
      <h4>后置钩子</h4>
      <p>全部用例跑完后的收尾操作，适用于退出应用、清理缓存、杀进程、恢复设备状态等。</p>
      <h4>推荐方式</h4>
      <p>优先使用「可视化步骤编辑」点击添加；高级用户可切换到「原生脚本文本」直接编辑 JSON。</p>
      <h4>完整中文示例（前置）</h4>
      <pre>{{ HELP_BEFORE_EXAMPLE }}</pre>
      <h4>完整中文示例（后置）</h4>
      <pre>{{ HELP_AFTER_EXAMPLE }}</pre>
      <el-button type="primary" plain @click="fillHelpExamples">一键填入示例到前后置钩子</el-button>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { suiteApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  suiteId: { type: [Number, String], default: null },
  allCases: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'saved', 'save-template'])

const router = useRouter()
const QUICK_TAGS = ['冒烟', '回归', '线上校验', '迭代测试']
const USER_TPL_KEY = 'atp_suite_user_templates'

const HELP_BEFORE_EXAMPLE = `{
  "steps": [
    { "type": "wait", "seconds": 2, "enabled": true },
    { "type": "launch", "app_package": "com.example.app", "enabled": true },
    { "type": "dismiss_popup", "enabled": true }
  ]
}`

const HELP_AFTER_EXAMPLE = `{
  "steps": [
    { "type": "force_stop", "enabled": true },
    { "type": "clear_cache", "mode": "all", "enabled": true },
    { "type": "wait", "seconds": 1, "enabled": true }
  ]
}`

const HOOK_TEMPLATES = {
  init_app: {
    phase: 'before',
    data: {
      steps: [
        { type: 'wait', seconds: 2, enabled: true },
        { type: 'launch', app_package: 'com.example.app', enabled: true }
      ]
    }
  },
  clear_cache: {
    phase: 'before',
    data: { steps: [{ type: 'clear_cache', mode: 'disk', enabled: true }] }
  },
  dismiss_popup: {
    phase: 'before',
    data: { steps: [{ type: 'dismiss_popup', enabled: true }] }
  },
  exit_app: {
    phase: 'after',
    data: { steps: [{ type: 'force_stop', enabled: true }] }
  },
  cleanup: {
    phase: 'after',
    data: {
      steps: [
        { type: 'force_stop', enabled: true },
        { type: 'clear_cache', mode: 'all', enabled: true },
        { type: 'wait', seconds: 1, enabled: true }
      ]
    }
  }
}

const saving = ref(false)
const loadingDetail = ref(false)
const showHookHelp = ref(false)
const beforeMode = ref('visual')
const afterMode = ref('visual')
const beforeSteps = ref([])
const afterSteps = ref([])
const beforeJsonError = ref('')
const afterJsonError = ref('')
const pickerCaseIds = ref([])
const suiteItems = ref([])
const dragFrom = ref(-1)
const snapshot = ref('')

const form = reactive({
  id: null,
  name: '',
  tags: '',
  exec_mode: 'serial',
  fail_policy: 'continue_on_fail',
  hook_before: '',
  hook_after: ''
})

const availableCases = computed(() =>
  props.allCases.filter(c => !suiteItems.value.some(i => i.case_id === c.id))
)

const typeLabels = {
  wait: '等待', launch: '启动应用', clear_cache: '清理缓存', dismiss_popup: '关闭弹窗',
  force_stop: '退出应用', swipe: '滑动', invoke_common: '公共步骤',
  set_relative_time: '设置相对时间',
  custom_script: '自定义脚本'
}

function stepTypeLabel(t) { return typeLabels[t] || t }

function stepDesc(s) {
  if (s.type === 'wait') return `${s.seconds || 1} 秒`
  if (s.type === 'launch') return s.app_package || '当前应用'
  if (s.type === 'clear_cache') return ({ disk: '磁盘缓存', memory: '杀进程', all: '全部' })[s.mode] || s.mode
  if (s.type === 'swipe') return '滑动页面'
  return ''
}

function statusLabel(s) {
  return { draft: '草稿', review: '待评审', active: '已生效', deprecated: '已禁用' }[s] || s || '-'
}

function hasTag(t) {
  return String(form.tags || '').split(/[,，]/).map(x => x.trim()).includes(t)
}

function toggleQuickTag(t) {
  const parts = String(form.tags || '').split(/[,，]/).map(x => x.trim()).filter(Boolean)
  const idx = parts.indexOf(t)
  if (idx >= 0) parts.splice(idx, 1)
  else parts.push(t)
  form.tags = parts.join(',')
}

function parseHookSteps(raw) {
  if (!raw || !String(raw).trim()) return []
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(obj?.steps) ? obj.steps : []
  } catch {
    return []
  }
}

function stringifySteps(steps) {
  if (!steps.length) return ''
  return JSON.stringify({ steps }, null, 2)
}

function validateJson(raw) {
  if (!raw || !String(raw).trim()) return ''
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object') return '必须是 JSON 对象'
    if (obj.steps != null && !Array.isArray(obj.steps)) return 'steps 必须是数组'
    return ''
  } catch (e) {
    return e.message || 'JSON 格式错误'
  }
}

function validateBeforeJson() {
  beforeJsonError.value = validateJson(form.hook_before)
}

function validateAfterJson() {
  afterJsonError.value = validateJson(form.hook_after)
}

function syncBeforeFromVisual() {
  form.hook_before = stringifySteps(beforeSteps.value)
  beforeJsonError.value = ''
}

function syncAfterFromVisual() {
  form.hook_after = stringifySteps(afterSteps.value)
  afterJsonError.value = ''
}

function onBeforeModeChange(mode) {
  if (mode === 'script') {
    syncBeforeFromVisual()
    validateBeforeJson()
  } else {
    const err = validateJson(form.hook_before)
    if (err && form.hook_before?.trim()) {
      ElMessage.warning(`前置钩子脚本有误，无法切换可视化：${err}`)
      beforeMode.value = 'script'
      return
    }
    beforeSteps.value = parseHookSteps(form.hook_before)
  }
}

function onAfterModeChange(mode) {
  if (mode === 'script') {
    syncAfterFromVisual()
    validateAfterJson()
  } else {
    const err = validateJson(form.hook_after)
    if (err && form.hook_after?.trim()) {
      ElMessage.warning(`后置钩子脚本有误，无法切换可视化：${err}`)
      afterMode.value = 'script'
      return
    }
    afterSteps.value = parseHookSteps(form.hook_after)
  }
}

function addVisualStep(phase, step) {
  if (phase === 'before') {
    beforeSteps.value.push({ enabled: true, ...step })
    syncBeforeFromVisual()
  } else {
    afterSteps.value.push({ enabled: true, ...step })
    syncAfterFromVisual()
  }
}

function applyHookTemplate(cmd) {
  const tpl = HOOK_TEMPLATES[cmd]
  if (!tpl) return
  const json = JSON.stringify(tpl.data, null, 2)
  if (tpl.phase === 'before') {
    form.hook_before = json
    beforeSteps.value = [...tpl.data.steps]
    beforeJsonError.value = ''
    beforeMode.value = 'visual'
  } else {
    form.hook_after = json
    afterSteps.value = [...tpl.data.steps]
    afterJsonError.value = ''
    afterMode.value = 'visual'
  }
  ElMessage.success('模板已填充')
}

function fillHelpExamples() {
  form.hook_before = HELP_BEFORE_EXAMPLE
  form.hook_after = HELP_AFTER_EXAMPLE
  beforeSteps.value = parseHookSteps(HELP_BEFORE_EXAMPLE)
  afterSteps.value = parseHookSteps(HELP_AFTER_EXAMPLE)
  beforeJsonError.value = ''
  afterJsonError.value = ''
  ElMessage.success('示例已填入')
}

function takeSnapshot() {
  return JSON.stringify({
    form: { ...form },
    items: suiteItems.value
  })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function blankForm() {
  form.id = null
  form.name = ''
  form.tags = ''
  form.exec_mode = 'serial'
  form.fail_policy = 'continue_on_fail'
  form.hook_before = ''
  form.hook_after = ''
  suiteItems.value = []
  pickerCaseIds.value = []
  beforeSteps.value = []
  afterSteps.value = []
  beforeJsonError.value = ''
  afterJsonError.value = ''
  beforeMode.value = 'visual'
  afterMode.value = 'visual'
}

async function loadSuite(id) {
  loadingDetail.value = true
  try {
    const res = await suiteApi.get(id)
    const row = res.data
    form.id = row.id
    form.name = row.name || ''
    form.tags = row.tags || ''
    form.exec_mode = row.exec_mode || 'serial'
    form.fail_policy = row.fail_policy || 'continue_on_fail'
    form.hook_before = row.hook_before || ''
    form.hook_after = row.hook_after || ''
    suiteItems.value = (row.items || []).map(i => ({
      case_id: i.case_id,
      case_name: i.case_name,
      case_status: i.case_status,
      enabled: i.enabled !== false
    }))
    beforeSteps.value = parseHookSteps(form.hook_before)
    afterSteps.value = parseHookSteps(form.hook_after)
    beforeJsonError.value = validateJson(form.hook_before)
    afterJsonError.value = validateJson(form.hook_after)
    beforeMode.value = beforeJsonError.value ? 'script' : 'visual'
    afterMode.value = afterJsonError.value ? 'script' : 'visual'
  } finally {
    loadingDetail.value = false
  }
}

async function initDialog() {
  blankForm()
  if (props.suiteId) {
    await loadSuite(props.suiteId)
  }
  await nextTick()
  snapshot.value = takeSnapshot()
}

watch(() => props.modelValue, (v) => {
  if (v) initDialog()
})

async function handleBeforeClose(done) {
  if (!isDirty()) {
    emit('update:modelValue', false)
    done()
    return
  }
  try {
    await ElMessageBox.confirm('当前内容尚未保存，确定关闭吗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
    done()
  } catch {
    // 继续编辑
  }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前内容尚未保存，确定关闭吗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch {
    // 继续编辑
  }
}

async function resetForm() {
  await ElMessageBox.confirm('将清空当前弹窗所有填写内容，是否继续？', '重置配置', { type: 'warning' })
  const keepId = form.id
  blankForm()
  form.id = keepId
  ElMessage.success('已重置')
}

function goCases() {
  router.push('/cases')
}

function addSelectedCases() {
  for (const id of pickerCaseIds.value) {
    const c = props.allCases.find(x => x.id === id)
    if (c && !suiteItems.value.some(i => i.case_id === c.id)) {
      suiteItems.value.push({
        case_id: c.id,
        case_name: c.name,
        case_status: c.case_status,
        enabled: true
      })
    }
  }
  pickerCaseIds.value = []
  ElMessage.success('用例已添加')
}

async function clearAllCases() {
  await ElMessageBox.confirm('确定清空全部关联用例？', '一键清空', { type: 'warning' })
  suiteItems.value = []
}

function focusCasePicker() {
  ElMessage.info('请在上方下拉框中选择用例后点击「添加用例」')
}

function moveItem(index, delta) {
  const target = index + delta
  if (target < 0 || target >= suiteItems.value.length) return
  const arr = [...suiteItems.value]
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  suiteItems.value = arr
}

function onDragStart(index, e) {
  dragFrom.value = index
  e.dataTransfer.effectAllowed = 'move'
}

function onDrop(index) {
  const from = dragFrom.value
  if (from < 0 || from === index) return
  const arr = [...suiteItems.value]
  const [item] = arr.splice(from, 1)
  arr.splice(index, 0, item)
  suiteItems.value = arr
  dragFrom.value = -1
}

async function saveAsTemplate() {
  if (!form.name?.trim()) {
    ElMessage.warning('请先填写套件名称后再保存模板')
    return
  }
  if (beforeMode.value === 'visual') syncBeforeFromVisual()
  if (afterMode.value === 'visual') syncAfterFromVisual()
  if (beforeJsonError.value || afterJsonError.value) {
    ElMessage.error('钩子脚本存在语法错误，请先修正')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入模板名称', '保存为模板', {
    inputValue: form.name.trim(),
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value) return

  const tpl = {
    key: `user_${Date.now()}`,
    name: value.trim(),
    desc: '用户自定义套件模板',
    tags: form.tags,
    exec_mode: form.exec_mode,
    fail_policy: form.fail_policy,
    hook_before: form.hook_before || null,
    hook_after: form.hook_after || null,
    items: suiteItems.value.map((item, idx) => ({
      case_id: item.case_id,
      sort_order: idx,
      enabled: item.enabled !== false,
      case_name: item.case_name,
      case_status: item.case_status
    }))
  }
  try {
    const list = JSON.parse(localStorage.getItem(USER_TPL_KEY) || '[]')
    list.unshift(tpl)
    localStorage.setItem(USER_TPL_KEY, JSON.stringify(list.slice(0, 50)))
  } catch { /* ignore */ }
  emit('save-template', tpl)
  ElMessage.success('模板已保存，可在套件模板库中复用')
}

async function saveSuite() {
  if (!form.name?.trim()) {
    ElMessage.warning('套件名称不能为空')
    return
  }
  if (form.name.length > 80) {
    ElMessage.warning('套件名称不能超过 80 字')
    return
  }
  if (beforeMode.value === 'visual') syncBeforeFromVisual()
  if (afterMode.value === 'visual') syncAfterFromVisual()
  validateBeforeJson()
  validateAfterJson()
  if (beforeJsonError.value || afterJsonError.value) {
    ElMessage.error('钩子脚本语法错误，禁止保存，请修正后再试')
    if (beforeJsonError.value) beforeMode.value = 'script'
    if (afterJsonError.value) afterMode.value = 'script'
    return
  }

  if (!suiteItems.value.length) {
    try {
      await ElMessageBox.confirm('当前套件未关联测试用例，确认创建空套件吗？', '确认保存', {
        type: 'warning',
        confirmButtonText: '仍要保存',
        cancelButtonText: '去添加用例'
      })
    } catch {
      return
    }
  }

  const payload = {
    name: form.name.trim(),
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

  saving.value = true
  try {
    if (form.id) await suiteApi.update(form.id, payload)
    else await suiteApi.create(payload)
    ElMessage.success('套件已保存')
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}
.dlg-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--atp-text);
}
.dlg-close {
  margin: -4px -8px -4px 0;
  color: #64748b;
}

.dlg-body {
  max-height: min(70vh, 720px);
  overflow-y: auto;
  padding-right: 4px;
}

.section-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 14px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 14px;
  color: var(--atp-text);
}
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.section-title-row .section-title { margin-bottom: 0; }
.section-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.field-block { margin-bottom: 14px; }
.field-block:last-child { margin-bottom: 0; }
.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--atp-text);
}
.req { color: #ef4444; }
.tip-label {
  cursor: help;
  border-bottom: 1px dashed rgba(100, 116, 139, 0.45);
}
.name-tips {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.tag-quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.tag-quick-label { font-size: 12px; color: var(--atp-text-secondary); }

.mode-radios,
.policy-radios {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.radio-text { font-weight: 600; margin-right: 8px; }
.radio-text.is-serial { color: #94a3b8; }
.radio-text.is-parallel { color: #059669; }
.radio-text.is-continue { color: #ca8a04; }
.radio-text.is-stop { color: #ea580c; }
.radio-desc { font-size: 12px; color: #94a3b8; font-weight: 400; }

.hook-block {
  background: #fff;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.hook-block:last-child { margin-bottom: 0; }
.hook-subtitle {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}
.hook-mode-bar { margin-bottom: 10px; }

.visual-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.visual-empty {
  font-size: 12px;
  color: #94a3b8;
  padding: 8px 0;
}
.visual-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  background: #f8fafc;
  border-radius: 8px;
}
.vs-idx {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--atp-primary, #0284c7);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.vs-desc { flex: 1; font-size: 12px; color: var(--atp-text-secondary); }

:deep(.is-json-error .el-textarea__inner) {
  border-color: #ef4444 !important;
  box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.2);
}
.json-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ef4444;
  line-height: 1.4;
}

.case-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.order-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  font-size: 12px;
  letter-spacing: -2px;
  user-select: none;
  padding: 0 4px;
}
.drag-handle:active { cursor: grabbing; }

.cases-empty {
  text-align: center;
  padding: 36px 12px;
  background: #fff;
  border-radius: 10px;
}
.cases-empty p {
  margin: 0 0 14px;
  color: var(--atp-text-secondary);
}
.cases-empty-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

.dlg-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-muted {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
}

.help-drawer h4 {
  margin: 16px 0 6px;
  font-size: 14px;
}
.help-drawer h4:first-child { margin-top: 0; }
.help-drawer p {
  margin: 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.6;
}
.help-drawer pre {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  font-size: 11px;
  overflow: auto;
  line-height: 1.5;
}
</style>
