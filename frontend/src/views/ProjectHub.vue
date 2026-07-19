<template>
  <div class="page-container project-hub-page">
    <PageHeader
      title="项目管理"
      subtitle="多业务资源隔离管理，统一维护测试环境、安装包、团队权限、测试数据集与版本基线"
    >
      <template #actions>
        <div class="project-switch">
          <span class="switch-label">当前项目</span>
          <el-select
            v-model="currentProjectId"
            filterable
            placeholder="切换业务项目"
            style="width:220px"
            @change="onProjectChange"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：资源统计 -->
    <div class="stats-row" v-loading="statsLoading">
      <div
        v-for="s in statItems"
        :key="s.key"
        class="stat-card"
        :class="s.tone"
        @click="goCard(cardByKey(s.key))"
      >
        <div class="stat-label-top">{{ s.title }}</div>
        <div class="stat-value">共 {{ s.value }} {{ s.unit }}</div>
      </div>
    </div>

    <!-- 模块 3：五大功能卡片 -->
    <div class="hub-grid">
      <AppCard
        v-for="card in visibleCards"
        :key="card.key"
        class="hub-card"
        :class="`hub-${card.tone}`"
        :hover="false"
      >
        <div class="hub-card-inner">
          <div class="hub-icon" :class="card.tone">
            <el-icon :size="28"><component :is="card.icon" /></el-icon>
          </div>
          <h3>{{ card.title }}</h3>
          <p class="hub-desc">{{ card.desc }}</p>
          <p class="hub-count">当前项目共 {{ card.count }} {{ card.unit }}</p>
          <div class="hub-actions">
            <el-button type="primary" link @click="goCard(card)">进入</el-button>
            <el-button
              size="small"
              class="btn-quick"
              :class="`quick-${card.tone}`"
              :disabled="card.adminOnly && !userStore.isAdmin"
              @click.stop="openQuick(card.key)"
            >{{ card.quickLabel }}</el-button>
          </div>
        </div>
      </AppCard>
    </div>

    <!-- 模块 4：底部指引 -->
    <section class="guide-bar">
      <div class="guide-left">
        <h4>五大模块业务场景科普</h4>
        <ul>
          <li><strong>环境配置：</strong>用例 / 任务执行时自动注入对应环境域名，一键切换多环境回归</li>
          <li><strong>应用包版本：</strong>执行任务时快速选择对应迭代安装包，无需本地重复上传</li>
          <li><strong>团队空间：</strong>划分业务测试小组，隔离用例、套件、设备资源访问权限</li>
          <li><strong>数据集：</strong>参数化步骤自动读取测试数据，实现多组数据循环执行</li>
          <li><strong>版本基线：</strong>固定一套回归全套配置，版本迭代一键复用基线发起全量测试</li>
        </ul>
      </div>
      <div class="guide-right">
        <h4>自动化页面快捷跳转</h4>
        <div class="jump-btns">
          <el-button @click="$router.push('/cases')">测试用例</el-button>
          <el-button @click="$router.push('/suites')">测试套件</el-button>
          <el-button @click="$router.push('/tasks')">测试任务</el-button>
          <el-button type="primary" plain @click="$router.push('/public-assets')">公共组件</el-button>
        </div>
        <p class="jump-hint">
          用例绑定环境与数据集 · 套件配置回归基线与安装包 · 任务执行选用项目环境 · 全局参数联动环境配置
        </p>
      </div>
    </section>

    <!-- 快捷：新建环境 -->
    <EnvEditorDialog v-model="showEnvDialog" :edit-row="null" @saved="loadAll" />

    <!-- 快捷：上传安装包 -->
    <AppPackageUploadDialog v-model="showPkgDialog" @saved="loadAll" />

    <!-- 快捷：新建团队 -->
    <TeamEditorDialog v-model="showTeamDialog" :edit-row="null" @saved="loadAll" />

    <!-- 快捷：新建数据集 -->
    <DatasetEditorDialog v-model="showDatasetDialog" :edit-row="null" @saved="loadAll" />

    <!-- 快捷：新建基线 -->
    <BaselineEditorDialog v-model="showBaselineDialog" :edit-row="null" @saved="loadAll" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { envApi, datasetApi, teamApi, baselineApi, appPackageApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import EnvEditorDialog from '@/components/EnvEditorDialog.vue'
import AppPackageUploadDialog from '@/components/AppPackageUploadDialog.vue'
import TeamEditorDialog from '@/components/TeamEditorDialog.vue'
import DatasetEditorDialog from '@/components/DatasetEditorDialog.vue'
import BaselineEditorDialog from '@/components/BaselineEditorDialog.vue'

const PROJECT_KEY = 'atp_current_project'
const PROJECT_LIST_KEY = 'atp_project_list'

const router = useRouter()
const userStore = useUserStore()
const statsLoading = ref(false)
const currentProjectId = ref('default')

const envs = ref([])
const packages = ref([])
const teams = ref([])
const datasets = ref([])
const baselines = ref([])
const localProjects = ref([])

const showEnvDialog = ref(false)
const showPkgDialog = ref(false)
const showTeamDialog = ref(false)
const showDatasetDialog = ref(false)
const showBaselineDialog = ref(false)

const projectOptions = computed(() => {
  const base = [{ id: 'default', name: '默认业务项目（全局）' }]
  const fromTeams = (teams.value || []).map(t => ({
    id: `team-${t.id}`,
    name: t.name,
    teamId: t.id
  }))
  const custom = (localProjects.value || []).map(p => ({
    id: p.id,
    name: p.name
  }))
  // 去重：团队名与自定义不重复展示 id
  const map = new Map()
  ;[...base, ...fromTeams, ...custom].forEach(p => map.set(p.id, p))
  return [...map.values()]
})

const currentProjectName = computed(() => {
  return projectOptions.value.find(p => p.id === currentProjectId.value)?.name || '默认业务项目'
})

const counts = computed(() => ({
  env: envs.value.length,
  pkg: packages.value.length,
  team: teams.value.length,
  dataset: datasets.value.length,
  baseline: baselines.value.length
}))

const cards = computed(() => [
  {
    key: 'env',
    title: '环境配置',
    desc: '维护测试 / 预发 / 生产三套环境，统一管理请求基础地址',
    icon: 'Monitor',
    tone: 'primary',
    path: '/platform-config',
    query: { tab: 'env' },
    count: counts.value.env,
    unit: '套环境',
    quickLabel: '新建环境'
  },
  {
    key: 'pkg',
    title: '应用包版本',
    desc: 'APP 安装包仓库管理，多迭代版本存档、快速选用',
    icon: 'Box',
    tone: 'success',
    path: '/app-packages',
    count: counts.value.pkg,
    unit: '个安装包',
    quickLabel: '上传安装包'
  },
  {
    key: 'team',
    title: '团队空间',
    desc: '业务团队资源隔离，管控成员读写、执行权限',
    icon: 'UserFilled',
    tone: 'warning',
    path: '/platform-config',
    query: { tab: 'teams' },
    count: counts.value.team,
    unit: '个业务团队',
    quickLabel: '新建团队',
    adminOnly: true
  },
  {
    key: 'dataset',
    title: '数据集',
    desc: '参数化测试数据、自动化造数模板统一维护',
    icon: 'Coin',
    tone: 'info',
    path: '/platform-config',
    query: { tab: 'dataset' },
    count: counts.value.dataset,
    unit: '套测试数据集',
    quickLabel: '新建数据集'
  },
  {
    key: 'baseline',
    title: '版本基线',
    desc: '绑定套件、环境、安装包，固定版本回归基线配置',
    icon: 'Flag',
    tone: 'cyan',
    path: '/platform-config',
    query: { tab: 'baseline' },
    count: counts.value.baseline,
    unit: '套回归基线',
    quickLabel: '新建基线'
  }
])

const visibleCards = computed(() =>
  cards.value.filter(c => !c.adminOnly || userStore.isAdmin)
)

const statItems = computed(() => [
  { key: 'env', title: '环境配置', value: counts.value.env, unit: '套环境', tone: 'tone-primary' },
  { key: 'pkg', title: '应用包版本', value: counts.value.pkg, unit: '个安装包', tone: 'tone-success' },
  { key: 'team', title: '团队空间', value: counts.value.team, unit: '个业务团队', tone: 'tone-warning' },
  { key: 'dataset', title: '数据集', value: counts.value.dataset, unit: '套测试数据模板', tone: 'tone-info' },
  { key: 'baseline', title: '版本基线', value: counts.value.baseline, unit: '套回归基线', tone: 'tone-cyan' }
])

function cardByKey(key) {
  return cards.value.find(c => c.key === key)
}

function goCard(card) {
  if (!card) return
  if (card.query) router.push({ path: card.path, query: card.query })
  else router.push(card.path)
}

function loadProjectState() {
  try {
    const saved = localStorage.getItem(PROJECT_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      if (parsed?.id) currentProjectId.value = parsed.id
    }
    const list = localStorage.getItem(PROJECT_LIST_KEY)
    if (list) localProjects.value = JSON.parse(list) || []
  } catch {
    currentProjectId.value = 'default'
  }
}

function persistProject() {
  const p = projectOptions.value.find(x => x.id === currentProjectId.value)
  localStorage.setItem(PROJECT_KEY, JSON.stringify({
    id: currentProjectId.value,
    name: p?.name || currentProjectName.value,
    teamId: p?.teamId || null
  }))
}

async function onProjectChange() {
  persistProject()
  await loadAll()
  ElMessage.success(`已切换至「${currentProjectName.value}」，资源统计已刷新`)
}

async function loadAll() {
  statsLoading.value = true
  try {
    const tasks = [
      envApi.list().then(r => { envs.value = r.data || [] }).catch(() => { envs.value = [] }),
      appPackageApi.list().then(r => { packages.value = r.data || [] }).catch(() => { packages.value = [] }),
      datasetApi.list().then(r => { datasets.value = r.data || [] }).catch(() => { datasets.value = [] }),
      baselineApi.list().then(r => { baselines.value = r.data || [] }).catch(() => { baselines.value = [] })
    ]
    if (userStore.isAdmin) {
      tasks.push(teamApi.list().then(r => { teams.value = r.data || [] }).catch(() => { teams.value = [] }))
    } else {
      teams.value = []
    }
    await Promise.all(tasks)
  } finally {
    statsLoading.value = false
  }
}

function openQuick(key) {
  if (key === 'env') {
    showEnvDialog.value = true
  } else if (key === 'pkg') {
    showPkgDialog.value = true
  } else if (key === 'team') {
    if (!userStore.isAdmin) { ElMessage.warning('仅管理员可新建团队'); return }
    showTeamDialog.value = true
  } else if (key === 'dataset') {
    showDatasetDialog.value = true
  } else if (key === 'baseline') {
    showBaselineDialog.value = true
  }
}

onMounted(async () => {
  loadProjectState()
  await loadAll()
  // 若当前选中项已不存在，回退默认
  if (!projectOptions.value.some(p => p.id === currentProjectId.value)) {
    currentProjectId.value = 'default'
  }
  persistProject()
})
</script>

<style scoped>
.project-hub-page :deep(.page-header__info h2) {
  font-size: 24px;
  font-weight: 700;
}
.project-switch {
  display: flex;
  align-items: center;
  gap: 10px;
}
.switch-label {
  font-size: 13px;
  color: var(--atp-text-secondary);
  white-space: nowrap;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.stat-card {
  padding: 16px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.stat-card.tone-primary { background: #eff6ff; }
.stat-card.tone-success { background: #ecfdf5; }
.stat-card.tone-warning { background: #fffbeb; }
.stat-card.tone-info { background: #eef2ff; }
.stat-card.tone-cyan { background: #ecfeff; }
.stat-label-top {
  font-size: 13px;
  color: var(--atp-text-secondary);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--atp-text);
}

.hub-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 8px;
}
.hub-card {
  margin-bottom: 0;
  min-height: 220px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.hub-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.1);
}
.hub-card-inner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
  height: 100%;
}
.hub-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 4px;
}
.hub-icon.primary { background: linear-gradient(135deg, #38bdf8, #0891b2); }
.hub-icon.success { background: linear-gradient(135deg, #34d399, #059669); }
.hub-icon.warning { background: linear-gradient(135deg, #fbbf24, #d97706); }
.hub-icon.info { background: linear-gradient(135deg, #818cf8, #4f46e5); }
.hub-icon.cyan { background: linear-gradient(135deg, #67e8f9, #0891b2); }
.hub-card-inner h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}
.hub-desc {
  margin: 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
  min-height: 40px;
  flex: 1;
}
.hub-count {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}
.hub-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}
.btn-quick {
  --el-button-bg-color: #fff;
  --el-button-border-color: #cbd5e1;
  --el-button-text-color: #334155;
}
.btn-quick.quick-primary { --el-button-border-color: #67e8f9; --el-button-text-color: #0e7490; }
.btn-quick.quick-success { --el-button-border-color: #6ee7b7; --el-button-text-color: #047857; }
.btn-quick.quick-warning { --el-button-border-color: #fcd34d; --el-button-text-color: #b45309; }
.btn-quick.quick-info { --el-button-border-color: #a5b4fc; --el-button-text-color: #4338ca; }
.btn-quick.quick-cyan { --el-button-border-color: #67e8f9; --el-button-text-color: #0e7490; }

.guide-bar {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 20px;
  margin-top: 20px;
  padding: 20px 22px;
  background: #f8fafc;
  border-radius: 14px;
  border: 1px solid var(--atp-border-neutral);
}
.guide-left h4,
.guide-right h4 {
  margin: 0 0 10px;
  font-size: 14px;
}
.guide-left ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.85;
}
.jump-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.jump-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

@media (max-width: 1200px) {
  .stats-row,
  .hub-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 800px) {
  .stats-row,
  .hub-grid { grid-template-columns: repeat(2, 1fr); }
  .guide-bar { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .stats-row,
  .hub-grid { grid-template-columns: 1fr; }
}
</style>
