<template>
  <div class="page-container">
    <PageHeader title="测试用例" subtitle="用例目录、版本管控与生命周期管理">
      <template #actions>
        <el-button v-if="userStore.isAdmin" :disabled="!selectedCaseIds.length" @click="openTransfer">移交选中</el-button>
        <el-button v-if="userStore.canEdit && !userStore.isReadonly" type="warning" plain @click="goQuickRecord">
          <el-icon><VideoCamera /></el-icon> 一键录制
        </el-button>
        <el-button @click="openFolderDialog()">新建目录</el-button>
        <el-button type="primary" @click="openEditor()">
          <el-icon><Plus /></el-icon> 新建用例
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="20">
      <el-col :span="6">
        <AppCard title="用例目录" :hover="false">
          <p class="tree-hint">拖拽可调整顺序与层级</p>
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
          >
            <template #default="{ data }">
              <div class="folder-node">
                <el-icon class="folder-icon"><Folder /></el-icon>
                <span class="folder-name">{{ data.name }}</span>
                <span v-if="data.id > 0" class="folder-actions" @click.stop>
                  <el-button size="small" type="primary" plain @click="openFolderDialog(data)">编辑</el-button>
                  <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteFolder(data)">删除</el-button>
                </span>
              </div>
            </template>
          </el-tree>
        </AppCard>
      </el-col>
      <el-col :span="18">
        <AppCard :hover="false">
          <div class="filter-bar" style="margin-bottom:0;border:none;padding:0;background:transparent">
            <el-select v-model="filters.status" placeholder="状态" clearable style="width:130px" @change="loadCases">
              <el-option label="草稿" value="draft" />
              <el-option label="待评审" value="review" />
              <el-option label="生效" value="active" />
              <el-option label="废弃" value="deprecated" />
            </el-select>
            <el-input v-model="filters.keyword" placeholder="搜索用例" style="width:180px" clearable @change="loadCases" />
            <el-button @click="loadCases"><el-icon><Refresh /></el-icon></el-button>
          </div>
          <el-table :data="cases" v-loading="loading" stripe style="margin-top:16px" @selection-change="rows => selectedCaseIds = rows.map(r => r.id)">
            <el-table-column v-if="userStore.isAdmin" type="selection" width="45" />
            <el-table-column prop="name" label="用例名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="case-name-cell">
                  <el-icon class="case-file-icon"><Document /></el-icon>
                  {{ row.name }}
                  <el-tag v-if="row.dataset_id" size="small" class="tag-parametric">参数化</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="case_status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="light">{{ statusLabel[row.case_status] || row.case_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="platform" label="平台" width="90" />
            <el-table-column prop="version_num" label="版本" width="70" align="center" />
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column prop="tags" label="标签" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="400" fixed="right">
              <template #default="{ row }">
                <div class="table-actions table-actions--grid">
                  <el-button size="small" type="primary" plain @click="openEditor(row)">编辑</el-button>
                  <el-button size="small" type="warning" plain @click="openDebug(row)">同屏调试</el-button>
                  <el-button size="small" type="success" plain @click="runCase(row)">执行</el-button>
                  <el-button size="small" plain @click="showComments(row)">批注</el-button>
                  <el-button size="small" plain @click="showVersions(row)">版本</el-button>
                  <el-button size="small" plain @click="exportOfflineCase(row)">离线包</el-button>
                  <el-button v-if="row.case_status === 'draft'" size="small" type="warning" plain @click="submitReview(row)">提交评审</el-button>
                  <el-button v-if="userStore.isAdmin && row.case_status === 'review'" size="small" type="success" plain @click="approveCase(row)">批准</el-button>
                  <el-button v-if="userStore.isAdmin && row.case_status === 'review'" size="small" type="warning" plain @click="rejectCase(row)">驳回</el-button>
                  <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteCase(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            style="margin-top:20px;justify-content:flex-end"
            @change="loadCases"
          />
        </AppCard>
      </el-col>
    </el-row>

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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { caseApi, authApi, commentApi, taskApi, deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import { markRecordingBoot } from '@/composables/useRecordingStartup'
import { operationRecordingState } from '@/composables/useOperationRecording'

const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const cases = ref([])
const folderTree = ref([])
const flatFolders = ref([])
const treeKey = ref(0)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ status: '', keyword: '', folder_id: null })
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
const userOptions = ref([])
const showTransferDialog = ref(false)
const transferOwnerId = ref(null)
const showCommentDialog = ref(false)
const commentCaseId = ref(null)
const commentCaseName = ref('')
const comments = ref([])
const newComment = ref('')

const statusLabel = { draft: '草稿', review: '待评审', active: '生效', deprecated: '废弃' }

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
  const res = await caseApi.folderTree()
  const nodes = res.data || []
  folderTree.value = [{ id: 0, name: '全部用例', children: nodes }]
  flatFolders.value = flattenFolders(nodes)
  treeKey.value += 1
}

async function loadCases() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (filters.status) params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.folder_id) params.folder_id = filters.folder_id
    const res = await caseApi.list(params)
    cases.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
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
  ElMessage.success(`已提交执行任务 #${taskId}`)
  router.push(`/cases/${row.id}/debug?taskId=${taskId}`)
}

function openDebug(row) {
  router.push(`/cases/${row.id}/debug`)
}

async function exportOfflineCase(row) {
  const res = await caseApi.offlinePackage(row.id)
  await taskApi.downloadOfflinePackage(res.data.filename)
  ElMessage.success('离线包已下载')
}

async function submitReview(row) {
  await caseApi.submitReview(row.id)
  ElMessage.success('已提交评审')
  loadCases()
}

async function approveCase(row) {
  await caseApi.approve(row.id)
  ElMessage.success('用例已生效')
  loadCases()
}

async function rejectCase(row) {
  const { value } = await ElMessageBox.prompt('填写驳回原因（可选）', '驳回评审', {
    confirmButtonText: '驳回', cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (value === null) return
  await caseApi.reject(row.id, value || '')
  ElMessage.success('已驳回至草稿')
  loadCases()
}

async function deleteCase(row) {
  await ElMessageBox.confirm('用例将移入回收站，90 天内可恢复。', '确认删除', { type: 'warning' })
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

onMounted(() => { loadFolders(); loadCases(); loadUsers() })
</script>

<style scoped>
.tree-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--atp-text-secondary);
}
.folder-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
  gap: 6px;
}
.folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.folder-actions {
  display: none;
  flex-shrink: 0;
}
.folder-node:hover .folder-actions {
  display: inline-flex;
  gap: 2px;
}
.comment-list { max-height: 320px; overflow-y: auto; }
.comment-empty { color: var(--atp-text-secondary); font-size: 13px; padding: 12px 0; text-align: center; }
.comment-item { padding: 10px 0; border-bottom: 1px solid var(--atp-border-neutral); }
.comment-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--atp-text-secondary); margin-bottom: 4px; }
.comment-author { font-weight: 600; color: var(--atp-text); }
.comment-time { flex: 1; }
.comment-body { font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.diff-pre { font-size: 11px; background: var(--atp-brand-50); padding: 6px 8px; border-radius: 4px; overflow-x: auto; white-space: pre-wrap; }
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

</style>
