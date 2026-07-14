<template>
  <div class="page-container">
    <PageHeader title="控件池管理" subtitle="全局 UI 控件定位资源池">
      <template #actions>
        <el-button @click="goBatchValidate">批量校验</el-button>
        <el-button @click="goFailureReport">失败报表</el-button>
        <el-button @click="showBatchReplace = true">批量修复</el-button>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 添加控件
        </el-button>
      </template>
    </PageHeader>

    <AppCard :hover="false">
      <div class="filter-bar" style="margin-bottom:0;border:none;padding:0;background:transparent;display:flex;flex-wrap:wrap;gap:8px">
        <el-input v-model="filters.app_package" placeholder="应用包名" style="width:180px" clearable @change="loadPool" />
        <el-input v-model="filters.page_name" placeholder="页面" style="width:120px" clearable @change="loadPool" />
        <el-input v-model="filters.version_tag" placeholder="版本" style="width:100px" clearable @change="loadPool" />
        <el-input v-model="filters.env_tag" placeholder="环境" style="width:100px" clearable @change="loadPool" />
        <el-select v-model="filters.status" placeholder="状态" style="width:110px" clearable @change="loadPool">
          <el-option label="active" value="active" />
          <el-option label="archived" value="archived" />
          <el-option label="deprecated" value="deprecated" />
        </el-select>
        <el-button @click="loadPool"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>

      <el-table :data="poolList" v-loading="loading" stripe style="margin-top:16px">
        <el-table-column prop="element_name" label="控件名称" min-width="140" />
        <el-table-column prop="app_package" label="应用包名" min-width="180" />
        <el-table-column prop="page_name" label="页面" width="120" />
        <el-table-column prop="locator_type" label="定位类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :class="locatorTagClass(row.locator_type)">{{ row.locator_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locator_value" label="定位表达式" min-width="200" show-overflow-tooltip />
        <el-table-column label="稳定性" width="88">
          <template #default="{ row }">
            <el-tag v-if="poolStabilityScore(row) != null" size="small" :type="stabilityScoreType(poolStabilityScore(row))" round>
              {{ poolStabilityScore(row) }}
            </el-tag>
            <span v-else class="hint">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hit_count" label="命中次数" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version_tag" label="版本" width="88" />
        <el-table-column prop="env_tag" label="环境" width="88" />
        <el-table-column label="分级" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ controlTagLabel(row.control_tag) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核心" width="72">
          <template #default="{ row }">
            <el-tag v-if="row.is_core" size="small" type="danger" effect="plain">核心</el-tag>
            <span v-else class="hint">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-tooltip v-if="!canEditPool(row)" content="生产环境核心控件仅管理员可改" placement="top">
              <el-button size="small" type="primary" plain disabled>编辑</el-button>
            </el-tooltip>
            <el-button v-else size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" plain @click="scanDeps(row)">依赖</el-button>
            <el-button size="small" plain @click="showChangeLogs(row)">变更</el-button>
            <el-button size="small" plain @click="showVersions(row)">版本</el-button>
            <el-button v-if="row.status === 'active'" size="small" type="danger" plain @click="archiveRow(row)">归档</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end"
        @change="loadPool"
      />
    </AppCard>

    <el-dialog v-model="showCreateDialog" title="添加控件池条目" width="560px">
      <el-form :model="poolForm" label-width="100px">
        <el-form-item label="应用包名" required>
          <el-input v-model="poolForm.app_package" />
        </el-form-item>
        <el-form-item label="页面名称">
          <el-input v-model="poolForm.page_name" />
        </el-form-item>
        <el-form-item label="控件名称" required>
          <el-input v-model="poolForm.element_name" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="poolForm.platform" style="width:100%">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="双端" value="both" />
          </el-select>
        </el-form-item>
        <el-form-item label="定位类型" required>
          <el-select v-model="poolForm.locator_type" style="width:100%">
            <el-option label="ID" value="id" />
            <el-option label="XPath" value="xpath" />
            <el-option label="Accessibility" value="accessibility" />
            <el-option label="AI" value="ai" />
            <el-option label="Image" value="image" />
          </el-select>
        </el-form-item>
        <el-form-item label="定位表达式" required>
          <el-input v-model="poolForm.locator_value" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input v-model="poolForm.version_tag" />
        </el-form-item>
        <el-form-item label="环境标签">
          <el-input v-model="poolForm.env_tag" placeholder="test / staging / prod" />
        </el-form-item>
        <el-form-item label="控件分级">
          <el-select v-model="poolForm.control_tag" style="width:100%">
            <el-option v-for="t in CONTROL_TAGS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="核心控件">
          <el-switch v-model="poolForm.is_core" />
          <span class="hint">生产环境核心控件仅管理员可编辑</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createPool">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑控件" width="640px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="控件名称"><el-input v-model="editForm.element_name" disabled /></el-form-item>
        <el-form-item label="主定位类型">
          <el-select v-model="editForm.locator_type" style="width:100%">
            <el-option label="ID" value="id" />
            <el-option label="XPath" value="xpath" />
            <el-option label="Accessibility" value="accessibility" />
          </el-select>
        </el-form-item>
        <el-form-item label="主定位值"><el-input v-model="editForm.locator_value" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="editChain.length" label="定位链">
          <div class="chain-editor">
            <div
              v-for="(item, idx) in editChain"
              :key="item.type + item.value + idx"
              class="chain-row"
              :class="{ primary: item.primary }"
              @click="setEditPrimary(idx)"
            >
              <span class="chain-rank">{{ idx + 1 }}</span>
              <div class="chain-main">
                <span class="chain-type">{{ formatLocatorType(item.type) }}</span>
                <el-tag v-if="item.primary" size="small" type="primary" round>主</el-tag>
                <code>{{ item.value }}</code>
              </div>
              <div class="chain-side" @click.stop>
                <el-switch v-model="item.enabled" size="small" />
                <el-button size="small" text :disabled="idx === 0" @click="moveEditChain(idx, -1)">↑</el-button>
                <el-button size="small" text :disabled="idx >= editChain.length - 1" @click="moveEditChain(idx, 1)">↓</el-button>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item v-if="editMeta.risk_tags?.length" label="风险标签">
          <el-tag v-for="tag in editMeta.risk_tags" :key="tag" size="small" type="warning" effect="plain" round style="margin-right:4px">
            {{ riskTagLabel(tag) }}
          </el-tag>
        </el-form-item>
        <el-form-item v-if="editMeta.stability_score != null" label="稳定性">
          <el-tag :type="stabilityScoreType(editMeta.stability_score)" size="small" round>
            {{ editMeta.stability_score }} · {{ stabilityScoreLabel(editMeta.stability_score) }}
          </el-tag>
        </el-form-item>
        <el-form-item v-if="editMeta.validate_result" label="上次校验">
          <span class="hint">{{ formatPoolValidateMeta(editMeta.validate_result, editMeta.validated_at) }}</span>
        </el-form-item>
        <el-form-item label="同步私有绑定"><el-switch v-model="editForm.propagate_bindings" /></el-form-item>
        <el-form-item label="变更说明"><el-input v-model="editForm.reason" /></el-form-item>
        <el-form-item label="截图路径"><el-input v-model="editForm.screenshot_path" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :disabled="editLocked" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDepsDialog" title="依赖影响分析" width="720px">
      <div v-if="depsResult">
        <p>
          控件 <strong>{{ depsResult.element_name }}</strong> ·
          引用合计 <el-tag size="small">{{ depsResult.total_refs }}</el-tag>
          · 影响等级 <el-tag :type="{ none: 'success', low: 'info', medium: 'warning', high: 'danger' }[depsResult.impact_level]" size="small">{{ depsResult.impact_level }}</el-tag>
        </p>
        <el-divider content-position="left">用例引用 ({{ depsResult.case_refs?.length || 0 }})</el-divider>
        <el-table :data="depsResult.case_refs || []" size="small" stripe max-height="160">
          <el-table-column prop="ref_name" label="用例" />
          <el-table-column prop="step_index" label="步骤" width="70" />
          <el-table-column prop="step_type" label="类型" width="100" />
        </el-table>
        <el-divider content-position="left">公共步骤 ({{ depsResult.common_step_refs?.length || 0 }})</el-divider>
        <el-table :data="depsResult.common_step_refs || []" size="small" stripe max-height="120">
          <el-table-column prop="ref_name" label="公共步骤" />
          <el-table-column prop="step_index" label="步骤" width="70" />
        </el-table>
        <el-divider content-position="left">测试套件 ({{ depsResult.suite_refs?.length || 0 }})</el-divider>
        <el-table :data="depsResult.suite_refs || []" size="small" stripe max-height="120">
          <el-table-column prop="ref_name" label="套件/用例" />
          <el-table-column prop="step_index" label="步骤" width="70" />
        </el-table>
        <el-divider content-position="left">私有绑定 ({{ depsResult.private_binding_refs?.length || 0 }})</el-divider>
        <el-table :data="depsResult.private_binding_refs || []" size="small" stripe max-height="120">
          <el-table-column prop="ref_name" label="绑定" />
          <el-table-column prop="element_name" label="控件" />
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="showVersionDialog" title="版本历史" width="720px">
      <el-table :data="versionList" stripe size="small">
        <el-table-column prop="version_num" label="版本" width="70" />
        <el-table-column prop="env_tag" label="环境" width="80" />
        <el-table-column prop="change_reason" label="变更说明" show-overflow-tooltip />
        <el-table-column prop="screenshot_path" label="截图" width="120" show-overflow-tooltip />
        <el-table-column prop="requirement_id" label="需求ID" width="100" />
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="warning" plain @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showChangeLogDialog" title="定位变更留痕" width="760px">
      <el-table :data="changeLogs" size="small" stripe max-height="420" v-loading="changeLogsLoading">
        <el-table-column prop="change_type" label="类型" width="90" />
        <el-table-column prop="reason" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operator_id" label="操作人" width="80" />
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column label="变更" min-width="200">
          <template #default="{ row }">
            <details v-if="row.before_value || row.after_value">
              <summary>查看 diff</summary>
              <pre class="log-diff">{{ formatChangeDiff(row) }}</pre>
            </details>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showBatchReplace" title="批量脚本修复" width="520px">
      <el-form label-width="100px">
        <el-form-item label="应用包名">
          <el-input v-model="batchForm.app_package" placeholder="com.example.app" />
        </el-form-item>
        <el-form-item label="旧控件名">
          <el-input v-model="batchForm.old_name" placeholder="btn_login_old" />
        </el-form-item>
        <el-form-item label="新控件名">
          <el-input v-model="batchForm.new_name" placeholder="btn_login" />
        </el-form-item>
        <el-form-item label="同步用例">
          <el-switch v-model="batchForm.update_cases" />
          <span class="hint">开启后同步替换用例脚本中的 element_name</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchReplace = false">取消</el-button>
        <el-button type="primary" @click="runBatchReplace">执行替换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { controlApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildLocatorChainFromPick, chainToLocators, primaryFromChain, computeStabilityScore, stabilityScoreLabel, stabilityScoreType, riskTagLabel, CONTROL_TAGS, validateElementName, controlTagLabel } from '@/utils/locatorAssist'
import { formatLocatorType } from '@/utils/stepDisplay'

const userStore = useUserStore()

const loading = ref(false)
const router = useRouter()
const poolList = ref([])
const versionList = ref([])
const versionPoolId = ref(null)
const showVersionDialog = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ app_package: '', page_name: '', version_tag: '', env_tag: '', status: '' })
const showChangeLogDialog = ref(false)
const changeLogs = ref([])
const changeLogsLoading = ref(false)
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const showDepsDialog = ref(false)
const showBatchReplace = ref(false)
const depsResult = ref(null)

const batchForm = reactive({
  app_package: '',
  old_name: '',
  new_name: '',
  update_cases: true
})

const poolForm = reactive({
  app_package: '',
  page_name: '',
  element_name: '',
  platform: 'both',
  locator_type: 'id',
  locator_value: '',
  version_tag: '',
  env_tag: '',
  control_tag: 'static',
  is_core: false
})

const editForm = reactive({
  id: null, element_name: '', locator_type: 'id', locator_value: '',
  propagate_bindings: true, reason: '更新定位', screenshot_path: ''
})
const editChain = ref([])
const editMeta = ref({})
const editLocked = ref(false)

function isProtectedPool(row) {
  if (!row?.is_core) return false
  const env = String(row.env_tag || '').toLowerCase()
  return env.includes('prod') || env.includes('production') || env.includes('gray')
}

function canEditPool(row) {
  return !isProtectedPool(row) || userStore.isAdmin
}

function parseFeatureVector(raw) {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try { return JSON.parse(raw) } catch { return {} }
}

function goBatchValidate() {
  router.push({
    path: '/controls/batch-validate',
    query: {
      app_package: filters.app_package || undefined,
      version_tag: filters.version_tag || undefined,
      env_tag: filters.env_tag || undefined
    }
  })
}

function goFailureReport() {
  router.push('/controls/locator-failures')
}

function formatChangeDiff(row) {
  try {
    const before = row.before_value ? JSON.parse(row.before_value) : {}
    const after = row.after_value ? JSON.parse(row.after_value) : {}
    return `前: ${JSON.stringify(before, null, 0)}\n后: ${JSON.stringify(after, null, 0)}`
  } catch {
    return `${row.before_value || '-'}\n→\n${row.after_value || '-'}`
  }
}

async function showChangeLogs(row) {
  showChangeLogDialog.value = true
  changeLogsLoading.value = true
  try {
    const res = await controlApi.changeLogs(row.id, { page: 1, page_size: 50 })
    changeLogs.value = res.data?.list || []
  } finally {
    changeLogsLoading.value = false
  }
}

async function archiveRow(row) {
  await ElMessageBox.confirm(`确认归档控件「${row.element_name}」？归档后执行将不再命中。`, '归档确认', { type: 'warning' })
  await controlApi.archivePool(row.id, { reason: '手动归档' })
  ElMessage.success('已归档')
  loadPool()
}

function poolStabilityScore(row) {
  const fv = parseFeatureVector(row.feature_vector)
  const pickLike = {
    risk_level: fv.risk_level,
    risk_tags: fv.risk_tags || [],
    validate_result: fv.validate_result || fv.last_validation
  }
  const score = computeStabilityScore(pickLike)
  return score > 0 || pickLike.risk_level || (pickLike.risk_tags?.length) || pickLike.validate_result ? score : null
}

function formatPoolValidateMeta(vr, validatedAt) {
  if (!vr) return '-'
  const at = validatedAt || vr.validated_at
  const time = at ? new Date(at).toLocaleString() : '未知时间'
  const status = vr.valid ? '通过' : (vr.error === 'not_clickable' ? '不可点' : '未通过')
  const by = vr.matched_by ? ` · ${formatLocatorType(vr.matched_by)}` : ''
  return `${time} · ${status}${by}`
}

function openEdit(row) {
  if (!canEditPool(row)) {
    ElMessage.warning('生产环境核心控件仅管理员可修改')
    return
  }
  editLocked.value = false
  const fv = parseFeatureVector(row.feature_vector)
  editMeta.value = {
    risk_tags: fv.risk_tags || [],
    validate_result: fv.validate_result || fv.last_validation || null,
    validated_at: fv.validated_at || null,
    stability_score: poolStabilityScore(row)
  }
  const pickLike = {
    locator_chain: fv.locator_chain,
    locators: fv.locators || { [row.locator_type]: row.locator_value },
    locator_type: row.locator_type,
    locator_value: row.locator_value
  }
  editChain.value = buildLocatorChainFromPick(pickLike)
  const primary = primaryFromChain(editChain.value)
  Object.assign(editForm, {
    id: row.id, element_name: row.element_name,
    locator_type: primary.locator_type || row.locator_type,
    locator_value: primary.locator_value || row.locator_value,
    propagate_bindings: true, reason: '更新定位'
  })
  showEditDialog.value = true
}

function setEditPrimary(index) {
  editChain.value = editChain.value.map((item, idx) => ({ ...item, primary: idx === index }))
  const primary = primaryFromChain(editChain.value)
  editForm.locator_type = primary.locator_type || editForm.locator_type
  editForm.locator_value = primary.locator_value || editForm.locator_value
}

function moveEditChain(index, delta) {
  const next = index + delta
  if (next < 0 || next >= editChain.value.length) return
  const arr = [...editChain.value]
  const tmp = arr[index]
  arr[index] = arr[next]
  arr[next] = tmp
  editChain.value = arr.map((item, idx) => ({ ...item, priority: idx + 1 }))
}

async function saveEdit() {
  if (editLocked.value) {
    ElMessage.warning('无编辑权限')
    return
  }
  const deps = await controlApi.scanDependencies(editForm.id)
  if (deps.data.total_refs > 0) {
    await ElMessageBox.confirm(
      `该控件被 ${deps.data.total_refs} 处引用（影响等级：${deps.data.impact_level}），确认继续更新？`,
      '变更确认', { type: 'warning' }
    )
  }
  const locators = chainToLocators(editChain.value)
  const primary = primaryFromChain(editChain.value)
  await controlApi.updatePool(editForm.id, {
    locator_type: primary.locator_type || editForm.locator_type,
    locator_value: primary.locator_value || editForm.locator_value,
    locators,
    locator_chain: editChain.value,
    propagate_bindings: editForm.propagate_bindings,
    reason: editForm.reason,
    screenshot_path: editForm.screenshot_path || undefined
  })
  ElMessage.success('控件已更新')
  showEditDialog.value = false
  loadPool()
}

async function loadPool() {
  loading.value = true
  try {
    const res = await controlApi.listPool({ page: page.value, page_size: pageSize.value, ...filters })
    poolList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function createPool() {
  const nameErr = validateElementName(poolForm.element_name?.trim())
  if (nameErr) {
    ElMessage.warning(nameErr)
    return
  }
  await controlApi.createPool({
    ...poolForm,
    isCore: poolForm.is_core,
    controlTag: poolForm.control_tag
  })
  ElMessage.success('控件池条目已添加')
  showCreateDialog.value = false
  loadPool()
}

async function scanDeps(row) {
  depsResult.value = (await controlApi.scanDependencies(row.id)).data
  showDepsDialog.value = true
}

async function showVersions(row) {
  versionPoolId.value = row.id
  versionList.value = (await controlApi.listVersions(row.id)).data
  showVersionDialog.value = true
}

async function rollbackVersion(ver) {
  await ElMessageBox.confirm(`回滚至版本 v${ver.version_num}？`, '确认', { type: 'warning' })
  await controlApi.rollback(versionPoolId.value, ver.id, { propagate_bindings: true })
  ElMessage.success('已回滚')
  showVersionDialog.value = false
  loadPool()
}

async function runBatchReplace() {
  if (!batchForm.app_package || !batchForm.old_name || !batchForm.new_name) {
    ElMessage.warning('请填写包名、旧控件名和新控件名')
    return
  }
  const res = await controlApi.batchReplace({
    app_package: batchForm.app_package,
    old_name: batchForm.old_name,
    new_name: batchForm.new_name,
    update_cases: batchForm.update_cases
  })
  const d = res.data || {}
  ElMessage.success(`替换完成：控件 ${d.pool_updated || 0}，用例 ${d.cases_updated || 0}`)
  showBatchReplace.value = false
  loadPool()
}

onMounted(loadPool)

function locatorTagClass(type) {
  const basic = ['id', 'xpath', 'accessibility', 'text']
  const composite = ['image', 'ai']
  if (basic.includes(type)) return 'tag-locator-basic'
  if (composite.includes(type)) return 'tag-locator-composite'
  return 'tag-locator-custom'
}
</script>

<style scoped>
.tag-locator-basic {
  background: var(--atp-brand-200) !important;
  border-color: var(--atp-brand-200) !important;
  color: var(--atp-brand-600) !important;
}
.tag-locator-composite {
  background: var(--atp-brand-300) !important;
  border-color: var(--atp-brand-300) !important;
  color: #fff !important;
}
.tag-locator-custom {
  background: var(--atp-primary) !important;
  border-color: var(--atp-primary) !important;
  color: #fff !important;
}
.hint { font-size: 12px; color: var(--atp-text-secondary); margin-left: 8px; }
.chain-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.chain-row {
  display: grid;
  grid-template-columns: 24px 1fr auto;
  gap: 8px;
  align-items: start;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  cursor: pointer;
}
.chain-row.primary {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.chain-rank {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.chain-main code {
  display: block;
  font-size: 11px;
  word-break: break-all;
  margin-top: 4px;
}
.chain-type {
  font-size: 12px;
  font-weight: 500;
  margin-right: 6px;
}
.chain-side {
  display: flex;
  align-items: center;
  gap: 2px;
}
.log-diff {
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 160px;
  overflow: auto;
  margin: 6px 0 0;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
</style>
