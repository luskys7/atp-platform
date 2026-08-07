<template>
  <div class="ui-hierarchy-tree">
    <div class="tree-toolbar">
      <span class="tree-title">控件层级</span>
      <span v-if="nodeCount" class="tree-meta">{{ nodeCount }} 节点</span>
      <el-button size="small" text :loading="loading" :disabled="!ready" @click="$emit('refresh')">刷新</el-button>
    </div>
    <div v-if="truncated" class="tree-warn">树过大已截断，请优先点选目标区域控件</div>
    <div v-loading="loading" class="tree-body">
      <el-tree
        v-if="treeData.length"
        ref="treeRef"
        :data="treeData"
        node-key="id"
        highlight-current
        :indent="12"
        :expand-on-click-node="false"
        :default-expanded-keys="expandedKeys"
        :props="{ label: 'label', children: 'children' }"
        @node-click="onNodeClick"
      >
        <template #default="{ data }">
          <span class="tree-node" :class="{ clickable: data.clickable, selected: data.id === currentId }">
            <span class="tree-class">{{ data.class || 'node' }}</span>
            <span v-if="nodeTip(data)" class="tree-tip" :title="nodeTip(data)">{{ nodeTip(data) }}</span>
          </span>
        </template>
      </el-tree>
      <el-empty v-else-if="!loading" description="请先刷新 UI 树" :image-size="56" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  root: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  ready: { type: Boolean, default: false },
  nodeCount: { type: Number, default: 0 },
  truncated: { type: Boolean, default: false },
  currentId: { type: String, default: '' },
  selectBounds: { type: String, default: '' }
})

const emit = defineEmits(['select', 'refresh'])

const treeRef = ref(null)
const treeData = ref([])
const expandedKeys = ref([])

function nodeTip(data) {
  const tip = data.contentDesc || data.text || data.resourceId || ''
  return tip ? String(tip) : ''
}

function collectExpandKeys(node, depth = 0, acc = []) {
  if (!node) return acc
  if (node.id && depth < 3) acc.push(node.id)
  for (const ch of node.children || []) collectExpandKeys(ch, depth + 1, acc)
  return acc
}

function findByBounds(node, bounds) {
  if (!node || !bounds) return null
  if (node.bounds === bounds) return node
  for (const ch of node.children || []) {
    const hit = findByBounds(ch, bounds)
    if (hit) return hit
  }
  return null
}

function collectAncestors(node, targetId, path = []) {
  if (!node) return null
  const next = [...path, node.id]
  if (node.id === targetId) return next
  for (const ch of node.children || []) {
    const hit = collectAncestors(ch, targetId, next)
    if (hit) return hit
  }
  return null
}

function syncTreeFromRoot() {
  const root = props.root
  if (!root) {
    treeData.value = []
    expandedKeys.value = []
    return
  }
  // hierarchy 根下可能只有一层 children；直接展示
  if (root.class === 'hierarchy' && Array.isArray(root.children)) {
    treeData.value = root.children
  } else {
    treeData.value = [root]
  }
  expandedKeys.value = collectExpandKeys({ id: 'virtual', children: treeData.value }, 0, [])
}

watch(() => props.root, () => {
  syncTreeFromRoot()
}, { immediate: true })

watch(() => [props.selectBounds, props.currentId, props.root], async () => {
  await nextTick()
  const tree = treeRef.value
  if (!tree || !props.root) return
  let target = null
  if (props.currentId) {
    target = { id: props.currentId }
  } else if (props.selectBounds) {
    target = findByBounds(props.root, props.selectBounds)
  }
  if (!target?.id) return
  const path = collectAncestors(props.root, target.id) || []
  path.forEach(id => {
    try { tree.store?.nodesMap?.[id]?.expand?.() } catch { /* ignore */ }
  })
  tree.setCurrentKey(target.id)
  await nextTick()
  const el = tree.$el?.querySelector?.('.is-current > .el-tree-node__content')
  el?.scrollIntoView?.({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
})

function onNodeClick(data) {
  if (!data || data.class === 'hierarchy') return
  emit('select', data)
}

defineExpose({
  findByBounds: (bounds) => (props.root ? findByBounds(props.root, bounds) : null)
})
</script>

<style scoped lang="scss">
.ui-hierarchy-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}
.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.tree-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.tree-meta {
  font-size: 12px;
  color: #94a3b8;
}
.tree-warn {
  padding: 6px 12px;
  font-size: 12px;
  color: #b45309;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
}
.tree-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 4px 12px;
}
.tree-body :deep(.el-tree) {
  display: inline-block;
  min-width: 100%;
  vertical-align: top;
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  flex-shrink: 0;
}
.tree-class {
  color: #1e293b;
  font-weight: 600;
  flex-shrink: 0;
}
.tree-tip {
  color: #64748b;
  white-space: nowrap;
}
.tree-node.clickable .tree-class {
  color: #0369a1;
}
.tree-node.selected .tree-class {
  color: #059669;
}
:deep(.el-tree-node__content) {
  height: auto;
  min-height: 28px;
  padding: 2px 8px 2px 0;
  width: max-content;
  min-width: 100%;
  box-sizing: border-box;
}
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #ecfdf5;
}
:deep(.el-tree-node__expand-icon) {
  flex-shrink: 0;
}
</style>
