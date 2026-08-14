<template>
  <div class="step-timeline" :class="{ nested: nested, 'nested-cards': nested }">
    <div
      v-for="(node, idx) in nodes"
      :key="node.key"
      class="tl-item"
      :class="{
        'is-block': node.kind === 'block',
        'is-last': idx === nodes.length - 1,
        'is-nested-card': nested
      }"
    >
      <!-- 嵌套子步骤：红点 + 步骤N + 蓝动作/灰目标 -->
      <template v-if="nested && node.kind !== 'block'">
        <div class="tl-rail nested-rail">
          <span class="tl-dot dot-normal" />
        </div>
        <div class="tl-content">
          <div class="child-label">步骤{{ node.index }}:</div>
          <div class="child-main">
            <template v-for="(p, pIdx) in resolveChildParts(node.step)" :key="`${node.key}-${pIdx}`">
              <span v-if="p.kind === 'action'" class="tl-pill-action">{{ p.text }}</span>
              <span v-else-if="p.kind === 'param'" class="tl-pill-param" :title="p.text">{{ p.text }}</span>
              <span v-else class="child-text">{{ p.text }}</span>
            </template>
            <el-button
              v-if="node.step.type === 'invoke_common' && node.step.common_step"
              size="small"
              plain
              @click="$emit('view-common', node.step)"
            >查看步骤</el-button>
            <el-button
              v-if="node.step.type === 'custom_script'"
              size="small"
              plain
              @click="toggleScript(node)"
            >{{ isScriptExpanded(node.key) ? '收起' : '展开' }}</el-button>
          </div>
          <div v-if="node.step.type === 'custom_script' && isScriptExpanded(node.key)" class="tl-script-panel">
            <el-input
              :model-value="scriptCodeOf(node.step)"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 16 }"
              class="tl-script-input"
              placeholder="在此编辑自定义脚本..."
              @update:model-value="(v) => setScriptCode(node.step, v)"
            />
            <div class="tl-script-actions">
              <el-button size="small" type="primary" @click="$emit('save-script', node.step)">保存脚本</el-button>
            </div>
          </div>
        </div>
      </template>

      <!-- 顶层普通步骤 / 判断块（含嵌套判断：同样保留轨道，避免竖线压到卡片） -->
      <template v-else>
        <div class="tl-rail" :class="{ 'nested-rail': nested }">
          <span class="tl-dot" :class="node.kind === 'block' ? 'dot-active' : 'dot-normal'" />
        </div>
        <div class="tl-content">
          <div v-if="!nested && node.kind !== 'block'" class="tl-header">步骤{{ node.index }}</div>

          <!-- 判断 / 循环卡片 -->
          <div v-if="node.kind === 'block'" class="judge-card">
            <div
              v-if="isLoop(node.step)"
              class="loop-bar"
              @click="toggleBlock(node.key)"
            >
              <span class="loop-tag">循环结构</span>
              <span class="loop-hint">点击展开/收起循环体</span>
              <span class="loop-chevron" :class="{ open: isBlockExpanded(node.key) }">▾</span>
            </div>
            <div class="judge-head" @click="toggleBlock(node.key)">
              <span class="judge-if-icon">{{ blockTag(node.step) }}</span>
              <div class="judge-mid">
                <span class="tl-pill-action">{{ resolveJudgeAction(node.step) }}</span>
                <span class="judge-assert-label">断言：</span>
                <span
                  v-if="showJudgeElement(node.step)"
                  class="tl-pill-param"
                  :class="{ 'tl-pill-warn': !resolveElement(node.step, node.children) }"
                  :title="resolveElement(node.step, node.children) || '请编辑步骤并选择要判断的控件'"
                >{{ resolveElement(node.step, node.children) || '未指定控件' }}</span>
                <span v-if="resolveAssertState(node.step, node.children)" class="judge-assert-state">{{ resolveAssertState(node.step, node.children) }}</span>
                <span class="tl-if-status">无异常</span>
              </div>
              <el-switch
                :model-value="node.step.enabled !== false"
                size="small"
                @click.stop
                @change="(v) => { node.step.enabled = !!v }"
              />
              <span class="loop-chevron" :class="{ open: isBlockExpanded(node.key) }">▾</span>
            </div>
            <div v-show="isBlockExpanded(node.key)" class="judge-body">
              <CommonStepTimeline
                v-if="visibleBlockChildren(node).length"
                nested
                :nodes="visibleBlockChildren(node)"
                :type-label="typeLabel"
                :step-summary="stepSummary"
                :param-text="paramText"
                :assert-text="assertText"
                :block-tag="blockTag"
                :judge-action-text="judgeActionText"
                :element-name="elementName"
                :child-parts="childParts"
                @view-common="$emit('view-common', $event)"
                @save-script="$emit('save-script', $event)"
              />
              <div v-else class="judge-empty">块内暂无步骤</div>
            </div>
          </div>

          <!-- 顶层普通步骤 -->
          <div v-else class="tl-step-body">
            <div class="tl-action-row">
              <template v-for="(p, pIdx) in resolveChildParts(node.step)" :key="`${node.key}-t-${pIdx}`">
                <span v-if="p.kind === 'action'" class="tl-pill-action">{{ p.text }}</span>
                <span v-else-if="p.kind === 'param'" class="tl-pill-param" :title="p.text">{{ p.text }}</span>
                <span v-else class="child-text">{{ p.text }}</span>
              </template>
              <el-button
                v-if="node.step.type === 'invoke_common' && node.step.common_step"
                size="small"
                type="warning"
                plain
                @click="$emit('view-common', node.step)"
              >查看步骤</el-button>
              <el-button
                v-if="node.step.type === 'custom_script'"
                size="small"
                plain
                @click="toggleScript(node)"
              >{{ isScriptExpanded(node.key) ? '收起' : '展开' }}</el-button>
            </div>
            <div v-if="node.step.type === 'custom_script' && isScriptExpanded(node.key)" class="tl-script-panel">
              <div class="tl-script-meta">
                <span>{{ (node.step.script_lang || node.step.language || 'python').toUpperCase() }}</span>
                <span v-if="node.step.element_name || node.step.display_name">{{ node.step.element_name || node.step.display_name }}</span>
              </div>
              <el-input
                :model-value="scriptCodeOf(node.step)"
                type="textarea"
                :autosize="{ minRows: 4, maxRows: 16 }"
                class="tl-script-input"
                placeholder="在此编辑自定义脚本..."
                @update:model-value="(v) => setScriptCode(node.step, v)"
              />
              <div class="tl-script-actions">
                <el-button size="small" type="primary" @click="$emit('save-script', node.step)">保存脚本</el-button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineOptions({ name: 'CommonStepTimeline' })

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  nested: { type: Boolean, default: false },
  typeLabel: { type: Function, required: true },
  stepSummary: { type: Function, required: true },
  paramText: { type: Function, required: true },
  assertText: { type: Function, required: true },
  blockTag: { type: Function, required: true },
  judgeActionText: { type: Function, default: null },
  elementName: { type: Function, default: null },
  childParts: { type: Function, default: null }
})

const emit = defineEmits(['view-common', 'save-script'])

const expandedScripts = ref(new Set())
/** 默认收起：仅用户点开的判断块会进此集合 */
const expandedBlocks = ref(new Set())

function isScriptExpanded(key) {
  return expandedScripts.value.has(key)
}

function toggleScript(node) {
  const key = node.key
  const next = new Set(expandedScripts.value)
  if (next.has(key)) next.delete(key)
  else {
    next.add(key)
    ensureScriptCode(node.step)
  }
  expandedScripts.value = next
}

function isBlockExpanded(key) {
  return expandedBlocks.value.has(key)
}

function toggleBlock(key) {
  const next = new Set(expandedBlocks.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedBlocks.value = next
}

function isLoop(step) {
  return step?.type === 'loop'
}

function resolveJudgeAction(step) {
  if (typeof props.judgeActionText === 'function') return props.judgeActionText(step)
  return props.stepSummary(step) || '条件判断'
}

function resolveElement(step, children) {
  if (typeof props.elementName === 'function') {
    const own = props.elementName(step)
    if (own) return own
  } else if (step) {
    const own = step.element_name || step.target_name || step.name || ''
    if (own) return own
  }
  const first = Array.isArray(children) ? children[0] : null
  const s = first?.step
  if (s && (s.type === 'assert_exists' || s.type === 'assert_not_exists')) {
    if (typeof props.elementName === 'function') return props.elementName(s)
    return s.element_name || s.target_name || s.name || ''
  }
  return ''
}

function resolveAssertState(step, children) {
  const own = props.assertText(step)
  const first = Array.isArray(children) ? children[0] : null
  const s = first?.step
  const hasOwnEl = typeof props.elementName === 'function'
    ? !!props.elementName(step)
    : !!(step?.element_name || step?.target_name || step?.name)
  if (!hasOwnEl && s) {
    if (s.type === 'assert_not_exists') return '不存在'
    if (s.type === 'assert_exists') return '存在'
  }
  return own
}

function visibleBlockChildren(node) {
  const children = node.children || []
  const step = node.step
  const hasOwnEl = typeof props.elementName === 'function'
    ? !!props.elementName(step)
    : !!(step?.element_name || step?.target_name || step?.name)
  if (hasOwnEl) return children
  const first = children[0]
  if (first?.step && (first.step.type === 'assert_exists' || first.step.type === 'assert_not_exists')) {
    return children.slice(1).map((c, i) => ({ ...c, index: i + 1 }))
  }
  return children
}

function showJudgeElement(step) {
  const kind = step?.condition_kind || ''
  if (kind === 'var_equals' || kind === 'var_not_equals' || kind === 'custom') return false
  if (kind === 'exists' || kind === 'not_exists' || kind === 'text_contains') return true
  const cond = String(step?.condition || '')
  if (cond.includes('变量') || cond.includes('{{')) return false
  return true
}

function resolveChildParts(step) {
  if (typeof props.childParts === 'function') {
    const parts = props.childParts(step)
    if (Array.isArray(parts) && parts.length) return parts
  }
  const action = props.typeLabel(step)
  const param = props.paramText(step)
  const out = []
  if (action) out.push({ kind: 'action', text: action })
  if (param && param !== action) out.push({ kind: 'param', text: param })
  return out
}

function scriptCodeOf(step) {
  if (!step) return ''
  if (step.script_code != null) return step.script_code
  return step.script || ''
}

function ensureScriptCode(step) {
  if (!step) return
  if (step.script_code == null) {
    step.script_code = step.script || ''
  }
}

function setScriptCode(step, value) {
  if (!step) return
  step.script_code = value
  if ('script' in step) step.script = value
}
</script>

<style scoped>
.step-timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 520px;
  overflow: auto;
  padding: 4px 4px 8px 2px;
}
.step-timeline.nested {
  max-height: none;
  overflow: visible;
  gap: 10px;
  padding: 4px 0 2px;
}
.tl-item {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 10px;
  position: relative;
}
.tl-item.is-nested-card {
  /* 嵌套子步骤 / 嵌套判断都走时间线栅格，竖线只在轨道列 */
  display: grid;
  grid-template-columns: 18px 1fr;
}
.tl-item:not(.is-last) .tl-rail::after {
  content: '';
  position: absolute;
  left: 7px;
  top: 16px;
  bottom: -14px;
  width: 2px;
  background: #e5e6eb;
  z-index: 0;
  pointer-events: none;
}
.tl-item.is-nested-card:not(.is-last) .tl-rail::after {
  bottom: -10px;
}
.tl-rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 4px;
  overflow: visible;
  z-index: 1;
}
.tl-rail.nested-rail {
  padding-top: 2px;
}
.tl-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  z-index: 2;
  background: #f98981;
}
.tl-dot.dot-normal {
  background: #f98981;
  box-shadow: 0 0 0 3px rgba(249, 137, 129, 0.2);
}
.tl-dot.dot-active {
  background: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}
.tl-content {
  min-width: 0;
  position: relative;
  z-index: 1;
}
.tl-header {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 6px;
  line-height: 1.4;
}
.tl-step-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.tl-action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.tl-pill-action {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #165dff;
  background: #e8f3ff;
}
.tl-pill-param {
  display: inline-flex;
  align-items: center;
  max-width: 280px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #1d2129;
  background: #f2f3f5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-pill-warn {
  color: #c9a227;
  background: #fff8ee;
  border: 1px dashed #f0c48a;
}
.tl-script-panel {
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fafbfc;
  padding: 10px 12px;
  margin-top: 6px;
}
.tl-script-meta {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #86909c;
}
.tl-script-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  background: #fff;
}
.tl-script-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* —— 判断 / 循环卡片（对齐截图） —— */
.judge-card {
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}
.loop-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fffbf5;
  border-bottom: 1px solid #f5e6d3;
  cursor: pointer;
  user-select: none;
}
.loop-tag {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #e8a054;
  background: #fff;
  border: 1px solid #f0c48a;
}
.loop-hint {
  flex: 1;
  font-size: 13px;
  color: #165dff;
}
.loop-chevron {
  color: #86909c;
  transition: transform 0.15s;
  font-size: 14px;
}
.loop-chevron.open {
  transform: rotate(180deg);
}
.judge-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
}
.judge-if-icon {
  flex-shrink: 0;
  min-width: 28px;
  height: 22px;
  padding: 0 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #d9892b;
  background: #fff3e0;
  border: 1px solid #f0c48a;
}
.judge-mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}
.judge-title {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 500;
  color: #165dff;
  line-height: 22px;
  white-space: nowrap;
}
.judge-assert-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
}
.judge-assert-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #4e5969;
}
.judge-assert-state {
  font-size: 13px;
  color: #1d2129;
}
.tl-if-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #d9892b;
  background: #fff8ee;
  border: 1px solid #f0c48a;
}
.judge-body {
  padding: 4px 12px 12px 10px;
  border-top: 1px solid #f2f3f5;
  background: #fcfcfd;
  overflow: hidden;
}
.judge-empty {
  padding: 12px 0;
  font-size: 12px;
  color: #c9cdd4;
}

/* 嵌套子步骤 */
.child-label {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 6px;
  line-height: 1.4;
}
.child-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.child-text {
  font-size: 13px;
  color: #1d2129;
  line-height: 22px;
}
</style>
