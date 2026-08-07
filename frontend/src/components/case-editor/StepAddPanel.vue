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
          :type="q.id === catalogId ? 'primary' : (q.primary ? 'primary' : 'default')"
          :plain="q.id !== catalogId"
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
          <span>{{ editingIndex !== null ? `编辑第 ${editingIndex + 1} 步` : '步骤参数' }}</span>
          <el-tag v-if="activeLeaf" size="small" type="primary" effect="plain">{{ activeLeaf.label }}</el-tag>
          <el-button v-if="editingIndex !== null" size="small" text type="primary" @click="$emit('cancel')">取消编辑</el-button>
        </div>

        <el-empty v-if="!activeLeaf" description="请从左侧树或上方快捷按钮选择指令" :image-size="72" />

        <el-form v-else :model="model" label-width="118px" size="small" class="dyn-form" @submit.prevent>
          <el-form-item v-for="key in activeFields" :key="`${catalogId}-${key}`" :label="fieldLabel(key)" :required="isRequired(key)">
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
          <div v-else-if="needsLocator" class="field-tip">可手动填写定位，或使用「拾取控件 / 元素库」回填</div>

          <div class="form-actions">
            <el-button type="primary" @click="$emit('submit')">
              {{ editingIndex !== null ? '保存步骤修改' : '添加步骤' }}
            </el-button>
            <el-button v-if="needsLocator" type="success" plain @click="$emit('pick')">拾取控件</el-button>
            <el-button v-if="needsLocator" plain @click="$emit('pool')">元素库</el-button>
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

const props = defineProps({
  modelValue: { type: Object, required: true },
  editingIndex: { type: Number, default: null },
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
  return FIELD_META[key] || { label: key, kind: 'text' }
}

function fieldLabel(key) {
  return fieldMeta(key).label || key
}

function isRequired(key) {
  return ['element_name', 'locator_value', 'seconds', 'expected', 'text', 'common_step', 'script_code'].includes(key)
}

function onFieldChanged(key) {
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
  gap: 10px;
}
.quick-bar {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  background: #f8fbff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  flex-shrink: 0;
}
.quick-bar-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  padding-top: 6px;
  flex-shrink: 0;
}
.quick-bar-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.quick-btn.is-active {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
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
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
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
  margin-bottom: 8px;
  flex-shrink: 0;
}
.tree-filter { margin-bottom: 8px; flex-shrink: 0; }
.step-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: transparent;
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
  color: var(--el-color-primary);
  font-weight: 600;
}
.dyn-form {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid #eef2f7;
  position: sticky;
  bottom: 0;
  background: #fff;
  padding-bottom: 4px;
}
.field-warn {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #ea580c;
}
.field-tip {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
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
