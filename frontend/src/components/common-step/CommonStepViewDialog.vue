<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="760px"
    destroy-on-close
    class="common-step-view-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <div v-loading="loading" class="view-body">
      <p v-if="row?.description" class="view-desc">{{ row.description }}</p>
      <div v-if="row" class="view-meta">
        <el-tag size="small" effect="plain" :type="row.status === 'active' ? 'success' : 'info'">
          {{ row.status === 'active' ? '已启用' : '已停用' }}
        </el-tag>
        <span class="meta-text">共 {{ flatSteps.length }} 步</span>
      </div>

      <el-empty
        v-if="!loading && !flatSteps.length"
        description="该公共步骤暂无内部步骤"
        :image-size="72"
      />

      <div v-else class="step-list">
        <div
          v-for="(item, idx) in flatSteps"
          :key="item.key"
          class="step-row"
          :style="{ paddingLeft: `${12 + item.depth * 18}px` }"
        >
          <span class="step-idx">{{ idx + 1 }}</span>
          <el-tag size="small" effect="plain" class="step-type">{{ typeLabel(item.step) }}</el-tag>
          <div class="step-main">
            <div class="step-summary">{{ summarize(item.step) || '—' }}</div>
            <div v-if="locatorOf(item.step)" class="step-locator" :title="locatorOf(item.step)">
              {{ locatorOf(item.step) }}
            </div>
          </div>
          <el-button
            v-if="item.step.type === 'invoke_common' && item.step.common_step"
            size="small"
            plain
            @click="openNested(item.step.common_step)"
          >查看嵌套</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button v-if="stack.length" @click="back">返回上级</el-button>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="canEdit && row?.id"
        type="primary"
        plain
        @click="goEdit"
      >去编辑</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { commonStepApi } from '@/api'
import { formatStepLocator, formatStepTarget } from '@/utils/stepDisplay'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 列表行或详情；打开后会按 id 拉最新 */
  stepRow: { type: Object, default: null },
  canEdit: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const loading = ref(false)
const row = ref(null)
const steps = ref([])
const stack = ref([])

const TYPE_LABELS = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', clear_input: '清空输入',
  launch: '启动', swipe: '滑动', long_press: '长按',
  assert_text: '断言', assert_exists: '断言存在', assert_not_exists: '断言不存在',
  assert_compare: '断言验证', assert_element_count: '断言个数', assert_attribute: '断言属性',
  invoke_common: '公共步骤', invoke_case: '调用用例',
  branch: 'if判断', else_if: 'else if', else: 'else', loop: 'while循环', end_block: '结束块',
  custom_script: '自定义脚本', screenshot: '获取截图', get_text: '获取文本',
  manual_wait: '人工介入', dismiss_popup: '关弹窗', press_key: '系统按键',
  robot_send_command: '机器人命令', data_factory: '动态造数'
}

const dialogTitle = computed(() =>
  row.value?.name ? `查看公共步骤 · ${row.value.name}` : '查看公共步骤'
)

const flatSteps = computed(() => {
  const list = Array.isArray(steps.value) ? steps.value : []
  const out = []
  let depth = 0
  list.forEach((step, i) => {
    if (!step) return
    const t = step.type
    if (t === 'end_block') {
      depth = Math.max(0, depth - 1)
      out.push({ key: `e-${i}`, step, depth })
      return
    }
    out.push({ key: `s-${i}-${step.id || i}`, step, depth })
    if (t === 'branch' || t === 'else_if' || t === 'else' || t === 'loop') {
      depth += 1
    }
  })
  return out
})

function parseStepsContent(text) {
  try {
    const obj = JSON.parse(text || '{}')
    if (Array.isArray(obj)) return obj
    if (obj && Array.isArray(obj.steps)) return obj.steps
  } catch { /* ignore */ }
  return []
}

function typeLabel(step) {
  return TYPE_LABELS[step?.type] || step?.type || '步骤'
}

function locatorOf(step) {
  return formatStepLocator(step) || ''
}

function summarize(step) {
  if (!step) return ''
  const target = formatStepTarget(step)
  switch (step.type) {
    case 'wait':
      if (step.wait_mode === 'appear') return `等待出现 ${step.element_name || ''} (${step.seconds || 10}s)`
      if (step.wait_mode === 'disappear') return `等待消失 ${step.element_name || ''} (${step.seconds || 10}s)`
      return `${step.seconds ?? 2}s`
    case 'click':
    case 'tap_xy':
    case 'long_press':
      return target || typeLabel(step)
    case 'input':
      return step.text || target || ''
    case 'assert_exists':
      return step.element_name || '控件存在'
    case 'assert_not_exists':
      return step.element_name || '控件不存在'
    case 'invoke_common':
      return step.common_step || ''
    case 'branch':
    case 'else_if':
    case 'loop':
      return step.condition || step.condition_kind || target || ''
    case 'else':
      return '否则分支'
    case 'end_block':
      return step.block_type === 'loop' ? '结束循环' : '结束判断'
    case 'custom_script':
      return (step.script_code || step.script || '').slice(0, 80)
    case 'swipe':
      if (step.swipe_start_name && step.swipe_end_name) {
        return `${step.swipe_start_name} → ${step.swipe_end_name}`
      }
      return target || ''
    default:
      return target || step.remark || step.element_name || step.expected || ''
  }
}

async function loadById(id, { pushStack = false } = {}) {
  if (!id) return
  if (pushStack && row.value) {
    stack.value.push({ row: row.value, steps: steps.value })
  }
  loading.value = true
  try {
    const res = await commonStepApi.get(id)
    const data = res.data
    if (!data) {
      ElMessage.warning('未找到该公共步骤')
      if (pushStack) stack.value.pop()
      return
    }
    row.value = data
    steps.value = parseStepsContent(data.steps_content)
  } catch {
    ElMessage.error('加载公共步骤失败')
    if (pushStack) stack.value.pop()
  } finally {
    loading.value = false
  }
}

async function openFromRow(src) {
  stack.value = []
  if (!src) {
    row.value = null
    steps.value = []
    return
  }
  if (src.id) {
    await loadById(src.id)
    return
  }
  row.value = src
  steps.value = parseStepsContent(src.steps_content)
}

async function openNested(name) {
  const n = String(name || '').trim()
  if (!n) return
  loading.value = true
  try {
    const list = (await commonStepApi.list()).data || []
    const hit = list.find(s => s.name === n)
    if (!hit?.id) {
      ElMessage.warning(`未找到公共步骤「${n}」`)
      return
    }
    await loadById(hit.id, { pushStack: true })
  } catch {
    ElMessage.error('加载嵌套公共步骤失败')
  } finally {
    loading.value = false
  }
}

function back() {
  const prev = stack.value.pop()
  if (!prev) return
  row.value = prev.row
  steps.value = prev.steps
}

function goEdit() {
  const id = row.value?.id
  if (!id) return
  emit('update:modelValue', false)
  router.push({ path: `/common-steps/${id}/edit` })
}

function onClosed() {
  stack.value = []
  row.value = null
  steps.value = []
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) openFromRow(props.stepRow)
  }
)
</script>

<style scoped>
.view-body {
  min-height: 160px;
}
.view-desc {
  margin: 0 0 10px;
  color: var(--atp-text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.5;
}
.view-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.meta-text {
  font-size: 12px;
  color: #94a3b8;
}
.step-list {
  max-height: 460px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.step-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.step-row:last-child {
  border-bottom: none;
}
.step-idx {
  flex: 0 0 28px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 24px;
  text-align: right;
}
.step-type {
  flex-shrink: 0;
  margin-top: 1px;
}
.step-main {
  flex: 1;
  min-width: 0;
}
.step-summary {
  font-size: 13px;
  color: #0f172a;
  line-height: 1.45;
  word-break: break-word;
}
.step-locator {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
