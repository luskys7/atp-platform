<template>
  <div class="step-add-panel">
    <!-- 高频快捷 -->
    <div class="quick-bar">
      <span class="quick-bar-label">常用</span>
      <div class="quick-bar-btns">
        <el-button
          v-for="q in quickActions"
          :key="q.id"
          size="small"
          plain
          class="quick-btn"
          :class="{ 'is-active': q.id === catalogId }"
          @click="selectQuick(q.id)"
        >{{ q.label }}</el-button>
      </div>
    </div>

    <div class="add-body">
      <div class="tree-pane">
        <div class="pane-title">完整指令分类</div>
        <el-input
          v-model="treeFilter"
          clearable
          size="small"
          placeholder="搜索指令"
          class="tree-filter"
        />
        <el-tree
          ref="treeRef"
          class="step-tree"
          :data="treeData"
          node-key="id"
          :props="{ label: 'label', children: 'children' }"
          highlight-current
          :default-expanded-keys="expandedKeys"
          :filter-node-method="filterNode"
          @node-click="onTreeClick"
          @node-expand="onExpand"
          @node-collapse="onCollapse"
        >
          <template #default="{ data }">
            <span
              class="tree-node"
              :class="{
                leaf: data.isLeaf,
                folder: !data.isLeaf,
                current: data.id === catalogId
              }"
            >{{ data.label }}</span>
          </template>
        </el-tree>
      </div>

      <div class="form-pane">
        <div class="pane-title">
          <span>
            {{
              editingIndex !== null
                ? `编辑第 ${editingIndex + 1} 步`
                : insertAtIndex !== null
                  ? `插入到第 ${insertAtIndex + 1} 步`
                  : '步骤参数'
            }}
          </span>
          <el-tag v-if="activeLeaf" size="small" type="primary" effect="plain">{{ activeLeaf.label }}</el-tag>
          <el-button
            v-if="editingIndex !== null || insertAtIndex !== null"
            size="small"
            text
            type="primary"
            @click="$emit('cancel')"
          >{{ editingIndex !== null ? '取消编辑' : '取消插入' }}</el-button>
        </div>

        <el-empty v-if="!activeLeaf" description="请从左侧树或上方快捷按钮选择指令" :image-size="72" />

        <el-form v-else :model="model" label-width="118px" size="small" class="dyn-form" @submit.prevent>
          <!-- 控件相关参数：统一从控件库选择 -->
          <template v-if="needsControlPick">
            <el-form-item v-if="coordsMode === 'swipe'" label="起点控件" required>
              <div class="pool-pick-row">
                <el-input
                  :model-value="model.swipe_start_name || ''"
                  readonly
                  placeholder="请从控件库选择起点坐标控件"
                />
                <el-button type="primary" plain @click="emit('pool', 'swipe_start')">控件库</el-button>
              </div>
            </el-form-item>
            <el-form-item v-if="coordsMode === 'swipe'" label="终点控件" required>
              <div class="pool-pick-row">
                <el-input
                  :model-value="model.swipe_end_name || ''"
                  readonly
                  placeholder="请从控件库选择终点坐标控件"
                />
                <el-button type="primary" plain @click="emit('pool', 'swipe_end')">控件库</el-button>
              </div>
            </el-form-item>

            <el-form-item v-else-if="coordsMode === 'tap'" label="坐标控件" required>
              <div class="pool-pick-row">
                <el-input
                  :model-value="model.element_name || ''"
                  readonly
                  placeholder="请从控件库选择坐标定位控件"
                />
                <el-button type="primary" plain @click="emit('pool', 'tap')">控件库</el-button>
              </div>
              <div v-if="model.element_name" class="pool-pick-meta">
                已回填坐标：({{ model.x ?? '-' }}, {{ model.y ?? '-' }})
              </div>
            </el-form-item>

            <el-form-item v-else label="目标控件" required>
              <div class="pool-pick-row">
                <el-input
                  :model-value="controlSummary"
                  readonly
                  placeholder="请从控件库选择控件"
                />
                <el-button type="primary" plain @click="emit('pool', 'locator')">控件库</el-button>
                <el-button type="success" plain @click="emit('pick')">投屏拾取</el-button>
              </div>
              <div v-if="model.locator_value" class="pool-pick-meta">
                {{ locatorTypeText }} · {{ model.locator_value }}
              </div>
            </el-form-item>
          </template>

          <el-form-item
            v-for="key in visibleFields"
            :key="`${catalogId}-${key}`"
            :label="fieldLabel(key)"
            :required="isRequired(key)"
          >
            <template v-if="key === 'common_step'">
              <el-select v-model="model.common_step" filterable placeholder="选择公共步骤" style="width:100%">
                <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
              </el-select>
              <el-button
                type="primary"
                link
                style="margin-top:6px"
                @click="emit('create-common')"
              >新建公共步骤</el-button>
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
                :rows="fieldMeta(key)?.kind === 'code' ? 8 : 3"
                :placeholder="fieldMeta(key)?.placeholder"
                class="script-code-input"
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

          <div v-if="locatorHint" class="field-warn">{{ locatorHint }}</div>
          <div v-else-if="needsControlPick" class="field-tip">控件参数请从「控件库」选择回填，勿手填定位/坐标</div>

          <div class="form-actions">
            <el-button type="primary" @click="$emit('submit')">
              {{ editingIndex !== null ? '保存步骤修改' : (insertAtIndex !== null ? '插入步骤' : '添加步骤') }}
            </el-button>
            <el-button
              v-if="needsControlPick"
              type="primary"
              plain
              @click="emit('pool', defaultPoolMode)"
            >控件库</el-button>
            <el-button v-if="needsLocator && !coordsMode" type="success" plain @click="$emit('pick')">投屏拾取</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import {
  toElTreeData,
  getCatalogLeaf,
  getQuickActions,
  findCatalogPath,
  resolveLeafFields,
  FIELD_META,
  DEFAULT_QUICK_ACTION_IDS
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
  /** 父组件提供的空白表单默认值工厂结果 */
  blankStep: { type: Object, default: () => ({}) }
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
  'create-common'
])

const treeRef = ref(null)
const treeFilter = ref('')
const treeData = toElTreeData()
const quickActions = getQuickActions(DEFAULT_QUICK_ACTION_IDS)

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
const defaultPoolMode = computed(() => {
  if (coordsMode.value === 'tap') return 'tap'
  if (coordsMode.value === 'swipe') return 'swipe_start'
  return 'locator'
})

/** 控件相关字段改由控件库区块展示，表单里不再手填 */
const HIDDEN_WHEN_POOL = new Set([
  'element_name', 'locator_type', 'locator_value',
  'x', 'y', 'x1', 'y1', 'x2', 'y2',
  'swipe_start_name', 'swipe_end_name'
])
const LOCATOR_FIELDS = new Set(['element_name', 'locator_type', 'locator_value'])
const visibleFields = computed(() =>
  activeFields.value.filter(k => {
    if (needsControlPick.value && HIDDEN_WHEN_POOL.has(k)) return false
    if (hasConditionKind.value) {
      if (LOCATOR_FIELDS.has(k) && !conditionNeedsLocator(conditionKind.value)) return false
      if (k === 'condition' && conditionKind.value !== 'custom') return false
      if (k === 'expected' && !conditionNeedsExpected(conditionKind.value)) return false
      if (k === 'var_name' && !conditionNeedsVarName(conditionKind.value)) return false
      if (k === 'timeout' && (conditionNeedsVarName(conditionKind.value) || conditionKind.value === 'custom')) return false
    } else if (k === 'var_name' && !activeFields.value.includes('var_name')) {
      return false
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

const locatorTypeText = computed(() => {
  const t = model.value.locator_type
  const opt = (FIELD_META.locator_type?.options || []).find(o => o.value === t)
  return opt?.label || t || '定位'
})

const KEEP_ON_SWITCH = new Set(['on_fail', 'retry_count', 'enabled'])

watch(treeFilter, (v) => {
  treeRef.value?.filter(v)
})

watch(() => props.catalogId, async (id) => {
  if (!id) return
  await nextTick()
  treeRef.value?.setCurrentKey(id)
  const path = findCatalogPath(id)
  if (path.length) {
    emit('update:expandedKeys', [...new Set([...props.expandedKeys, ...path.slice(0, -1)])])
  }
})

function filterNode(value, data) {
  if (!value) return true
  return String(data.label || '').toLowerCase().includes(String(value).toLowerCase())
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
  if (key === 'element_name' && activeLeaf.value?.type === 'screenshot') {
    return '截图名称'
  }
  if (key === 'expected' && hasConditionKind.value && conditionNeedsVarName(conditionKind.value)) {
    return '期望值'
  }
  if (key === 'var_name' && hasConditionKind.value && conditionNeedsVarName(conditionKind.value)) {
    return '变量名'
  }
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
    // 编辑态：保留已有字段，仅覆盖 type/extras
    Object.assign(base, current, { type: leaf.type }, leaf.extras || {})
  } else {
    // 新建切换指令：清掉无关字段，仅保留备注/失败策略
    for (const k of KEEP_ON_SWITCH) {
      if (current[k] != null && current[k] !== '') base[k] = current[k]
    }
    Object.assign(base, { type: leaf.type }, leaf.extras || {})
  }
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
  nextTick(() => {
    treeRef.value?.setCurrentKey(leaf.id)
    emit('field-change', 'locator_value')
  })
}

function onTreeClick(data) {
  if (!data?.isLeaf) return
  // 树选择视为切换指令：新建时重置；编辑时保留定位等
  applyLeaf(getCatalogLeaf(data.id), { keepCurrent: props.editingIndex != null })
}

function selectQuick(id) {
  applyLeaf(getCatalogLeaf(id), { keepCurrent: false })
}

function onExpand(data) {
  if (!data?.id) return
  if (!props.expandedKeys.includes(data.id)) {
    emit('update:expandedKeys', [...props.expandedKeys, data.id])
  }
}

function onCollapse(data) {
  if (!data?.id) return
  emit('update:expandedKeys', props.expandedKeys.filter(k => k !== data.id))
}

defineExpose({
  selectCatalog: (id, opts) => applyLeaf(getCatalogLeaf(id), opts),
  applyLeaf
})
</script>

<style scoped>
.step-add-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 12px;
}
.quick-bar {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #eef2ff 0%, #f8fafc 100%);
  border: 1px solid #e0e7ff;
  border-radius: 12px;
  flex-shrink: 0;
}
.quick-bar-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--atp-accent, #6366f1);
  padding-top: 6px;
  flex-shrink: 0;
}
.quick-bar-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.quick-btn {
  --el-button-bg-color: #fff;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #eef2ff;
  --el-button-hover-border-color: #c7d2fe;
  --el-button-hover-text-color: var(--atp-accent, #6366f1);
}
.quick-btn.is-active,
.quick-btn.is-active.is-plain {
  --el-button-bg-color: var(--atp-accent, #6366f1) !important;
  --el-button-border-color: var(--atp-accent, #6366f1) !important;
  --el-button-text-color: #fff !important;
  --el-button-hover-bg-color: #4f46e5 !important;
  --el-button-hover-border-color: #4f46e5 !important;
  --el-button-hover-text-color: #fff !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}
.add-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 12px;
}
.tree-pane,
.form-pane {
  border: 1px solid var(--atp-border-neutral, #e8edf3);
  border-radius: 12px;
  background: #fff;
  padding: 12px 14px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
}
.pane-title :deep(.el-tag) {
  --el-tag-bg-color: rgba(99, 102, 241, 0.1);
  --el-tag-border-color: #c7d2fe;
  --el-tag-text-color: var(--atp-accent, #6366f1);
}
.tree-filter { margin-bottom: 8px; flex-shrink: 0; }
.step-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: transparent;
}
.step-tree :deep(.el-tree-node__content) {
  border-radius: 6px;
  height: 30px;
}
.step-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(99, 102, 241, 0.1);
}
.tree-node.folder {
  font-weight: 700;
  color: #1e293b;
}
.tree-node.leaf {
  font-weight: 400;
  color: #334155;
}
.tree-node.current {
  color: var(--atp-accent, #6366f1);
  font-weight: 600;
}
.dyn-form {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}
.dyn-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #64748b;
}
.dyn-form :deep(.el-input__wrapper),
.dyn-form :deep(.el-select .el-select__wrapper) {
  min-height: 32px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}
.dyn-form :deep(.el-input__wrapper:hover),
.dyn-form :deep(.el-select .el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #c7d2fe inset;
}
.dyn-form :deep(.el-input__wrapper.is-focus),
.dyn-form :deep(.el-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--atp-accent, #6366f1) inset !important;
}
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
  position: sticky;
  bottom: 0;
  background: #fff;
  padding-bottom: 4px;
}
.pool-pick-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.pool-pick-row .el-input { flex: 1; }
.pool-pick-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  word-break: break-all;
}
.form-actions :deep(.el-button--primary) {
  --el-button-bg-color: var(--atp-primary, #0284c7);
  --el-button-border-color: var(--atp-primary, #0284c7);
}
.form-actions :deep(.el-button--success.is-plain) {
  --el-button-text-color: var(--atp-accent, #6366f1);
  --el-button-bg-color: rgba(99, 102, 241, 0.08);
  --el-button-border-color: #c7d2fe;
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: var(--atp-accent, #6366f1);
  --el-button-hover-border-color: var(--atp-accent, #6366f1);
}
.field-tip {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #64748b;
}
.field-warn {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #ea580c;
}
.script-code-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
@media (max-width: 1100px) {
  .add-body { grid-template-columns: 1fr; }
  .tree-pane { max-height: 240px; }
}
</style>
