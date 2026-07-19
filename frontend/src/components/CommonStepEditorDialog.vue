<template>
  <el-dialog
    :model-value="modelValue"
    width="920px"
    top="4vh"
    class="common-step-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑公共步骤' : '新建公共步骤' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础信息</div>
        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="全局复用积木，可在任意测试用例、套件钩子内直接引用">步骤名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="例：APP 登录初始化步骤、清理缓存退出流程"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>
        <div class="field-block">
          <div class="field-label">描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="填写步骤用途说明，例：执行前自动登录账号，适用于所有支付类回归用例"
          />
        </div>
      </section>

      <!-- 分区 2：步骤流程 -->
      <section class="section-card">
        <div class="section-head">
          <div class="section-title" style="margin:0">自动化执行步骤流程</div>
          <div class="mode-switch">
            <el-radio-group v-model="editMode" size="small" @change="onModeChange">
              <el-radio-button value="visual">可视化拖拽编辑（推荐）</el-radio-button>
              <el-radio-button value="raw">原生脚本文本编辑</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click" @command="applyBuiltinTemplate">
              <el-button size="small" class="btn-tpl">
                填充通用模板
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="init_app">APP 初始化</el-dropdown-item>
                  <el-dropdown-item command="clear_cache">清理缓存</el-dropdown-item>
                  <el-dropdown-item command="login">账号登录</el-dropdown-item>
                  <el-dropdown-item command="dismiss_popup">关闭弹窗</el-dropdown-item>
                  <el-dropdown-item command="exit_app">退出应用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- 可视化 -->
        <div v-if="editMode === 'visual'" class="visual-layout">
          <aside class="palette">
            <div class="palette-title">快捷步骤库</div>
            <div v-for="group in PALETTE" :key="group.key" class="palette-group">
              <div class="palette-group-name">{{ group.label }}</div>
              <button
                v-for="item in group.items"
                :key="item.type + (item.extraKey || '')"
                type="button"
                class="palette-item"
                draggable="true"
                @dragstart="onPaletteDragStart(item, $event)"
                @click="addStepFromPalette(item)"
              >
                {{ item.label }}
              </button>
            </div>
            <el-button class="picker-link" type="primary" plain size="small" @click="goElementPicker">
              一键跳转控件拾取
            </el-button>
          </aside>

          <div
            class="canvas"
            @dragover.prevent
            @drop="onCanvasDrop"
          >
            <div class="canvas-head">
              <span>步骤画布</span>
              <span class="canvas-hint">拖拽库中步骤添加 · 支持上下排序、复制、删除</span>
            </div>
            <div v-if="!visualSteps.length" class="canvas-empty">
              从左侧步骤库点击或拖拽添加步骤，也可前往控件拾取生成点击 / 输入步骤
            </div>
            <div
              v-for="(s, i) in visualSteps"
              :key="s._uid"
              class="canvas-step"
              :class="{ 'is-drag-over': dragOverIndex === i }"
              draggable="true"
              @dragstart="onStepDragStart(i, $event)"
              @dragover.prevent="dragOverIndex = i"
              @dragleave="dragOverIndex = -1"
              @drop.stop="onStepDrop(i)"
            >
              <span class="drag-handle" title="拖拽排序">⋮⋮</span>
              <span class="step-idx">{{ i + 1 }}</span>
              <el-tag size="small" :type="stepTagType(s.type)">{{ stepTypeLabel(s.type) }}</el-tag>
              <div class="step-main">
                <div class="step-desc">{{ stepDesc(s) }}</div>
                <div v-if="stepSub(s)" class="step-sub">{{ stepSub(s) }}</div>
              </div>
              <div class="step-acts">
                <el-button size="small" text :disabled="i === 0" @click="moveStep(i, -1)">↑</el-button>
                <el-button size="small" text :disabled="i === visualSteps.length - 1" @click="moveStep(i, 1)">↓</el-button>
                <el-button size="small" text @click="copyStep(i)">复制</el-button>
                <el-button size="small" text type="danger" @click="removeStep(i)">删除</el-button>
              </div>
            </div>
            <div class="canvas-foot">已配置 {{ visualSteps.length }} 步，保存时自动转为标准步骤脚本</div>
          </div>
        </div>

        <!-- 原生文本 -->
        <div v-else class="raw-editor">
          <el-input
            v-model="form.steps_content"
            type="textarea"
            :rows="14"
            :class="{ 'is-error-input': !!errors.steps }"
            class="raw-textarea"
            placeholder='{"steps":[{"type":"wait","seconds":2},{"type":"launch","app_package":"com.example.app"}]}'
            @input="onRawInput"
          />
          <div v-if="errors.steps" class="field-error">{{ errors.steps }}</div>
          <div v-else class="field-hint">脚本需为含 steps 数组的 JSON 对象；语法错误时会实时标红并提示行号</div>
        </div>
      </section>

      <!-- 分区 4：说明 -->
      <section class="hint-box">
        <div>复用场景：公共步骤为全局复用积木，可在可视化用例、套件前置 / 后置钩子一键引用，避免重复编写相同操作流程</div>
        <div>规范提示：步骤内可关联控件池、全局参数，适配多环境、多版本自动化执行</div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button class="btn-aux" @click="openReusePicker">套用步骤模板</el-button>
          <el-button class="btn-aux" @click="goElementPicker">拾取控件</el-button>
        </div>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>

  <el-dialog
    v-model="showReusePicker"
    title="套用步骤模板"
    width="480px"
    append-to-body
    destroy-on-close
  >
    <p class="tpl-tip">选择已有公共步骤，复制其执行流程快速复用（不会覆盖当前名称，除非名称为空）。</p>
    <el-select v-model="pickedReuseId" filterable placeholder="请选择公共步骤" style="width:100%">
      <el-option
        v-for="s in stepOptions"
        :key="s.id"
        :label="s.name"
        :value="s.id"
        :disabled="s.id === form.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="showReusePicker = false">取消</el-button>
      <el-button type="primary" :loading="importingReuse" @click="applyReuseTemplate">套用</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Close, ArrowDown } from '@element-plus/icons-vue'
import { commonStepApi } from '@/api'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const router = useRouter()
let uidSeq = 1

const PALETTE = [
  {
    key: 'app',
    label: '应用操作',
    items: [
      { type: 'launch', label: '启动应用', defaults: { app_package: 'com.example.app' } },
      { type: 'clear_cache', label: '清理缓存', defaults: { mode: 'disk' }, extraKey: 'disk' },
      { type: 'force_stop', label: '强制停止' },
      { type: 'dismiss_popup', label: '关闭弹窗' },
      { type: 'install_apk', label: '安装包' }
    ]
  },
  {
    key: 'ctrl',
    label: '控件操作',
    items: [
      { type: 'click', label: '点击控件', defaults: { locator_type: 'id', locator_value: '', element_name: '待拾取控件' } },
      { type: 'input', label: '文本输入', defaults: { locator_type: 'id', locator_value: '', text: '' } },
      { type: 'swipe', label: '滑动页面', defaults: { x1: 500, y1: 1200, x2: 500, y2: 400 } }
    ]
  },
  {
    key: 'wait',
    label: '等待',
    items: [{ type: 'wait', label: '等待秒数', defaults: { seconds: 2 } }]
  },
  {
    key: 'assert',
    label: '断言',
    items: [{ type: 'assert_text', label: '文本断言', defaults: { expected: '', locator_type: 'text', locator_value: '' } }]
  },
  {
    key: 'branch',
    label: '分支判断',
    items: [{
      type: 'branch',
      label: '条件分支',
      defaults: { condition: '控件存在', branch_true: '成立分支', branch_false: '否则分支' }
    }]
  },
  {
    key: 'loop',
    label: '循环',
    items: [{ type: 'loop', label: '循环步骤', defaults: { loop_count: 3, loop_body: '循环体' } }]
  }
]

const BUILTIN_TEMPLATES = {
  init_app: {
    name: 'APP 初始化',
    steps: [
      { type: 'wait', seconds: 2, enabled: true },
      { type: 'launch', app_package: 'com.example.app', enabled: true },
      { type: 'dismiss_popup', enabled: true, optional: true }
    ]
  },
  clear_cache: {
    name: '清理缓存',
    steps: [
      { type: 'clear_cache', mode: 'all', enabled: true },
      { type: 'wait', seconds: 1, enabled: true }
    ]
  },
  login: {
    name: '账号登录',
    steps: [
      { type: 'launch', app_package: 'com.example.app', enabled: true },
      { type: 'input', name: '输入账号', locator_type: 'id', locator_value: 'username', text: '${username}', enabled: true },
      { type: 'input', name: '输入密码', locator_type: 'id', locator_value: 'password', text: '${password}', enabled: true },
      { type: 'click', name: '点击登录', locator_type: 'id', locator_value: 'login_btn', enabled: true },
      { type: 'wait', seconds: 2, enabled: true }
    ]
  },
  dismiss_popup: {
    name: '关闭弹窗',
    steps: [{ type: 'dismiss_popup', enabled: true }]
  },
  exit_app: {
    name: '退出应用',
    steps: [
      { type: 'force_stop', enabled: true },
      { type: 'clear_cache', mode: 'memory', enabled: true }
    ]
  }
}

const TYPE_LABELS = {
  wait: '等待',
  launch: '启动应用',
  launch_app: '启动应用',
  clear_cache: '清理缓存',
  force_stop: '强制停止',
  dismiss_popup: '关闭弹窗',
  install_apk: '安装包',
  click: '点击',
  input: '输入',
  swipe: '滑动',
  assert_text: '文本断言',
  branch: '分支判断',
  loop: '循环',
  shell: 'Shell',
  invoke_common: '调用公共步骤'
}

const saving = ref(false)
const editMode = ref('visual')
const snapshot = ref('')
const visualSteps = ref([])
const dragFromIndex = ref(-1)
const dragOverIndex = ref(-1)
const paletteDragItem = ref(null)
const showReusePicker = ref(false)
const pickedReuseId = ref(null)
const stepOptions = ref([])
const importingReuse = ref(false)

const form = reactive(blankForm())
const errors = reactive({ name: '', steps: '' })

const isEdit = computed(() => !!form.id)

function blankForm() {
  return { id: null, name: '', description: '', steps_content: '{"steps":[]}' }
}

function clearErrors() {
  errors.name = ''
  errors.steps = ''
}

function takeSnapshot() {
  return JSON.stringify({ form: { ...form }, editMode: editMode.value, visualSteps: visualSteps.value })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  errors.name = form.name?.trim() ? '' : '请填写公共步骤业务名称'
}

function withUid(step) {
  return { ...step, _uid: uidSeq++ }
}

function stripUid(step) {
  const { _uid, ...rest } = step
  return rest
}

function stepTypeLabel(t) {
  return TYPE_LABELS[t] || t || '步骤'
}

function stepTagType(t) {
  if (['click', 'input', 'swipe'].includes(t)) return 'primary'
  if (['assert_text'].includes(t)) return 'success'
  if (['branch', 'loop'].includes(t)) return 'warning'
  if (['force_stop', 'clear_cache'].includes(t)) return 'danger'
  return 'info'
}

function stepDesc(s) {
  if (s.name) return s.name
  const target = formatStepTarget(s)
  if (target) return `${stepTypeLabel(s.type)} · ${target}`
  if (s.type === 'clear_cache') {
    return ({ disk: '清理磁盘缓存', memory: '杀进程', all: '清理全部缓存' })[s.mode] || '清理缓存'
  }
  if (s.type === 'branch') return `若 ${s.condition || '条件'} → ${s.branch_true || '成立'} / ${s.branch_false || '否则'}`
  if (s.type === 'loop') return `循环 ${s.loop_count || 1} 次`
  if (s.type === 'dismiss_popup') return '关闭干扰弹窗'
  if (s.type === 'force_stop') return '强制停止应用'
  if (s.type === 'install_apk') return '安装应用包'
  return stepTypeLabel(s.type)
}

function stepSub(s) {
  return formatStepLocator(s) || ''
}

function findJsonErrorLine(text) {
  try {
    JSON.parse(text || '{}')
    return null
  } catch (e) {
    const msg = String(e.message || '')
    const m = msg.match(/position\s+(\d+)/i) || msg.match(/at position\s+(\d+)/i)
    if (m) {
      const pos = Number(m[1])
      const line = (text || '').slice(0, pos).split('\n').length
      return { line, message: `步骤脚本格式错误，约在第 ${line} 行` }
    }
    return { line: null, message: '步骤脚本格式错误，请检查 JSON 语法' }
  }
}

function parseStepsContent(text) {
  const err = findJsonErrorLine(text)
  if (err) return { ok: false, error: err.message, steps: [] }
  try {
    const obj = JSON.parse(text || '{"steps":[]}')
    if (Array.isArray(obj)) {
      return { ok: true, error: '', steps: obj }
    }
    if (!obj || typeof obj !== 'object') {
      return { ok: false, error: '脚本必须是 JSON 对象，且包含 steps 数组', steps: [] }
    }
    if (obj.steps == null) {
      return { ok: true, error: '', steps: [] }
    }
    if (!Array.isArray(obj.steps)) {
      return { ok: false, error: 'steps 必须是数组', steps: [] }
    }
    return { ok: true, error: '', steps: obj.steps }
  } catch {
    return { ok: false, error: '步骤脚本格式错误', steps: [] }
  }
}

function stringifySteps(steps) {
  return JSON.stringify({ steps: steps.map(stripUid) }, null, 2)
}

function syncRawFromVisual() {
  form.steps_content = stringifySteps(visualSteps.value)
  errors.steps = ''
}

function applyStepsToVisual(steps) {
  visualSteps.value = (steps || []).map(s => withUid({ ...s, enabled: s.enabled !== false }))
}

function addStepFromPalette(item) {
  const step = withUid({
    type: item.type,
    enabled: true,
    ...(item.defaults || {})
  })
  visualSteps.value.push(step)
  syncRawFromVisual()
}

function onPaletteDragStart(item, ev) {
  paletteDragItem.value = item
  dragFromIndex.value = -1
  ev.dataTransfer?.setData('text/plain', 'palette')
}

function onCanvasDrop() {
  if (paletteDragItem.value) {
    addStepFromPalette(paletteDragItem.value)
    paletteDragItem.value = null
  }
  dragOverIndex.value = -1
}

function onStepDragStart(i, ev) {
  dragFromIndex.value = i
  paletteDragItem.value = null
  ev.dataTransfer?.setData('text/plain', String(i))
}

function onStepDrop(toIndex) {
  const from = dragFromIndex.value
  dragOverIndex.value = -1
  if (paletteDragItem.value) {
    const step = withUid({
      type: paletteDragItem.value.type,
      enabled: true,
      ...(paletteDragItem.value.defaults || {})
    })
    visualSteps.value.splice(toIndex, 0, step)
    paletteDragItem.value = null
    syncRawFromVisual()
    return
  }
  if (from < 0 || from === toIndex) return
  const arr = visualSteps.value
  const [item] = arr.splice(from, 1)
  arr.splice(toIndex, 0, item)
  dragFromIndex.value = -1
  syncRawFromVisual()
}

function moveStep(i, delta) {
  const t = i + delta
  if (t < 0 || t >= visualSteps.value.length) return
  const arr = visualSteps.value
  ;[arr[i], arr[t]] = [arr[t], arr[i]]
  syncRawFromVisual()
}

function copyStep(i) {
  const src = visualSteps.value[i]
  visualSteps.value.splice(i + 1, 0, withUid({ ...stripUid(src) }))
  syncRawFromVisual()
  ElMessage.success('步骤已复制')
}

function removeStep(i) {
  visualSteps.value.splice(i, 1)
  syncRawFromVisual()
}

function onRawInput() {
  const parsed = parseStepsContent(form.steps_content)
  errors.steps = parsed.ok ? '' : parsed.error
}

function onModeChange(mode) {
  if (mode === 'visual') {
    const parsed = parseStepsContent(form.steps_content)
    if (!parsed.ok) {
      ElMessage.warning('当前脚本文本有误，请先修正后再切换可视化')
      editMode.value = 'raw'
      return
    }
    applyStepsToVisual(parsed.steps)
    syncRawFromVisual()
  } else {
    syncRawFromVisual()
    onRawInput()
  }
}

function applyBuiltinTemplate(cmd) {
  const tpl = BUILTIN_TEMPLATES[cmd]
  if (!tpl) return
  applyStepsToVisual(tpl.steps)
  syncRawFromVisual()
  if (!form.name?.trim()) {
    form.name = tpl.name
    errors.name = ''
  }
  ElMessage.success(`已填充「${tpl.name}」模板`)
}

function applyRow(row) {
  clearErrors()
  editMode.value = 'visual'
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      description: row.description || '',
      steps_content: row.steps_content || '{"steps":[]}'
    })
  } else {
    Object.assign(form, blankForm())
  }
  try {
    const pretty = JSON.stringify(JSON.parse(form.steps_content || '{"steps":[]}'), null, 2)
    form.steps_content = pretty
  } catch { /* keep */ }
  const parsed = parseStepsContent(form.steps_content)
  if (parsed.ok) applyStepsToVisual(parsed.steps)
  else {
    editMode.value = 'raw'
    visualSteps.value = []
    errors.steps = parsed.error
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前步骤内容未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    done()
  } catch { /* stay */ }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前步骤内容未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空名称、描述与全部步骤流程，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  visualSteps.value = []
  clearErrors()
  ElMessage.success('已重置')
}

async function openReusePicker() {
  try {
    stepOptions.value = (await commonStepApi.list()).data || []
  } catch {
    stepOptions.value = []
  }
  if (!stepOptions.value.length) {
    ElMessage.warning('暂无可用的公共步骤模板')
    return
  }
  pickedReuseId.value = null
  showReusePicker.value = true
}

async function applyReuseTemplate() {
  if (!pickedReuseId.value) {
    ElMessage.warning('请选择要套用的公共步骤')
    return
  }
  importingReuse.value = true
  try {
    let row = stepOptions.value.find(s => s.id === pickedReuseId.value)
    try {
      const res = await commonStepApi.get(pickedReuseId.value)
      row = res.data || row
    } catch { /* use list row */ }
    const content = row?.steps_content || '{"steps":[]}'
    form.steps_content = content
    try {
      form.steps_content = JSON.stringify(JSON.parse(content), null, 2)
    } catch { /* keep */ }
    if (!form.name?.trim() && row?.name) {
      form.name = `${row.name}_复用`
      errors.name = ''
    }
    if (!form.description?.trim() && row?.description) {
      form.description = row.description
    }
    const parsed = parseStepsContent(form.steps_content)
    if (parsed.ok) {
      applyStepsToVisual(parsed.steps)
      syncRawFromVisual()
      errors.steps = ''
    } else {
      editMode.value = 'raw'
      errors.steps = parsed.error
    }
    showReusePicker.value = false
    ElMessage.success('已套用步骤流程，可继续修改')
  } finally {
    importingReuse.value = false
  }
}

async function leaveToPicker() {
  if (isDirty()) {
    try {
      await ElMessageBox.confirm('当前步骤内容未保存，是否确认关闭并前往控件拾取？', '未保存确认', {
        type: 'warning',
        confirmButtonText: '仍要前往',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
  router.push('/element-picker')
}

function goElementPicker() {
  leaveToPicker()
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写公共步骤业务名称'
    ok = false
  }
  if (editMode.value === 'visual') syncRawFromVisual()
  const parsed = parseStepsContent(form.steps_content)
  if (!parsed.ok) {
    errors.steps = parsed.error
    if (editMode.value === 'visual') editMode.value = 'raw'
    ok = false
  }
  return { ok, steps: parsed.steps || [] }
}

async function submit() {
  const { ok, steps } = validateAll()
  if (!ok) {
    ElMessage.warning('请完善必填项并修正步骤脚本错误')
    return
  }
  if (!steps.length) {
    try {
      await ElMessageBox.confirm('当前未配置任何执行步骤，确认保存空公共步骤吗？', '空步骤确认', {
        type: 'warning',
        confirmButtonText: '仍要保存',
        cancelButtonText: '返回配置'
      })
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description || '',
      steps_content: stringifySteps(steps.map(s => (s._uid != null ? stripUid(s) : s)))
    }
    // 保证 raw 与 visual 一致
    if (editMode.value === 'visual') {
      payload.steps_content = stringifySteps(visualSteps.value)
    } else {
      const parsed = parseStepsContent(form.steps_content)
      payload.steps_content = stringifySteps(parsed.steps)
    }
    if (isEdit.value) {
      await commonStepApi.update(form.id, payload)
      ElMessage.success('公共步骤已更新')
    } else {
      await commonStepApi.create({ ...payload, status: 'active' })
      ElMessage.success('公共步骤已添加')
    }
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
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
}
.dlg-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.dlg-close { margin-right: -6px; }

.dlg-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 72vh;
  overflow: auto;
}

.section-card {
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.mode-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-tpl {
  --el-button-bg-color: #fff;
  --el-button-border-color: #cbd5e1;
}

.field-block { margin-bottom: 12px; }
.field-block:last-child { margin-bottom: 0; }
.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.req { color: #ef4444; }
.tip-label {
  border-bottom: 1px dashed #94a3b8;
  cursor: help;
}
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}
.field-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}
:deep(.is-error-input .el-input__wrapper),
:deep(.is-error-input .el-textarea__inner) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.visual-layout {
  display: grid;
  grid-template-columns: 168px 1fr;
  gap: 12px;
  min-height: 320px;
}
.palette {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  overflow: auto;
  max-height: 420px;
}
.palette-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}
.palette-group { margin-bottom: 10px; }
.palette-group-name {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
  font-weight: 600;
}
.palette-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 6px;
  padding: 6px 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #334155;
  cursor: grab;
}
.palette-item:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}
.picker-link {
  width: 100%;
  margin-top: 4px;
}

.canvas {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  overflow: auto;
  max-height: 420px;
}
.canvas-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}
.canvas-hint {
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
}
.canvas-empty {
  padding: 28px 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}
.canvas-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.canvas-step.is-drag-over {
  border-color: #3b82f6;
  background: #eff6ff;
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  font-size: 14px;
  user-select: none;
}
.step-idx {
  width: 22px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
}
.step-main {
  flex: 1;
  min-width: 0;
}
.step-desc {
  font-size: 13px;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.step-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.step-acts {
  display: flex;
  flex-shrink: 0;
  gap: 0;
}
.canvas-foot {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.raw-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.55;
}

.hint-box {
  padding: 12px 14px;
  background: #f1f5f9;
  border-radius: 10px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.75;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
.tpl-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

@media (max-width: 800px) {
  .visual-layout {
    grid-template-columns: 1fr;
  }
}
</style>
