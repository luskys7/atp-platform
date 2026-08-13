<template>
  <div class="page-container">
    <PageHeader title="机型适配 · 基础配置" subtitle="维护功能集 → 一级板块 → 板块内容，以及机型能力与用例打标">
      <template #actions>
        <el-button type="success" plain :loading="loadingDemo" @click="loadFullDemoData">加载完整结果 Demo</el-button>
        <el-button type="primary" plain @click="$router.push('/machine-adaptation')">去创建任务</el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <el-tabs v-model="tab">
        <el-tab-pane label="机型管理" name="machines">
          <div class="toolbar">
            <el-button type="primary" @click="openMachineDialog()">新增机型</el-button>
            <el-button @click="loadMachines">刷新</el-button>
          </div>
          <el-table :data="machines" v-loading="loadingMachines" stripe>
            <el-table-column prop="machine_name" label="机型" min-width="120" />
            <el-table-column prop="hard_version" label="硬件版本" width="120" />
            <el-table-column prop="firm_version" label="固件版本" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 || row.status === '1' ? 'success' : 'info'" size="small">
                  {{ row.status === 1 || row.status === '1' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openMachineDialog(row)">编辑</el-button>
                <el-button link type="primary" @click="openCapability(row)">功能配置</el-button>
                <el-button link @click="toggleMachineStatus(row)">
                  {{ row.status === 1 || row.status === '1' ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="removeMachine(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="功能集管理" name="tags">
          <div class="toolbar">
            <el-button type="primary" @click="openTagDialog()">新增板块内容</el-button>
            <el-button type="primary" plain @click="openBatchTagDialog">批量添加</el-button>
            <el-button type="success" plain :loading="loadingDemo" @click="loadFullDemoData">加载完整结果 Demo</el-button>
            <el-button @click="loadTags">刷新</el-button>
          </div>
          <el-table :data="tags" v-loading="loadingTags" stripe>
            <el-table-column prop="tag_type" label="功能集" width="140" />
            <el-table-column prop="tag_name" label="一级板块" width="120" />
            <el-table-column prop="content_name" label="板块内容" min-width="160" />
            <el-table-column label="绑定公共步骤" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="commonStepNameOf(row)">{{ commonStepNameOf(row) }}</span>
                <span v-else class="muted">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 || row.status === '1' ? 'success' : 'info'" size="small">
                  {{ row.status === 1 || row.status === '1' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTagDialog(row)">编辑</el-button>
                <el-button link @click="toggleTagStatus(row)">
                  {{ row.status === 1 || row.status === '1' ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="removeTag(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用例打标" name="case-tags">
          <div class="toolbar">
            <el-input
              v-model="caseKeyword"
              placeholder="搜索用例名称"
              clearable
              style="width: 220px"
              @keyup.enter="loadCases"
              @clear="loadCases"
            />
            <el-button type="primary" @click="loadCases">查询</el-button>
            <el-button @click="loadCases">刷新</el-button>
            <el-button :disabled="!selectedCaseIds.length" @click="openBatchTag">批量打标</el-button>
            <el-button link type="primary" @click="$router.push('/cases')">前往用例管理</el-button>
          </div>
          <el-table
            :data="cases"
            v-loading="loadingCases"
            stripe
            empty-text="暂无用例，请先在「用例管理」中创建，或点上方刷新"
            @selection-change="rows => { selectedCaseIds = rows.map(r => r.id) }"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="用例" min-width="180" show-overflow-tooltip />
            <el-table-column prop="module_name" label="模块" width="120" show-overflow-tooltip />
            <el-table-column prop="case_status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.case_status === 'active' ? 'success' : 'info'">
                  {{ row.case_status || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="已绑标签" min-width="200">
              <template #default="{ row }">
                <el-tag
                  v-for="t in (caseTagMap[row.id] || [])"
                  :key="t.tag_id"
                  size="small"
                  class="mr4"
                  closable
                  @close="unbindCaseTag(row.id, t.tag_id)"
                >{{ tagLabel(t) }}</el-tag>
                <span v-if="!(caseTagMap[row.id] || []).length" class="muted">无</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSingleTag(row)">打标</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="casePage"
              v-model:page-size="casePageSize"
              :total="caseTotal"
              :page-sizes="[20, 50, 100, 200]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadCases"
              @size-change="onCasePageSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="批量导入" name="batch">
          <p class="hint">粘贴 JSON（含 machines / tags / machine_tags / case_tags）。也可直接点「加载测试 Demo」导入完整演示数据（含设置类型分组）。</p>
          <el-input
            v-model="batchJson"
            type="textarea"
            :rows="16"
            placeholder='{"machines":[...],"tags":[...],"machine_tags":[...],"case_tags":[...]}'
          />
          <div class="toolbar" style="margin-top: 12px">
            <el-button type="primary" :loading="batching" @click="runBatch">执行导入</el-button>
            <el-button type="success" plain :loading="loadingDemo" @click="loadFullDemoData">加载完整结果 Demo</el-button>
            <el-button @click="loadSampleHint">填入示例结构</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </AppCard>

    <!-- 机型编辑 -->
    <el-dialog v-model="machineDialog" :title="machineForm.id ? '编辑机型' : '新增机型'" width="480px">
      <el-form :model="machineForm" label-width="96px">
        <el-form-item label="机型名称" required>
          <el-input v-model="machineForm.machine_name" />
        </el-form-item>
        <el-form-item label="硬件版本">
          <el-input v-model="machineForm.hard_version" />
        </el-form-item>
        <el-form-item label="固件版本">
          <el-input v-model="machineForm.firm_version" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="machineForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="machineEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="machineDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMachine">保存</el-button>
      </template>
    </el-dialog>

    <!-- 机型功能配置：左侧功能集 + 右侧当前板块 -->
    <el-dialog
      v-model="capDialog"
      :title="`功能配置 · ${capMachine?.machine_name || ''}`"
      fullscreen
      destroy-on-close
      class="cap-fullscreen-dialog"
    >
      <div v-if="!capabilitySections.length" class="cap-empty">暂无启用中的功能项，请先在「功能集管理」中维护</div>
      <div v-else class="cap-layout">
        <aside class="cap-nav">
          <div class="cap-nav-head">
            <span>已选 {{ capSelected.length }} / {{ enabledTags.length }}</span>
            <div>
              <el-button link type="primary" @click="capSelectAll">全选</el-button>
              <el-button link @click="capSelected = []">清空</el-button>
            </div>
          </div>
          <button
            v-for="sec in capabilitySections"
            :key="sec.section_name"
            type="button"
            class="cap-nav-item"
            :class="{ active: capActiveSection === sec.section_name }"
            @click="capActiveSection = sec.section_name"
          >
            <span class="cap-nav-name">{{ sec.section_name }}</span>
            <span class="cap-nav-count">{{ capSectionSelected(sec) }}/{{ sec.items.length }}</span>
          </button>
        </aside>
        <main class="cap-main" v-if="capActiveSec">
          <div class="cap-main-head">
            <el-checkbox
              :model-value="isCapSectionChecked(capActiveSec)"
              :indeterminate="isCapSectionPartial(capActiveSec)"
              @change="(v) => toggleCapSection(capActiveSec, v)"
            >
              {{ capActiveSec.section_name }}
            </el-checkbox>
            <span class="muted">{{ capSectionSelected(capActiveSec) }} / {{ capActiveSec.items.length }}</span>
          </div>
          <div class="cap-groups">
            <div
              v-for="board in capActiveSec.boards"
              :key="`${capActiveSec.section_name}-${board.board_name}`"
              class="cap-group"
            >
              <div class="cap-group-head">
                <el-checkbox
                  :model-value="isCapBoardChecked(board)"
                  :indeterminate="isCapBoardPartial(board)"
                  @change="(v) => toggleCapBoard(board, v)"
                >
                  {{ board.board_name }}
                </el-checkbox>
                <span class="muted">{{ capBoardSelected(board) }}/{{ board.items.length }}</span>
              </div>
              <el-checkbox-group v-model="capSelected" class="cap-chips">
                <el-checkbox
                  v-for="t in board.items"
                  :key="t.id"
                  :label="t.id"
                  border
                  size="small"
                  class="cap-chip"
                >{{ tagLabel(t) }}</el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </main>
      </div>
      <template #footer>
        <el-button @click="capDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCapability">保存</el-button>
      </template>
    </el-dialog>

    <!-- 标签编辑 -->
    <el-dialog v-model="tagDialog" :title="tagForm.id ? '编辑板块内容' : '新增板块内容'" width="520px">
      <el-form :model="tagForm" label-width="108px">
        <el-form-item label="功能集" required>
          <el-select v-model="tagForm.tag_type" allow-create filterable placeholder="如：清洁功能" style="width: 100%">
            <el-option v-for="s in featureSections" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="一级板块" required>
          <el-input v-model="tagForm.tag_name" placeholder="如：清洁" />
        </el-form-item>
        <el-form-item label="板块内容" required>
          <el-input v-model="tagForm.content_name" placeholder="如：清扫、任务完成通知" />
        </el-form-item>
        <el-form-item label="公共步骤">
          <el-select
            v-model="tagForm.common_step_id"
            clearable
            filterable
            placeholder="绑定后可用于组合生成用例"
            style="width: 100%"
          >
            <el-option
              v-for="s in commonSteps"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
          <div class="hint" style="margin-top: 6px; margin-bottom: 0">
            勾选功能拼装用例时，将按序插入 invoke_common 引用该步骤
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="tagForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="tagEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量添加板块内容 -->
    <el-dialog v-model="batchTagDialog" title="批量添加板块内容" width="560px">
      <el-form :model="batchTagForm" label-width="96px">
        <el-form-item label="功能集" required>
          <el-select v-model="batchTagForm.tag_type" allow-create filterable placeholder="如：清洁功能" style="width: 100%">
            <el-option v-for="s in featureSections" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="一级板块" required>
          <el-input v-model="batchTagForm.tag_name" placeholder="如：基站、清洁" />
        </el-form-item>
        <el-form-item label="板块内容" required>
          <el-input
            v-model="batchTagForm.contents"
            type="textarea"
            :rows="10"
            placeholder="每行一项，也支持用逗号、顿号、分号分隔&#10;例如：&#10;设备面板-基站集尘&#10;设备面板-基站清洗&#10;设备面板-基站烘干"
          />
          <div class="hint" style="margin-top: 6px; margin-bottom: 0">
            将写入同一功能集 / 一级板块下；已存在的项会更新为启用。当前可解析 {{ batchContentPreview.length }} 项
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="batchTagForm.description" type="textarea" :rows="2" placeholder="可选，批量共用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchTagDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchTagSaving" :disabled="!batchContentPreview.length" @click="saveBatchTags">
          添加 {{ batchContentPreview.length || '' }} 项
        </el-button>
      </template>
    </el-dialog>

    <!-- 用例打标：与功能配置一致的左侧功能集 + 右侧分组勾选 -->
    <el-dialog
      v-model="caseTagDialog"
      :title="caseTagTitle"
      fullscreen
      destroy-on-close
      class="cap-fullscreen-dialog"
    >
      <div v-if="!capabilitySections.length" class="cap-empty">暂无启用中的功能项，请先在「功能集管理」中维护</div>
      <div v-else class="cap-layout">
        <aside class="cap-nav">
          <div class="cap-nav-head">
            <span>已选 {{ caseTagSelected.length }} / {{ enabledTags.length }}</span>
            <div>
              <el-button link type="primary" @click="caseTagSelectAll">全选</el-button>
              <el-button link @click="caseTagSelected = []">清空</el-button>
            </div>
          </div>
          <button
            v-for="sec in caseTagDisplaySections"
            :key="sec.section_name"
            type="button"
            class="cap-nav-item"
            :class="{ active: caseTagActiveSection === sec.section_name }"
            @click="caseTagActiveSection = sec.section_name"
          >
            <span class="cap-nav-name">{{ sec.section_name }}</span>
            <span class="cap-nav-count">{{ caseTagSectionSelected(sec) }}/{{ sec.items.length }}</span>
          </button>
        </aside>
        <main class="cap-main" v-if="caseTagActiveSec">
          <div class="cap-main-head">
            <el-checkbox
              :model-value="isCaseTagSectionChecked(caseTagActiveSec)"
              :indeterminate="isCaseTagSectionPartial(caseTagActiveSec)"
              @change="(v) => toggleCaseTagSection(caseTagActiveSec, v)"
            >
              {{ caseTagActiveSec.section_name }}
            </el-checkbox>
            <div class="cap-main-head-right">
              <el-input
                v-model="caseTagKeyword"
                clearable
                size="small"
                placeholder="搜索板块内容"
                style="width: 220px"
              />
              <span class="muted">
                {{ caseTagSectionSelected(caseTagActiveSec) }} / {{ caseTagActiveSec.items.length }}
                <template v-if="caseTagMode === 'batch'"> · 批量追加到 {{ caseTagTargetIds.length }} 条用例</template>
              </span>
            </div>
          </div>
          <div class="cap-groups">
            <div
              v-for="board in caseTagActiveSec.boards"
              :key="`${caseTagActiveSec.section_name}-${board.board_name}`"
              class="cap-group"
            >
              <div class="cap-group-head">
                <el-checkbox
                  :model-value="isCaseTagBoardChecked(board)"
                  :indeterminate="isCaseTagBoardPartial(board)"
                  @change="(v) => toggleCaseTagBoard(board, v)"
                >
                  {{ board.board_name }}
                </el-checkbox>
                <span class="muted">{{ caseTagBoardSelected(board) }}/{{ board.items.length }}</span>
              </div>
              <el-checkbox-group v-model="caseTagSelected" class="cap-chips">
                <el-checkbox
                  v-for="t in board.items"
                  :key="t.id"
                  :label="t.id"
                  border
                  size="small"
                  class="cap-chip"
                >{{ tagLabel(t) }}</el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </main>
      </div>
      <template #footer>
        <el-button @click="caseTagDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCaseTags">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { caseApi, commonStepApi, machineAdaptationApi } from '@/api'

const route = useRoute()
const router = useRouter()
const tab = ref(route.query.tab || 'machines')

/** 标准功能集（固定排版顺序） */
const featureSections = [
  '清洁功能',
  '地图功能',
  '智能功能',
  '设备管理',
  '通知体系',
  '用户体系'
]

function featureOrderIndex(name) {
  const i = featureSections.indexOf(name)
  return i >= 0 ? i : featureSections.length + 1
}

function sortTagsByFeature(list) {
  const rows = list || []
  const boardFirstId = new Map()
  for (const t of rows) {
    const key = `${t.tag_type || ''}::${t.tag_name || ''}`
    const id = Number(t.id) || 0
    if (!boardFirstId.has(key) || id < boardFirstId.get(key)) {
      boardFirstId.set(key, id)
    }
  }
  return [...rows].sort((a, b) => {
    const d = featureOrderIndex(a.tag_type) - featureOrderIndex(b.tag_type)
    if (d !== 0) return d
    const keyA = `${a.tag_type || ''}::${a.tag_name || ''}`
    const keyB = `${b.tag_type || ''}::${b.tag_name || ''}`
    const boardOrder = (boardFirstId.get(keyA) || 0) - (boardFirstId.get(keyB) || 0)
    if (boardOrder !== 0) return boardOrder
    return (Number(a.id) || 0) - (Number(b.id) || 0)
  })
}

const loadingMachines = ref(false)
const loadingTags = ref(false)
const loadingCases = ref(false)
const saving = ref(false)
const batching = ref(false)
const loadingDemo = ref(false)

const machines = ref([])
const tags = ref([])
const commonSteps = ref([])
const cases = ref([])
const caseTagMap = reactive({})
const selectedCaseIds = ref([])
const caseKeyword = ref('')
const casePage = ref(1)
const casePageSize = ref(100)
const caseTotal = ref(0)
const batchJson = ref('')
const loadingCaseTags = ref(false)

const machineDialog = ref(false)
const machineForm = reactive({
  id: null,
  machine_name: '',
  hard_version: '',
  firm_version: '',
  remark: '',
  status: 1
})
const machineEnabled = computed({
  get: () => machineForm.status === 1 || machineForm.status === '1',
  set: (v) => { machineForm.status = v ? 1 : 0 }
})

const tagDialog = ref(false)
const tagForm = reactive({
  id: null,
  tag_name: '',
  tag_type: '',
  content_name: '',
  description: '',
  common_step_id: null,
  status: 1
})
const tagEnabled = computed({
  get: () => tagForm.status === 1 || tagForm.status === '1',
  set: (v) => { tagForm.status = v ? 1 : 0 }
})

function tagLabel(t) {
  if (!t) return ''
  return t.content_name || t.tag_name || ''
}

const batchTagDialog = ref(false)
const batchTagSaving = ref(false)
const batchTagForm = reactive({
  tag_type: '',
  tag_name: '',
  contents: '',
  description: ''
})

const batchContentPreview = computed(() => parseBatchContents(batchTagForm.contents))

function parseBatchContents(text) {
  if (!text || !String(text).trim()) return []
  const parts = String(text)
    .split(/[\n\r,，;；、]+/)
    .map(s => s.trim())
    .filter(Boolean)
  return [...new Set(parts)]
}

function openBatchTagDialog() {
  Object.assign(batchTagForm, {
    tag_type: featureSections[0] || '',
    tag_name: '',
    contents: '',
    description: ''
  })
  batchTagDialog.value = true
}

async function saveBatchTags() {
  if (!batchTagForm.tag_type?.trim()) {
    ElMessage.warning('请填写功能集')
    return
  }
  if (!batchTagForm.tag_name?.trim()) {
    ElMessage.warning('请填写一级板块')
    return
  }
  const contents = parseBatchContents(batchTagForm.contents)
  if (!contents.length) {
    ElMessage.warning('请填写至少一项板块内容')
    return
  }
  batchTagSaving.value = true
  try {
    const tagsPayload = contents.map(content_name => ({
      tag_type: batchTagForm.tag_type.trim(),
      tag_name: batchTagForm.tag_name.trim(),
      content_name,
      description: batchTagForm.description || undefined,
      status: 1
    }))
    const res = await machineAdaptationApi.batch({ tags: tagsPayload })
    const d = res.data || {}
    ElMessage.success(`批量完成：新增 ${d.tag_created ?? 0}，更新 ${d.tag_updated ?? 0}`)
    batchTagDialog.value = false
    await loadTags()
  } finally {
    batchTagSaving.value = false
  }
}

const capDialog = ref(false)
const capMachine = ref(null)
const capSelected = ref([])
const capActiveSection = ref('')

const caseTagDialog = ref(false)
const caseTagMode = ref('single')
const caseTagTargetIds = ref([])
const caseTagSelected = ref([])
const caseTagActiveSection = ref('')
const caseTagTargetNames = ref([])
const caseTagKeyword = ref('')
const caseTagTitle = computed(() => {
  if (caseTagMode.value === 'batch') {
    return `批量打标（${caseTagTargetIds.value.length} 条）`
  }
  const name = caseTagTargetNames.value[0]
  return name ? `用例打标 · ${name}` : '用例打标'
})

const enabledTags = computed(() =>
  (tags.value || []).filter(t => t.status === 1 || t.status === '1')
)

const capabilitySections = computed(() => {
  const list = enabledTags.value
  const sectionMap = new Map()
  for (const name of featureSections) {
    sectionMap.set(name, [])
  }
  for (const t of list) {
    const section = (t.tag_type || '').trim() || '未分类'
    if (!sectionMap.has(section)) sectionMap.set(section, [])
    sectionMap.get(section).push(t)
  }
  const sections = []
  for (const [section_name, items] of sectionMap.entries()) {
    if (!items.length && featureSections.includes(section_name)) {
      // 固定六项占位，无内容也展示空板块便于对齐；若完全无标签则跳过空项
      continue
    }
    if (!items.length) continue
    const boardMap = new Map()
    for (const t of items) {
      const board = (t.tag_name || '').trim() || '未分组'
      if (!boardMap.has(board)) boardMap.set(board, [])
      boardMap.get(board).push(t)
    }
    const boards = [...boardMap.entries()]
      .map(([board_name, boardItems]) => ({
        board_name,
        items: [...boardItems].sort((a, b) => (Number(a.id) || 0) - (Number(b.id) || 0)),
        first_id: Math.min(...boardItems.map(i => Number(i.id) || Number.MAX_SAFE_INTEGER))
      }))
      .sort((a, b) => a.first_id - b.first_id)
    sections.push({
      section_name,
      items,
      boards
    })
  }
  // 固定功能集顺序，其余追加
  return sections.sort((a, b) => featureOrderIndex(a.section_name) - featureOrderIndex(b.section_name))
})

const caseTagFilteredSections = computed(() => {
  const kw = caseTagKeyword.value.trim().toLowerCase()
  const sections = capabilitySections.value || []
  if (!kw) return sections
  return sections
    .map(sec => {
      const boards = (sec.boards || [])
        .map(board => ({
          ...board,
          items: (board.items || []).filter(t => {
            const label = `${tagLabel(t)} ${t.tag_name || ''} ${t.tag_type || ''}`.toLowerCase()
            return label.includes(kw)
          })
        }))
        .filter(board => board.items.length)
      const items = boards.flatMap(b => b.items)
      return { ...sec, boards, items }
    })
    .filter(sec => sec.items.length)
})

/** 有匹配则展示过滤结果；无匹配仍保留完整列表（配合 toast，避免全屏空态） */
const caseTagDisplaySections = computed(() => {
  const kw = caseTagKeyword.value.trim()
  if (!kw) return capabilitySections.value
  const filtered = caseTagFilteredSections.value
  return filtered.length ? filtered : capabilitySections.value
})

const caseTagActiveSec = computed(() =>
  caseTagDisplaySections.value.find(s => s.section_name === caseTagActiveSection.value)
  || caseTagDisplaySections.value[0]
  || null
)

watch(caseTagDisplaySections, (secs) => {
  if (!secs.length) return
  if (!secs.some(s => s.section_name === caseTagActiveSection.value)) {
    caseTagActiveSection.value = secs[0].section_name
  }
})

let caseTagSearchToastTimer = null
watch(caseTagKeyword, (kw) => {
  if (caseTagSearchToastTimer) clearTimeout(caseTagSearchToastTimer)
  if (!caseTagDialog.value) return
  caseTagSearchToastTimer = setTimeout(() => {
    const key = String(kw || '').trim()
    if (key && !caseTagFilteredSections.value.length) {
      ElMessage.warning('未找到匹配的功能项')
    }
  }, 320)
})

const capActiveSec = computed(() =>
  capabilitySections.value.find(s => s.section_name === capActiveSection.value)
  || capabilitySections.value[0]
  || null
)

function capSelectAll() {
  capSelected.value = enabledTags.value.map(t => t.id)
}

function capSectionSelected(sec) {
  const ids = new Set((sec.items || []).map(t => t.id))
  return capSelected.value.filter(id => ids.has(id)).length
}

function isCapSectionChecked(sec) {
  const items = sec.items || []
  return items.length > 0 && items.every(t => capSelected.value.includes(t.id))
}

function isCapSectionPartial(sec) {
  const n = capSectionSelected(sec)
  return n > 0 && n < (sec.items || []).length
}

function toggleCapSection(sec, checked) {
  const ids = (sec.items || []).map(t => t.id)
  if (checked) {
    capSelected.value = [...new Set([...capSelected.value, ...ids])]
  } else {
    const drop = new Set(ids)
    capSelected.value = capSelected.value.filter(id => !drop.has(id))
  }
}

function capBoardSelected(board) {
  const ids = new Set((board.items || []).map(t => t.id))
  return capSelected.value.filter(id => ids.has(id)).length
}

function isCapBoardChecked(board) {
  const items = board.items || []
  return items.length > 0 && items.every(t => capSelected.value.includes(t.id))
}

function isCapBoardPartial(board) {
  const n = capBoardSelected(board)
  return n > 0 && n < (board.items || []).length
}

function toggleCapBoard(board, checked) {
  const ids = (board.items || []).map(t => t.id)
  if (checked) {
    capSelected.value = [...new Set([...capSelected.value, ...ids])]
  } else {
    const drop = new Set(ids)
    capSelected.value = capSelected.value.filter(id => !drop.has(id))
  }
}

function caseTagSelectAll() {
  caseTagSelected.value = enabledTags.value.map(t => t.id)
}

function caseTagSectionSelected(sec) {
  const ids = new Set((sec.items || []).map(t => t.id))
  return caseTagSelected.value.filter(id => ids.has(id)).length
}

function isCaseTagSectionChecked(sec) {
  const items = sec.items || []
  return items.length > 0 && items.every(t => caseTagSelected.value.includes(t.id))
}

function isCaseTagSectionPartial(sec) {
  const n = caseTagSectionSelected(sec)
  return n > 0 && n < (sec.items || []).length
}

function toggleCaseTagSection(sec, checked) {
  const ids = (sec.items || []).map(t => t.id)
  if (checked) {
    caseTagSelected.value = [...new Set([...caseTagSelected.value, ...ids])]
  } else {
    const drop = new Set(ids)
    caseTagSelected.value = caseTagSelected.value.filter(id => !drop.has(id))
  }
}

function caseTagBoardSelected(board) {
  const ids = new Set((board.items || []).map(t => t.id))
  return caseTagSelected.value.filter(id => ids.has(id)).length
}

function isCaseTagBoardChecked(board) {
  const items = board.items || []
  return items.length > 0 && items.every(t => caseTagSelected.value.includes(t.id))
}

function isCaseTagBoardPartial(board) {
  const n = caseTagBoardSelected(board)
  return n > 0 && n < (board.items || []).length
}

function toggleCaseTagBoard(board, checked) {
  const ids = (board.items || []).map(t => t.id)
  if (checked) {
    caseTagSelected.value = [...new Set([...caseTagSelected.value, ...ids])]
  } else {
    const drop = new Set(ids)
    caseTagSelected.value = caseTagSelected.value.filter(id => !drop.has(id))
  }
}

async function loadMachines() {
  loadingMachines.value = true
  try {
    const res = await machineAdaptationApi.listMachines()
    machines.value = res.data || []
  } finally {
    loadingMachines.value = false
  }
}

async function loadTags() {
  loadingTags.value = true
  try {
    const res = await machineAdaptationApi.listTags()
    tags.value = sortTagsByFeature(res.data || [])
  } finally {
    loadingTags.value = false
  }
}

async function loadCommonSteps() {
  try {
    const res = await commonStepApi.list()
    const list = res?.data || res || []
    commonSteps.value = (Array.isArray(list) ? list : []).filter(s => !s.deleted_at)
  } catch {
    commonSteps.value = []
  }
}

function commonStepNameOf(row) {
  const id = row?.common_step_id
  if (id == null || id === '') return ''
  const hit = (commonSteps.value || []).find(s => Number(s.id) === Number(id))
  return hit?.name || row.common_step_name || `#${id}`
}

async function loadCases() {
  loadingCases.value = true
  try {
    const res = await caseApi.list({
      keyword: caseKeyword.value || undefined,
      page: casePage.value,
      page_size: casePageSize.value
    })
    // interceptor 已解包为 { code, data }；兼容 list / items / 直接数组
    const data = res?.data
    const list = Array.isArray(data)
      ? data
      : (data?.list || data?.items || data?.content || [])
    cases.value = list
    caseTotal.value = Number(data?.total ?? data?.total_elements ?? list.length) || list.length
    selectedCaseIds.value = []
    // 先展示用例，标签异步补齐，避免 N 次请求卡住表格
    loadCaseTagsForPage()
  } catch (e) {
    cases.value = []
    caseTotal.value = 0
    console.error('loadCases failed', e)
  } finally {
    loadingCases.value = false
  }
}

function onCasePageSizeChange() {
  casePage.value = 1
  loadCases()
}

async function loadCaseTagsForPage() {
  const rows = cases.value || []
  if (!rows.length) return
  loadingCaseTags.value = true
  try {
    await Promise.all(rows.map(async (c) => {
      try {
        const res = await machineAdaptationApi.listCaseTags(c.id)
        caseTagMap[c.id] = res.data || []
      } catch {
        caseTagMap[c.id] = []
      }
    }))
  } finally {
    loadingCaseTags.value = false
  }
}

function ensureCaseTagsTab() {
  if (route.query.tab === 'case-tags' || tab.value === 'case-tags') {
    tab.value = 'case-tags'
    loadCases()
  }
}

function openMachineDialog(row) {
  if (row) {
    Object.assign(machineForm, {
      id: row.id,
      machine_name: row.machine_name,
      hard_version: row.hard_version || '',
      firm_version: row.firm_version || '',
      remark: row.remark || '',
      status: row.status ?? 1
    })
  } else {
    Object.assign(machineForm, {
      id: null,
      machine_name: '',
      hard_version: '',
      firm_version: '',
      remark: '',
      status: 1
    })
  }
  machineDialog.value = true
}

async function saveMachine() {
  if (!machineForm.machine_name?.trim()) {
    ElMessage.warning('请填写机型名称')
    return
  }
  saving.value = true
  try {
    const body = {
      machine_name: machineForm.machine_name.trim(),
      hard_version: machineForm.hard_version,
      firm_version: machineForm.firm_version,
      remark: machineForm.remark,
      status: machineForm.status
    }
    if (machineForm.id) {
      await machineAdaptationApi.updateMachine(machineForm.id, body)
    } else {
      await machineAdaptationApi.createMachine(body)
    }
    ElMessage.success('已保存')
    machineDialog.value = false
    await loadMachines()
  } finally {
    saving.value = false
  }
}

async function toggleMachineStatus(row) {
  const next = (row.status === 1 || row.status === '1') ? 0 : 1
  await machineAdaptationApi.updateMachine(row.id, { status: next })
  ElMessage.success(next ? '已启用' : '已停用')
  await loadMachines()
}

async function removeMachine(row) {
  await ElMessageBox.confirm(`确认删除机型「${row.machine_name}」？`, '删除确认', { type: 'warning' })
  await machineAdaptationApi.deleteMachine(row.id)
  ElMessage.success('已删除')
  await loadMachines()
}

async function openCapability(row) {
  capMachine.value = row
  if (!tags.value.length) await loadTags()
  const res = await machineAdaptationApi.listMachineTags(row.id)
  capSelected.value = (res.data || [])
    .filter(r => r.is_support === 1 || r.is_support === '1')
    .map(r => r.tag_id)
  capActiveSection.value = capabilitySections.value[0]?.section_name || ''
  capDialog.value = true
}

async function saveCapability() {
  if (!capMachine.value) return
  saving.value = true
  try {
    const items = capSelected.value.map(tagId => ({ tag_id: tagId, is_support: 1 }))
    await machineAdaptationApi.replaceMachineTags(capMachine.value.id, items)
    ElMessage.success('功能配置已保存')
    capDialog.value = false
  } finally {
    saving.value = false
  }
}

function openTagDialog(row) {
  if (row) {
    Object.assign(tagForm, {
      id: row.id,
      tag_name: row.tag_name,
      tag_type: row.tag_type || '',
      content_name: row.content_name || '',
      description: row.description || '',
      common_step_id: row.common_step_id ?? null,
      status: row.status ?? 1
    })
  } else {
    Object.assign(tagForm, {
      id: null,
      tag_name: '',
      tag_type: '',
      content_name: '',
      description: '',
      common_step_id: null,
      status: 1
    })
  }
  if (!commonSteps.value.length) loadCommonSteps()
  tagDialog.value = true
}

async function saveTag() {
  if (!tagForm.tag_type?.trim()) {
    ElMessage.warning('请填写功能集')
    return
  }
  if (!tagForm.tag_name?.trim()) {
    ElMessage.warning('请填写一级板块')
    return
  }
  if (!tagForm.content_name?.trim()) {
    ElMessage.warning('请填写板块内容')
    return
  }
  saving.value = true
  try {
    const body = {
      tag_name: tagForm.tag_name.trim(),
      tag_type: tagForm.tag_type.trim(),
      content_name: tagForm.content_name.trim(),
      description: tagForm.description,
      common_step_id: tagForm.common_step_id ?? null,
      status: tagForm.status
    }
    if (tagForm.id) {
      await machineAdaptationApi.updateTag(tagForm.id, body)
      ElMessage.success('已更新')
    } else {
      await machineAdaptationApi.createTag(body)
      ElMessage.success('已创建')
    }
    tagDialog.value = false
    await loadTags()
  } finally {
    saving.value = false
  }
}

async function toggleTagStatus(row) {
  const next = (row.status === 1 || row.status === '1') ? 0 : 1
  await machineAdaptationApi.updateTag(row.id, { status: next })
  ElMessage.success(next ? '已启用' : '已停用')
  await loadTags()
}

async function removeTag(row) {
  const label = tagLabel(row) || row.tag_name
  await ElMessageBox.confirm(`确认删除「${label}」？关联关系将一并删除`, '删除确认', { type: 'warning' })
  await machineAdaptationApi.deleteTag(row.id)
  ElMessage.success('已删除')
  await loadTags()
}

async function openSingleTag(row) {
  if (!tags.value.length) await loadTags()
  caseTagMode.value = 'single'
  caseTagTargetIds.value = [row.id]
  caseTagTargetNames.value = [row.name || `用例 #${row.id}`]
  caseTagSelected.value = (caseTagMap[row.id] || []).map(t => t.tag_id)
  caseTagKeyword.value = ''
  const selected = new Set(caseTagSelected.value)
  const hit = capabilitySections.value.find(sec =>
    (sec.items || []).some(t => selected.has(t.id))
  )
  caseTagActiveSection.value = hit?.section_name || capabilitySections.value[0]?.section_name || ''
  caseTagDialog.value = true
}

async function openBatchTag() {
  if (!selectedCaseIds.value.length) return
  if (!tags.value.length) await loadTags()
  caseTagMode.value = 'batch'
  caseTagTargetIds.value = [...selectedCaseIds.value]
  const idSet = new Set(caseTagTargetIds.value)
  caseTagTargetNames.value = (cases.value || [])
    .filter(c => idSet.has(c.id))
    .map(c => c.name || `用例 #${c.id}`)
  caseTagSelected.value = []
  caseTagKeyword.value = ''
  caseTagActiveSection.value = capabilitySections.value[0]?.section_name || ''
  caseTagDialog.value = true
}

async function saveCaseTags() {
  saving.value = true
  try {
    for (const caseId of caseTagTargetIds.value) {
      let tagIds = [...caseTagSelected.value]
      if (caseTagMode.value === 'batch') {
        const existing = (caseTagMap[caseId] || []).map(t => t.tag_id)
        tagIds = [...new Set([...existing, ...caseTagSelected.value])]
      }
      await machineAdaptationApi.replaceCaseTags(caseId, tagIds)
    }
    ElMessage.success('打标已保存')
    caseTagDialog.value = false
    await loadCaseTagsForPage()
  } finally {
    saving.value = false
  }
}

async function unbindCaseTag(caseId, tagId) {
  await machineAdaptationApi.removeCaseTag(caseId, tagId)
  await loadCaseTagsForPage()
}

function loadSampleHint() {
  batchJson.value = JSON.stringify({
    machines: [{ machine_name: 'CX2', hard_version: 'HW-C', firm_version: '3.0.0' }],
    tags: [
      { tag_type: '设备管理', tag_name: '设置', content_name: '鲸灵托管-扫地吸力' },
      { tag_type: '设备管理', tag_name: '设置', content_name: '通用-勿扰模式' },
      { tag_type: '清洁功能', tag_name: '清洁', content_name: '清扫' }
    ],
    machine_tags: [{ machine_name: 'CX2', content_name: '通用-勿扰模式', is_support: 1 }]
  }, null, 2)
}

async function loadFullDemoData() {
  try {
    await ElMessageBox.confirm(
      '将生成完整结果 Demo：功能标签 + 用例 + 已完成任务/日志/测试报告（成功+失败样例），无需真实设备。',
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
    await Promise.all([loadMachines(), loadTags()])
    await ElMessageBox.confirm(
      `已生成报告任务 ${(d.report_task_ids || []).length} 条。是否前往报告中心？`,
      'Demo 加载完成',
      { type: 'success', confirmButtonText: '查看报告', cancelButtonText: '留在本页' }
    ).then(() => router.push('/reports')).catch(() => {})
  } finally {
    loadingDemo.value = false
  }
}

async function runBatch() {
  let body
  try {
    body = JSON.parse(batchJson.value || '{}')
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  batching.value = true
  try {
    const res = await machineAdaptationApi.batch(body)
    ElMessage.success(`导入完成：${JSON.stringify(res.data)}`)
    await Promise.all([loadMachines(), loadTags()])
  } finally {
    batching.value = false
  }
}

onMounted(async () => {
  if (route.query.tab) tab.value = String(route.query.tab)
  await Promise.all([loadMachines(), loadTags(), loadCommonSteps()])
  if (tab.value === 'case-tags') await loadCases()
})

onActivated(() => {
  if (route.query.tab) tab.value = String(route.query.tab)
  ensureCaseTagsTab()
})

watch(tab, async (v) => {
  router.replace({ query: { ...route.query, tab: v } })
  if (v === 'case-tags') await loadCases()
})

watch(() => route.query.tab, (v) => {
  if (v && String(v) !== tab.value) {
    tab.value = String(v)
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.muted {
  color: var(--atp-text-muted, #94a3b8);
  font-size: 12px;
}
.mr4 {
  margin-right: 4px;
  margin-bottom: 2px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  color: var(--atp-text-muted, #64748b);
  font-size: 13px;
  margin-bottom: 8px;
}
.cap-empty {
  text-align: center;
  color: #86909c;
  padding: 48px 16px;
}
.cap-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 0;
  height: calc(100vh - 150px);
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.cap-nav {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e5e6eb;
  background: #fafbfc;
  overflow: auto;
}
.cap-nav-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 12px;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}
.cap-nav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 11px 14px;
  border: none;
  border-left: 3px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: #4e5969;
  font-size: 13px;
}
.cap-nav-item:hover {
  background: #f2f3f5;
}
.cap-nav-item.active {
  background: #fff;
  border-left-color: #5c3fbf;
  color: #1d2129;
  font-weight: 600;
}
.cap-nav-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cap-nav-count {
  flex-shrink: 0;
  font-size: 12px;
  color: #86909c;
  font-weight: 400;
}
.cap-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: auto;
  padding: 14px 18px 20px;
}
.cap-main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}
.cap-main-head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.cap-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.cap-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.cap-chips {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}
.cap-chip {
  margin: 0 !important;
  height: auto !important;
  min-height: 32px;
  width: 100% !important;
  padding: 5px 10px !important;
  border-radius: 6px !important;
  box-sizing: border-box;
  border-color: #e5e6eb !important;
  background: #fff !important;
}
.cap-chip :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.3;
  font-size: 12px;
  padding-left: 6px;
  color: #4e5969;
}
.cap-chip.is-checked {
  border-color: #d4c8f5 !important;
  background: #f7f5fc !important;
}
.cap-chip.is-checked :deep(.el-checkbox__label) {
  color: #5c3fbf;
}
.cap-chip.is-checked :deep(.el-checkbox__inner) {
  background-color: #9e82f3;
  border-color: #9e82f3;
}
@media (max-width: 1200px) {
  .cap-chips { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .cap-chips { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .cap-layout {
    grid-template-columns: 1fr;
    height: auto;
    max-height: calc(100vh - 150px);
  }
  .cap-nav {
    flex-direction: row;
    flex-wrap: wrap;
    border-right: none;
    border-bottom: 1px solid #e5e6eb;
  }
  .cap-nav-item {
    width: auto;
    border-left: none;
    border-bottom: 2px solid transparent;
    padding: 8px 12px;
  }
  .cap-nav-item.active {
    border-bottom-color: #5c3fbf;
  }
  .cap-chips { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

<style>
.cap-fullscreen-dialog .el-dialog__body {
  padding-top: 8px;
  padding-bottom: 8px;
}
.cap-fullscreen-dialog .el-dialog__footer {
  padding-top: 8px;
}
</style>
