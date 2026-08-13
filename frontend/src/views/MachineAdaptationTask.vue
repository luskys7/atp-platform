<template>
  <div class="page-container ma-task-page">
    <PageHeader title="新品适配 · 功能集勾选" subtitle="勾选功能可筛选用例，或将已绑定公共步骤的功能项组合成一条新用例">
      <template #actions>
        <el-button type="success" plain :loading="loadingDemo" @click="loadFullDemoData">加载完整结果 Demo</el-button>
        <el-button @click="$router.push('/machine-adaptation/config')">功能集/机型配置</el-button>
        <el-button @click="$router.push('/reports')">报告中心</el-button>
        <el-button @click="$router.push('/tasks')">任务列表</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false" class="top-card">
      <el-form :model="meta" inline size="default" class="meta-form">
        <el-form-item label="任务名称">
          <el-input v-model="meta.name" placeholder="例如：AX17 新品适配" style="width: 220px" clearable />
        </el-form-item>
        <el-form-item label="关联版本">
          <el-input v-model="meta.version" placeholder="迭代/版本号" style="width: 140px" clearable />
        </el-form-item>
        <el-form-item label="待测机型">
          <el-select
            v-model="machineId"
            filterable
            clearable
            placeholder="可选：按机型预勾支持功能"
            style="width: 200px"
            @change="onMachineChange"
          >
            <el-option
              v-for="m in enabledMachines"
              :key="m.id"
              :label="machineLabel(m)"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行设备">
          <el-select v-model="deviceId" filterable clearable placeholder="可选" style="width: 200px">
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.name || d.serial_number} (${d.platform})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建人">
          <span class="meta-text">{{ creatorName }}</span>
        </el-form-item>
        <el-form-item label="创建时间">
          <span class="meta-text">{{ createdAtText }}</span>
        </el-form-item>
      </el-form>
    </AppCard>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="12" class="tree-col">
        <div class="feature-panel">
          <div class="card-head">
            <span class="panel-title">App 功能集</span>
            <div class="card-head-actions">
              <el-button link type="primary" @click="expandAll">展开</el-button>
              <el-button link @click="collapseAll">收起</el-button>
              <el-button link type="primary" @click="checkAll">全选</el-button>
              <el-button link @click="clearChecks">清空</el-button>
              <el-button link @click="loadFeatureTree">刷新</el-button>
            </div>
          </div>

          <div v-loading="loadingTree" class="feature-board">
            <div v-if="!sections.length" class="empty-hint">暂无功能集，请先在「功能集/机型配置」中维护（功能集 → 一级板块 → 板块内容）</div>
            <div v-for="sec in sections" :key="sec.section_name" class="section-block">
              <div
                class="section-bar"
                :class="{ active: isSectionFullyChecked(sec), collapsed: !isExpanded(sec.section_name) }"
                @click="toggleExpand(sec.section_name)"
              >
                <span
                  class="caret"
                  :class="{ open: isExpanded(sec.section_name) }"
                  @click.stop="toggleExpand(sec.section_name)"
                />
                <span
                  class="dot-check"
                  :class="{
                    on: isSectionFullyChecked(sec),
                    partial: isSectionPartial(sec)
                  }"
                  @click.stop="toggleSectionCheck(sec)"
                />
                <span class="section-title">{{ sec.section_name }}</span>
                <span class="section-count">{{ sectionSelectedCount(sec) }} / {{ sec.items?.length || 0 }}</span>
              </div>
              <div v-show="isExpanded(sec.section_name)" class="board-list">
                <div
                  v-for="board in (sec.boards || [{ board_name: '', items: sec.items || [] }])"
                  :key="boardKey(sec.section_name, board.board_name)"
                  class="board-block"
                >
                  <div
                    v-if="board.board_name"
                    class="board-bar"
                    :class="{
                      active: isBoardFullyChecked(board),
                      collapsed: !isBoardExpanded(sec.section_name, board.board_name)
                    }"
                    @click.stop="toggleBoardExpand(sec.section_name, board.board_name)"
                  >
                    <span
                      class="caret"
                      :class="{ open: isBoardExpanded(sec.section_name, board.board_name) }"
                      @click.stop="toggleBoardExpand(sec.section_name, board.board_name)"
                    />
                    <span
                      class="dot-check"
                      :class="{
                        on: isBoardFullyChecked(board),
                        partial: isBoardPartial(board)
                      }"
                      @click.stop="toggleBoardCheck(board)"
                    />
                    <span class="board-title">{{ board.board_name }}</span>
                    <span class="board-count">{{ boardSelectedCount(board) }} / {{ board.items?.length || 0 }}</span>
                  </div>
                  <div
                    v-show="!board.board_name || isBoardExpanded(sec.section_name, board.board_name)"
                    class="type-list"
                  >
                    <template v-if="shouldGroupByType(board)">
                      <div
                        v-for="grp in groupItemsByType(board.items)"
                        :key="typeKey(sec.section_name, board.board_name, grp.type_name)"
                        class="type-block"
                      >
                        <div
                          v-if="grp.show_header"
                          class="type-bar"
                          :class="{
                            active: isTypeFullyChecked(grp),
                            collapsed: !isTypeExpanded(sec.section_name, board.board_name, grp.type_name)
                          }"
                          @click.stop="toggleTypeExpand(sec.section_name, board.board_name, grp.type_name)"
                        >
                          <span
                            class="caret"
                            :class="{ open: isTypeExpanded(sec.section_name, board.board_name, grp.type_name) }"
                            @click.stop="toggleTypeExpand(sec.section_name, board.board_name, grp.type_name)"
                          />
                          <span
                            class="dot-check"
                            :class="{
                              on: isTypeFullyChecked(grp),
                              partial: isTypePartial(grp)
                            }"
                            @click.stop="toggleTypeCheck(grp)"
                          />
                          <span class="type-title">{{ grp.type_name }}</span>
                          <span class="type-count">{{ typeSelectedCount(grp) }} / {{ grp.items.length }}</span>
                        </div>
                        <div
                          v-show="!grp.show_header || isTypeExpanded(sec.section_name, board.board_name, grp.type_name)"
                          class="item-list"
                        >
                          <div
                            v-for="item in grp.items"
                            :key="item.tag_id"
                            class="item-bar"
                            :class="{ checked: isChecked(item.tag_id), composable: item.composable }"
                            @click="toggleItem(item.tag_id)"
                          >
                            <span class="dot-check" :class="{ on: isChecked(item.tag_id) }" />
                            <span class="item-name">{{ itemDisplayName(item, grp) }}</span>
                            <el-tooltip v-if="item.composable" content="已绑定公共步骤，可参与组合用例" placement="top">
                              <span class="item-bind">可组合</span>
                            </el-tooltip>
                            <span class="item-count">{{ item.case_count || 0 }}</span>
                          </div>
                        </div>
                      </div>
                    </template>
                    <div v-else class="item-list">
                      <div
                        v-for="item in board.items"
                        :key="item.tag_id"
                        class="item-bar"
                        :class="{ checked: isChecked(item.tag_id), composable: item.composable }"
                        @click="toggleItem(item.tag_id)"
                      >
                        <span class="dot-check" :class="{ on: isChecked(item.tag_id) }" />
                        <span class="item-name">{{ item.content_name || item.tag_name }}</span>
                        <el-tooltip v-if="item.composable" content="已绑定公共步骤，可参与组合用例" placement="top">
                          <span class="item-bind">可组合</span>
                        </el-tooltip>
                        <span class="item-count">{{ item.case_count || 0 }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <AppCard :hover="false" title="关联用例（平台现有用例）">
          <div class="case-toolbar">
            <el-tag type="warning" size="small">已勾选功能 {{ selectedTagIds.length }}</el-tag>
            <el-tag type="success" size="small">可组合 {{ composableSelectedCount }}</el-tag>
            <el-tag type="success" size="small">匹配用例 {{ cases.length }}</el-tag>
            <el-tag type="info" size="small">已选用例 {{ selectedCaseIds.length }}</el-tag>
            <el-radio-group v-model="matchMode" size="small" @change="reloadCases">
              <el-radio-button label="any">命中任一功能</el-radio-button>
              <el-radio-button label="all">覆盖全部依赖</el-radio-button>
            </el-radio-group>
          </div>
          <el-table
            ref="tableRef"
            v-loading="loadingCases"
            :data="cases"
            stripe
            height="640"
            row-key="id"
            @selection-change="onCaseSelection"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="name" label="用例" min-width="160" show-overflow-tooltip />
            <el-table-column prop="module_name" label="模块" width="100" show-overflow-tooltip />
            <el-table-column label="板块内容" min-width="160">
              <template #default="{ row }">
                <el-tag
                  v-for="t in (row.tags || [])"
                  :key="t.tag_id"
                  size="small"
                  class="case-tag"
                  type="success"
                >{{ t.content_name || t.tag_name }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-col>
    </el-row>

    <AppCard :hover="false" class="footer-bar">
      <div class="footer-left">
        <el-button @click="selectAllCases" :disabled="!cases.length">全选用例</el-button>
        <el-button @click="clearCaseSelection" :disabled="!selectedCaseIds.length">清空用例勾选</el-button>
      </div>
      <div class="footer-right">
        <el-button :disabled="!selectedCaseIds.length" @click="exportCsv">导出用例清单</el-button>
        <el-button
          type="warning"
          plain
          :loading="composing"
          :disabled="!selectedTagIds.length"
          @click="composeCombinedCase"
        >生成组合用例</el-button>
        <el-button type="success" plain :loading="saving" :disabled="!selectedCaseIds.length" @click="saveAsSuite">保存任务</el-button>
        <el-button type="primary" :loading="running" :disabled="!selectedCaseIds.length" @click="startTest">开始测试</el-button>
      </div>
    </AppCard>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { caseApi, deviceApi, machineAdaptationApi, suiteApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const tableRef = ref(null)

/** App 功能集固定排版顺序 */
const FEATURE_SECTION_ORDER = [
  '清洁功能',
  '地图功能',
  '智能功能',
  '设备管理',
  '通知体系',
  '用户体系'
]

function sectionOrderIndex(name) {
  const i = FEATURE_SECTION_ORDER.indexOf(name)
  return i >= 0 ? i : FEATURE_SECTION_ORDER.length + 1
}

function sortSections(list) {
  return [...(list || [])].sort((a, b) => {
    const d = sectionOrderIndex(a.section_name) - sectionOrderIndex(b.section_name)
    if (d !== 0) return d
    return String(a.section_name || '').localeCompare(String(b.section_name || ''), 'zh')
  })
}

const machines = ref([])
const devices = ref([])
const machineId = ref(null)
const deviceId = ref(null)
const sections = ref([])
const selectedTagIds = ref([])
const expandedSections = ref([])
const expandedBoards = ref([])
const expandedTypes = ref([])
const cases = ref([])
const selectedCaseIds = ref([])
const matchMode = ref('any')
const loadingTree = ref(false)
const loadingCases = ref(false)
const loadingDemo = ref(false)
const saving = ref(false)
const running = ref(false)
const composing = ref(false)
const createdAt = ref(new Date())
const meta = ref({ name: '', version: '' })

const creatorName = computed(() => userStore.user?.display_name || userStore.user?.username || '-')
const createdAtText = computed(() => formatTime(createdAt.value))
const enabledMachines = computed(() => (machines.value || []).filter(m => m.status !== 0 && m.status !== '0'))
const checkedSet = computed(() => new Set(selectedTagIds.value))

const featureItemById = computed(() => {
  const map = new Map()
  for (const sec of sections.value || []) {
    for (const board of sec.boards || [{ items: sec.items || [] }]) {
      for (const item of board.items || []) {
        map.set(item.tag_id, item)
      }
    }
  }
  return map
})

const composableSelectedCount = computed(() =>
  selectedTagIds.value.filter(id => featureItemById.value.get(id)?.composable).length
)

let reloadTimer = null

watch(selectedTagIds, () => {
  clearTimeout(reloadTimer)
  reloadTimer = setTimeout(reloadCases, 200)
}, { deep: true })

function machineLabel(m) {
  return m.firm_version ? `${m.machine_name} · ${m.firm_version}` : m.machine_name
}

function formatTime(d) {
  const x = d instanceof Date ? d : new Date(d)
  const p = n => String(n).padStart(2, '0')
  return `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())} ${p(x.getHours())}:${p(x.getMinutes())}`
}

function isChecked(tagId) {
  return checkedSet.value.has(tagId)
}

function isExpanded(name) {
  return expandedSections.value.includes(name)
}

function toggleExpand(name) {
  if (isExpanded(name)) {
    expandedSections.value = expandedSections.value.filter(n => n !== name)
  } else {
    expandedSections.value = [...expandedSections.value, name]
  }
}

function boardKey(sectionName, boardName) {
  return `${sectionName}::${boardName || ''}`
}

function isBoardExpanded(sectionName, boardName) {
  return expandedBoards.value.includes(boardKey(sectionName, boardName))
}

function toggleBoardExpand(sectionName, boardName) {
  const key = boardKey(sectionName, boardName)
  if (isBoardExpanded(sectionName, boardName)) {
    expandedBoards.value = expandedBoards.value.filter(k => k !== key)
  } else {
    expandedBoards.value = [...expandedBoards.value, key]
  }
}

function allBoardKeys() {
  const keys = []
  for (const sec of sections.value) {
    for (const board of (sec.boards || [])) {
      if (board.board_name) keys.push(boardKey(sec.section_name, board.board_name))
    }
  }
  return keys
}

function contentTypeName(item) {
  const name = String(item?.content_name || item?.tag_name || '').trim()
  if (!name) return '其他'
  const idx = name.indexOf('-')
  if (idx <= 0) return '其他'
  return name.slice(0, idx).trim() || '其他'
}

/** 仅「设置」一级板块按名称前缀做类型分组 */
function shouldGroupByType(board) {
  return (board?.board_name || '').trim() === '设置'
}

function groupItemsByType(items) {
  const list = items || []
  const map = new Map()
  for (const item of list) {
    const type = contentTypeName(item)
    if (!map.has(type)) map.set(type, [])
    map.get(type).push(item)
  }
  const groups = [...map.entries()].map(([type_name, typeItems]) => {
    const sorted = [...typeItems].sort((a, b) => (Number(a.tag_id) || 0) - (Number(b.tag_id) || 0))
    const firstId = sorted.reduce((m, it) => Math.min(m, Number(it.tag_id) || Number.MAX_SAFE_INTEGER), Number.MAX_SAFE_INTEGER)
    return {
      type_name,
      items: sorted,
      first_id: firstId,
      // 仅有一类且名称就是「其他」（无前缀）时不显示类型头，避免多余一层
      show_header: !(map.size === 1 && type_name === '其他')
    }
  })
  groups.sort((a, b) => a.first_id - b.first_id)
  // 多类型时一律显示类型头；单类型但有前缀（如全是鲸灵托管）也显示
  if (groups.length === 1 && groups[0].type_name !== '其他') {
    groups[0].show_header = true
  }
  return groups
}

function typeKey(sectionName, boardName, typeName) {
  return `${sectionName}::${boardName || ''}::${typeName}`
}

function isTypeExpanded(sectionName, boardName, typeName) {
  return expandedTypes.value.includes(typeKey(sectionName, boardName, typeName))
}

function toggleTypeExpand(sectionName, boardName, typeName) {
  const key = typeKey(sectionName, boardName, typeName)
  if (isTypeExpanded(sectionName, boardName, typeName)) {
    expandedTypes.value = expandedTypes.value.filter(k => k !== key)
  } else {
    expandedTypes.value = [...expandedTypes.value, key]
  }
}

function allTypeKeys() {
  const keys = []
  for (const sec of sections.value) {
    for (const board of (sec.boards || [])) {
      if (!shouldGroupByType(board)) continue
      for (const grp of groupItemsByType(board.items)) {
        if (grp.show_header) {
          keys.push(typeKey(sec.section_name, board.board_name, grp.type_name))
        }
      }
    }
  }
  return keys
}

function typeSelectedCount(grp) {
  return (grp.items || []).filter(i => isChecked(i.tag_id)).length
}

function isTypeFullyChecked(grp) {
  const items = grp.items || []
  return items.length > 0 && items.every(i => isChecked(i.tag_id))
}

function isTypePartial(grp) {
  const n = typeSelectedCount(grp)
  return n > 0 && n < (grp.items || []).length
}

function toggleTypeCheck(grp) {
  const items = grp.items || []
  if (!items.length) return
  const ids = items.map(i => i.tag_id)
  if (isTypeFullyChecked(grp)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => !ids.includes(id))
  } else {
    selectedTagIds.value = [...new Set([...selectedTagIds.value, ...ids])]
  }
}

function itemDisplayName(item, grp) {
  const full = String(item?.content_name || item?.tag_name || '')
  if (!grp?.show_header || !grp.type_name || grp.type_name === '其他') return full
  const prefix = `${grp.type_name}-`
  return full.startsWith(prefix) ? full.slice(prefix.length) : full
}

function expandAll() {
  expandedSections.value = sections.value.map(s => s.section_name)
  expandedBoards.value = allBoardKeys()
  expandedTypes.value = allTypeKeys()
}

function collapseAll() {
  expandedSections.value = []
  expandedBoards.value = []
  expandedTypes.value = []
}

function sectionSelectedCount(sec) {
  return (sec.items || []).filter(i => isChecked(i.tag_id)).length
}

function isSectionFullyChecked(sec) {
  const items = sec.items || []
  return items.length > 0 && items.every(i => isChecked(i.tag_id))
}

function isSectionPartial(sec) {
  const n = sectionSelectedCount(sec)
  return n > 0 && n < (sec.items || []).length
}

function boardSelectedCount(board) {
  return (board.items || []).filter(i => isChecked(i.tag_id)).length
}

function isBoardFullyChecked(board) {
  const items = board.items || []
  return items.length > 0 && items.every(i => isChecked(i.tag_id))
}

function isBoardPartial(board) {
  const n = boardSelectedCount(board)
  return n > 0 && n < (board.items || []).length
}

function toggleBoardCheck(board) {
  const items = board.items || []
  if (!items.length) return
  const ids = items.map(i => i.tag_id)
  if (isBoardFullyChecked(board)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => !ids.includes(id))
  } else {
    selectedTagIds.value = [...new Set([...selectedTagIds.value, ...ids])]
  }
}

function toggleItem(tagId) {
  if (isChecked(tagId)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => id !== tagId)
  } else {
    selectedTagIds.value = [...selectedTagIds.value, tagId]
  }
}

function setItem(tagId, checked) {
  if (checked) {
    if (!isChecked(tagId)) selectedTagIds.value = [...selectedTagIds.value, tagId]
  } else {
    selectedTagIds.value = selectedTagIds.value.filter(id => id !== tagId)
  }
}

function toggleSectionCheck(sec) {
  const items = sec.items || []
  if (!items.length) return
  const ids = items.map(i => i.tag_id)
  if (isSectionFullyChecked(sec)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => !ids.includes(id))
  } else {
    selectedTagIds.value = [...new Set([...selectedTagIds.value, ...ids])]
  }
}

function checkAll() {
  const ids = []
  for (const sec of sections.value) {
    for (const item of (sec.items || [])) ids.push(item.tag_id)
  }
  selectedTagIds.value = [...new Set(ids)]
}

function clearChecks() {
  selectedTagIds.value = []
  cases.value = []
  selectedCaseIds.value = []
}

async function loadFullDemoData() {
  try {
    await ElMessageBox.confirm(
      '将生成完整结果 Demo：功能标签 + 4 条用例 + 已完成任务/执行日志/测试报告（含成功与失败样例）。无需真实设备。',
      '加载完整结果 Demo',
      { type: 'info', confirmButtonText: '开始加载', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loadingDemo.value = true
  try {
    const res = await machineAdaptationApi.loadFullDemo()
    const d = res.data || {}
    await Promise.all([loadMachines(), loadFeatureTree()])
    const primary = d.primary_report_task_id
    await ElMessageBox.confirm(
      `已生成：用例 ${d.case_created ?? 0} 新建 / ${d.case_updated ?? 0} 更新，报告任务 ${ (d.report_task_ids || []).length } 条。是否打开报告中心查看结果？`,
      'Demo 加载完成',
      { type: 'success', confirmButtonText: primary ? '查看报告' : '去报告中心', cancelButtonText: '留在本页' }
    ).then(() => {
      if (primary) router.push(`/reports/${primary}`)
      else router.push('/reports')
    }).catch(() => {})
  } finally {
    loadingDemo.value = false
  }
}

async function loadMachines() {
  const res = await machineAdaptationApi.listMachines()
  machines.value = res.data || []
}

async function loadDevices() {
  try {
    const res = await deviceApi.list()
    devices.value = res.data?.items || res.data || []
  } catch {
    devices.value = []
  }
}

async function loadFeatureTree() {
  loadingTree.value = true
  try {
    const res = await machineAdaptationApi.featureTree(
      machineId.value ? { machine_id: machineId.value } : undefined
    )
    sections.value = sortSections(res.data?.sections || [])
    // 默认收起：仅保留仍存在的已展开项
    const names = new Set(sections.value.map(s => s.section_name))
    expandedSections.value = expandedSections.value.filter(n => names.has(n))
    const boardKeys = new Set(allBoardKeys())
    expandedBoards.value = expandedBoards.value.filter(k => boardKeys.has(k))
    const typeKeys = new Set(allTypeKeys())
    expandedTypes.value = expandedTypes.value.filter(k => typeKeys.has(k))
  } finally {
    loadingTree.value = false
  }
}

async function onMachineChange() {
  selectedTagIds.value = []
  await loadFeatureTree()
  const m = machines.value.find(x => x.id === machineId.value)
  if (m && !meta.value.name) meta.value.name = `${m.machine_name} 新品适配`
  const pre = []
  for (const sec of sections.value) {
    for (const item of (sec.items || [])) {
      if (item.supported === true) pre.push(item.tag_id)
    }
  }
  selectedTagIds.value = pre
  await reloadCases()
}

async function reloadCases() {
  const tagIds = [...selectedTagIds.value]
  if (!tagIds.length) {
    cases.value = []
    selectedCaseIds.value = []
    return
  }
  loadingCases.value = true
  try {
    const res = await machineAdaptationApi.casesByFeatures({
      tag_ids: tagIds,
      match_mode: matchMode.value
    })
    cases.value = res.data?.cases || []
    await nextTick()
    // 默认全选匹配用例
    selectAllCases()
  } finally {
    loadingCases.value = false
  }
}

function onCaseSelection(rows) {
  selectedCaseIds.value = rows.map(r => r.id)
}

function selectAllCases() {
  tableRef.value?.clearSelection()
  nextTick(() => {
    for (const row of cases.value) tableRef.value?.toggleRowSelection(row, true)
  })
}

function clearCaseSelection() {
  tableRef.value?.clearSelection()
  selectedCaseIds.value = []
}

function exportCsv() {
  const rows = cases.value.filter(r => selectedCaseIds.value.includes(r.id))
  if (!rows.length) {
    ElMessage.warning('请先勾选用例')
    return
  }
  const header = ['用例ID', '用例名称', '模块', '功能集', '机型', '版本']
  const machine = machines.value.find(m => m.id === machineId.value)
  const lines = [header.join(',')]
  for (const r of rows) {
    const tags = (r.tags || []).map(t => t.content_name || t.tag_name).join('|')
    lines.push([
      r.id,
      csvEscape(r.name),
      csvEscape(r.module_name || ''),
      csvEscape(tags),
      csvEscape(machine?.machine_name || ''),
      csvEscape(meta.value.version || '')
    ].join(','))
  }
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${meta.value.name || '新品适配用例清单'}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

function csvEscape(v) {
  const s = String(v ?? '')
  if (/[",\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

function orderedSelectedTagIds() {
  const set = new Set(selectedTagIds.value)
  const ordered = []
  for (const sec of sections.value || []) {
    for (const board of sec.boards || [{ items: sec.items || [] }]) {
      for (const item of board.items || []) {
        if (set.has(item.tag_id)) ordered.push(item.tag_id)
      }
    }
  }
  return ordered
}

async function composeCombinedCase() {
  const tagIds = orderedSelectedTagIds()
  if (!tagIds.length) {
    ElMessage.warning('请先勾选功能项')
    return
  }
  if (!composableSelectedCount.value) {
    ElMessage.warning('勾选的功能项均未绑定公共步骤，请先在「功能集/机型配置」中绑定')
    return
  }
  const defaultName = meta.value.name?.trim()
    || `新品适配组合 · ${(meta.value.version || '').trim()}`.replace(/·\s*$/, '').trim()
    || undefined
  composing.value = true
  try {
    const res = await machineAdaptationApi.composeCase({
      tag_ids: tagIds,
      name: defaultName,
      platform: 'android',
      case_status: 'draft'
    })
    const data = res.data || {}
    const created = data.case || {}
    const unbound = data.unbound || []
    const tip = unbound.length
      ? `已生成用例「${created.name || created.id}」（${data.step_count || 0} 步），另有 ${unbound.length} 项未绑定已跳过`
      : `已生成组合用例「${created.name || created.id}」（${data.step_count || 0} 步）`
    ElMessage.success(tip)
    await reloadCases()
    if (created.id) {
      await ElMessageBox.confirm('是否打开可视化编辑器查看/完善？', '组合用例已创建', {
        type: 'success',
        confirmButtonText: '打开编辑器',
        cancelButtonText: '留在本页'
      }).then(() => router.push(`/cases/editor/${created.id}`)).catch(() => {})
    }
  } finally {
    composing.value = false
  }
}

async function saveAsSuite() {
  if (!selectedCaseIds.value.length) return
  const name = meta.value.name?.trim() || `新品适配 ${formatTime(new Date())}`
  saving.value = true
  try {
    await suiteApi.create({
      name,
      tags: ['新品适配', meta.value.version || ''].filter(Boolean).join(','),
      exec_mode: 'serial',
      fail_policy: 'continue',
      items: selectedCaseIds.value.map((id, idx) => ({
        case_id: id,
        sort_order: idx,
        enabled: true
      }))
    })
    ElMessage.success('已保存为测试套件')
    await ElMessageBox.confirm('是否跳转到套件列表？', '保存成功', {
      type: 'success',
      confirmButtonText: '去套件',
      cancelButtonText: '留下'
    }).then(() => router.push('/suites')).catch(() => {})
  } finally {
    saving.value = false
  }
}

async function startTest() {
  if (!selectedCaseIds.value.length) return
  running.value = true
  let ok = 0
  let fail = 0
  const payload = deviceId.value ? { device_ids: [deviceId.value] } : {}
  try {
    for (const id of selectedCaseIds.value) {
      try {
        await caseApi.run(id, payload)
        ok++
      } catch {
        fail++
      }
    }
    if (ok) {
      ElMessage.success(fail ? `成功 ${ok}，失败 ${fail}` : `已提交 ${ok} 个执行任务`)
      router.push('/tasks')
    } else {
      ElMessage.error('全部提交失败')
    }
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  createdAt.value = new Date()
  await Promise.all([loadMachines(), loadDevices(), loadFeatureTree()])
})
</script>

<style scoped>
.ma-task-page { max-width: 1760px; }
.top-card { margin-bottom: 16px; }
.meta-form { margin-bottom: 0; }
.meta-text { color: var(--atp-text-muted, #64748b); }
.main-row {
  margin-bottom: 16px;
  align-items: stretch;
}
.tree-col {
  display: flex;
}
.feature-panel {
  flex: 1;
  width: 100%;
  min-height: 680px;
  display: flex;
  flex-direction: column;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--atp-text, #1D2129);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 10px;
  padding: 0 2px;
}
.card-head-actions { display: flex; gap: 4px; flex-wrap: wrap; }
.feature-board {
  flex: 1;
  min-height: 0;
  max-height: none;
  height: calc(100vh - 320px);
  overflow: auto;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 0;
}
.empty-hint {
  color: var(--atp-text-muted, #86909C);
  text-align: center;
  padding: 48px 16px;
  font-size: 13px;
}
.section-block {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 8px;
  margin-bottom: 12px;
}
.board-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-left: 6px;
  padding: 8px;
  background: #f7f8fa;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
}
.board-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.section-bar,
.board-bar,
.item-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  box-sizing: border-box;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  gap: 10px;
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}
/* L1 功能集：实心紫，与下层拉开 */
.section-bar {
  background: #8b6cf0;
  border: 1px solid #7a5ce0;
  color: #fff;
  box-shadow: 0 1px 4px rgba(91, 64, 180, 0.18);
}
.section-bar:hover {
  background: #7c5ce8;
  border-color: #6f50db;
}
.section-bar.active {
  background: #5c3fbf;
  border-color: #4f35a8;
  box-shadow: 0 2px 8px rgba(92, 63, 191, 0.35);
}
.section-bar .dot-check {
  border-color: rgba(255, 255, 255, 0.85);
  opacity: 0.85;
}
.section-bar .dot-check.on,
.section-bar .dot-check.partial {
  opacity: 1;
  background: #fff;
  border-color: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35);
}
/* L2 一级板块：白底 + 左侧紫条 */
.board-bar {
  height: 36px;
  background: #fff;
  border: 1px solid #d0d3d9;
  border-left: 3px solid #8b6cf0;
  color: #1d2129;
}
.board-bar:hover {
  background: #fafbff;
  border-color: #b9a3f7;
}
.board-bar.active {
  background: #f0ebff;
  border-color: #8b6cf0;
  border-left-color: #5c3fbf;
}
.board-bar .caret {
  border-top-width: 4px;
  border-bottom-width: 4px;
  border-left-width: 5px;
  color: #86909c;
}
.board-bar.active .caret {
  color: #5c3fbf;
}
.board-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 36px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.board-count {
  font-size: 12px;
  color: #86909c;
  flex-shrink: 0;
}
.board-bar.active .board-count {
  color: #5c3fbf;
}
.type-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-left: 4px;
  padding: 8px;
  background: #eef6ff;
  border: 1px solid #c9dff5;
  border-radius: 8px;
}
.type-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
/* L3 类型（设置下）：蓝色系，避免与紫色混淆 */
.type-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  height: 32px;
  padding: 0 10px;
  box-sizing: border-box;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  gap: 8px;
  background: #fff;
  border: 1px solid #9ec5e8;
  border-left: 3px solid #3b82c4;
  color: #1f4e79;
}
.type-bar:hover {
  background: #f5faff;
  border-color: #6ba3d6;
}
.type-bar.active {
  background: #dbebff;
  border-color: #3b82c4;
}
.type-bar .caret {
  border-top-width: 4px;
  border-bottom-width: 4px;
  border-left-width: 5px;
  color: #5b8cba;
}
.type-title {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 32px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.type-count {
  font-size: 12px;
  color: #5b8cba;
  flex-shrink: 0;
}
/* 纯 CSS 箭头，避免字体基线导致上下偏移 */
.caret {
  width: 0;
  height: 0;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
  border-left: 6px solid currentColor;
  flex-shrink: 0;
  transition: transform 0.15s ease;
  opacity: 0.85;
}
.caret.open {
  transform: rotate(90deg);
}
/* 小圆点选择态：空心未选 / 绿色实心已选 / 半选浅色实心 */
.dot-check {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-sizing: border-box;
  flex-shrink: 0;
  border: 1.5px solid currentColor;
  background: transparent;
  opacity: 0.45;
}
.dot-check.on {
  opacity: 1;
  background: #36C972;
  border-color: #36C972;
  box-shadow: 0 0 0 2px rgba(54, 201, 114, 0.28);
}
.dot-check.partial {
  opacity: 1;
  background: #86efac;
  border-color: #36C972;
  box-shadow: 0 0 0 2px rgba(54, 201, 114, 0.18);
}
.section-bar.active .dot-check {
  border-color: rgba(255, 255, 255, 0.85);
  opacity: 0.7;
}
.section-bar.active .dot-check.on,
.section-bar.active .dot-check.partial {
  opacity: 1;
  background: #fff;
  border-color: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35);
}
/* 功能集始终白字，勾选态用白点，避免绿色在紫底上对比弱 */
.section-bar .dot-check.on,
.section-bar .dot-check.partial {
  background: #fff;
  border-color: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35);
}
.section-title,
.item-name {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 40px; /* 与行高一致，保证文字垂直居中 */
  color: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-name {
  font-size: 12px;
  font-weight: 500;
  line-height: 36px;
}
.item-bind {
  flex-shrink: 0;
  margin-right: 6px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  border-radius: 4px;
  color: #0f766e;
  background: #ccfbf1;
}
.item-bar.checked .item-bind {
  color: #115e59;
  background: rgba(255, 255, 255, 0.85);
}
.section-count,
.item-count {
  flex-shrink: 0;
  margin: 0;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 40px;
  opacity: 0.85;
  color: inherit;
}
.item-count {
  line-height: 36px;
}
.item-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  width: 100%;
  padding-left: 0;
  box-sizing: border-box;
}
.item-bar {
  height: 36px;
  padding: 0 10px;
  background: #fff;
  border: 1px solid #e5e6eb;
  color: #1d2129;
}
.item-bar:hover {
  border-color: #8b6cf0;
  background: #faf8ff;
}
.item-bar.checked {
  background: rgba(139, 108, 240, 0.1);
  border-color: #8b6cf0;
  color: #5c3fbf;
}
.type-list .item-bar:hover {
  border-color: #3b82c4;
  background: #f5faff;
}
.type-list .item-bar.checked {
  background: rgba(59, 130, 196, 0.12);
  border-color: #3b82c4;
  color: #1f4e79;
}
.case-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.case-tag { margin-right: 4px; }
.footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-left,
.footer-right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 1200px) {
  .item-list { grid-template-columns: 1fr; }
}
</style>
