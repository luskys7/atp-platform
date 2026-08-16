<template>
  <div class="step-add-panel">
    <div class="add-toolbar">
      <div class="add-toolbar-left">
        <strong>已添加步骤</strong>
        <span class="add-count">共 {{ addedSteps.length }} 步</span>
        <el-button
          v-if="editingIndex !== null || insertAtIndex !== null"
          size="small"
          text
          type="primary"
          @click="onCancelEdit"
        >{{ editingIndex !== null ? '取消编辑' : '取消插入' }}</el-button>
      </div>
      <el-button type="primary" @click="startAddStep">添加步骤</el-button>
    </div>

    <div class="added-steps-body">
      <el-empty
        v-if="!addedSteps.length"
        description="暂无已添加步骤，点击右上角「添加步骤」开始配置"
        :image-size="80"
      />
      <div v-else class="added-steps-list">
        <div
          v-for="(step, idx) in addedSteps"
          :key="step.id ?? idx"
          class="added-step-row"
          :class="{ editing: editingIndex === idx, disabled: step.enabled === false }"
        >
          <span class="step-idx">{{ idx + 1 }}</span>
          <el-tag size="small" effect="plain" :type="tagType(step.type)">{{ typeLabel(step) }}</el-tag>
          <span class="step-desc" :title="summaryOf(step)">{{ summaryOf(step) || '—' }}</span>
          <div class="step-row-actions">
            <el-button size="small" type="primary" link @click="emit('edit', idx)">编辑</el-button>
            <el-button size="small" type="danger" link @click="emit('remove', idx)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤信息弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="步骤信息"
      width="880px"
      top="6vh"
      append-to-body
      destroy-on-close
      class="step-info-dialog"
      @closed="onDialogClosed"
    >
      <el-form
        :model="model"
        label-width="128px"
        size="large"
        class="step-info-form"
        @submit.prevent
      >
        <el-form-item label="步骤类型" required>
          <el-cascader
            :model-value="cascaderPath"
            :options="cascaderOptions"
            :props="{ expandTrigger: 'hover', label: 'label', value: 'value', children: 'children' }"
            filterable
            clearable
            size="large"
            style="width:100%"
            placeholder="请选择步骤类型"
            popper-class="step-type-cascader-popper"
            @update:model-value="onCascaderChange"
          />
        </el-form-item>

        <el-divider class="step-info-divider">
          <el-icon :size="16"><Document /></el-icon>
        </el-divider>

        <template v-if="activeLeaf">
          <template v-if="needsControlPick">
            <el-form-item v-if="coordsMode === 'swipe'" label="起点控件" required>
              <div class="pool-pick-row">
                <el-select
                  :model-value="model.swipe_start_name || undefined"
                  filterable
                  clearable
                  placeholder="已选控件 请选择"
                  style="width:100%"
                  @clear="model.swipe_start_name = ''"
                >
                  <el-option
                    v-if="model.swipe_start_name"
                    :label="model.swipe_start_name"
                    :value="model.swipe_start_name"
                  />
                </el-select>
                <el-button type="primary" plain @click="emit('pool', 'swipe_start')">选择</el-button>
              </div>
            </el-form-item>
            <el-form-item v-if="coordsMode === 'swipe'" label="终点控件" required>
              <div class="pool-pick-row">
                <el-select
                  :model-value="model.swipe_end_name || undefined"
                  filterable
                  clearable
                  placeholder="已选控件 请选择"
                  style="width:100%"
                  @clear="model.swipe_end_name = ''"
                >
                  <el-option
                    v-if="model.swipe_end_name"
                    :label="model.swipe_end_name"
                    :value="model.swipe_end_name"
                  />
                </el-select>
                <el-button type="primary" plain @click="emit('pool', 'swipe_end')">选择</el-button>
              </div>
            </el-form-item>

            <el-form-item v-else-if="coordsMode === 'tap'" label="坐标控件" required>
              <div class="pool-pick-row">
                <el-select
                  :model-value="model.element_name || undefined"
                  filterable
                  clearable
                  placeholder="已选控件 请选择"
                  style="width:100%"
                  @clear="clearTapControl"
                >
                  <el-option
                    v-if="model.element_name"
                    :label="model.element_name"
                    :value="model.element_name"
                  />
                </el-select>
                <el-button type="primary" plain @click="emit('pool', 'tap')">选择</el-button>
              </div>
            </el-form-item>

            <el-form-item v-else label="控件元素" required>
              <div class="pool-pick-row">
                <el-select
                  :model-value="controlSummary || undefined"
                  filterable
                  clearable
                  placeholder="已选控件 请选择"
                  style="width:100%"
                  @clear="clearLocatorControl"
                >
                  <el-option
                    v-if="controlSummary"
                    :label="controlSummary"
                    :value="controlSummary"
                  />
                </el-select>
                <el-button type="primary" plain @click="emit('pool', 'locator')">选择</el-button>
              </div>
            </el-form-item>
          </template>

          <el-form-item v-if="isExistsAssert" label="存在与否" required>
            <el-select v-model="existExpect" placeholder="请选择" style="width:100%">
              <el-option label="存在" value="exists" />
              <el-option label="不存在" value="not_exists" />
            </el-select>
          </el-form-item>

          <el-form-item
            v-for="key in dialogExtraFields"
            :key="`${catalogId}-${key}`"
            :label="fieldLabel(key)"
            :required="isRequired(key)"
          >
            <template v-if="key === 'common_step'">
              <el-select v-model="model.common_step" filterable placeholder="选择公共步骤" style="width:100%">
                <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
              </el-select>
            </template>
            <template v-else-if="fieldMeta(key)?.kind === 'select'">
              <el-select
                v-model="model[key]"
                clearable
                style="width:100%"
                @change="onFieldChanged(key)"
              >
                <el-option
                  v-for="o in fieldMeta(key).options"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </template>
            <template v-else-if="fieldMeta(key)?.kind === 'number'">
              <el-input-number
                v-model="model[key]"
                :min="fieldMeta(key).min ?? 0"
                :max="fieldMeta(key).max ?? 99999"
                style="width:100%"
                @change="onFieldChanged(key)"
              />
            </template>
            <template v-else-if="fieldMeta(key)?.kind === 'textarea' || fieldMeta(key)?.kind === 'code'">
              <el-input
                v-model="model[key]"
                type="textarea"
                :rows="fieldMeta(key)?.kind === 'code' ? 6 : 3"
                :placeholder="fieldMeta(key)?.placeholder"
                @input="onFieldChanged(key)"
              />
            </template>
            <template v-else>
              <el-input
                v-model="model[key]"
                :placeholder="fieldMeta(key)?.placeholder"
                clearable
                @input="onFieldChanged(key)"
              />
            </template>
          </el-form-item>
        </template>
        <p v-else class="pick-type-hint">请先选择步骤类型，再配置控件与其它参数</p>

        <el-form-item label="逻辑处理">
          <div class="field-with-tip">
            <el-select v-model="model.logic_process" style="width:100%">
              <el-option
                v-for="o in logicProcessOptions"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
            <el-tooltip
              content="生成真实可执行控制流：会插入 if/else if/else/while 判断头、块内步骤与结束块；保存后脚本按条件跳转（非仅展示选项）"
              placement="top"
            >
              <el-icon class="tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>

        <el-form-item label="异常处理">
          <div class="field-with-tip">
            <el-select v-model="model.on_fail" style="width:100%">
              <el-option
                v-for="o in onFailOptions"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
            <el-tooltip content="本步失败时的处理策略，可覆盖用例级默认策略" placement="top">
              <el-icon class="tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>

        <div v-if="locatorHint" class="field-warn">{{ locatorHint }}</div>
      </el-form>

      <template #footer>
        <div class="dialog-submit">
          <el-button type="primary" class="submit-btn" :disabled="!activeLeaf" @click="onSubmit">提交</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Document, QuestionFilled } from '@element-plus/icons-vue'
import {
  toCascaderOptions,
  getCatalogLeaf,
  findCatalogPath,
  resolveLeafFields,
  FIELD_META,
  ON_FAIL_OPTIONS,
  LOGIC_PROCESS_OPTIONS
} from '@/config/stepCatalog'
import {
  conditionLabel,
  conditionNeedsLocator,
  conditionNeedsExpected,
  conditionNeedsVarName
} from '@/config/commonStepCatalog'

const props = defineProps({
  modelValue: { type: Object, required: true },
  editingIndex: { type: Number, default: null },
  insertAtIndex: { type: Number, default: null },
  commonSteps: { type: Array, default: () => [] },
  catalogId: { type: String, default: '' },
  expandedKeys: { type: Array, default: () => [] },
  locatorHint: { type: String, default: '' },
  blankStep: { type: Object, default: () => ({}) },
  /** 当前用例已添加的步骤列表 */
  addedSteps: { type: Array, default: () => [] },
  typeLabelFn: { type: Function, default: null },
  summaryFn: { type: Function, default: null },
  tagTypeFn: { type: Function, default: null }
})

const emit = defineEmits([
  'update:modelValue',
  'update:catalogId',
  'update:expandedKeys',
  'submit',
  'cancel',
  'pick',
  'pool',
  'field-change',
  'create-common',
  'edit',
  'remove'
])

const cascaderOptions = toCascaderOptions()
const dialogVisible = ref(false)
const onFailOptions = ON_FAIL_OPTIONS
const logicProcessOptions = LOGIC_PROCESS_OPTIONS

const model = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const activeLeaf = computed(() => getCatalogLeaf(props.catalogId))
const activeFields = computed(() => resolveLeafFields(activeLeaf.value))
const needsLocator = computed(() => !!(activeLeaf.value?.needsLocator || activeFields.value.includes('locator_value')))
const coordsMode = computed(() => {
  const mode = activeLeaf.value?.needsCoords
  if (mode === 'tap' || mode === 'swipe') return mode
  if (activeFields.value.some(k => ['x', 'y'].includes(k))) return 'tap'
  if (activeFields.value.some(k => ['x1', 'y1', 'x2', 'y2', 'swipe_start_name', 'swipe_end_name'].includes(k))) return 'swipe'
  return ''
})
const hasConditionKind = computed(() => activeFields.value.includes('condition_kind'))
const conditionKind = computed(() => model.value.condition_kind || 'exists')
const needsControlPick = computed(() => {
  if (hasConditionKind.value && !conditionNeedsLocator(conditionKind.value)) return false
  return needsLocator.value || !!coordsMode.value
})

const isExistsAssert = computed(() =>
  ['assert_exists', 'assert_not_exists'].includes(model.value.type)
    || (activeLeaf.value?.type === 'assert_exists')
)

const existExpect = computed({
  get() {
    return model.value.type === 'assert_not_exists' ? 'not_exists' : 'exists'
  },
  set(v) {
    model.value.type = v === 'not_exists' ? 'assert_not_exists' : 'assert_exists'
  }
})

const HIDDEN_WHEN_POOL = new Set([
  'element_name', 'locator_type', 'locator_value',
  'x', 'y', 'x1', 'y1', 'x2', 'y2',
  'swipe_start_name', 'swipe_end_name'
])
const LOCATOR_FIELDS = new Set(['element_name', 'locator_type', 'locator_value'])
const DIALOG_DEDUP = new Set(['logic_process', 'on_fail', 'remark'])

const dialogExtraFields = computed(() =>
  activeFields.value.filter(k => {
    if (DIALOG_DEDUP.has(k)) return false
    if (needsControlPick.value && HIDDEN_WHEN_POOL.has(k)) return false
    if (isExistsAssert.value && (k === 'expected' || k === 'condition_kind')) return false
    if (hasConditionKind.value) {
      if (LOCATOR_FIELDS.has(k) && !conditionNeedsLocator(conditionKind.value)) return false
      if (k === 'condition' && conditionKind.value !== 'custom') return false
      if (k === 'expected' && !conditionNeedsExpected(conditionKind.value)) return false
      if (k === 'var_name' && !conditionNeedsVarName(conditionKind.value)) return false
      if (k === 'timeout' && (conditionNeedsVarName(conditionKind.value) || conditionKind.value === 'custom')) return false
    }
    return true
  })
)

const controlSummary = computed(() => {
  const name = (model.value.element_name || '').trim()
  if (name) return name
  if ((model.value.locator_value || '').trim()) return '已选控件（未命名）'
  return ''
})

const cascaderPath = computed(() => {
  if (!props.catalogId) return []
  return findCatalogPath(props.catalogId)
})

const KEEP_ON_SWITCH = new Set(['on_fail', 'logic_process', 'retry_count', 'enabled'])

watch(
  () => [props.editingIndex, props.insertAtIndex, props.catalogId],
  ([editIdx, insertIdx, catId]) => {
    if ((editIdx != null || insertIdx != null) && catId) {
      dialogVisible.value = true
    }
  }
)

function typeLabel(step) {
  if (typeof props.typeLabelFn === 'function') return props.typeLabelFn(step)
  return step?.type || '步骤'
}

function summaryOf(step) {
  if (typeof props.summaryFn === 'function') return props.summaryFn(step)
  return step?.element_name || step?.remark || ''
}

function tagType(type) {
  if (typeof props.tagTypeFn === 'function') return props.tagTypeFn(type)
  return ''
}

function fieldMeta(key) {
  if (key === 'element_name' && activeLeaf.value?.type === 'screenshot') {
    return { label: '截图名称', kind: 'text', placeholder: '例：蓝牙列表页' }
  }
  if (key === 'expected' && conditionNeedsVarName(conditionKind.value)) {
    return { label: '期望值', kind: 'text', placeholder: '例：AX17' }
  }
  if (key === 'var_name' && conditionNeedsVarName(conditionKind.value)) {
    return { label: '变量名', kind: 'text', placeholder: '例：product_id 或 {{product_id}}' }
  }
  return FIELD_META[key] || { label: key, kind: 'text' }
}

function fieldLabel(key) {
  if (key === 'element_name' && activeLeaf.value?.type === 'screenshot') return '截图名称'
  if (key === 'expected' && hasConditionKind.value && conditionNeedsVarName(conditionKind.value)) return '期望值'
  if (key === 'var_name' && hasConditionKind.value && conditionNeedsVarName(conditionKind.value)) return '变量名'
  return fieldMeta(key).label || key
}

function isRequired(key) {
  if (hasConditionKind.value) {
    if (key === 'var_name' && conditionNeedsVarName(conditionKind.value)) return true
    if (key === 'expected' && conditionNeedsExpected(conditionKind.value)) return true
    if (key === 'condition' && conditionKind.value === 'custom') return true
  }
  return ['element_name', 'locator_value', 'seconds', 'expected', 'text', 'common_step', 'script_code'].includes(key)
}

function syncConditionLabel() {
  if (!hasConditionKind.value) return
  const kind = conditionKind.value
  if (kind === 'custom') return
  model.value.condition = conditionLabel(kind, model.value.condition, {
    var_name: model.value.var_name,
    expected: model.value.expected
  })
}

function onFieldChanged(key) {
  if (key === 'condition_kind' || key === 'var_name' || key === 'expected') {
    syncConditionLabel()
  }
  emit('field-change', key)
}

function buildFormForLeaf(leaf, { keepCurrent = false } = {}) {
  const base = { ...(props.blankStep || {}) }
  const current = props.modelValue || {}
  if (keepCurrent) {
    Object.assign(base, current, { type: leaf.type }, leaf.extras || {})
  } else {
    for (const k of KEEP_ON_SWITCH) {
      if (current[k] != null && current[k] !== '') base[k] = current[k]
    }
    Object.assign(base, { type: leaf.type }, leaf.extras || {})
  }
  if (base.logic_process == null || base.logic_process === '') base.logic_process = 'none'
  if (base.on_fail == null || base.on_fail === '') base.on_fail = 'interrupt'
  if (leaf.needsLocator && (base.wait_timeout == null || base.wait_timeout === '')) {
    base.wait_timeout = 10
  }
  if (leaf.type === 'wait' && !base.wait_mode) base.wait_mode = 'fixed'
  if (leaf.type === 'wait' && (base.seconds == null || base.seconds === '')) base.seconds = 2
  if (leaf.type === 'swipe') {
    base.x1 ??= 500
    base.y1 ??= 1200
    base.x2 ??= 500
    base.y2 ??= 400
    base.duration_ms ??= 300
  }
  if (leaf.type === 'assert_compare' && !base.op) {
    base.op = leaf.extras?.op || 'contains'
  }
  base.catalog_id = leaf.id
  return base
}

function applyLeaf(leaf, opts = {}) {
  if (!leaf) return
  const keepCurrent = opts.keepCurrent ?? (props.editingIndex != null)
  emit('update:catalogId', leaf.id)
  emit('update:modelValue', buildFormForLeaf(leaf, { keepCurrent }))
  const path = findCatalogPath(leaf.id)
  emit('update:expandedKeys', [...new Set([...props.expandedKeys, ...path.slice(0, -1)])])
  emit('field-change', 'locator_value')
  if (opts.openDialog !== false) dialogVisible.value = true
}

function onCascaderChange(val) {
  if (!Array.isArray(val) || !val.length) {
    emit('update:catalogId', '')
    return
  }
  const leafId = val[val.length - 1]
  const leaf = getCatalogLeaf(leafId)
  if (leaf) applyLeaf(leaf, { keepCurrent: props.editingIndex != null, openDialog: false })
}

function startAddStep() {
  emit('cancel')
  emit('update:catalogId', '')
  if (model.value.logic_process == null || model.value.logic_process === '') {
    model.value.logic_process = 'none'
  }
  if (model.value.on_fail == null || model.value.on_fail === '') {
    model.value.on_fail = 'interrupt'
  }
  dialogVisible.value = true
}

function openStepDialog() {
  if (model.value.logic_process == null || model.value.logic_process === '') {
    model.value.logic_process = 'none'
  }
  if (model.value.on_fail == null || model.value.on_fail === '') {
    model.value.on_fail = 'interrupt'
  }
  dialogVisible.value = true
}

function onSubmit() {
  if (!activeLeaf.value) return
  emit('submit')
  dialogVisible.value = false
}

function onCancelEdit() {
  dialogVisible.value = false
  emit('cancel')
}

function onDialogClosed() {}

function clearTapControl() {
  model.value.element_name = ''
  model.value.x = 0
  model.value.y = 0
}

function clearLocatorControl() {
  model.value.element_name = ''
  model.value.locator_type = ''
  model.value.locator_value = ''
  model.value.pool_id = null
}

defineExpose({
  selectCatalog: (id, opts) => applyLeaf(getCatalogLeaf(id), opts),
  applyLeaf,
  openStepDialog,
  startAddStep
})
</script>

<style scoped>
.step-add-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 360px;
  gap: 12px;
}
.add-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid var(--atp-border-neutral, #e8edf3);
  border-radius: 12px;
  flex-shrink: 0;
}
.add-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.add-toolbar-left strong {
  font-size: 14px;
  color: #0f172a;
}
.add-count {
  font-size: 12px;
  color: #94a3b8;
}
.added-steps-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--atp-border-neutral, #e8edf3);
  border-radius: 12px;
  background: #fff;
  padding: 8px;
}
.added-steps-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.added-step-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.added-step-row:hover {
  background: #f1f5f9;
}
.added-step-row.editing {
  border-color: var(--atp-primary, #0284c7);
  background: #f0f9ff;
}
.added-step-row.disabled {
  opacity: 0.55;
}
.step-idx {
  width: 22px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  flex-shrink: 0;
}
.step-desc {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-row-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.pool-pick-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.field-with-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.tip-icon {
  color: #94a3b8;
  cursor: help;
  flex-shrink: 0;
}
.field-warn {
  margin: 0 0 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
}
.pick-type-hint {
  margin: 0 0 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
}
.dialog-submit {
  display: flex;
  justify-content: center;
  width: 100%;
}
.submit-btn {
  min-width: 140px;
  height: 40px;
  padding: 0 28px;
}
</style>

<style>
.step-info-dialog .el-dialog__header {
  padding: 20px 28px 12px;
}
.step-info-dialog .el-dialog__title {
  font-size: 18px;
  font-weight: 700;
}
.step-info-dialog .el-dialog__body {
  padding: 8px 28px 20px;
  min-height: 0;
  max-height: calc(88vh - 140px);
  overflow-y: auto;
}
.step-info-dialog .step-info-form .el-form-item {
  margin-bottom: 22px;
}
.step-info-dialog .step-info-form .el-form-item__label {
  line-height: 40px;
  height: 40px;
  color: #334155;
  font-weight: 500;
}
.step-info-dialog .step-info-form .el-form-item__content {
  line-height: 40px;
}
.step-info-dialog .step-info-divider {
  margin: 4px 0 28px;
}
.step-info-dialog .step-info-divider .el-divider__text {
  background: #fff;
  padding: 0 14px;
  color: #94a3b8;
}
.step-info-dialog .el-dialog__footer {
  border-top: 1px solid #f1f5f9;
  padding: 20px 28px 24px;
}
.step-info-dialog .pick-type-hint {
  margin: 0 0 24px;
  padding: 14px 16px;
}

/* 步骤类型级联下拉：挂载在 body，需全局类名；宽度随实际列数伸缩，避免右侧空列留白 */
.step-type-cascader-popper.el-cascader__dropdown,
.step-type-cascader-popper {
  min-width: 0 !important;
}
.step-type-cascader-popper .el-cascader-panel {
  font-size: 15px;
  height: auto !important;
  max-height: none;
  align-items: flex-start;
}
/* 一级分类：完整展示，不滚动 */
.step-type-cascader-popper .el-cascader-menu:first-child {
  min-width: 200px;
  width: max-content;
  max-width: 280px;
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
}
.step-type-cascader-popper .el-cascader-menu:first-child .el-scrollbar {
  height: auto !important;
  max-height: none !important;
}
.step-type-cascader-popper .el-cascader-menu:first-child .el-scrollbar__wrap {
  max-height: none !important;
  height: auto !important;
  overflow: visible !important;
}
.step-type-cascader-popper .el-cascader-menu:first-child .el-scrollbar__bar {
  display: none !important;
}
/* 二、三级：选项多时再滚动 */
.step-type-cascader-popper .el-cascader-menu:not(:first-child) {
  min-width: 200px;
  width: max-content;
  max-width: 280px;
  height: auto !important;
  max-height: min(480px, 65vh);
  overflow-x: hidden;
  overflow-y: auto;
}
.step-type-cascader-popper .el-cascader-node {
  padding: 4px 24px 4px 16px;
  height: 40px;
  line-height: 40px;
}
.step-type-cascader-popper .el-cascader-node__label {
  font-size: 15px;
  padding: 0 8px;
}
.step-type-cascader-popper .el-cascader__search-input {
  font-size: 15px;
}
</style>
