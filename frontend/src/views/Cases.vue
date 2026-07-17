<template>
  <div class="page-container cases-page">
    <PageHeader title="测试用例" subtitle="用例目录与编辑（批量执行请跳转至「测试任务」页面）">
      <template #actions>
        <div class="header-actions">
          <div class="action-group batch-group">
            <el-tooltip content="请先勾选目标用例后再执行批量操作" :disabled="hasSelection" placement="bottom">
              <span class="batch-wrap">
                <el-button :disabled="!hasSelection" @click="openTransfer">移交选中</el-button>
                <el-button :disabled="!hasSelection" @click="batchCopy">批量复制</el-button>
                <el-button :disabled="!hasSelection" @click="openBatchMove">批量移动目录</el-button>
                <el-button :disabled="!hasSelection" type="danger" plain @click="batchDelete">批量删除</el-button>
                <el-button :disabled="!hasSelection" @click="batchToggleStatus(true)">批量启用</el-button>
                <el-button :disabled="!hasSelection" @click="batchToggleStatus(false)">批量停用</el-button>
              </span>
            </el-tooltip>
          </div>
          <el-divider direction="vertical" class="action-divider" />
          <div class="action-group core-group">
            <el-button type="success" class="btn-run" @click="goBatchRun">
              <el-icon><VideoPlay /></el-icon> 去跑测
            </el-button>
            <el-button
              v-if="userStore.canEdit && !userStore.isReadonly"
              type="warning"
              class="btn-record"
              @click="goQuickRecord"
            >
              <el-icon><VideoCamera /></el-icon> 一键录制
            </el-button>
            <el-button class="btn-muted" @click="openFolderDialog()">新建目录</el-button>
            <el-button type="primary" class="btn-create" @click="openEditor()">
              <el-icon><Plus /></el-icon> 新建用例
            </el-button>
          </div>
        </div>
      </template>
    </PageHeader>

    <el-row :gutter="20" class="cases-main">
      <el-col :xs="24" :md="6">
        <AppCard class="folder-card" :hover="false">
          <template #header>
            <div class="folder-card-head">
              <div>
                <div class="folder-card-title">用例目录</div>
                <p class="tree-hint">拖拽文件夹可调整层级与排序</p>
              </div>
              <div class="folder-card-ops">
                <el-button size="small" @click="openFolderDialog()">新建文件夹</el-button>
                <el-button size="small" :loading="folderLoading" @click="loadFolders">刷新目录</el-button>
              </div>
            </div>
          </template>

          <el-tree
            class="case-tree"
            :key="treeKey"
            :data="folderTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            draggable
            :allow-drag="allowDrag"
            :allow-drop="allowDrop"
            @node-click="onFolderClick"
            @node-drop="handleNodeDrop"
            @node-contextmenu="onFolderContext"
          >
            <template #default="{ data }">
              <div class="folder-node">
                <el-icon class="folder-icon"><Folder /></el-icon>
                <span class="folder-name">{{ data.name }}</span>
                <span class="folder-count">({{ folderCaseCount(data) }})</span>
              </div>
            </template>
          </el-tree>

          <div class="recycle-entry" @click="goRecycle">
            <el-icon><Delete /></el-icon>
            <span>回收站</span>
          </div>
        </AppCard>
      </el-col>

      <el-col :xs="24" :md="18">
        <AppCard :hover="false" class="list-card">
          <div class="filter-bar">
            <el-select v-model="filters.status" placeholder="状态" clearable style="width:120px" @change="onFilterChange">
              <el-option label="全部" value="" />
              <el-option label="待评审" value="review" />
              <el-option label="已生效" value="active" />
              <el-option label="已禁用" value="deprecated" />
            </el-select>
            <el-select v-model="filters.platform" placeholder="平台" clearable style="width:110px" @change="onFilterChange">
              <el-option label="安卓" value="android" />
              <el-option label="iOS" value="ios" />
            </el-select>
            <el-select v-model="filters.version" placeholder="版本" clearable style="width:120px" @change="onFilterChange">
              <el-option v-for="v in versionOptions" :key="v" :label="`v${v}`" :value="String(v)" />
            </el-select>
            <el-select v-model="filters.priority" placeholder="优先级" clearable style="width:110px" @change="onFilterChange">
              <el-option label="高" value="high" />
              <el-option label="中" value="medium" />
              <el-option label="低" value="low" />
            </el-select>
            <el-select v-model="filters.tag" placeholder="标签" clearable filterable style="width:140px" @change="onFilterChange">
              <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
            </el-select>
            <el-input
              v-model="filters.keyword"
              placeholder="用例名称 / 备注"
              clearable
              style="width:200px"
              @change="onFilterChange"
              @clear="onFilterChange"
            />
            <div class="filter-right">
              <el-button type="primary" plain :loading="loading" @click="loadCases">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
              <el-button @click="resetFilters">重置筛选条件</el-button>
            </div>
          </div>

          <el-table
            :data="displayCases"
            v-loading="loading"
            stripe
            class="cases-table"
            empty-text=""
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="name" label="用例名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="case-name-cell">
                  <el-icon class="case-file-icon"><Document /></el-icon>
                  <strong>{{ row.name }}</strong>
                  <el-tag v-if="row.dataset_id" size="small" class="tag-parametric">参数化</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="case_status" label="状态" width="100">
              <template #default="{ row }">
                <span class="status-text" :class="statusClass(row.case_status)">{{ statusDisplay(row.case_status) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="platform" label="平台" width="90">
              <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
            </el-table-column>
            <el-table-column prop="version_num" label="版本" width="80" align="center">
              <template #default="{ row }">{{ row.version_num != null ? `v${row.version_num}` : '-' }}</template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="center">
              <template #default="{ row }">{{ priorityLabel(row.priority) }}</template>
            </el-table-column>
            <el-table-column prop="tags" label="标签" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.tags || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button size="small" type="success" @click="runCase(row)">执行</el-button>
                  <el-button size="small" @click="openEditor(row)">编辑</el-button>
                  <el-button size="small" class="btn-copy" @click="copyCase(row)">复制</el-button>
                  <el-button size="small" type="danger" plain @click="deleteCase(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!loading && !displayCases.length" class="table-empty">
            <p>暂无测试用例</p>
            <div class="table-empty__actions">
              <el-button type="primary" @click="openEditor()">新建用例</el-button>
              <el-button
                v-if="userStore.canEdit && !userStore.isReadonly"
                type="warning"
                class="btn-record"
                @click="goQuickRecord"
              >一键录制</el-button>
            </div>
          </div>

          <div class="pager-bar">
            <div class="pager-stats">
              当前目录共 <strong>{{ folderStats.total }}</strong> 条用例｜
              已生效 <strong class="ok">{{ folderStats.active }}</strong> 条｜
              待评审 <strong class="warn">{{ folderStats.review }}</strong> 条
            </div>
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              :total="filteredTotal"
              layout="total, prev, pager, next"
              @current-change="page = $event"
            />
          </div>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 5：底部快捷区 -->
    <section class="shortcut-bar">
      <div class="shortcut-card" @click="$router.push({ path: '/platform-config', query: { tab: 'steps' } })">
        <el-icon :size="22"><Connection /></el-icon>
        <div>
          <h4>公共步骤库</h4>
          <p>跳转公共组件页面，拖拽复用通用操作步骤</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/element-picker')">
        <el-icon :size="22"><Aim /></el-icon>
        <div>
          <h4>控件拾取库</h4>
          <p>跳转控件拾取页面，统一管理页面控件定位</p>
        </div>
      </div>
      <div class="shortcut-card" @click="showTemplateDialog = true">
        <el-icon :size="22"><CopyDocument /></el-icon>
        <div>
          <h4>模板用例</h4>
          <p>内置登录、首页跳转等通用基础用例，一键复制新建</p>
        </div>
      </div>
    </section>

    <!-- 右键菜单 -->
    <ul
      v-show="ctxMenu.visible"
      class="folder-ctx-menu"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
      @click.stop
    >
      <li @click="ctxRename">重命名文件夹</li>
      <li @click="ctxMove">移动文件夹</li>
      <li class="danger" @click="ctxDelete">删除空文件夹</li>
    </ul>

    <el-dialog v-model="showFolderDialog" :title="folderForm.id ? '编辑目录' : '新建目录'" width="400px">
      <el-form label-width="80px">
        <el-form-item label="目录名"><el-input v-model="folderForm.name" /></el-form-item>
        <el-form-item label="上级目录">
          <el-select v-model="folderForm.parent_id" placeholder="根目录" clearable style="width:100%">
            <el-option label="根目录（全部用例）" :value="null" />
            <el-option
              v-for="f in flatFolders.filter(x => x.id !== folderForm.id)"
              :key="f.id"
              :label="f.label"
              :value="f.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFolderDialog = false">取消</el-button>
        <el-button type="primary" @click="saveFolder">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBatchMove" title="批量移动目录" width="420px">
      <el-form label-width="90px">
        <el-form-item label="目标目录">
          <el-select v-model="batchMoveFolderId" placeholder="根目录" clearable style="width:100%">
            <el-option label="根目录（全部用例）" :value="null" />
            <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchMove = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="confirmBatchMove">确认移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTemplateDialog" title="模板用例" width="520px">
      <div
        v-for="tpl in CASE_TEMPLATES"
        :key="tpl.key"
        class="tpl-item"
      >
        <div>
          <strong>{{ tpl.name }}</strong>
          <p>{{ tpl.desc }}</p>
        </div>
        <el-button type="primary" size="small" :loading="tplLoading === tpl.key" @click="createFromTemplate(tpl)">
          一键复制新建
        </el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="showVersionDialog" title="版本历史" width="720px">
      <div v-if="versions.length >= 2" style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <el-select v-model="compareA" placeholder="版本 A" size="small" style="width:140px">
          <el-option v-for="v in versions" :key="v.id" :label="`v${v.version_num}`" :value="v.id" />
        </el-select>
        <span>对比</span>
        <el-select v-model="compareB" placeholder="版本 B" size="small" style="width:140px">
          <el-option v-for="v in versions" :key="v.id" :label="`v${v.version_num}`" :value="v.id" />
        </el-select>
        <el-button type="primary" size="small" :disabled="!compareA || !compareB || compareA === compareB" @click="runCompare">对比 Diff</el-button>
      </div>
      <el-table :data="versions" size="small">
        <el-table-column prop="version_num" label="版本" width="70" />
        <el-table-column prop="change_note" label="变更说明" />
        <el-table-column prop="created_at" label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="rollback(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showCompareDialog" title="版本 Diff" width="680px">
      <div v-if="versionDiff">
        <p>v{{ versionDiff.version_a }} → v{{ versionDiff.version_b }} · 步骤数 {{ versionDiff.steps_a_count }} / {{ versionDiff.steps_b_count }}</p>
        <el-tag :type="versionDiff.has_diff ? 'warning' : 'success'" size="small">{{ versionDiff.has_diff ? '存在差异' : '无差异' }}</el-tag>
        <div v-if="versionDiff.added?.length" style="margin-top:12px">
          <strong>新增 ({{ versionDiff.added.length }})</strong>
          <pre v-for="(item, i) in versionDiff.added" :key="'a'+i" class="diff-pre">#{{ item.index }} {{ JSON.stringify(item.step) }}</pre>
        </div>
        <div v-if="versionDiff.removed?.length" style="margin-top:12px">
          <strong>删除 ({{ versionDiff.removed.length }})</strong>
          <pre v-for="(item, i) in versionDiff.removed" :key="'r'+i" class="diff-pre">#{{ item.index }} {{ JSON.stringify(item.step) }}</pre>
        </div>
        <div v-if="versionDiff.modified?.length" style="margin-top:12px">
          <strong>修改 ({{ versionDiff.modified.length }})</strong>
          <pre v-for="(item, i) in versionDiff.modified" :key="'m'+i" class="diff-pre">#{{ item.index }}
- {{ JSON.stringify(item.before) }}
+ {{ JSON.stringify(item.after) }}</pre>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showCommentDialog" :title="`协同批注 · ${commentCaseName}`" width="560px">
      <div class="comment-list">
        <div v-if="!comments.length" class="comment-empty">暂无批注，添加第一条评论开始协作</div>
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-meta">
            <span class="comment-author">{{ c.author_name }}</span>
            <span class="comment-time">{{ fmtTime(c.created_at) }}</span>
            <el-button v-if="c.user_id === userStore.user?.id || userStore.isAdmin" size="small" type="danger" plain @click="deleteComment(c)">删除</el-button>
          </div>
          <div class="comment-body">{{ c.content }}</div>
        </div>
      </div>
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="输入批注内容..." style="margin-top:12px" />
      <template #footer>
        <el-button @click="showCommentDialog = false">关闭</el-button>
        <el-button type="primary" :disabled="!newComment.trim()" @click="submitComment">发表批注</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTransferDialog" title="用例移交" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新负责人">
          <el-select v-model="transferOwnerId" filterable placeholder="选择用户" style="width:100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.display_name || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTransferDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer">确认移交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { caseApi, authApi, commentApi, deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import { markRecordingBoot } from '@/composables/useRecordingStartup'
import { operationRecordingState } from '@/composables/useOperationRecording'

const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'
const CASE_TEMPLATES = [
  {
    key: 'login',
    name: '登录流程',
    desc: '通用登录页打开与账号密码输入模板',
    payload: {
      name: '模板·登录流程',
      platform: 'android',
      script_type: 'visual',
      case_status: 'draft',
      priority: 'high',
      tags: '模板,登录',
      steps_content: JSON.stringify({
        version: 1,
        steps: [
          { action: 'launch', desc: '启动应用' },
          { action: 'click', desc: '点击登录入口' },
          { action: 'input', desc: '输入账号' },
          { action: 'input', desc: '输入密码' },
          { action: 'click', desc: '点击登录按钮' }
        ]
      })
    }
  },
  {
    key: 'home',
    name: '首页跳转',
    desc: '启动应用并进入首页的基础导航模板',
    payload: {
      name: '模板·首页跳转',
      platform: 'android',
      script_type: 'visual',
      case_status: 'draft',
      priority: 'medium',
      tags: '模板,首页',
      steps_content: JSON.stringify({
        version: 1,
        steps: [
          { action: 'launch', desc: '启动应用' },
          { action: 'wait', desc: '等待首页加载', seconds: 2 },
          { action: 'assert', desc: '断言首页关键元素可见' }
        ]
      })
    }
  }
]

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const folderLoading = ref(false)
const batchLoading = ref(false)
const cases = ref([])
const allCasesCache = ref([])
const folderTree = ref([])
const flatFolders = ref([])
const treeKey = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({
  status: '',
  keyword: '',
  folder_id: null,
  platform: '',
  version: '',
  priority: '',
  tag: ''
})
const showFolderDialog = ref(false)
const folderForm = reactive({ id: null, name: '', parent_id: null })
const showVersionDialog = ref(false)
const showCompareDialog = ref(false)
const versions = ref([])
const compareA = ref(null)
const compareB = ref(null)
const versionDiff = ref(null)
const currentCaseId = ref(null)
const selectedCaseIds = ref([])
const selectedRows = ref([])
const userOptions = ref([])
const showTransferDialog = ref(false)
const transferOwnerId = ref(null)
const showCommentDialog = ref(false)
const commentCaseId = ref(null)
const commentCaseName = ref('')
const comments = ref([])
const newComment = ref('')
const showBatchMove = ref(false)
const batchMoveFolderId = ref(null)
const showTemplateDialog = ref(false)
const tplLoading = ref('')
const ctxMenu = reactive({ visible: false, x: 0, y: 0, node: null })
const folderCountMap = ref({})

const hasSelection = computed(() => selectedCaseIds.value.length > 0)

const filteredCases = computed(() => {
  let list = [...cases.value]
  if (filters.platform) list = list.filter(c => c.platform === filters.platform)
  if (filters.version) list = list.filter(c => String(c.version_num) === String(filters.version))
  if (filters.priority) list = list.filter(c => normalizePriority(c.priority) === filters.priority)
  if (filters.tag) {
    list = list.filter(c => String(c.tags || '').split(/[,，]/).map(s => s.trim()).includes(filters.tag))
  }
  return list
})

const filteredTotal = computed(() => filteredCases.value.length)

const displayCases = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredCases.value.slice(start, start + pageSize.value)
})

const versionOptions = computed(() => {
  const set = new Set()
  for (const c of allCasesCache.value) {
    if (c.version_num != null && c.version_num !== '') set.add(c.version_num)
  }
  return [...set].sort((a, b) => Number(b) - Number(a))
})

const tagOptions = computed(() => {
  const set = new Set()
  for (const c of allCasesCache.value) {
    String(c.tags || '').split(/[,，]/).map(s => s.trim()).filter(Boolean).forEach(t => set.add(t))
  }
  return [...set].sort()
})

const folderStats = computed(() => {
  const base = filters.folder_id
    ? allCasesCache.value.filter(c => c.folder_id === filters.folder_id)
    : allCasesCache.value
  return {
    total: base.length,
    active: base.filter(c => c.case_status === 'active').length,
    review: base.filter(c => c.case_status === 'review').length
  }
})

function statusDisplay(s) {
  return { draft: '草稿', review: '待评审', active: '已生效', deprecated: '已禁用' }[s] || s || '-'
}

function statusClass(s) {
  if (s === 'review') return 'is-review'
  if (s === 'active') return 'is-active'
  return 'is-disabled'
}

function platformLabel(p) {
  if (p === 'android') return '安卓'
  if (p === 'ios') return 'iOS'
  if (p === 'both') return '双端'
  return p || '-'
}

function normalizePriority(p) {
  const v = String(p || '').toLowerCase()
  if (['high', '高', '1', 'p0', 'p1'].includes(v)) return 'high'
  if (['low', '低', '3', 'p3'].includes(v)) return 'low'
  if (['medium', 'mid', '中', '2', 'p2'].includes(v)) return 'medium'
  return v || ''
}

function priorityLabel(p) {
  const n = normalizePriority(p)
  return { high: '高', medium: '中', low: '低' }[n] || p || '-'
}

function folderCaseCount(data) {
  if (!data) return 0
  if (data.id === 0) return allCasesCache.value.length
  if (data.case_count != null) return data.case_count
  return folderCountMap.value[data.id] || 0
}

function rebuildFolderCounts() {
  const map = {}
  for (const c of allCasesCache.value) {
    const fid = c.folder_id
    if (fid) map[fid] = (map[fid] || 0) + 1
  }
  folderCountMap.value = map
}

async function goQuickRecord() {
  markRecordingBoot()
  if (operationRecordingState.active && operationRecordingState.deviceId) {
    router.push(`/devices/${operationRecordingState.deviceId}/screen`)
    return
  }
  const lastId = localStorage.getItem(LAST_RECORD_DEVICE_KEY)
  if (lastId) {
    router.push(`/devices/${lastId}/screen?auto_record=1`)
    return
  }
  try {
    const res = await deviceApi.list({ page: 1, page_size: 100 })
    const online = (res.data?.list || []).filter(d => ['online', 'busy'].includes(d.status))
    if (online.length === 1) {
      localStorage.setItem(LAST_RECORD_DEVICE_KEY, String(online[0].id))
      router.push(`/devices/${online[0].id}/screen?auto_record=1`)
      return
    }
    if (!online.length) {
      ElMessage.warning('暂无在线设备，请先在设备管理连接设备')
      router.push('/devices')
      return
    }
    ElMessage.info('请在设备管理中选择设备后开始录制')
    router.push('/devices')
  } catch {
    ElMessage.warning('获取设备列表失败')
    router.push('/devices')
  }
}

function goBatchRun() {
  if (hasSelection.value) {
    ElMessage.info(`已选中 ${selectedCaseIds.value.length} 条用例，请在任务页创建执行`)
  }
  router.push('/tasks')
}

function goRecycle() {
  router.push({ path: '/platform-config', query: { tab: 'recycle' } })
}

function flattenFolders(nodes, prefix = '', excludeId = null) {
  const list = []
  for (const node of nodes || []) {
    if (!node.id || node.id === excludeId) continue
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    list.push({ id: node.id, label })
    if (node.children?.length) list.push(...flattenFolders(node.children, label, excludeId))
  }
  return list
}

function collectFolderOrders(nodes, parentId = null, list = []) {
  const siblings = (nodes || []).filter(n => n.id > 0)
  siblings.forEach((node, index) => {
    list.push({ id: node.id, parent_id: parentId, sort_order: index })
    if (node.children?.length) collectFolderOrders(node.children, node.id, list)
  })
  return list
}

async function loadFolders() {
  folderLoading.value = true
  try {
    const res = await caseApi.folderTree()
    const nodes = res.data || []
    folderTree.value = [{ id: 0, name: '全部用例', children: nodes }]
    flatFolders.value = flattenFolders(nodes)
    treeKey.value += 1
  } finally {
    folderLoading.value = false
  }
}

async function loadAllCasesCache() {
  try {
    const res = await caseApi.list({ page: 1, page_size: 500 })
    allCasesCache.value = res.data?.list || []
    rebuildFolderCounts()
  } catch {
    allCasesCache.value = []
  }
}

async function loadCases() {
  loading.value = true
  try {
    const params = { page: 1, page_size: 200 }
    if (filters.status) params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.folder_id) params.folder_id = filters.folder_id
    const res = await caseApi.list(params)
    cases.value = res.data?.list || []
    await loadAllCasesCache()
    page.value = 1
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
}

function resetFilters() {
  filters.status = ''
  filters.keyword = ''
  filters.platform = ''
  filters.version = ''
  filters.priority = ''
  filters.tag = ''
  page.value = 1
  loadCases()
}

function onSelectionChange(rows) {
  selectedRows.value = rows
  selectedCaseIds.value = rows.map(r => r.id)
}

function onFolderClick(node) {
  filters.folder_id = node.id > 0 ? node.id : null
  loadCases()
}

function allowDrag(node) {
  return node.data.id > 0
}

function allowDrop(draggingNode, dropNode, type) {
  if (dropNode.data.id === 0 && type === 'inner') return true
  if (dropNode.data.id === 0) return type !== 'inner'
  return true
}

async function handleNodeDrop() {
  try {
    const items = collectFolderOrders(folderTree.value[0]?.children || [], null, [])
    await caseApi.reorderFolders(items)
    ElMessage.success('目录顺序已更新')
    await loadFolders()
  } catch {
    await loadFolders()
  }
}

function hideCtxMenu() {
  ctxMenu.visible = false
  ctxMenu.node = null
}

function onFolderContext(event, data) {
  if (!data?.id || data.id <= 0) return
  event.preventDefault()
  ctxMenu.visible = true
  ctxMenu.x = event.clientX
  ctxMenu.y = event.clientY
  ctxMenu.node = data
}

function ctxRename() {
  const node = ctxMenu.node
  hideCtxMenu()
  if (node) openFolderDialog(node)
}

function ctxMove() {
  const node = ctxMenu.node
  hideCtxMenu()
  if (node) openFolderDialog(node)
}

async function ctxDelete() {
  const node = ctxMenu.node
  hideCtxMenu()
  if (node) await deleteFolder(node)
}

function openFolderDialog(row) {
  if (row?.id) {
    folderForm.id = row.id
    folderForm.name = row.name
    folderForm.parent_id = row.parent_id ?? null
  } else {
    folderForm.id = null
    folderForm.name = ''
    folderForm.parent_id = filters.folder_id > 0 ? filters.folder_id : null
  }
  showFolderDialog.value = true
}

async function saveFolder() {
  if (!folderForm.name?.trim()) {
    ElMessage.warning('请输入目录名')
    return
  }
  const payload = { name: folderForm.name.trim(), parent_id: folderForm.parent_id || null }
  if (folderForm.id) {
    await caseApi.updateFolder(folderForm.id, payload)
    ElMessage.success('目录已更新')
  } else {
    await caseApi.createFolder(payload)
    ElMessage.success('目录已创建')
  }
  showFolderDialog.value = false
  folderForm.id = null
  folderForm.name = ''
  folderForm.parent_id = null
  await loadFolders()
}

async function deleteFolder(row) {
  await ElMessageBox.confirm(`确定删除目录「${row.name}」？需先移走子目录和用例。`, '确认删除', { type: 'warning' })
  await caseApi.deleteFolder(row.id)
  if (filters.folder_id === row.id) filters.folder_id = null
  ElMessage.success('目录已删除')
  await loadFolders()
  loadCases()
}

function openEditor(row) {
  if (row?.id) {
    router.push(`/cases/editor/${row.id}?asset=1`)
  } else {
    const query = { asset: '1' }
    if (filters.folder_id) query.folder_id = String(filters.folder_id)
    router.push({ path: '/cases/editor', query })
  }
}

async function runCase(row) {
  const res = await caseApi.run(row.id)
  const taskId = res.data.id
  ElMessage.success(`已创建执行任务 #${taskId}，正在跳转任务中心`)
  router.push(`/tasks/${taskId}`)
}

async function copyCase(row, { openAfter = true } = {}) {
  try {
    const detail = (await caseApi.get(row.id)).data || row
    const payload = {
      name: `${detail.name || row.name}_副本`,
      platform: detail.platform || 'android',
      script_type: detail.script_type || 'visual',
      steps_content: detail.steps_content || detail.script_content || JSON.stringify({ version: 1, steps: [] }),
      script_content: detail.script_content || '',
      app_package: detail.app_package || '',
      case_status: 'draft',
      priority: detail.priority || 'medium',
      tags: detail.tags || '',
      folder_id: detail.folder_id || filters.folder_id || null,
      timeout_seconds: detail.timeout_seconds || 3600,
      enable_recording: detail.enable_recording !== false
    }
    const res = await caseApi.create(payload)
    if (openAfter) {
      ElMessage.success('用例已复制')
      await loadCases()
      if (res.data?.id) openEditor(res.data)
    }
    return res.data
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '复制失败')
    return null
  }
}

async function batchCopy() {
  if (!hasSelection.value) return
  batchLoading.value = true
  try {
    let ok = 0
    for (const row of selectedRows.value) {
      const created = await copyCase(row, { openAfter: false })
      if (created) ok++
    }
    ElMessage.success(`已复制 ${ok} 条用例`)
    await loadCases()
  } finally {
    batchLoading.value = false
  }
}

function openBatchMove() {
  if (!hasSelection.value) return
  batchMoveFolderId.value = filters.folder_id
  showBatchMove.value = true
}

async function confirmBatchMove() {
  batchLoading.value = true
  try {
    for (const id of selectedCaseIds.value) {
      await caseApi.update(id, { folder_id: batchMoveFolderId.value || null })
    }
    ElMessage.success('已批量移动')
    showBatchMove.value = false
    selectedCaseIds.value = []
    await loadCases()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '移动失败')
  } finally {
    batchLoading.value = false
  }
}

async function batchDelete() {
  if (!hasSelection.value) return
  await ElMessageBox.confirm(`确定将选中的 ${selectedCaseIds.value.length} 条用例移入回收站？`, '批量删除', { type: 'warning' })
  batchLoading.value = true
  try {
    for (const id of selectedCaseIds.value) {
      await caseApi.delete(id)
    }
    ElMessage.success('已移入回收站')
    selectedCaseIds.value = []
    await loadCases()
  } finally {
    batchLoading.value = false
  }
}

async function batchToggleStatus(enable) {
  if (!hasSelection.value) return
  const status = enable ? 'active' : 'deprecated'
  batchLoading.value = true
  try {
    for (const id of selectedCaseIds.value) {
      await caseApi.update(id, { case_status: status })
    }
    ElMessage.success(enable ? '已批量启用' : '已批量停用')
    selectedCaseIds.value = []
    await loadCases()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
}

async function createFromTemplate(tpl) {
  tplLoading.value = tpl.key
  try {
    const payload = {
      ...tpl.payload,
      folder_id: filters.folder_id || null
    }
    const res = await caseApi.create(payload)
    ElMessage.success('模板用例已创建')
    showTemplateDialog.value = false
    await loadCases()
    if (res.data?.id) openEditor(res.data)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    tplLoading.value = ''
  }
}

async function deleteCase(row) {
  await ElMessageBox.confirm('用例将移入回收站，可在回收站恢复或永久删除。', '确认删除', { type: 'warning' })
  await caseApi.delete(row.id)
  ElMessage.success('已移入回收站')
  loadCases()
}

async function showVersions(row) {
  currentCaseId.value = row.id
  const res = await caseApi.versions(row.id)
  versions.value = res.data
  compareA.value = res.data[1]?.id || null
  compareB.value = res.data[0]?.id || null
  versionDiff.value = null
  showVersionDialog.value = true
}

async function runCompare() {
  const res = await caseApi.compareVersions(currentCaseId.value, compareA.value, compareB.value)
  versionDiff.value = res.data
  showCompareDialog.value = true
}

async function rollback(ver) {
  await caseApi.rollback(currentCaseId.value, ver.id)
  ElMessage.success('已回滚')
  showVersionDialog.value = false
  loadCases()
}

async function showComments(row) {
  commentCaseId.value = row.id
  commentCaseName.value = row.name
  newComment.value = ''
  const res = await commentApi.list('test_case', row.id)
  comments.value = res.data
  showCommentDialog.value = true
}

async function submitComment() {
  if (!newComment.value.trim()) return
  await commentApi.create({ asset_type: 'test_case', asset_id: commentCaseId.value, content: newComment.value.trim() })
  newComment.value = ''
  const res = await commentApi.list('test_case', commentCaseId.value)
  comments.value = res.data
  ElMessage.success('批注已发表')
}

async function deleteComment(c) {
  await ElMessageBox.confirm('确定删除该批注？', '确认', { type: 'warning' })
  await commentApi.delete(c.id)
  comments.value = (await commentApi.list('test_case', commentCaseId.value)).data
}

function openTransfer() {
  if (!hasSelection.value) return
  if (!userStore.isAdmin) {
    ElMessage.warning('仅管理员可移交用例')
    return
  }
  transferOwnerId.value = null
  showTransferDialog.value = true
}

async function confirmTransfer() {
  if (!transferOwnerId.value) { ElMessage.warning('请选择负责人'); return }
  await caseApi.transfer(selectedCaseIds.value, transferOwnerId.value)
  ElMessage.success('用例已移交')
  showTransferDialog.value = false
  selectedCaseIds.value = []
  loadCases()
}

async function loadUsers() {
  if (userStore.isAdmin) {
    userOptions.value = (await authApi.listUsers()).data
  }
}

onMounted(() => {
  const status = route.query.status
  if (typeof status === 'string' && status) filters.status = status
  loadFolders()
  loadCases()
  loadUsers()
  window.addEventListener('click', hideCtxMenu)
})

onUnmounted(() => {
  window.removeEventListener('click', hideCtxMenu)
})

watch(() => route.query.status, (status) => {
  filters.status = typeof status === 'string' ? status : ''
  page.value = 1
  loadCases()
})

watch(filteredTotal, (n) => {
  const maxPage = Math.max(1, Math.ceil(n / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.action-group {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.batch-wrap {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
.batch-group :deep(.el-button) {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
  --el-button-hover-text-color: #334155;
}
.action-divider {
  height: 28px;
  margin: 0 4px;
}
.btn-run { font-weight: 600; }
.btn-record {
  font-weight: 700;
  box-shadow: 0 2px 10px rgba(245, 158, 11, 0.35);
}
.btn-muted {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}
.btn-create { font-weight: 700; }

.folder-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}
.folder-card-title { font-weight: 700; }
.folder-card-ops { display: flex; gap: 6px; flex-shrink: 0; }
.tree-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  font-weight: 400;
}
.folder-node {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 6px;
  min-width: 0;
}
.folder-icon { color: var(--atp-primary); flex-shrink: 0; }
.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-count {
  color: var(--atp-text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}
.recycle-entry {
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}
.recycle-entry:hover {
  background: #fff1f2;
  color: #e11d48;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}
.filter-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.cases-table { width: 100%; }
.case-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.case-file-icon {
  color: var(--atp-primary);
  font-size: 15px;
  flex-shrink: 0;
}
.status-text {
  font-size: 13px;
  font-weight: 700;
}
.status-text.is-review { color: #ea580c; }
.status-text.is-active { color: #059669; }
.status-text.is-disabled { color: #94a3b8; }

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.btn-copy {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.table-empty {
  text-align: center;
  padding: 48px 16px 24px;
  color: var(--atp-text-secondary);
}
.table-empty p { margin: 0 0 16px; font-size: 14px; }
.table-empty__actions { display: flex; justify-content: center; gap: 12px; }

.pager-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
}
.pager-stats {
  font-size: 13px;
  color: var(--atp-text-secondary);
}
.pager-stats .ok { color: #059669; }
.pager-stats .warn { color: #ea580c; }

.shortcut-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 20px;
}
.shortcut-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 16px 18px;
  border-radius: 14px;
  background: #f8fafc;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}
.shortcut-card:hover {
  transform: translateY(-2px);
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
.shortcut-card h4 {
  margin: 0 0 4px;
  font-size: 14px;
}
.shortcut-card p {
  margin: 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.45;
}
.shortcut-card .el-icon { color: var(--atp-primary); margin-top: 2px; }

.folder-ctx-menu {
  position: fixed;
  z-index: 3000;
  margin: 0;
  padding: 6px 0;
  list-style: none;
  min-width: 160px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
}
.folder-ctx-menu li {
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
}
.folder-ctx-menu li:hover { background: #f1f5f9; }
.folder-ctx-menu li.danger { color: #e11d48; }

.tpl-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--atp-border-neutral);
}
.tpl-item:last-child { border-bottom: none; }
.tpl-item p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
}

.comment-list { max-height: 320px; overflow-y: auto; }
.comment-empty { color: var(--atp-text-secondary); font-size: 13px; padding: 12px 0; text-align: center; }
.comment-item { padding: 10px 0; border-bottom: 1px solid var(--atp-border-neutral); }
.comment-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--atp-text-secondary); margin-bottom: 4px; }
.comment-author { font-weight: 600; color: var(--atp-text); }
.comment-time { flex: 1; }
.comment-body { font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.diff-pre { font-size: 11px; background: var(--atp-brand-50); padding: 6px 8px; border-radius: 4px; overflow-x: auto; white-space: pre-wrap; }

@media (max-width: 960px) {
  .shortcut-bar { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
