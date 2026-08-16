<template>
  <div class="page-container controls-page">
    <PageHeader title="控件池管理" subtitle="全局 UI 控件定位资源池，统一管理页面元素、修复定位失效问题">
      <template #actions>
        <div class="header-actions">
          <el-tooltip content="请先勾选控件再执行批量操作" :disabled="hasSelection" placement="bottom">
            <span class="batch-wrap">
              <el-tooltip content="批量校验控件当前页面是否可正常识别" :disabled="!hasSelection" placement="bottom">
                <el-button class="btn-muted" :disabled="!hasSelection" @click="goBatchValidate">批量校验</el-button>
              </el-tooltip>
              <el-tooltip content="导出校验失败控件清单，查看定位失效原因" :disabled="!hasSelection" placement="bottom">
                <el-button class="btn-muted" :disabled="!hasSelection" @click="goFailureReport">失败报表</el-button>
              </el-tooltip>
              <el-tooltip content="自动批量刷新控件多套定位表达式，提升稳定性" :disabled="!hasSelection" placement="bottom">
                <el-button class="btn-muted" :disabled="!hasSelection" @click="openBatchRepair">批量修复</el-button>
              </el-tooltip>
            </span>
          </el-tooltip>

          <el-dropdown split-button type="primary" @click="openCreateForm" @command="onCreateCommand">
            <el-icon><Plus /></el-icon> 添加控件
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="manual">手动录入控件</el-dropdown-item>
                <el-dropdown-item command="picker">跳转控件拾取</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：质量统计 -->
    <div class="stats-row" v-loading="statsLoading">
      <div class="stat-card tone-all" @click="quickFilterStatus('')">
        <div class="stat-value">{{ qualityStats.all }}</div>
        <div class="stat-label">全部控件</div>
      </div>
      <div class="stat-card tone-ok" @click="quickFilterStatus('active')">
        <div class="stat-value is-ok">{{ qualityStats.active }}</div>
        <div class="stat-label">正常生效控件</div>
      </div>
      <div class="stat-card tone-warn" @click="filterUnstable">
        <div class="stat-value is-warn">{{ qualityStats.unstable }}</div>
        <div class="stat-label">低风险不稳定控件</div>
      </div>
      <div class="stat-card tone-archived" @click="quickFilterStatus('archived')">
        <div class="stat-value is-muted">{{ qualityStats.archived }}</div>
        <div class="stat-label">已归档废弃控件</div>
      </div>
    </div>

    <AppCard :hover="false" class="list-card">
      <!-- 模块 3：筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.page_name"
          placeholder="所属页面（中文）"
          clearable
          style="width:140px"
          @change="onFilterChange"
          @clear="onFilterChange"
        />
        <el-select
          v-model="filters.version_tag"
          placeholder="版本"
          clearable
          filterable
          allow-create
          style="width:130px"
          @change="onFilterChange"
        >
          <el-option v-for="v in versionOptions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-select v-model="filters.env_tag" placeholder="环境" clearable style="width:130px" @change="onFilterChange">
          <el-option label="测试" value="test" />
          <el-option label="预发" value="staging" />
          <el-option label="生产" value="prod" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:120px" @change="onFilterChange">
          <el-option label="生效" value="active" />
          <el-option label="已归档" value="archived" />
        </el-select>
        <div class="filter-right">
          <el-button type="primary" plain :loading="loading" @click="refreshAll">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
          <el-button @click="resetFilters">重置筛选</el-button>
        </div>
      </div>

      <!-- 模块 4：表格 -->
      <el-table
        :data="displayList"
        v-loading="loading"
        stripe
        empty-text=""
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="element_name" label="控件名称" min-width="180" class-name="name-col">
          <template #default="{ row }">
            <span class="name-cell">{{ row.element_name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="page_name" label="所属页面" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ formatPageName(row.page_name) }}</template>
        </el-table-column>
        <el-table-column label="定位类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :class="locatorToneClass(row.locator_type)">
              {{ locatorTypeLabel(row.locator_type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="定位表达式" min-width="200">
          <template #default="{ row }">
            <el-popover placement="top" :width="420" trigger="hover">
              <template #reference>
                <span class="expr-cell">{{ truncateExpr(row.locator_value) }}</span>
              </template>
              <div class="expr-pop">
                <pre>{{ row.locator_value || '-' }}</pre>
                <el-button size="small" type="primary" plain @click="copyText(row.locator_value)">一键复制</el-button>
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="稳定性" width="100">
          <template #default="{ row }">
            <span v-if="poolStabilityScore(row) != null" class="stab-score" :class="stabClass(poolStabilityScore(row))">
              {{ poolStabilityScore(row) }}
            </span>
            <span v-else class="hint-inline">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hit_count" label="命中次数" width="90" align="center" />
        <el-table-column label="控件状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version_tag" label="版本" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.version_tag || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="268" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip v-if="!canEditPool(row)" content="生产环境核心控件仅管理员可改" placement="top">
                <el-button size="small" class="btn-act btn-act-primary" disabled>编辑</el-button>
              </el-tooltip>
              <el-button v-else size="small" class="btn-act btn-act-primary" @click="openPoolFormEdit(row)">编辑</el-button>
              <el-button size="small" class="btn-act btn-act-warn" @click="scanDeps(row)">依赖</el-button>
              <el-button size="small" class="btn-act" @click="showChangeLogs(row)">变更</el-button>
              <el-button size="small" class="btn-act" @click="showVersions(row)">版本</el-button>
              <el-button size="small" class="btn-act" @click="copyControl(row)">复制</el-button>
              <el-button
                v-if="row.status === 'active'"
                size="small"
                class="btn-act btn-act-danger"
                @click="archiveRow(row)"
              >归档</el-button>
              <el-button
                v-if="canEditPool(row)"
                size="small"
                class="btn-act btn-act-danger"
                @click="deleteRow(row)"
              >删除</el-button>
              <el-tooltip v-else content="生产环境核心控件仅管理员可删" placement="top">
                <el-button size="small" class="btn-act btn-act-danger" disabled>删除</el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !displayList.length" class="table-empty">
        <p>暂无控件资源</p>
        <div class="empty-actions">
          <el-button type="primary" @click="openCreateForm">添加控件</el-button>
          <el-button @click="$router.push('/element-picker')">跳转控件拾取页面抓取元素</el-button>
        </div>
      </div>

      <div class="pager-bar">
        <div class="pager-stats">当前筛选结果共 <strong>{{ filteredTotal }}</strong> 条控件</div>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredTotal"
          layout="total, prev, pager, next"
          @change="loadPool"
        />
      </div>
    </AppCard>

    <!-- 模块 5：底部快捷栏 -->
    <section class="shortcut-bar">
      <div class="shortcut-card" @click="$router.push('/element-picker')">
        <el-icon :size="22"><Aim /></el-icon>
        <div>
          <h4>控件拾取</h4>
          <p>一键跳转控件拾取页面，投屏抓取新控件自动入库</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/common-steps')">
        <el-icon :size="22"><Connection /></el-icon>
        <div>
          <h4>公共步骤库</h4>
          <p>查看哪些公共步骤引用池内控件</p>
        </div>
      </div>
      <div class="shortcut-card" @click="$router.push('/cases')">
        <el-icon :size="22"><Document /></el-icon>
        <div>
          <h4>测试用例</h4>
          <p>快速打开用例编辑页，拖拽引用控件池元素</p>
        </div>
      </div>
      <div class="shortcut-card" @click="exportControls">
        <el-icon :size="22"><Download /></el-icon>
        <div>
          <h4>导出控件清单</h4>
          <p>Excel 导出全部控件定位信息备份</p>
        </div>
      </div>
    </section>

    <ControlPoolEntryDialog
      v-model="showCreateDialog"
      :edit-row="editingPoolRow"
      @saved="onPoolFormSaved"
    />

    <el-dialog v-model="showEditDialog" title="编辑控件" width="640px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="控件名称">
          <el-input v-model="editForm.element_name" maxlength="256" show-word-limit placeholder="业务可读控件名" />
        </el-form-item>
        <el-form-item label="主定位类型">
          <el-select v-model="editForm.locator_type" style="width:100%">
            <el-option label="ID 定位" value="id" />
            <el-option label="xpath" value="xpath" />
            <el-option label="文案定位" value="accessibility" />
            <el-option label="文本定位" value="text" />
            <el-option label="坐标定位" value="bounds" />
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
          · 影响等级
          <el-tag :type="{ none: 'success', low: 'info', medium: 'warning', high: 'danger' }[depsResult.impact_level]" size="small">
            {{ impactLabel(depsResult.impact_level) }}
          </el-tag>
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
              <summary>查看对比</summary>
              <pre class="log-diff">{{ formatChangeDiff(row) }}</pre>
            </details>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showBatchReplace" title="批量脚本修复" width="520px">
      <el-form label-width="100px">
        <el-form-item label="旧控件名">
          <el-input v-model="batchForm.old_name" placeholder="btn_login_old" />
        </el-form-item>
        <el-form-item label="新控件名">
          <el-input v-model="batchForm.new_name" placeholder="btn_login" />
        </el-form-item>
        <el-form-item label="同步用例">
          <el-switch v-model="batchForm.update_cases" />
          <span class="hint">开启后同步替换用例脚本中的控件名称</span>
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
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { controlApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import ControlPoolEntryDialog from '@/components/ControlPoolEntryDialog.vue'
import {
  buildLocatorChainFromPick, chainToLocators, primaryFromChain, computeStabilityScore,
  stabilityScoreLabel, stabilityScoreType, riskTagLabel,
  mapLocatorTypeForPool, mapLocatorValueForPool
} from '@/utils/locatorAssist'
import { formatLocatorType } from '@/utils/stepDisplay'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const statsLoading = ref(false)
const poolList = ref([])
const versionList = ref([])
const versionPoolId = ref(null)
const showVersionDialog = ref(false)
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const filters = reactive({ page_name: '', version_tag: '', env_tag: '', status: '' })
const onlyUnstable = ref(false)
const selectedRows = ref([])
const versionOptions = ref([])
const qualityStats = reactive({ all: 0, active: 0, unstable: 0, archived: 0 })

const showChangeLogDialog = ref(false)
const changeLogs = ref([])
const changeLogsLoading = ref(false)
const showCreateDialog = ref(false)
const editingPoolRow = ref(null)
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

const editForm = reactive({
  id: null, element_name: '', locator_type: 'id', locator_value: '',
  propagate_bindings: true, reason: '更新定位', screenshot_path: ''
})
const editChain = ref([])
const editMeta = ref({})
const editLocked = ref(false)

const hasSelection = computed(() => selectedRows.value.length > 0)

const displayList = computed(() => {
  if (!onlyUnstable.value) return poolList.value
  return poolList.value.filter(row => {
    const s = poolStabilityScore(row)
    return s != null && s < 80
  })
})

const filteredTotal = computed(() => {
  if (onlyUnstable.value) return displayList.value.length
  return total.value
})

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

function statusLabel(s) {
  return { active: '生效', archived: '已归档', deprecated: '已废弃' }[s] || s || '-'
}

const PAGE_NAME_MAP = {
  login: '登录页',
  login_page: '登录页',
  loginpage: '登录页',
  signin: '登录页',
  sign_in: '登录页',
  register: '注册页',
  signup: '注册页',
  sign_up: '注册页',
  home: '首页',
  homepage: '首页',
  home_page: '首页',
  index: '首页',
  main: '首页',
  main_page: '首页',
  splash: '启动页',
  launch: '启动页',
  welcome: '欢迎页',
  profile: '个人中心',
  mine: '个人中心',
  me: '个人中心',
  user: '个人中心',
  user_center: '个人中心',
  personal: '个人中心',
  personal_center: '个人中心',
  settings: '设置页',
  setting: '设置页',
  preference: '设置页',
  search: '搜索页',
  cart: '购物车',
  shopping_cart: '购物车',
  pay: '支付页',
  payment: '支付页',
  checkout: '结算页',
  order: '订单页',
  orders: '订单页',
  order_list: '订单列表',
  order_detail: '订单详情',
  detail: '详情页',
  product: '商品页',
  product_detail: '商品详情',
  message: '消息页',
  msg: '消息页',
  chat: '聊天页',
  webview: 'H5 页面',
  h5: 'H5 页面',
  native: '原生页面',
  tab: '底部导航',
  tabbar: '底部导航',
  about: '关于页',
  feedback: '反馈页',
  help: '帮助页',
  address: '地址页',
  coupon: '优惠券页',
  wallet: '钱包页'
}



function formatPageName(name) {
  const raw = String(name || '').trim()
  if (!raw) return '未命名页面'
  // 已含中文则直接展示
  if (/[\u4e00-\u9fff]/.test(raw)) return raw

  const key = raw
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[\s\-]+/g, '_')
    .toLowerCase()

  if (PAGE_NAME_MAP[key]) return PAGE_NAME_MAP[key]

  // 常见后缀 _page / Page
  const stripped = key.replace(/_page$/, '').replace(/page$/, '')
  if (PAGE_NAME_MAP[stripped]) return PAGE_NAME_MAP[stripped]
  if (PAGE_NAME_MAP[`${stripped}_page`]) return PAGE_NAME_MAP[`${stripped}_page`]

  // 英文可读化兜底：login_page → 登录页类无法识别时保留「页面·原文」避免空白英文突兀
  const readable = raw
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
  return `页面·${readable}`
}

function impactLabel(level) {
  return { none: '无', low: '低', medium: '中', high: '高' }[level] || level || '-'
}

function locatorTypeLabel(type) {
  const map = {
    id: 'ID 定位',
    resource_id: 'ID 定位',
    accessibility: '文案定位',
    accessibility_id: '文案定位',
    content_desc: '文案定位',
    desc: '文案定位',
    text: '文本定位',
    xpath: 'xpath',
    xpath_desc: 'xpath',
    absolute_xpath: '绝对 xpath',
    relative_xpath: '相对 xpath',
    bounds: '坐标定位',
    coordinate: '坐标定位',
    xy: '坐标定位',
    ai: 'AI 定位',
    image: '图像定位'
  }
  return map[type] || formatLocatorType(type)
}

function locatorToneClass(type) {
  const t = String(type || '')
  if (['id', 'resource_id'].includes(t)) return 'loc-id'
  if (['accessibility', 'accessibility_id', 'content_desc', 'desc'].includes(t)) return 'loc-desc'
  if (['text'].includes(t)) return 'loc-text'
  if (t.includes('xpath') || t === 'uiselector') return 'loc-path'
  return 'loc-other'
}

function truncateExpr(val, max = 42) {
  const s = String(val || '')
  if (s.length <= max) return s || '-'
  return `${s.slice(0, max - 3)}...`
}

function stabClass(score) {
  if (score >= 90) return 'stab-good'
  if (score >= 60) return 'stab-mid'
  return 'stab-bad'
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function onFilterChange() {
  onlyUnstable.value = false
  page.value = 1
  loadPool()
}

function resetFilters() {
  filters.page_name = ''
  filters.version_tag = ''
  filters.env_tag = ''
  filters.status = ''
  onlyUnstable.value = false
  page.value = 1
  loadPool()
  loadQualityStats()
}

function quickFilterStatus(status) {
  filters.status = status
  onlyUnstable.value = false
  page.value = 1
  loadPool()
}

function filterUnstable() {
  filters.status = 'active'
  onlyUnstable.value = true
  page.value = 1
  loadPool()
}

async function refreshAll() {
  await Promise.all([loadPool(), loadQualityStats()])
}

function openCreateForm() {
  editingPoolRow.value = null
  showCreateDialog.value = true
}

function openPoolFormEdit(row) {
  if (!canEditPool(row)) {
    ElMessage.warning('生产环境核心控件仅管理员可修改')
    return
  }
  editingPoolRow.value = row
  showCreateDialog.value = true
}

function onPoolFormSaved() {
  page.value = 1
  refreshAll()
}

function onCreateCommand(cmd) {
  if (cmd === 'picker') router.push('/element-picker')
  else openCreateForm()
}

function goBatchValidate() {
  if (!hasSelection.value) return
  const first = selectedRows.value[0]
  router.push({
    path: '/controls/batch-validate',
    query: {
      app_package: first?.app_package || undefined,
      version_tag: first?.version_tag || filters.version_tag || undefined,
      env_tag: first?.env_tag || filters.env_tag || undefined,
      ids: selectedRows.value.map(r => r.id).join(',')
    }
  })
}

function goFailureReport() {
  if (!hasSelection.value) return
  router.push('/controls/locator-failures')
}

function openBatchRepair() {
  if (!hasSelection.value) return
  const first = selectedRows.value[0]
  batchForm.app_package = first?.app_package || ''
  batchForm.old_name = first?.element_name || ''
  batchForm.new_name = first?.element_name ? `${first.element_name}_fixed` : ''
  showBatchReplace.value = true
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
  await ElMessageBox.confirm(
    `确认归档控件「${row.element_name}」？归档后不再参与自动化执行。`,
    '归档确认',
    { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' }
  )
  await controlApi.archivePool(row.id, { reason: '手动归档' })
  ElMessage.success('已归档')
  refreshAll()
}

async function deleteRow(row) {
  if (!canEditPool(row)) {
    ElMessage.warning('生产环境核心控件仅管理员可删除')
    return
  }
  let depTotal = 0
  try {
    const depRes = await controlApi.scanDependencies(row.id)
    depTotal = depRes.data?.total_refs || 0
  } catch {
    depTotal = 0
  }

  try {
    if (depTotal > 0) {
      await ElMessageBox.confirm(
        `控件「${row.element_name}」仍被 ${depTotal} 处用例 / 套件 / 绑定引用。删除后相关步骤可能定位失败，且不可恢复。是否强制删除？`,
        '高危删除确认',
        { type: 'error', confirmButtonText: '强制删除', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消' }
      )
      await controlApi.deletePool(row.id, true)
    } else {
      await ElMessageBox.confirm(
        `确定删除控件「${row.element_name}」？删除后不可恢复，版本与变更记录将一并清除。`,
        '删除确认',
        { type: 'warning', confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消' }
      )
      await controlApi.deletePool(row.id, false)
    }
    ElMessage.success('控件已删除')
    refreshAll()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e?.message || ''
    if (/引用|HAS_DEPS|依赖/.test(msg)) {
      try {
        await ElMessageBox.confirm(
          `${msg}\n是否强制删除？强制删除不可恢复。`,
          '强制删除确认',
          { type: 'error', confirmButtonText: '强制删除', confirmButtonClass: 'el-button--danger' }
        )
        await controlApi.deletePool(row.id, true)
        ElMessage.success('控件已强制删除')
        refreshAll()
      } catch { /* cancel */ }
      return
    }
    if (msg) ElMessage.error(msg)
  }
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
    locator_type: mapLocatorTypeForPool(primary.raw_type || primary.locator_type || row.locator_type, primary.locator_value || row.locator_value),
    locator_value: mapLocatorValueForPool(primary.raw_type || primary.locator_type || row.locator_type, primary.locator_value || row.locator_value),
    propagate_bindings: true, reason: '更新定位'
  })
  showEditDialog.value = true
}

function setEditPrimary(index) {
  editChain.value = editChain.value.map((item, idx) => ({ ...item, primary: idx === index }))
  const primary = primaryFromChain(editChain.value)
  editForm.locator_type = mapLocatorTypeForPool(primary.raw_type || primary.locator_type, primary.locator_value) || editForm.locator_type
  editForm.locator_value = mapLocatorValueForPool(primary.raw_type || primary.locator_type, primary.locator_value) || editForm.locator_value
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
      `该控件被 ${deps.data.total_refs} 处引用（影响等级：${impactLabel(deps.data.impact_level)}），确认继续更新？`,
      '变更确认', { type: 'warning' }
    )
  }
  const locators = chainToLocators(editChain.value)
  const primary = primaryFromChain(editChain.value)
  await controlApi.updatePool(editForm.id, {
    element_name: (editForm.element_name || '').trim(),
    locator_type: mapLocatorTypeForPool(primary.raw_type || primary.locator_type || editForm.locator_type, primary.locator_value || editForm.locator_value),
    locator_value: mapLocatorValueForPool(primary.raw_type || primary.locator_type || editForm.locator_type, primary.locator_value || editForm.locator_value),
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
    const params = { page: page.value, page_size: pageSize.value, ...filters }
    // 不稳定筛选时多取一些再前端过滤
    if (onlyUnstable.value) {
      params.page = 1
      params.page_size = 200
      params.status = 'active'
    }
    const res = await controlApi.listPool(params)
    poolList.value = res.data.list || []
    total.value = res.data.total || 0
    // 收集版本选项
    const vers = new Set(versionOptions.value)
    for (const row of poolList.value) {
      if (row.version_tag) vers.add(row.version_tag)
    }
    versionOptions.value = [...vers].sort()
  } finally {
    loading.value = false
  }
}

async function loadQualityStats() {
  statsLoading.value = true
  try {
    const [allRes, activeRes, archivedRes, sampleRes] = await Promise.allSettled([
      controlApi.listPool({ page: 1, page_size: 1 }),
      controlApi.listPool({ page: 1, page_size: 1, status: 'active' }),
      controlApi.listPool({ page: 1, page_size: 1, status: 'archived' }),
      controlApi.listPool({ page: 1, page_size: 200, status: 'active' })
    ])
    if (allRes.status === 'fulfilled') qualityStats.all = allRes.value.data?.total || 0
    if (activeRes.status === 'fulfilled') qualityStats.active = activeRes.value.data?.total || 0
    if (archivedRes.status === 'fulfilled') qualityStats.archived = archivedRes.value.data?.total || 0
    if (sampleRes.status === 'fulfilled') {
      const list = sampleRes.value.data?.list || []
      qualityStats.unstable = list.filter(row => {
        const s = poolStabilityScore(row)
        return s != null && s < 80
      }).length
      const vers = new Set(versionOptions.value)
      for (const row of list) {
        if (row.version_tag) vers.add(row.version_tag)
      }
      versionOptions.value = [...vers].sort()
    }
  } finally {
    statsLoading.value = false
  }
}

async function copyControl(row) {
  try {
    await controlApi.createPool({
      app_package: row.app_package,
      page_name: row.page_name || '',
      element_name: `${row.element_name}_副本`,
      platform: row.platform || 'both',
      locator_type: row.locator_type,
      locator_value: row.locator_value,
      version_tag: row.version_tag || '',
      env_tag: row.env_tag || '',
      control_tag: row.control_tag || 'static',
      isCore: !!row.is_core,
      controlTag: row.control_tag || 'static'
    })
    ElMessage.success('控件已复制，请修改名称后使用')
    page.value = 1
    refreshAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '复制失败')
  }
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
  if (!batchForm.old_name || !batchForm.new_name) {
    ElMessage.warning('请填写旧控件名和新控件名')
    return
  }
  if (!batchForm.app_package) {
    ElMessage.warning('所选控件缺少应用包名，无法批量替换')
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

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(String(text || ''))
    ElMessage.success('已复制定位表达式')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function exportControls() {
  try {
    const res = await controlApi.listPool({ page: 1, page_size: 500, ...filters })
    const list = res.data?.list || []
    const header = ['控件名称', '所属页面', '定位类型', '定位表达式', '稳定性', '命中次数', '控件状态', '版本', '环境']
    const rows = list.map(r => [
      r.element_name,
      r.page_name ? formatPageName(r.page_name) : '未命名页面',
      locatorTypeLabel(r.locator_type),
      `"${String(r.locator_value || '').replace(/"/g, '""')}"`,
      poolStabilityScore(r) ?? '',
      r.hit_count ?? 0,
      statusLabel(r.status),
      r.version_tag || '',
      r.env_tag || ''
    ])
    const bom = '\uFEFF'
    const csv = [header.join(','), ...rows.map(r => r.join(','))].join('\n')
    const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `控件清单_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${list.length} 条控件`)
  } catch {
    ElMessage.error('导出失败')
  }
}

function maybeOpenFormFromQuery() {
  if (route.query.open_form === '1') {
    openCreateForm()
    router.replace({ path: '/controls', query: {} })
  }
}

onMounted(() => {
  loadPool()
  loadQualityStats()
  maybeOpenFormFromQuery()
})

onActivated(() => {
  maybeOpenFormFromQuery()
})
</script>

<style scoped>
.controls-page {
  max-width: none;
  width: 100%;
  padding: 16px 12px 24px;
  margin: 0;
  box-sizing: border-box;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
}
.batch-wrap {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
.btn-muted {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
}
.btn-muted-sm {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 12px;
  padding: 16px 18px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.stat-card.tone-all { background: var(--atp-info-bg, #eff6ff); }
.stat-card.tone-ok { background: var(--atp-success-bg, #ecfdf5); }
.stat-card.tone-warn { background: var(--atp-warning-bg, #fffbeb); }
.stat-card.tone-archived { background: #f1f5f9; }
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--atp-text);
  line-height: 1.15;
}
.stat-value.is-ok { color: var(--atp-success, #059669); }
.stat-value.is-warn { color: #ea580c; }
.stat-value.is-muted { color: #94a3b8; }
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--atp-text-secondary);
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

.expr-cell {
  color: var(--atp-text-secondary);
  font-size: 12px;
  cursor: help;
}
.expr-pop pre {
  margin: 0 0 10px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 10px;
  border-radius: 8px;
}

.loc-id {
  --el-tag-bg-color: #eff6ff;
  --el-tag-border-color: #bfdbfe;
  --el-tag-text-color: #1d4ed8;
}
.loc-desc {
  --el-tag-bg-color: #ecfdf5;
  --el-tag-border-color: #a7f3d0;
  --el-tag-text-color: #047857;
}
.loc-text {
  --el-tag-bg-color: #fffbeb;
  --el-tag-border-color: #fde68a;
  --el-tag-text-color: #b45309;
}
.loc-path {
  --el-tag-bg-color: #f1f5f9;
  --el-tag-border-color: #e2e8f0;
  --el-tag-text-color: #64748b;
}
.loc-other {
  --el-tag-bg-color: #f8fafc;
  --el-tag-border-color: #e2e8f0;
  --el-tag-text-color: #475569;
}

.stab-score {
  font-weight: 700;
  font-size: 13px;
}
.stab-good { color: #059669; }
.stab-mid { color: #ca8a04; }
.stab-bad { color: #ea580c; }
.hint-inline { color: #94a3b8; font-size: 12px; }

.name-cell {
  display: inline-block;
  white-space: normal;
  word-break: break-word;
  line-height: 1.45;
  color: var(--atp-text-primary, #0f172a);
}

:deep(.el-table .name-col .cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: unset;
  line-height: 1.45;
}

.row-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px 4px;
  width: 100%;
}

.row-actions > .el-button,
.row-actions > .el-tooltip {
  min-width: 0;
}

.row-actions :deep(.el-button) {
  width: 100%;
  margin: 0;
  padding-left: 4px;
  padding-right: 4px;
}

.row-actions :deep(.btn-act) {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
  --el-button-hover-bg-color: #f1f5f9;
  --el-button-hover-border-color: #cbd5e1;
  --el-button-hover-text-color: #475569;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-border-color: #e2e8f0;
  --el-button-disabled-text-color: #94a3b8;
}

.row-actions :deep(.btn-act-primary) {
  --el-button-bg-color: #eef2ff;
  --el-button-border-color: #c7d2fe;
  --el-button-text-color: #4f46e5;
  --el-button-hover-bg-color: #e0e7ff;
  --el-button-hover-border-color: #a5b4fc;
  --el-button-hover-text-color: #4338ca;
}

.row-actions :deep(.btn-act-warn) {
  --el-button-bg-color: #fff7ed;
  --el-button-border-color: #fed7aa;
  --el-button-text-color: #c2410c;
  --el-button-hover-bg-color: #ffedd5;
  --el-button-hover-border-color: #fdba74;
  --el-button-hover-text-color: #9a3412;
}

.row-actions :deep(.btn-act-danger) {
  --el-button-bg-color: #fef2f2;
  --el-button-border-color: #fecaca;
  --el-button-text-color: #b91c1c;
  --el-button-hover-bg-color: #fee2e2;
  --el-button-hover-border-color: #fca5a5;
  --el-button-hover-text-color: #991b1b;
}

.table-empty {
  text-align: center;
  padding: 48px 16px 24px;
  color: var(--atp-text-secondary);
}
.table-empty p { margin: 0 0 14px; }
.empty-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

.pager-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}
.pager-stats {
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.shortcut-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.shortcut-card h4 { margin: 0 0 4px; font-size: 14px; }
.shortcut-card p {
  margin: 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.45;
}
.shortcut-card .el-icon { color: var(--atp-primary); margin-top: 2px; }

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

@media (max-width: 1100px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .stats-row,
  .shortcut-bar { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
