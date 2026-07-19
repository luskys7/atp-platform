<template>
  <div class="wallboard">
    <!-- 模块 1：标题 & 控制 -->
    <header class="wb-header">
      <div>
        <h1>TestFlow 全局执行大屏</h1>
        <p class="wb-sub">设备资源、自动化任务、用例通过率实时监控总览</p>
      </div>
      <div class="wb-controls">
        <el-radio-group v-model="timeRange" size="small" @change="reload">
          <el-radio-button value="1h">近 1 小时</el-radio-button>
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="7d">近 7 天</el-radio-button>
        </el-radio-group>
        <el-select v-model="refreshSec" size="small" style="width:140px" @change="resetTimer">
          <el-option :value="10" label="自动刷新 10s" />
          <el-option :value="30" label="自动刷新 30s" />
          <el-option :value="60" label="自动刷新 60s" />
          <el-option :value="0" label="关闭自动刷新" />
        </el-select>
        <el-select v-model="scopeType" size="small" style="width:120px" @change="onScopeTypeChange">
          <el-option value="all" label="全平台" />
          <el-option value="project" label="指定项目" />
          <el-option value="team" label="指定团队" />
        </el-select>
        <el-select
          v-if="scopeType === 'project'"
          v-model="projectCode"
          size="small"
          clearable
          filterable
          placeholder="选择项目"
          style="width:160px"
          @change="reload"
        >
          <el-option v-for="p in projectOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <el-select
          v-if="scopeType === 'team'"
          v-model="teamId"
          size="small"
          clearable
          filterable
          placeholder="选择团队"
          style="width:140px"
          @change="reload"
        >
          <el-option v-for="t in teams" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-button size="small" class="btn-exit" @click="exitWallboard">退出大屏</el-button>
        <span class="wb-clock">{{ clockText }}</span>
      </div>
    </header>

    <!-- 模块 2：风险告警 -->
    <section v-if="alertMessage" class="wb-alert" id="wb-alert">
      <div class="wb-alert__text">{{ alertMessage }}</div>
      <el-button type="danger" size="small" @click="locateAlert">一键定位</el-button>
    </section>

    <!-- 模块 3：指标卡片 -->
    <div class="wb-grid" v-loading="loading">
      <button
        id="card-pass"
        type="button"
        class="wb-card tone-quality"
        :class="{ 'is-risk': !passOk }"
        @click="go('/reports')"
      >
        <div class="wb-label">全局用例通过率</div>
        <div class="wb-value" :class="passOk ? 'text-green' : 'text-amber'">{{ formatRate(data.pass_rate) }}</div>
        <div class="wb-foot">治理基准线 {{ data.min_pass_rate ?? 99 }}%</div>
      </button>

      <button type="button" class="wb-card tone-quality" @click="go('/recording-quality')">
        <div class="wb-label">累计执行结果</div>
        <div class="wb-value wb-value--split">
          <span class="text-green">{{ data.tasks?.success ?? 0 }}</span>
          <span class="sep">/</span>
          <span class="text-red">{{ data.tasks?.failed ?? 0 }}</span>
        </div>
        <div class="wb-foot">成功 / 失败 · 异常设备 {{ data.devices?.error ?? 0 }}</div>
      </button>

      <button
        id="card-device"
        type="button"
        class="wb-card tone-device"
        :class="{ 'is-risk': deviceRisk }"
        @click="go('/devices')"
      >
        <div class="wb-label">设备总览</div>
        <div class="wb-value wb-value--sm" :class="{ 'text-amber': deviceRisk }">
          {{ data.devices?.total ?? 0 }}
        </div>
        <div class="wb-foot">
          在线 {{ data.devices?.online ?? 0 }} · 忙碌 {{ data.devices?.busy ?? 0 }} ·
          <span :class="{ 'text-amber': (data.devices?.offline || 0) > 0 }">离线 {{ data.devices?.offline ?? 0 }}</span> ·
          <span :class="{ 'text-red': (data.devices?.error || 0) > 0 }">异常 {{ data.devices?.error ?? 0 }}</span>
        </div>
      </button>

      <button type="button" class="wb-card tone-device" @click="go('/devices')">
        <div class="wb-label">忙碌占用设备</div>
        <div class="wb-value text-amber">{{ data.devices?.busy ?? 0 }}</div>
        <div class="wb-foot">离线设备 {{ data.devices?.offline ?? 0 }} 台</div>
      </button>

      <button
        type="button"
        class="wb-card tone-device"
        :class="{ 'is-risk': (data.devices?.error || 0) > 0 }"
        @click="go('/devices')"
      >
        <div class="wb-label">异常故障设备</div>
        <div class="wb-value" :class="(data.devices?.error || 0) > 0 ? 'text-red' : ''">
          {{ data.devices?.error ?? 0 }}
        </div>
        <div class="wb-foot">心跳失联 / 执行崩溃</div>
      </button>

      <button
        id="card-queue"
        type="button"
        class="wb-card tone-task"
        :class="{ 'is-risk': queueRisk }"
        @click="go('/tasks')"
      >
        <div class="wb-label">任务调度队列</div>
        <div class="wb-value wb-value--sm">
          <span class="text-blue">{{ data.tasks?.running ?? 0 }}</span>
          <span class="sep">/</span>
          <span class="text-amber">{{ data.tasks?.queued ?? 0 }}</span>
          <span class="sep">/</span>
          <span :class="queueRisk ? 'text-amber' : ''">{{ pendingDepth }}</span>
        </div>
        <div class="wb-foot">运行 / 排队 / 待提交（阈值 {{ data.queue_threshold ?? 20 }}）</div>
      </button>
    </div>

    <!-- 模块 4：双栏 -->
    <el-row :gutter="16" class="wb-charts">
      <el-col :xs="24" :lg="14">
        <div class="wb-panel">
          <div class="wb-panel__head">
            <el-radio-group v-model="chartTab" size="small" @change="renderChart">
              <el-radio-button value="pass">用例通过率趋势</el-radio-button>
              <el-radio-button value="device">设备负载趋势</el-radio-button>
              <el-radio-button value="volume">每日执行总量</el-radio-button>
              <el-radio-button value="fail">失败用例走势</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="chartRef" class="chart-area"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="10">
        <div class="wb-panel">
          <h3>实时运行任务明细</h3>
          <el-table
            :data="data.running_tasks || []"
            size="small"
            class="wb-table"
            max-height="320"
          >
            <el-table-column prop="id" label="任务编号" width="78" />
            <el-table-column prop="name" label="任务名称" min-width="110" show-overflow-tooltip />
            <el-table-column prop="version_label" label="基线版本" width="88" show-overflow-tooltip>
              <template #default="{ row }">{{ row.version_label || '-' }}</template>
            </el-table-column>
            <el-table-column prop="device_id" label="设备 ID" width="78">
              <template #default="{ row }">{{ row.device_id || '-' }}</template>
            </el-table-column>
            <el-table-column prop="duration_label" label="时长" width="72" />
            <el-table-column prop="current_step" label="当前步骤" min-width="90" show-overflow-tooltip />
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)" effect="plain" class="wb-status-tag">
                  {{ row.status_label || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="148">
              <template #default="{ row }">
                <el-button class="wb-link-btn" link @click="goRecording(row.id)">录屏</el-button>
                <el-button class="wb-link-btn" link @click="go(`/tasks/${row.id}`)">详情</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="wb-empty">
                <p>暂无运行任务</p>
                <el-button type="primary" size="small" @click="go('/suites')">新建回归任务</el-button>
              </div>
            </template>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <!-- 模块 5：释义 -->
    <footer class="wb-legend">
      <span>治理线标准：平台自动化验收基准通过率 99%，低于该值视为版本质量不达标，需复盘失败用例。</span>
      <span>队列深度规则：待提交任务 &gt; 20 判定为调度积压，需扩充设备池或错峰执行定时任务。</span>
      <span>设备异常判定：连续 3 次心跳失联、执行崩溃自动标记异常，需重启设备或排查 adb 连接。</span>
      <span>数据刷新逻辑：默认 30 秒自动拉取最新设备、任务、执行结果数据。</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi, teamApi } from '@/api'
import * as echarts from 'echarts'

const router = useRouter()
const loading = ref(false)
const data = ref({})
const timeRange = ref('today')
const refreshSec = ref(30)
const scopeType = ref('all')
const projectCode = ref('')
const teamId = ref(null)
const teams = ref([])
const chartTab = ref('pass')
const chartRef = ref()
const clockText = ref('')
const alertTarget = ref('card-pass')

let chart
let pollTimer
let clockTimer

const passOk = computed(() => data.value.pass_rate_ok !== false)
const deviceRisk = computed(() => (data.value.devices?.offline || 0) + (data.value.devices?.error || 0) > 0)
const pendingDepth = computed(() => Math.max(data.value.queue_depth || 0, data.value.tasks?.pending || 0))
const queueRisk = computed(() => pendingDepth.value > (data.value.queue_threshold || 20))
const alertMessage = computed(() => data.value.alert_message || '')
const projectOptions = computed(() => {
  const fromApi = data.value.projects || []
  try {
    const local = JSON.parse(localStorage.getItem('atp_project_list') || '[]')
      .map(p => p.key || p.code || p.name)
      .filter(Boolean)
    return [...new Set([...fromApi, ...local])]
  } catch {
    return fromApi
  }
})

function formatRate(v) {
  return `${Number(v || 0).toFixed(1)}%`
}

function statusTagType(status) {
  if (status === 'running') return 'primary'
  if (status === 'queued') return 'warning'
  if (status === 'waiting_manual' || status === 'paused') return 'danger'
  return 'info'
}

function buildParams() {
  const params = { time_range: timeRange.value }
  if (scopeType.value === 'project' && projectCode.value) params.project_code = projectCode.value
  if (scopeType.value === 'team' && teamId.value) params.team_id = teamId.value
  return params
}

function onScopeTypeChange() {
  projectCode.value = ''
  teamId.value = null
  reload()
}

async function loadTeams() {
  try {
    const res = await teamApi.list()
    teams.value = res.data || []
  } catch {
    teams.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    const res = await reportApi.wallboard(buildParams())
    data.value = res.data || {}
    const alerts = data.value.alerts || []
    alertTarget.value = alerts[0]?.target || 'card-pass'
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function resetTimer() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
  if (refreshSec.value > 0) {
    pollTimer = setInterval(reload, refreshSec.value * 1000)
  }
}

function tickClock() {
  clockText.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

function go(path) {
  router.push(path)
}

function exitWallboard() {
  router.push('/dashboard')
}

function goRecording(taskId) {
  router.push({ path: '/recordings', query: { task_id: taskId } })
}

function locateAlert() {
  const el = document.getElementById(alertTarget.value) || document.getElementById('wb-alert')
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el?.classList.add('flash')
  setTimeout(() => el?.classList.remove('flash'), 1500)
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const axis = '#8b93a7'
  const grid = 'rgba(255,255,255,0.05)'
  const minPass = data.value.min_pass_rate ?? 99
  let option = {
    backgroundColor: 'transparent',
    grid: { left: 48, right: 24, top: 36, bottom: 36 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(26,31,43,0.95)',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#e8eaef', fontSize: 12 }
    },
    textStyle: { color: axis }
  }

  if (chartTab.value === 'pass') {
    const trend = data.value.pass_rate_trend || []
    option = {
      ...option,
      tooltip: {
        ...option.tooltip,
        formatter(params) {
          const i = params[0]?.dataIndex
          const t = trend[i]
          if (!t) return ''
          return `#${t.task_id}<br/>通过率 ${Number(t.pass_rate).toFixed(1)}%<br/>成功 ${t.success_count} / 失败 ${t.failed_count}`
        }
      },
      xAxis: {
        type: 'category',
        data: trend.map(t => `#${t.task_id}`),
        axisLabel: { color: axis },
        axisLine: { lineStyle: { color: grid } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value', min: 0, max: 100,
        axisLabel: { color: axis, formatter: '{value}%' },
        splitLine: { lineStyle: { color: grid } }
      },
      series: [{
        type: 'line',
        smooth: true,
        data: trend.map(t => t.pass_rate),
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(122, 158, 199, 0.22)' },
              { offset: 1, color: 'rgba(122, 158, 199, 0)' }
            ]
          }
        },
        lineStyle: { color: '#7a9ec7', width: 2.5 },
        itemStyle: { color: '#9bb6d4' },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: 'rgba(194, 117, 117, 0.75)', type: 'dashed', width: 1.2 },
          data: [{ yAxis: minPass, name: `治理线 ${minPass}%` }],
          label: { color: '#c27575', formatter: '{b}' }
        }
      }]
    }
  } else if (chartTab.value === 'device') {
    const series = data.value.device_load_trend || []
    option = {
      ...option,
      legend: { data: ['在线', '忙碌', '离线'], textStyle: { color: axis }, top: 0 },
      xAxis: {
        type: 'category',
        data: series.map(s => s.label || s.time),
        axisLabel: { color: axis },
        axisLine: { lineStyle: { color: grid } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value', minInterval: 1,
        axisLabel: { color: axis },
        splitLine: { lineStyle: { color: grid } }
      },
      series: [
        { name: '在线', type: 'line', smooth: true, data: series.map(s => s.online), lineStyle: { color: '#5b9a8b' }, itemStyle: { color: '#5b9a8b' } },
        { name: '忙碌', type: 'line', smooth: true, data: series.map(s => s.busy), lineStyle: { color: '#c4a35a' }, itemStyle: { color: '#c4a35a' } },
        { name: '离线', type: 'line', smooth: true, data: series.map(s => s.offline), lineStyle: { color: '#7a8296' }, itemStyle: { color: '#7a8296' } }
      ]
    }
  } else if (chartTab.value === 'volume') {
    const vol = data.value.daily_volume || []
    option = {
      ...option,
      xAxis: {
        type: 'category',
        data: vol.map(v => v.date),
        axisLabel: { color: axis },
        axisLine: { lineStyle: { color: grid } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value', minInterval: 1,
        axisLabel: { color: axis },
        splitLine: { lineStyle: { color: grid } }
      },
      series: [{
        type: 'bar',
        data: vol.map(v => v.total),
        itemStyle: { color: 'rgba(122, 158, 199, 0.75)', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 32
      }]
    }
  } else {
    const fail = data.value.fail_trend || []
    option = {
      ...option,
      xAxis: {
        type: 'category',
        data: fail.map(v => v.date),
        axisLabel: { color: axis },
        axisLine: { lineStyle: { color: grid } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value', minInterval: 1,
        axisLabel: { color: axis },
        splitLine: { lineStyle: { color: grid } }
      },
      series: [{
        type: 'line',
        smooth: true,
        data: fail.map(v => v.failed),
        lineStyle: { color: '#c27575', width: 2.5 },
        itemStyle: { color: '#d49a9a' },
        areaStyle: { color: 'rgba(194, 117, 117, 0.14)' }
      }]
    }
  }
  chart.setOption(option, true)
}

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  await loadTeams()
  await reload()
  resetTimer()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
  chart?.dispose()
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped lang="scss">
.wallboard {
  --wb-bg: #12161f;
  --wb-surface: #1a1f2b;
  --wb-surface-2: #222834;
  --wb-border: rgba(255, 255, 255, 0.06);
  --wb-border-strong: rgba(255, 255, 255, 0.1);
  --wb-text: #e8eaef;
  --wb-muted: #8b93a7;
  --wb-ok: #5b9a8b;
  --wb-warn: #c4a35a;
  --wb-danger: #c27575;
  --wb-info: #6b8cae;
  --wb-accent: #7a9ec7;

  min-height: 100vh;
  color: var(--wb-text);
  padding: 22px 28px 32px;
  background:
    radial-gradient(ellipse 80% 50% at 10% -10%, rgba(90, 110, 140, 0.12), transparent 55%),
    radial-gradient(ellipse 60% 40% at 90% 0%, rgba(80, 100, 90, 0.08), transparent 50%),
    linear-gradient(180deg, #151922 0%, var(--wb-bg) 40%, #10141c 100%);
}

.wb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;

  h1 {
    font-size: 24px;
    font-weight: 650;
    margin: 0 0 6px;
    letter-spacing: 0.01em;
    color: #f3f4f6;
  }
}
.wb-sub {
  margin: 0;
  font-size: 13px;
  color: var(--wb-muted);
  letter-spacing: 0.01em;
}
.wb-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;

  :deep(.el-radio-group) {
    --el-border-color: var(--wb-border-strong);
    background: var(--wb-surface);
    border-radius: 8px;
    padding: 2px;
  }
  :deep(.el-radio-button__inner) {
    background: transparent;
    border: none !important;
    color: var(--wb-muted);
    border-radius: 6px !important;
    box-shadow: none !important;
  }
  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: rgba(122, 158, 199, 0.18);
    color: #d6e4f5;
  }
  :deep(.el-select .el-select__wrapper) {
    background: var(--wb-surface);
    box-shadow: 0 0 0 1px var(--wb-border-strong) inset;
  }
  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    color: var(--wb-text);
  }
}
.btn-exit {
  background: transparent !important;
  color: var(--wb-text) !important;
  border: 1px solid var(--wb-border-strong) !important;
  &:hover {
    background: rgba(255, 255, 255, 0.06) !important;
    border-color: rgba(255, 255, 255, 0.18) !important;
  }
}
.wb-clock {
  font-size: 12px;
  color: var(--wb-muted);
  font-variant-numeric: tabular-nums;
  min-width: 150px;
  text-align: right;
  letter-spacing: 0.02em;
}

.wb-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px 12px 14px;
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(90, 40, 44, 0.92), rgba(55, 36, 40, 0.88));
  border: 1px solid rgba(194, 117, 117, 0.35);
  border-left: 3px solid #b87a7a;
  color: #f0dede;
  box-shadow: none;
}
.wb-alert__text {
  font-size: 13px;
  font-weight: 550;
  line-height: 1.5;
  letter-spacing: 0.01em;
}
.wb-alert :deep(.el-button--danger) {
  --el-button-bg-color: rgba(184, 122, 122, 0.25);
  --el-button-border-color: rgba(184, 122, 122, 0.45);
  --el-button-text-color: #f0dede;
  --el-button-hover-bg-color: rgba(184, 122, 122, 0.4);
  --el-button-hover-border-color: rgba(184, 122, 122, 0.6);
  --el-button-hover-text-color: #fff;
}

.wb-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.wb-card {
  appearance: none;
  border: 1px solid var(--wb-border);
  border-radius: 12px;
  padding: 16px 15px;
  text-align: left;
  cursor: pointer;
  color: inherit;
  background: linear-gradient(165deg, var(--wb-surface-2) 0%, var(--wb-surface) 100%);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.03) inset;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, background 0.18s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 16px;
    right: 16px;
    height: 1px;
    opacity: 0.85;
  }

  &:hover {
    transform: translateY(-1px);
    border-color: var(--wb-border-strong);
    box-shadow: 0 10px 28px rgba(0, 0, 0, 0.28);
    background: linear-gradient(165deg, #262c3a 0%, var(--wb-surface) 100%);
  }

  &.is-risk {
    border-color: rgba(194, 117, 117, 0.4);
    background: linear-gradient(165deg, #2a2226 0%, var(--wb-surface) 100%);
  }

  &.flash {
    animation: flash-border 0.45s ease 3;
  }
}

.tone-quality::before { background: rgba(91, 154, 139, 0.7); }
.tone-device::before { background: rgba(196, 163, 90, 0.65); }
.tone-task::before { background: rgba(107, 140, 174, 0.7); }

.wb-label {
  font-size: 12px;
  color: var(--wb-muted);
  margin-bottom: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
}
.wb-value {
  font-size: 30px;
  font-weight: 650;
  line-height: 1.15;
  color: #f1f3f7;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.wb-value--sm { font-size: 20px; font-weight: 600; }
.wb-value--split { display: flex; align-items: baseline; gap: 6px; }
.sep { color: #5c6578; font-weight: 500; font-size: 18px; }
.wb-foot {
  margin-top: 10px;
  font-size: 11px;
  color: #7a8296;
  line-height: 1.45;
}

.text-green { color: var(--wb-ok); }
.text-amber { color: var(--wb-warn); }
.text-blue { color: var(--wb-info); }
.text-red { color: var(--wb-danger); }

.wb-charts { margin-bottom: 16px; }
.wb-panel {
  background: linear-gradient(180deg, var(--wb-surface-2), var(--wb-surface));
  border: 1px solid var(--wb-border);
  border-radius: 12px;
  padding: 16px 18px;
  min-height: 380px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.03) inset;

  h3 {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 600;
    color: #d5dae6;
    letter-spacing: 0.01em;
  }

  :deep(.el-radio-group) {
    background: rgba(0, 0, 0, 0.2);
    border-radius: 8px;
    padding: 2px;
  }
  :deep(.el-radio-button__inner) {
    background: transparent;
    border: none !important;
    color: var(--wb-muted);
    box-shadow: none !important;
    border-radius: 6px !important;
    font-size: 12px;
  }
  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: rgba(122, 158, 199, 0.16);
    color: #d6e4f5;
  }
}
.wb-panel__head {
  margin-bottom: 10px;
  overflow-x: auto;
}
.chart-area { height: 300px; }

.wb-table {
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.04) !important;
  --el-table-row-hover-bg-color: rgba(122, 158, 199, 0.1) !important;
  --el-table-border-color: rgba(255, 255, 255, 0.06) !important;
  --el-table-text-color: #c8cedc !important;
  --el-table-header-text-color: #8b93a7 !important;
  --el-fill-color-blank: transparent !important;
  background: transparent !important;
  width: 100%;

  :deep(.el-table__inner-wrapper::before),
  :deep(.el-table__border-left-patch),
  :deep(.el-table__border-bottom-patch) {
    background-color: transparent !important;
  }

  :deep(.el-table__header-wrapper),
  :deep(.el-table__body-wrapper),
  :deep(.el-table__empty-block),
  :deep(.el-scrollbar__view),
  :deep(table),
  :deep(.el-table__body),
  :deep(.el-table__header) {
    background: transparent !important;
  }

  :deep(th.el-table__cell),
  :deep(td.el-table__cell) {
    background: transparent !important;
    border-bottom-color: rgba(255, 255, 255, 0.06) !important;
  }

  :deep(th.el-table__cell) {
    font-weight: 550;
    font-size: 12px;
    color: #8b93a7 !important;
  }

  :deep(td.el-table__cell) {
    color: #c8cedc !important;
  }

  :deep(.el-table__row:hover > td.el-table__cell) {
    background: rgba(122, 158, 199, 0.08) !important;
  }

  :deep(.el-table__empty-text) {
    color: #8b93a7;
  }

  :deep(.el-table__body tr.hover-row > td.el-table__cell) {
    background: rgba(122, 158, 199, 0.08) !important;
  }
}

.wb-status-tag {
  --el-tag-bg-color: rgba(255, 255, 255, 0.06);
  --el-tag-border-color: rgba(255, 255, 255, 0.1);
  --el-tag-text-color: #c8cedc;
}
.wb-link-btn {
  color: #9bb6d4 !important;
  padding: 0 4px !important;
  &:hover { color: #c5d6ea !important; }
}

.wb-empty {
  padding: 36px 8px;
  text-align: center;
  color: #8b93a7;
  background: transparent;
  p { margin: 0 0 14px; font-size: 13px; }
  :deep(.el-button--primary) {
    --el-button-bg-color: rgba(122, 158, 199, 0.18);
    --el-button-border-color: rgba(122, 158, 199, 0.35);
    --el-button-text-color: #d6e4f5;
    --el-button-hover-bg-color: rgba(122, 158, 199, 0.3);
    --el-button-hover-border-color: rgba(122, 158, 199, 0.5);
    --el-button-hover-text-color: #fff;
  }
}

.wb-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  padding-top: 4px;
  font-size: 11px;
  line-height: 1.55;
  color: #6a7388;
}

@keyframes flash-border {
  50% { box-shadow: 0 0 0 1px rgba(194, 117, 117, 0.55); }
}

@media (max-width: 1400px) {
  .wb-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .wb-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .wb-value { font-size: 26px; }
}
</style>
