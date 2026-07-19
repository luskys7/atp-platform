<template>
  <el-dialog
    :model-value="modelValue"
    width="760px"
    top="4vh"
    class="schedule-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑定时回归任务' : '新建定时回归任务' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingOptions">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础任务信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="区分不同周期自动化回归任务，用于报表、任务列表快速识别">任务名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="例：每日凌晨 2 点全量回归、每小时冒烟巡检"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">启用开关</div>
          <div class="enable-row">
            <el-switch v-model="form.enabled" />
            <span class="enable-desc">开启 = 按周期自动执行；关闭 = 暂停调度，不删除任务配置</span>
          </div>
          <div v-if="form.enabled" class="field-ok">任务已启用，到达周期自动发起回归</div>
          <div v-else class="field-muted">任务已暂停，不会自动执行</div>
        </div>
      </section>

      <!-- 分区 2：绑定资源 -->
      <section class="section-card">
        <div class="section-title">绑定回归资源</div>

        <div class="field-block">
          <div class="field-label-row">
            <div class="field-label" style="margin:0">
              <span class="req">*</span>
              <span class="tip-label" title="定时任务会周期性自动执行选中的全套测试套件">绑定回归套件</span>
            </div>
            <el-button type="primary" link size="small" @click="goSuites">前往套件管理</el-button>
          </div>
          <el-select
            v-model="form.suite_id"
            filterable
            clearable
            placeholder="请选择回归套件"
            style="width:100%"
            :class="{ 'is-error-select': !!errors.suite_id }"
            @change="errors.suite_id = ''"
          >
            <el-option
              v-for="s in suites"
              :key="s.id"
              :label="`${s.name}（ID: ${s.id}）`"
              :value="s.id"
            />
          </el-select>
          <div v-if="errors.suite_id" class="field-error">{{ errors.suite_id }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">配套执行环境</div>
          <el-select
            v-model="form.env_id"
            filterable
            clearable
            placeholder="下拉选择项目内已创建环境（选填）"
            style="width:100%"
          >
            <el-option
              v-for="e in envs"
              :key="e.id"
              :label="envLabel(e)"
              :value="e.id"
            />
          </el-select>
          <div class="field-hint">执行时自动注入该环境域名与变量</div>
        </div>

        <div class="field-block">
          <div class="field-label">配套安装包</div>
          <el-select
            v-model="form.app_package_id"
            filterable
            clearable
            placeholder="下拉选择仓库内安装包（选填）"
            style="width:100%"
          >
            <el-option
              v-for="p in packages"
              :key="p.id"
              :label="pkgLabel(p)"
              :value="p.id"
            />
          </el-select>
          <div class="field-hint">定时执行时自动下发对应版本包至测试设备</div>
        </div>
      </section>

      <!-- 分区 3：调度周期 -->
      <section class="section-card">
        <div class="section-head">
          <div class="section-title" style="margin:0">调度周期表达式（Cron）</div>
          <div class="mode-switch">
            <el-radio-group v-model="cronMode" size="small" @change="onCronModeChange">
              <el-radio-button value="visual">可视化周期选择（推荐）</el-radio-button>
              <el-radio-button value="raw">手动输入表达式</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click" @command="applyCronTemplate">
              <el-button size="small" class="btn-tpl">
                常用周期模板
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="t in CRON_TEMPLATES"
                    :key="t.key"
                    :command="t.key"
                  >
                    {{ t.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div v-if="cronMode === 'visual'" class="visual-cron">
          <div class="preset-row">
            <span class="preset-label">周期类型</span>
            <el-radio-group v-model="visual.preset" size="small" @change="syncCronFromVisual">
              <el-radio-button value="daily">每日</el-radio-button>
              <el-radio-button value="hourly">每小时</el-radio-button>
              <el-radio-button value="weekly">每周</el-radio-button>
              <el-radio-button value="weekdays">工作日</el-radio-button>
              <el-radio-button value="custom">自定义</el-radio-button>
            </el-radio-group>
          </div>
          <div class="visual-grid">
            <div class="visual-item">
              <span>分</span>
              <el-select v-model="visual.minute" style="width:100%" @change="syncCronFromVisual">
                <el-option v-for="n in 60" :key="'m'+n" :label="String(n - 1).padStart(2, '0')" :value="n - 1" />
              </el-select>
            </div>
            <div class="visual-item" v-if="visual.preset !== 'hourly'">
              <span>时</span>
              <el-select v-model="visual.hour" style="width:100%" @change="syncCronFromVisual">
                <el-option v-for="n in 24" :key="'h'+n" :label="String(n - 1).padStart(2, '0')" :value="n - 1" />
              </el-select>
            </div>
            <div class="visual-item" v-if="visual.preset === 'weekly' || visual.preset === 'custom'">
              <span>周</span>
              <el-select
                v-model="visual.weekday"
                :multiple="visual.preset === 'custom'"
                collapse-tags
                style="width:100%"
                @change="syncCronFromVisual"
              >
                <el-option v-for="w in WEEKDAYS" :key="w.v" :label="w.l" :value="w.v" />
              </el-select>
            </div>
            <div class="visual-item" v-if="visual.preset === 'custom'">
              <span>日</span>
              <el-select v-model="visual.dayMode" style="width:100%" @change="syncCronFromVisual">
                <el-option label="每天（*）" value="every" />
                <el-option label="指定日期" value="day" />
                <el-option label="不指定（?）" value="none" />
              </el-select>
            </div>
            <div class="visual-item" v-if="visual.preset === 'custom' && visual.dayMode === 'day'">
              <span>日期</span>
              <el-select v-model="visual.day" style="width:100%" @change="syncCronFromVisual">
                <el-option v-for="n in 31" :key="'d'+n" :label="String(n)" :value="n" />
              </el-select>
            </div>
          </div>
          <div class="cron-preview-box">
            已生成表达式：<code>{{ form.cron_expression }}</code>
            <div class="cron-cn">{{ cronChinese }}</div>
          </div>
        </div>

        <div v-else class="raw-cron">
          <el-input
            v-model="form.cron_expression"
            placeholder="秒 分 时 日 月 星期，例：每日凌晨 2 点 → 0 0 2 * * ?"
            :class="{ 'is-error-input': !!errors.cron }"
            @input="onCronRawInput"
          />
          <div v-if="errors.cron" class="field-error">{{ errors.cron }}</div>
          <div v-else class="field-hint">6 位标准格式：秒 分 时 日 月 星期</div>
        </div>

        <div class="next-runs" v-if="nextRuns.length">
          <div class="next-title">最近 3 次执行时间预览</div>
          <ol>
            <li v-for="(t, i) in nextRuns" :key="i">{{ t }}</li>
          </ol>
        </div>
        <div v-else-if="previewLoading" class="field-hint">正在解析执行时间…</div>
      </section>

      <!-- 分区 5：说明 -->
      <section class="guide-panel">
        <div class="guide-title">定时任务使用说明</div>

        <div class="guide-block">
          <div class="guide-h">1. Cron 表达式基础语法（6 位标准格式）</div>
          <p>格式：<code>秒 分 时 日 月 星期</code></p>
          <ul>
            <li>秒：0-59　分：0-59　时：0-23</li>
            <li>日：1-31　月：1-12　星期：1-7（1 = 周日，7 = 周六）</li>
          </ul>
          <p>通配符：<code>*</code> 每单位周期；<code>?</code> 不指定（日 / 星期互斥）；<code>,</code> 多时间点；<code>-</code> 连续时间段</p>
        </div>

        <div class="guide-block">
          <div class="guide-h">2. 常用示例对照</div>
          <ul>
            <li><code>0 0 2 * * ?</code> → 每日凌晨 2 点执行</li>
            <li><code>0 0 */1 * * ?</code> → 每小时整点执行冒烟用例</li>
            <li><code>0 30 18 ? * 2-6</code> → 每周一至周五 18:30 执行下班回归</li>
          </ul>
        </div>

        <div class="guide-block">
          <div class="guide-h">3. 任务生效规则</div>
          <ul>
            <li>仅开关为「启用」状态时，到达周期才会自动创建测试任务</li>
            <li>执行资源（套件、环境、安装包）删除后，定时任务会自动置为失效状态</li>
            <li>同一时间多任务并行会占用设备池，可在任务内限制并发设备数量</li>
          </ul>
        </div>

        <div class="guide-block">
          <div class="guide-h">4. 业务适用场景</div>
          <ul>
            <li>每日凌晨版本全量回归</li>
            <li>每小时线上冒烟巡检</li>
            <li>每周日线上完整业务回归</li>
            <li>工作日下班自动化验收</li>
          </ul>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-dropdown trigger="click" @command="applyCronTemplate">
            <el-button class="btn-aux">
              填充常用周期模板
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="t in CRON_TEMPLATES"
                  :key="'f-'+t.key"
                  :command="t.key"
                >
                  {{ t.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Close, ArrowDown } from '@element-plus/icons-vue'
import { scheduleApi, suiteApi, envApi, appPackageApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const router = useRouter()

const WEEKDAYS = [
  { v: 1, l: '周日' },
  { v: 2, l: '周一' },
  { v: 3, l: '周二' },
  { v: 4, l: '周三' },
  { v: 5, l: '周四' },
  { v: 6, l: '周五' },
  { v: 7, l: '周六' }
]

const CRON_TEMPLATES = [
  { key: 'daily2', label: '每日凌晨 2 点', cron: '0 0 2 * * ?', preset: 'daily', hour: 2, minute: 0 },
  { key: 'hourly', label: '每小时巡检', cron: '0 0 * * * ?', preset: 'hourly', hour: 0, minute: 0 },
  { key: 'sunday', label: '每周日全量回归', cron: '0 0 2 ? * 1', preset: 'weekly', hour: 2, minute: 0, weekday: 1 },
  { key: 'weekdays', label: '工作日夜间回归', cron: '0 30 22 ? * 2-6', preset: 'weekdays', hour: 22, minute: 30 }
]

const saving = ref(false)
const loadingOptions = ref(false)
const previewLoading = ref(false)
const cronMode = ref('visual')
const snapshot = ref('')
const nextRuns = ref([])
const suites = ref([])
const envs = ref([])
const packages = ref([])
let previewTimer = null

const form = reactive(blankForm())
const errors = reactive({ name: '', suite_id: '', cron: '' })
const visual = reactive({
  preset: 'daily',
  minute: 0,
  hour: 2,
  weekday: 1,
  dayMode: 'every',
  day: 1
})

const isEdit = computed(() => !!form.id)

const cronChinese = computed(() => describeCron(form.cron_expression, visual))

function blankForm() {
  return {
    id: null,
    name: '',
    enabled: true,
    suite_id: null,
    env_id: null,
    app_package_id: null,
    cron_expression: '0 0 2 * * ?'
  }
}

function clearErrors() {
  errors.name = ''
  errors.suite_id = ''
  errors.cron = ''
}

function takeSnapshot() {
  return JSON.stringify({ form: { ...form }, cronMode: cronMode.value, visual: { ...visual } })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  errors.name = form.name?.trim() ? '' : '请填写定时任务业务名称'
}

function envLabel(e) {
  const typeMap = { test: '测试', staging: '预发', gray: '预发', prod: '生产' }
  const t = typeMap[e.env_type] || e.env_type || ''
  return t ? `${e.name}（${t}）` : e.name
}

function pkgLabel(p) {
  const ver = p.version_name || p.version_code || '-'
  return `${p.name} · ${ver}`
}

function describeCron(expr, v) {
  if (v.preset === 'hourly') return `每小时的第 ${String(v.minute).padStart(2, '0')} 分执行`
  if (v.preset === 'daily') return `每日 ${String(v.hour).padStart(2, '0')}:${String(v.minute).padStart(2, '0')} 执行`
  if (v.preset === 'weekly') {
    const w = WEEKDAYS.find(x => x.v === v.weekday)?.l || '指定日'
    return `每${w} ${String(v.hour).padStart(2, '0')}:${String(v.minute).padStart(2, '0')} 执行`
  }
  if (v.preset === 'weekdays') return `每周一至周五 ${String(v.hour).padStart(2, '0')}:${String(v.minute).padStart(2, '0')} 执行`
  return `表达式：${expr}`
}

function buildCronFromVisual() {
  const m = visual.minute
  const h = visual.hour
  switch (visual.preset) {
    case 'hourly':
      return `0 ${m} * * * ?`
    case 'daily':
      return `0 ${m} ${h} * * ?`
    case 'weekly':
      return `0 ${m} ${h} ? * ${visual.weekday}`
    case 'weekdays':
      return `0 ${m} ${h} ? * 2-6`
    case 'custom': {
      let day = '*'
      let week = '?'
      if (visual.dayMode === 'day') {
        day = String(visual.day)
        week = '?'
      } else if (visual.dayMode === 'none') {
        day = '?'
        const wd = Array.isArray(visual.weekday) ? visual.weekday : [visual.weekday]
        week = wd.length ? [...wd].sort((a, b) => a - b).join(',') : '?'
        if (week === '?') day = '*'
      } else {
        day = '*'
        week = '?'
      }
      return `0 ${m} ${h} ${day} * ${week}`
    }
    default:
      return `0 ${m} ${h} * * ?`
  }
}

function syncCronFromVisual() {
  if (visual.preset === 'custom' && !Array.isArray(visual.weekday)) {
    visual.weekday = [visual.weekday || 2]
  }
  if (visual.preset === 'weekly' && Array.isArray(visual.weekday)) {
    visual.weekday = visual.weekday[0] || 1
  }
  form.cron_expression = buildCronFromVisual()
  errors.cron = ''
  schedulePreview()
}

function parseVisualFromCron(expr) {
  const tpl = CRON_TEMPLATES.find(t => t.cron === expr)
  if (tpl) {
    visual.preset = tpl.preset
    visual.hour = tpl.hour
    visual.minute = tpl.minute
    if (tpl.weekday != null) visual.weekday = tpl.weekday
    return
  }
  const parts = String(expr || '').trim().split(/\s+/)
  if (parts.length !== 6) {
    visual.preset = 'custom'
    return
  }
  const [, minute, hour, day, , week] = parts
  visual.minute = Number(minute) || 0
  if (hour === '*') {
    visual.preset = 'hourly'
    return
  }
  visual.hour = Number(hour) || 0
  if (week === '2-6' && day === '?') {
    visual.preset = 'weekdays'
    return
  }
  if (/^\d$/.test(week) && day === '?') {
    visual.preset = 'weekly'
    visual.weekday = Number(week)
    return
  }
  if (day === '*' && week === '?') {
    visual.preset = 'daily'
    return
  }
  visual.preset = 'custom'
  visual.dayMode = day === '?' ? 'none' : (day === '*' ? 'every' : 'day')
  if (/^\d+$/.test(day)) visual.day = Number(day)
  if (week !== '?' && week !== '*') {
    visual.weekday = week.includes(',') || week.includes('-')
      ? week.split(/[,]/).flatMap(seg => {
        if (seg.includes('-')) {
          const [a, b] = seg.split('-').map(Number)
          const arr = []
          for (let i = a; i <= b; i++) arr.push(i)
          return arr
        }
        return [Number(seg)]
      }).filter(Boolean)
      : [Number(week)]
  }
}

function basicCronCheck(expr) {
  const v = String(expr || '').trim()
  if (!v) return '请填写调度周期表达式'
  const parts = v.split(/\s+/)
  if (parts.length !== 6) return 'Cron 需为 6 位：秒 分 时 日 月 星期'
  return ''
}

async function schedulePreview() {
  clearTimeout(previewTimer)
  const basic = basicCronCheck(form.cron_expression)
  if (basic) {
    errors.cron = basic
    nextRuns.value = []
    return
  }
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    try {
      const res = await scheduleApi.cronPreview(form.cron_expression.trim())
      nextRuns.value = res.data?.next_runs || []
      errors.cron = ''
    } catch (e) {
      nextRuns.value = []
      errors.cron = e?.response?.data?.message || 'Cron 表达式语法错误，请检查格式'
    } finally {
      previewLoading.value = false
    }
  }, 350)
}

function onCronRawInput() {
  schedulePreview()
}

function onCronModeChange(mode) {
  if (mode === 'visual') {
    const basic = basicCronCheck(form.cron_expression)
    if (basic) {
      ElMessage.warning('当前表达式有误，请先修正后再切换可视化')
      cronMode.value = 'raw'
      return
    }
    parseVisualFromCron(form.cron_expression)
    syncCronFromVisual()
  } else {
    schedulePreview()
  }
}

function applyCronTemplate(key) {
  const tpl = CRON_TEMPLATES.find(t => t.key === key)
  if (!tpl) return
  form.cron_expression = tpl.cron
  visual.preset = tpl.preset
  visual.hour = tpl.hour
  visual.minute = tpl.minute
  if (tpl.weekday != null) visual.weekday = tpl.weekday
  errors.cron = ''
  if (!form.name?.trim()) {
    form.name = tpl.label
    errors.name = ''
  }
  schedulePreview()
  ElMessage.success(`已填充「${tpl.label}」`)
}

async function loadOptions() {
  loadingOptions.value = true
  try {
    const [sRes, eRes, pRes] = await Promise.all([
      suiteApi.list().catch(() => ({ data: [] })),
      envApi.list().catch(() => ({ data: [] })),
      appPackageApi.list().catch(() => ({ data: [] }))
    ])
    suites.value = sRes.data || []
    envs.value = eRes.data || []
    packages.value = pRes.data || []
  } finally {
    loadingOptions.value = false
  }
}

function applyRow(row) {
  clearErrors()
  cronMode.value = 'visual'
  nextRuns.value = []
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      enabled: row.enabled !== false,
      suite_id: row.suite_id != null ? Number(row.suite_id) : null,
      env_id: row.env_id != null ? Number(row.env_id) : null,
      app_package_id: row.app_package_id != null ? Number(row.app_package_id) : null,
      cron_expression: row.cron_expression || '0 0 2 * * ?'
    })
  } else {
    Object.assign(form, blankForm())
  }
  parseVisualFromCron(form.cron_expression)
  syncCronFromVisual()
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  await loadOptions()
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前定时任务配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    done()
  } catch { /* stay */ }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前定时任务配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function leaveTo(path) {
  if (isDirty()) {
    try {
      await ElMessageBox.confirm('当前定时任务配置未保存，是否确认关闭并前往？', '未保存确认', {
        type: 'warning',
        confirmButtonText: '仍要前往',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
  router.push(path)
}

function goSuites() {
  leaveTo('/suites')
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空任务名称、套件与调度周期全部配置，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  visual.preset = 'daily'
  visual.minute = 0
  visual.hour = 2
  visual.weekday = 1
  syncCronFromVisual()
  clearErrors()
  ElMessage.success('已重置')
}

async function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写定时任务业务名称'
    ok = false
  }
  if (!form.suite_id) {
    errors.suite_id = '请选择一条回归套件'
    ok = false
  }
  if (cronMode.value === 'visual') syncCronFromVisual()
  const basic = basicCronCheck(form.cron_expression)
  if (basic) {
    errors.cron = basic
    ok = false
  } else {
    try {
      await scheduleApi.cronPreview(form.cron_expression.trim())
      errors.cron = ''
    } catch (e) {
      errors.cron = e?.response?.data?.message || 'Cron 表达式语法不合法'
      ok = false
    }
  }
  return ok
}

async function submit() {
  if (!(await validateAll())) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      suite_id: form.suite_id,
      env_id: form.env_id || null,
      app_package_id: form.app_package_id || null,
      cron_expression: form.cron_expression.trim(),
      enabled: !!form.enabled
    }
    if (isEdit.value) {
      await scheduleApi.update(form.id, payload)
      ElMessage.success('定时任务已保存')
    } else {
      await scheduleApi.create(payload)
      ElMessage.success('定时任务已创建')
    }
    snapshot.value = takeSnapshot()
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.dlg-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.dlg-close { margin-right: -6px; }

.dlg-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 72vh;
  overflow: auto;
}

.section-card {
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.mode-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-tpl {
  --el-button-bg-color: #fff;
  --el-button-border-color: #cbd5e1;
}

.field-block { margin-bottom: 12px; }
.field-block:last-child { margin-bottom: 0; }
.field-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.req { color: #ef4444; }
.tip-label {
  border-bottom: 1px dashed #94a3b8;
  cursor: help;
}
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}
.field-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}
.field-ok {
  margin-top: 8px;
  font-size: 12px;
  color: #16a34a;
}
.field-muted {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}
:deep(.is-error-input .el-input__wrapper),
:deep(.is-error-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.enable-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.enable-desc {
  flex: 1;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  padding-top: 2px;
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.preset-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.visual-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.visual-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.cron-preview-box {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
}
.cron-preview-box code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #1d4ed8;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
}
.cron-cn {
  margin-top: 6px;
  color: #16a34a;
}

.next-runs {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}
.next-title {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 6px;
}
.next-runs ol {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #475569;
  line-height: 1.7;
}

.guide-panel {
  padding: 14px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
}
.guide-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 12px;
}
.guide-block {
  margin-bottom: 12px;
  font-size: 12px;
  color: #334155;
  line-height: 1.65;
}
.guide-block:last-child { margin-bottom: 0; }
.guide-h {
  font-weight: 700;
  margin-bottom: 4px;
  color: #1e3a8a;
}
.guide-block p { margin: 4px 0; }
.guide-block ul {
  margin: 4px 0;
  padding-left: 18px;
}
.guide-block code {
  background: #fff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  padding: 1px 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11.5px;
  color: #1d4ed8;
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-aux {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
}
</style>
