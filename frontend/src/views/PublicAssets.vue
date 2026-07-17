<template>
  <div class="page-container public-assets-page">
    <PageHeader title="公共组件" subtitle="全局复用步骤、环境变量、控件元素定位库，搭建自动化复用积木">
      <template #actions>
        <div class="header-actions">
          <el-button class="btn-muted" :loading="exporting" @click="exportAll">批量导出全部组件</el-button>
          <el-button class="btn-muted" @click="triggerImport">批量导入组件</el-button>
          <el-button type="primary" plain @click="showGuide = true">组件使用教程</el-button>
          <input ref="importInput" type="file" accept="application/json,.json" class="hidden-file" @change="onImportFile" />
        </div>
      </template>
    </PageHeader>

    <!-- 模块 2：统计卡片 -->
    <div class="stats-row" v-loading="statsLoading">
      <div class="stat-card tone-primary" @click="goCard(cardByKey('steps'))">
        <div class="stat-icon"><el-icon :size="20"><Connection /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.steps }}<span class="stat-unit">套</span></div>
          <div class="stat-label">公共步骤总数</div>
        </div>
      </div>
      <div class="stat-card tone-warning" @click="goCard(cardByKey('params'))">
        <div class="stat-icon"><el-icon :size="20"><SetUp /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.params }}<span class="stat-unit">条</span></div>
          <div class="stat-label">全局参数总数</div>
        </div>
      </div>
      <div class="stat-card tone-success" @click="goCard(cardByKey('controls'))">
        <div class="stat-icon"><el-icon :size="20"><Grid /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.controls }}<span class="stat-unit">个</span></div>
          <div class="stat-label">存储控件元素</div>
        </div>
      </div>
      <div class="stat-card tone-accent" @click="goCard(cardByKey('picker'))">
        <div class="stat-icon"><el-icon :size="20"><Aim /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.picks }}<span class="stat-unit">条</span></div>
          <div class="stat-label">历史拾取记录</div>
        </div>
      </div>
    </div>

    <!-- 模块 3：四大功能卡片 -->
    <el-row :gutter="16" class="hub-row">
      <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <AppCard class="hub-card" :class="`hub-${card.tone}`" :hover="false">
          <div class="hub-card-inner">
            <div class="hub-icon" :class="card.tone">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <h3>{{ card.title }}</h3>
            <p class="hub-desc">{{ card.desc }}</p>
            <p class="hub-count">{{ card.countText }}</p>
            <div class="hub-actions">
              <el-button type="primary" link @click="goCard(card)">进入</el-button>
              <el-button size="small" class="btn-quick" :class="`quick-${card.tone}`" @click.stop="card.onQuick()">
                {{ card.quickLabel }}
              </el-button>
            </div>
          </div>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 4：底部指引 -->
    <section class="guide-bar">
      <div class="guide-left">
        <h4>组件场景使用指引</h4>
        <ul>
          <li><strong>公共步骤：</strong>登录、初始化、清理缓存等重复操作统一封装，避免每条用例重复编写</li>
          <li><strong>全局参数：</strong>多环境切换地址、账号、版本号统一管理，一键切换执行环境</li>
          <li><strong>元素定位库：</strong>页面改版控件 ID 变动时，仅修改库内定位，无需逐条修改用例</li>
          <li><strong>控件拾取：</strong>投屏可视化抓取控件，一键存入定位库，快速复用至用例步骤</li>
        </ul>
      </div>
      <div class="guide-right">
        <h4>跨页面快捷跳转</h4>
        <div class="jump-btns">
          <el-button @click="$router.push('/cases')">前往测试用例</el-button>
          <el-button @click="$router.push('/suites')">前往测试套件</el-button>
          <el-button type="primary" plain @click="$router.push('/devices')">设备管理</el-button>
        </div>
        <p class="jump-hint">编辑用例时引用公共组件 · 套件钩子调用公共步骤 · 设备投屏后进入控件拾取</p>
      </div>
    </section>

    <!-- 教程 -->
    <el-drawer v-model="showGuide" title="组件使用教程" size="460px">
      <div class="tutorial">
        <div class="tutorial-item">
          <div class="t-icon primary"><el-icon><Connection /></el-icon></div>
          <div>
            <h5>公共步骤</h5>
            <p>把登录、初始化、清理缓存等重复流程封装成积木，在用例与套件钩子中一键调用，减少重复编写。</p>
          </div>
        </div>
        <div class="tutorial-item">
          <div class="t-icon warning"><el-icon><SetUp /></el-icon></div>
          <div>
            <h5>全局参数</h5>
            <p>管理平台 / 环境级变量（地址、账号、版本号等），执行时自动注入，切换环境无需改脚本。</p>
          </div>
        </div>
        <div class="tutorial-item">
          <div class="t-icon success"><el-icon><Grid /></el-icon></div>
          <div>
            <h5>元素定位库</h5>
            <p>统一维护页面控件定位链。改版后只需更新库内定位，相关用例自动生效。</p>
          </div>
        </div>
        <div class="tutorial-item">
          <div class="t-icon info"><el-icon><Aim /></el-icon></div>
          <div>
            <h5>控件拾取</h5>
            <p>连接设备投屏，点击画面即可抓取控件并写入定位库，快速用于用例步骤编排。</p>
          </div>
        </div>
        <el-divider />
        <p class="tutorial-tip">建议路径：控件拾取 → 存入定位库 → 公共步骤封装 → 用例/套件引用。</p>
      </div>
    </el-drawer>

    <!-- 快捷：新建步骤 -->
    <el-dialog v-model="showStepDialog" title="新建步骤" width="560px" destroy-on-close>
      <el-form :model="stepForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="stepForm.name" placeholder="如：登录流程" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="stepForm.description" /></el-form-item>
        <el-form-item label="步骤 JSON">
          <el-input v-model="stepForm.steps_content" type="textarea" :rows="6" placeholder='{"steps":[{"type":"wait","seconds":2}]}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStepDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStep">保存</el-button>
      </template>
    </el-dialog>

    <!-- 快捷：新增参数 -->
    <el-dialog v-model="showParamDialog" title="新增参数" width="520px" destroy-on-close>
      <el-form :model="paramForm" label-width="90px">
        <el-form-item label="参数键" required><el-input v-model="paramForm.param_key" placeholder="API_HOST" /></el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="paramForm.scope" style="width:100%">
            <el-option label="平台级" value="platform" />
            <el-option label="环境级" value="env" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="paramForm.scope === 'env'" label="环境ID">
          <el-input v-model="paramForm.env_id" placeholder="环境 ID" />
        </el-form-item>
        <el-form-item label="值"><el-input v-model="paramForm.param_value" /></el-form-item>
        <el-form-item label="敏感"><el-switch v-model="paramForm.sensitive" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="paramForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showParamDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveParam">保存</el-button>
      </template>
    </el-dialog>

    <!-- 快捷：新建控件 -->
    <el-dialog v-model="showControlDialog" title="新建控件" width="560px" destroy-on-close>
      <el-form :model="controlForm" label-width="100px">
        <el-form-item label="应用包名" required><el-input v-model="controlForm.app_package" /></el-form-item>
        <el-form-item label="页面名称"><el-input v-model="controlForm.page_name" /></el-form-item>
        <el-form-item label="控件名称" required><el-input v-model="controlForm.element_name" /></el-form-item>
        <el-form-item label="平台">
          <el-select v-model="controlForm.platform" style="width:100%">
            <el-option label="安卓" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="双端" value="both" />
          </el-select>
        </el-form-item>
        <el-form-item label="定位类型" required>
          <el-select v-model="controlForm.locator_type" style="width:100%">
            <el-option label="ID" value="id" />
            <el-option label="XPath" value="xpath" />
            <el-option label="Accessibility" value="accessibility" />
            <el-option label="AI" value="ai" />
            <el-option label="Image" value="image" />
          </el-select>
        </el-form-item>
        <el-form-item label="定位表达式" required>
          <el-input v-model="controlForm.locator_value" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showControlDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveControl">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { commonStepApi, globalParamApi, controlApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const PICK_HISTORY_KEY = 'atp_element_pick_history'

const router = useRouter()
const userStore = useUserStore()

const statsLoading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const showGuide = ref(false)
const importInput = ref(null)

const stats = reactive({ steps: 0, params: 0, controls: 0, picks: 0 })
const cache = reactive({ steps: [], params: [], controls: [] })

const showStepDialog = ref(false)
const showParamDialog = ref(false)
const showControlDialog = ref(false)

const stepForm = reactive({ name: '', description: '', steps_content: '{"steps":[]}' })
const paramForm = reactive({
  param_key: '', scope: 'platform', env_id: '', param_value: '', sensitive: false, description: ''
})
const controlForm = reactive({
  app_package: '', page_name: '', element_name: '', platform: 'android',
  locator_type: 'id', locator_value: ''
})

const cards = computed(() => {
  const list = [
    {
      key: 'steps',
      title: '公共步骤',
      desc: '封装可复用操作积木，全项目用例、套件钩子共享调用',
      icon: 'Connection',
      path: '/platform-config',
      query: { tab: 'steps' },
      tone: 'primary',
      countText: `当前共 ${stats.steps} 套通用步骤`,
      quickLabel: '新建步骤',
      onQuick: () => openCreateStep()
    },
    {
      key: 'params',
      title: '全局参数',
      desc: '平台 / 环境级变量，执行自动化时自动注入用例',
      icon: 'SetUp',
      path: '/platform-config',
      query: { tab: 'global-params' },
      tone: 'warning',
      adminOnly: true,
      countText: `当前共 ${stats.params} 条环境参数`,
      quickLabel: '新增参数',
      onQuick: () => openCreateParam()
    },
    {
      key: 'controls',
      title: '元素定位库',
      desc: '统一维护页面控件，解决迭代后控件定位失效问题',
      icon: 'Grid',
      path: '/controls',
      tone: 'success',
      countText: `当前共 ${stats.controls} 个存储控件元素`,
      quickLabel: '新建控件',
      onQuick: () => openCreateControl()
    },
    {
      key: 'picker',
      title: '控件拾取',
      desc: '投屏实时抓取页面控件，自动写入定位链存入控件库',
      icon: 'Aim',
      path: '/element-picker',
      tone: 'info',
      countText: `历史共 ${stats.picks} 次拾取记录`,
      quickLabel: '一键拾取',
      onQuick: () => router.push('/element-picker')
    }
  ]
  return list.filter(c => !c.adminOnly || userStore.isAdmin)
})

function cardByKey(key) {
  return cards.value.find(c => c.key === key)
}

function goCard(card) {
  if (!card) return
  if (card.query) router.push({ path: card.path, query: card.query })
  else router.push(card.path)
}

function loadPickHistoryCount() {
  try {
    const raw = JSON.parse(localStorage.getItem(PICK_HISTORY_KEY) || '[]')
    return Array.isArray(raw) ? raw.length : 0
  } catch {
    return 0
  }
}

async function loadStats() {
  statsLoading.value = true
  try {
    const [stepsRes, paramsRes, controlsRes] = await Promise.allSettled([
      commonStepApi.list(),
      userStore.isAdmin ? globalParamApi.list() : Promise.resolve({ data: [] }),
      controlApi.listPool({ page: 1, page_size: 1 })
    ])
    if (stepsRes.status === 'fulfilled') {
      cache.steps = stepsRes.value.data || []
      stats.steps = cache.steps.length
    }
    if (paramsRes.status === 'fulfilled') {
      cache.params = paramsRes.value.data || []
      stats.params = cache.params.length
    }
    if (controlsRes.status === 'fulfilled') {
      stats.controls = controlsRes.value.data?.total ?? (controlsRes.value.data?.list || []).length
      // 导出时再拉全量
    }
    stats.picks = loadPickHistoryCount()
  } finally {
    statsLoading.value = false
  }
}

function openCreateStep() {
  Object.assign(stepForm, { name: '', description: '', steps_content: '{"steps":[]}' })
  showStepDialog.value = true
}

function openCreateParam() {
  if (!userStore.isAdmin) {
    ElMessage.warning('仅管理员可新增全局参数')
    return
  }
  Object.assign(paramForm, {
    param_key: '', scope: 'platform', env_id: '', param_value: '', sensitive: false, description: ''
  })
  showParamDialog.value = true
}

function openCreateControl() {
  Object.assign(controlForm, {
    app_package: '', page_name: '', element_name: '', platform: 'android',
    locator_type: 'id', locator_value: ''
  })
  showControlDialog.value = true
}

async function saveStep() {
  if (!stepForm.name?.trim()) {
    ElMessage.warning('请填写步骤名称')
    return
  }
  saving.value = true
  try {
    await commonStepApi.create({
      name: stepForm.name.trim(),
      description: stepForm.description,
      steps_content: stepForm.steps_content || '{"steps":[]}'
    })
    ElMessage.success('公共步骤已创建')
    showStepDialog.value = false
    await loadStats()
  } finally {
    saving.value = false
  }
}

async function saveParam() {
  if (!paramForm.param_key?.trim()) {
    ElMessage.warning('请填写参数键')
    return
  }
  saving.value = true
  try {
    const payload = {
      param_key: paramForm.param_key.trim(),
      scope: paramForm.scope,
      sensitive: paramForm.sensitive,
      description: paramForm.description
    }
    if (paramForm.scope === 'env' && paramForm.env_id) payload.env_id = Number(paramForm.env_id)
    if (paramForm.param_value) payload.param_value = paramForm.param_value
    await globalParamApi.create(payload)
    ElMessage.success('全局参数已创建')
    showParamDialog.value = false
    await loadStats()
  } finally {
    saving.value = false
  }
}

async function saveControl() {
  if (!controlForm.app_package?.trim() || !controlForm.element_name?.trim() || !controlForm.locator_value?.trim()) {
    ElMessage.warning('请完善包名、控件名称与定位表达式')
    return
  }
  saving.value = true
  try {
    await controlApi.createPool({ ...controlForm })
    ElMessage.success('控件已创建')
    showControlDialog.value = false
    await loadStats()
  } finally {
    saving.value = false
  }
}

async function exportAll() {
  exporting.value = true
  try {
    let controls = []
    try {
      const res = await controlApi.listPool({ page: 1, page_size: 500 })
      controls = res.data?.list || []
    } catch { /* ignore */ }
    let steps = cache.steps
    let params = cache.params
    try { steps = (await commonStepApi.list()).data || [] } catch { /* ignore */ }
    if (userStore.isAdmin) {
      try { params = (await globalParamApi.list()).data || [] } catch { /* ignore */ }
    }
    const payload = {
      version: 1,
      exported_at: new Date().toISOString(),
      common_steps: steps,
      global_params: params.map(p => ({
        ...p,
        param_value: p.sensitive ? '' : p.param_value
      })),
      controls,
      pick_history: (() => {
        try { return JSON.parse(localStorage.getItem(PICK_HISTORY_KEY) || '[]') } catch { return [] }
      })()
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `公共组件备份_${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出组件资源备份')
  } finally {
    exporting.value = false
  }
}

function triggerImport() {
  importInput.value?.click()
}

async function onImportFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    await ElMessageBox.confirm(
      '将导入公共步骤、全局参数与控件资源（敏感参数值若为空需手动补全）。是否继续？',
      '批量导入组件',
      { type: 'warning' }
    )
    saving.value = true
    let ok = 0
    for (const s of data.common_steps || []) {
      try {
        await commonStepApi.create({
          name: s.name,
          description: s.description || '',
          steps_content: s.steps_content || '{"steps":[]}'
        })
        ok++
      } catch { /* skip dup */ }
    }
    if (userStore.isAdmin) {
      for (const p of data.global_params || []) {
        try {
          await globalParamApi.create({
            param_key: p.param_key,
            scope: p.scope || 'platform',
            env_id: p.env_id,
            param_value: p.param_value || '',
            sensitive: !!p.sensitive,
            description: p.description || ''
          })
          ok++
        } catch { /* skip */ }
      }
    }
    for (const c of data.controls || []) {
      try {
        await controlApi.createPool({
          app_package: c.app_package,
          page_name: c.page_name || '',
          element_name: c.element_name,
          platform: c.platform || 'android',
          locator_type: c.locator_type || 'id',
          locator_value: c.locator_value || c.primary_locator || ''
        })
        ok++
      } catch { /* skip */ }
    }
    if (Array.isArray(data.pick_history) && data.pick_history.length) {
      try {
        const cur = JSON.parse(localStorage.getItem(PICK_HISTORY_KEY) || '[]')
        const merged = [...data.pick_history, ...(Array.isArray(cur) ? cur : [])].slice(0, 100)
        localStorage.setItem(PICK_HISTORY_KEY, JSON.stringify(merged))
      } catch { /* ignore */ }
    }
    ElMessage.success(`导入完成，成功写入 ${ok} 条资源`)
    await loadStats()
  } catch (err) {
    if (err !== 'cancel' && err?.toString?.() !== 'cancel') {
      ElMessage.error('导入失败：文件格式无效或已取消')
    }
  } finally {
    saving.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.public-assets-page {
  background:
    radial-gradient(ellipse 80% 40% at 10% -10%, rgba(2, 132, 199, 0.08), transparent 55%),
    radial-gradient(ellipse 60% 35% at 90% 0%, rgba(99, 102, 241, 0.07), transparent 50%),
    var(--atp-bg-page);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.btn-muted {
  --el-button-bg-color: #fff;
  --el-button-border-color: var(--atp-brand-200, #bae6fd);
  --el-button-text-color: var(--atp-primary, #0284c7);
  --el-button-hover-bg-color: var(--atp-brand-50, #f0f9ff);
  --el-button-hover-border-color: var(--atp-primary, #0284c7);
}
.hidden-file { display: none; }

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 14px;
  padding: 16px 18px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
}
.stat-card.tone-primary {
  background: linear-gradient(145deg, #e0f2fe 0%, #f0f9ff 100%);
}
.stat-card.tone-primary::before { background: linear-gradient(90deg, #38bdf8, #0284c7); }
.stat-card.tone-primary .stat-icon {
  background: rgba(2, 132, 199, 0.16);
  color: var(--atp-primary, #0284c7);
}
.stat-card.tone-warning {
  background: linear-gradient(145deg, #fef3c7 0%, #fffbeb 100%);
}
.stat-card.tone-warning::before { background: linear-gradient(90deg, #fbbf24, #d97706); }
.stat-card.tone-warning .stat-icon {
  background: rgba(217, 119, 6, 0.16);
  color: #d97706;
}
.stat-card.tone-success {
  background: linear-gradient(145deg, #d1fae5 0%, #ecfdf5 100%);
}
.stat-card.tone-success::before { background: linear-gradient(90deg, #34d399, #059669); }
.stat-card.tone-success .stat-icon {
  background: rgba(16, 185, 129, 0.16);
  color: var(--atp-success, #10b981);
}
.stat-card.tone-accent {
  background: linear-gradient(145deg, #e0e7ff 0%, #eef2ff 100%);
}
.stat-card.tone-accent::before { background: linear-gradient(90deg, #818cf8, #4f46e5); }
.stat-card.tone-accent .stat-icon {
  background: rgba(99, 102, 241, 0.16);
  color: var(--atp-accent, #6366f1);
}
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--atp-text);
  line-height: 1.15;
}
.stat-unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--atp-text-muted, #94a3b8);
}
.stat-label {
  margin-top: 2px;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.hub-row { margin-bottom: 8px; }
.hub-card {
  margin-bottom: 16px;
  min-height: 228px;
  transition: transform 0.2s, box-shadow 0.2s;
  overflow: hidden;
}
.hub-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12) !important;
}
.hub-card :deep(.el-card__body) {
  height: 100%;
}
.hub-primary {
  background: linear-gradient(180deg, #f0f9ff 0%, #fff 48%) !important;
  box-shadow: 0 1px 3px rgba(2, 132, 199, 0.12);
}
.hub-primary :deep(.el-card__body) {
  border-top: 3px solid #38bdf8;
}
.hub-warning {
  background: linear-gradient(180deg, #fffbeb 0%, #fff 48%) !important;
  box-shadow: 0 1px 3px rgba(217, 119, 6, 0.12);
}
.hub-warning :deep(.el-card__body) {
  border-top: 3px solid #fbbf24;
}
.hub-success {
  background: linear-gradient(180deg, #ecfdf5 0%, #fff 48%) !important;
  box-shadow: 0 1px 3px rgba(16, 185, 129, 0.12);
}
.hub-success :deep(.el-card__body) {
  border-top: 3px solid #34d399;
}
.hub-info {
  background: linear-gradient(180deg, #eef2ff 0%, #fff 48%) !important;
  box-shadow: 0 1px 3px rgba(99, 102, 241, 0.12);
}
.hub-info :deep(.el-card__body) {
  border-top: 3px solid #818cf8;
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
  flex-shrink: 0;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.12);
}
.hub-icon.primary { background: linear-gradient(135deg, #38bdf8, #0891b2); }
.hub-icon.success { background: linear-gradient(135deg, #34d399, #059669); }
.hub-icon.warning { background: linear-gradient(135deg, #fbbf24, #d97706); }
.hub-icon.info { background: linear-gradient(135deg, #818cf8, #4f46e5); }
.hub-card-inner h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--atp-text);
}
.hub-desc {
  margin: 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.55;
  min-height: 40px;
  flex: 1;
}
.hub-count {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(226, 232, 240, 0.9);
  width: fit-content;
}
.hub-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.btn-quick {
  --el-button-bg-color: #fff;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
.quick-primary {
  --el-button-border-color: #7dd3fc;
  --el-button-text-color: var(--atp-primary, #0284c7);
  --el-button-hover-bg-color: #e0f2fe;
}
.quick-warning {
  --el-button-border-color: #fcd34d;
  --el-button-text-color: #d97706;
  --el-button-hover-bg-color: #fef3c7;
}
.quick-success {
  --el-button-border-color: #6ee7b7;
  --el-button-text-color: #059669;
  --el-button-hover-bg-color: #d1fae5;
}
.quick-info {
  --el-button-border-color: #a5b4fc;
  --el-button-text-color: #4f46e5;
  --el-button-hover-bg-color: #e0e7ff;
}

.guide-bar {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
  margin-top: 8px;
  padding: 22px 24px;
  border-radius: 16px;
  background:
    linear-gradient(135deg, rgba(224, 242, 254, 0.9) 0%, rgba(238, 242, 255, 0.85) 55%, rgba(236, 253, 245, 0.75) 100%);
  border: 1px solid rgba(186, 230, 253, 0.7);
  box-shadow: 0 4px 16px rgba(2, 132, 199, 0.06);
}
.guide-left h4,
.guide-right h4 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--atp-brand-700, #075985);
}
.guide-left ul {
  margin: 0;
  padding-left: 18px;
  color: var(--atp-text-secondary);
  font-size: 13px;
  line-height: 1.75;
}
.guide-left li { margin-bottom: 6px; }
.guide-left strong { color: var(--atp-text); }
.guide-right {
  background: rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  padding: 16px 18px;
  border: 1px solid rgba(255, 255, 255, 0.9);
}
.jump-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.jump-hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.tutorial-item {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.t-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.t-icon.primary { background: linear-gradient(135deg, #38bdf8, #0891b2); }
.t-icon.success { background: linear-gradient(135deg, #34d399, #059669); }
.t-icon.warning { background: linear-gradient(135deg, #fbbf24, #d97706); }
.t-icon.info { background: linear-gradient(135deg, #818cf8, #4f46e5); }
.tutorial-item h5 { margin: 0 0 4px; font-size: 14px; }
.tutorial-item p {
  margin: 0;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.55;
}
.tutorial-tip {
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .guide-bar { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .stats-row { grid-template-columns: 1fr; }
}
</style>
