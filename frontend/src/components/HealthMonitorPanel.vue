<template>
  <div class="health-monitor" v-loading="loading">
    <!-- 模块 1：头部 -->
    <div class="hm-header">
      <div class="hm-header-text">
        <h2 class="hm-title">平台健康监控</h2>
        <p class="hm-sub">实时监控数据库、执行器、设备池、调度队列、存储、后端服务运行状态，展示故障自愈切换记录</p>
      </div>
      <div class="hm-header-actions">
        <div class="auto-refresh">
          <el-switch v-model="autoRefresh" />
          <span>自动刷新（30 秒）</span>
        </div>
        <el-button :loading="loading" @click="refresh">手动刷新</el-button>
        <el-button type="primary" plain @click="exportReport">导出监控报表</el-button>
      </div>
    </div>

    <!-- 模块 2：总览卡片 -->
    <div id="hm-overview" class="overview-grid">
      <div class="ov-card" :class="overallOk ? 'tone-ok' : 'tone-alert'">
        <div class="ov-label">整体运行状态</div>
        <div class="ov-value">{{ overallOk ? '运行正常' : '存在组件故障' }}</div>
      </div>
      <div class="ov-card tone-green">
        <div class="ov-label">在线测试设备</div>
        <div class="ov-value">{{ devices.online ?? 0 }}</div>
        <div class="ov-corner">离线 {{ devices.offline ?? 0 }} 台</div>
      </div>
      <div class="ov-card tone-cream">
        <div class="ov-label">当前运行任务</div>
        <div class="ov-value">{{ scheduler.running_tasks ?? 0 }}</div>
      </div>
      <div class="ov-card" :class="failoverCount > 0 ? 'tone-alert' : 'tone-gray'">
        <div class="ov-label">执行器故障事件</div>
        <div class="ov-value" :class="{ 'is-warn': failoverCount > 0 }">{{ failoverCount }}</div>
        <div v-if="failoverCount > 0" class="ov-corner warn">待运维处理</div>
      </div>
    </div>

    <!-- 模块 3：六大组件 -->
    <div class="component-grid">
      <div
        v-for="card in componentCards"
        :key="card.key"
        class="comp-card"
        :class="{ 'is-bad': !card.ok }"
      >
        <div class="comp-head">
          <h3>{{ card.title }}</h3>
          <el-tag size="small" :type="card.ok ? 'success' : 'danger'" effect="light">
            {{ card.ok ? '运行正常' : '离线故障' }}
          </el-tag>
        </div>
        <ul class="comp-metrics">
          <li v-for="(m, idx) in card.metrics" :key="idx" :class="{ 'is-warn-text': m.warn }">
            <span class="m-label">{{ m.label }}</span>
            <span class="m-value">{{ m.value }}</span>
          </li>
        </ul>
        <div class="comp-foot">
          <el-button
            type="primary"
            plain
            size="small"
            class="comp-nav-btn"
            @click.stop="go(card)"
          >{{ card.linkLabel }}</el-button>
        </div>
      </div>
    </div>

    <!-- 模块 4：故障自愈事件 -->
    <section id="hm-failover" class="failover-panel">
      <div class="failover-head">
        <h3>执行器故障自愈切换事件</h3>
      </div>
      <div class="failover-toolbar">
        <el-radio-group v-model="rangePreset" size="small" @change="onRangePreset">
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="7d">近 7 天</el-radio-button>
          <el-radio-button value="custom">自定义时间</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="rangePreset === 'custom'"
          v-model="customRange"
          type="datetimerange"
          size="small"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        <el-input
          v-model="filterFrom"
          size="small"
          clearable
          placeholder="原故障节点"
          style="width:160px"
        />
        <el-input
          v-model="filterTo"
          size="small"
          clearable
          placeholder="切换目标节点"
          style="width:160px"
        />
        <el-input
          v-model="filterTask"
          size="small"
          clearable
          placeholder="关联任务 ID"
          style="width:120px"
        />
        <el-button size="small" @click="clearEvents" :disabled="!events.length">清空记录</el-button>
        <el-button size="small" type="primary" plain @click="exportEvents" :disabled="!filteredEvents.length">批量导出故障日志</el-button>
      </div>

      <el-table
        :data="filteredEvents"
        stripe
        size="small"
        max-height="280"
      >
        <el-table-column prop="at" label="故障发生时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.at) }}</template>
        </el-table-column>
        <el-table-column prop="from_url" label="故障原节点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="to_url" label="切换至新节点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="task_id" label="关联测试任务 ID" width="140">
          <template #default="{ row }">{{ row.task_id ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="故障触发原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ reasonLabel(row.reason) }}</template>
        </el-table-column>
        <template #empty>
          <div class="empty-failover">
            <div class="empty-title">暂无执行器故障自愈切换记录</div>
            <div class="empty-hint">当执行器节点离线、卡死时，平台自动将任务迁移至健康节点，记录将展示在此处</div>
          </div>
        </template>
      </el-table>
    </section>

    <!-- 模块 5：底部辅助 -->
    <section class="hm-footer">
      <div class="quick-links">
        <div class="footer-title">运维快捷跳转</div>
        <div class="quick-btns">
          <el-button @click="go('/devices')">设备池管理</el-button>
          <el-button @click="go('/tasks')">测试任务</el-button>
          <el-button @click="go({ path: '/platform-config', query: { tab: 'monitor' } })">执行器节点</el-button>
          <el-button @click="go('/reports')">存储文件</el-button>
          <el-button @click="go({ path: '/platform-config', query: { tab: 'env' } })">环境配置</el-button>
          <el-button @click="go({ path: '/platform-config', query: { tab: 'schedule' } })">定时任务</el-button>
        </div>
      </div>
      <div class="rule-panel">
        <div class="footer-title">健康监控规则说明</div>
        <div class="rule-block">
          <div class="rule-h">组件状态判定规则</div>
          <ul>
            <li>运行正常：服务心跳上报正常，无报错日志</li>
            <li>离线故障：连续 3 次心跳失联、服务进程崩溃</li>
          </ul>
        </div>
        <div class="rule-block">
          <div class="rule-h">自愈切换机制</div>
          <p>执行器节点故障后，平台自动将排队 / 运行任务迁移至健康节点，保障自动化任务不中断</p>
        </div>
        <div class="rule-block">
          <div class="rule-h">存储告警阈值规则</div>
          <p>磁盘使用率≥80% 触发橙色预警；≥90% 触发全局告警，需及时清理录屏、报告文件</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { monitorApi } from '@/api'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  active: { type: Boolean, default: true }
})
const emit = defineEmits(['updated'])

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const autoRefresh = ref(false)
const monitor = ref({})
const events = ref([])
let timer = null

const rangePreset = ref('7d')
const customRange = ref(null)
const filterFrom = ref('')
const filterTo = ref('')
const filterTask = ref('')

const db = computed(() => monitor.value.database || {})
const executor = computed(() => monitor.value.executor || {})
const pool = computed(() => monitor.value.executor_pool || {})
const storage = computed(() => monitor.value.storage || {})
const devices = computed(() => monitor.value.devices || {})
const scheduler = computed(() => monitor.value.scheduler || {})
const backend = computed(() => monitor.value.backend || {})

const overallOk = computed(() => {
  const o = monitor.value.overall
  return o === 'healthy' || (!o && isUp(db.value.status) && isUp(executor.value.status) && isUp(backend.value.status))
})

const failoverCount = computed(() => (events.value || []).length)

function isUp(s) {
  return s === 'up' || s === 'healthy'
}

function fmtBytes(b) {
  if (b == null) return '—'
  return `${(Number(b) / 1048576).toFixed(1)} MB`
}

function fmtPct(v) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  return `${Number(v).toFixed(1)}%`
}

const componentCards = computed(() => {
  const diskPct = storage.value.disk_usage_percent
  const diskWarn = diskPct != null && Number(diskPct) >= 80
  const storeOk = storage.value.status !== 'down' && storage.value.status !== 'critical'
  const poolStatus = pool.value.status
  const execOk = isUp(executor.value.status) && poolStatus !== 'down'

  return [
    {
      key: 'db',
      title: '数据库模块',
      ok: isUp(db.value.status),
      metrics: [
        { label: '存储类型', value: '本地内置数据库' },
        { label: '错误日志', value: db.value.error || '无' }
      ],
      link: { path: '/platform-config', query: { tab: 'backup' } },
      linkLabel: '前往数据库运维'
    },
    {
      key: 'exec',
      title: '执行器调度节点',
      ok: execOk,
      metrics: [
        { label: '主节点地址', value: executor.value.url ? `本地服务地址 ${executor.value.url}` : '—' },
        { label: '健康节点数量', value: pool.value.total_count != null ? `${pool.value.healthy_count ?? 0}/${pool.value.total_count} 总节点` : '—' },
        { label: '节点异常记录', value: executor.value.error || '无' }
      ],
      link: { path: '/platform-config', query: { tab: 'monitor' } },
      scroll: 'hm-failover',
      linkLabel: '执行器节点管理'
    },
    {
      key: 'store',
      title: '文件存储',
      ok: storeOk,
      metrics: [
        { label: '录屏文件占用', value: fmtBytes(storage.value.recordings_bytes) },
        { label: '测试报告占用', value: fmtBytes(storage.value.reports_bytes) },
        { label: '总存储占用', value: fmtBytes(storage.value.total_bytes) },
        {
          label: '磁盘使用率',
          value: diskPct != null ? `${fmtPct(diskPct)}（阈值 80%，超阈值橙字告警）` : '—',
          warn: diskWarn
        },
        { label: '存储告警', value: storage.value.alert || storage.value.error || '无', warn: !!storage.value.alert }
      ],
      link: '/recordings',
      linkLabel: '存储文件管理'
    },
    {
      key: 'dev',
      title: '测试设备池',
      ok: true,
      metrics: [
        { label: '在线设备', value: `${devices.value.online ?? 0} 台` },
        { label: '占用执行设备', value: `${devices.value.busy ?? 0} 台` },
        { label: '离线设备', value: `${devices.value.offline ?? 0} 台` },
        { label: '设备异常', value: `${devices.value.error ?? 0} 台` }
      ],
      link: '/devices',
      linkLabel: '设备池管理页面'
    },
    {
      key: 'sched',
      title: '任务调度队列',
      ok: true,
      metrics: [
        { label: '排队等待任务', value: scheduler.value.queue_size ?? 0 },
        { label: '正在运行任务', value: scheduler.value.running_tasks ?? 0 },
        { label: '待调度积压任务', value: scheduler.value.queued_tasks ?? scheduler.value.pending_tasks ?? 0 }
      ],
      link: '/tasks',
      linkLabel: '测试任务队列看板'
    },
    {
      key: 'be',
      title: '后端核心服务',
      ok: isUp(backend.value.status),
      metrics: [
        { label: '服务实例', value: '自动化测试平台后端服务' }
      ],
      link: { path: '/platform-config', query: { tab: 'monitor' } },
      scroll: 'hm-overview',
      linkLabel: '后端服务监控面板'
    }
  ]
})

function parseEventTime(at) {
  if (!at) return null
  const d = new Date(String(at).replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

function rangeBounds() {
  const now = new Date()
  if (rangePreset.value === 'today') {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    return { start, end: now }
  }
  if (rangePreset.value === '7d') {
    const start = new Date(now.getTime() - 7 * 24 * 3600 * 1000)
    return { start, end: now }
  }
  if (customRange.value?.length === 2) {
    return {
      start: parseEventTime(customRange.value[0]),
      end: parseEventTime(customRange.value[1])
    }
  }
  return { start: null, end: null }
}

const filteredEvents = computed(() => {
  const { start, end } = rangeBounds()
  const fromKw = filterFrom.value.trim().toLowerCase()
  const toKw = filterTo.value.trim().toLowerCase()
  const taskKw = filterTask.value.trim()
  return (events.value || []).filter((ev) => {
    const t = parseEventTime(ev.at)
    if (start && t && t < start) return false
    if (end && t && t > end) return false
    if (fromKw && !String(ev.from_url || '').toLowerCase().includes(fromKw)) return false
    if (toKw && !String(ev.to_url || '').toLowerCase().includes(toKw)) return false
    if (taskKw && !String(ev.task_id ?? '').includes(taskKw)) return false
    return true
  })
})

function reasonLabel(r) {
  if (!r) return '—'
  const map = {
    unhealthy: '节点健康检查失败',
    timeout: '节点响应超时',
    connection_refused: '节点连接拒绝',
    down: '节点离线',
    failover: '任务执行失败，自动切换备用节点',
    node_offline_no_backup: '节点离线，当前无可用备用节点',
    node_offline_failover: '节点离线，已标记切换至备用节点',
    heartbeat_lost_3: '连续 3 次心跳失联，判定离线故障',
    marked_unhealthy: '节点被标记为不可用'
  }
  return map[r] || r
}

function onRangePreset() {
  if (rangePreset.value !== 'custom') customRange.value = null
}

function scrollToId(id) {
  if (!id) return
  nextTick(() => {
    const el = document.getElementById(id)
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function samePlatformTab(target) {
  if (!target || typeof target === 'string') return false
  if (target.path !== '/platform-config') return false
  const curTab = String(route.query.tab || '')
  const nextTab = String(target.query?.tab || '')
  return route.path === '/platform-config' && curTab === nextTab
}

async function go(card) {
  if (!card?.link) return
  const target = card.link
  const scroll = card.scroll
  try {
    if (samePlatformTab(target)) {
      scrollToId(scroll || 'hm-overview')
      return
    }
    await router.push(target)
    if (scroll) {
      setTimeout(() => scrollToId(scroll), 120)
    }
  } catch (e) {
    // 忽略重复导航
    if (scroll) scrollToId(scroll)
    else if (e?.name !== 'NavigationDuplicated') {
      ElMessage.error('页面跳转失败')
    }
  }
}

async function refresh() {
  loading.value = true
  try {
    const [snap, ev] = await Promise.all([
      monitorApi.snapshot(),
      monitorApi.executorEvents()
    ])
    monitor.value = snap.data || {}
    events.value = ev.data || []
    emit('updated', { monitor: monitor.value, events: events.value })
  } catch {
    monitor.value = {}
    events.value = []
    emit('updated', { monitor: {}, events: [] })
  } finally {
    loading.value = false
  }
}

function downloadCsv(filename, header, rows) {
  const bom = '\uFEFF'
  const csv = [header.join(','), ...rows.map((r) => r.map((c) => {
    const s = String(c ?? '')
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }).join(','))].join('\n')
  const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function exportReport() {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  const header = ['分区', '指标', '值']
  const rows = [
    ['总览', '整体运行状态', overallOk.value ? '运行正常' : '存在组件故障'],
    ['总览', '在线测试设备', devices.value.online ?? 0],
    ['总览', '离线设备', devices.value.offline ?? 0],
    ['总览', '当前运行任务', scheduler.value.running_tasks ?? 0],
    ['总览', '执行器故障事件', failoverCount.value],
    ['总览', '检测时间', monitor.value.checked_at || '']
  ]
  for (const card of componentCards.value) {
    rows.push([card.title, '运行状态', card.ok ? '运行正常' : '离线故障'])
    for (const m of card.metrics) {
      rows.push([card.title, m.label, m.value])
    }
  }
  for (const ev of events.value || []) {
    rows.push([
      '故障自愈',
      fmtTime(ev.at),
      `原节点=${ev.from_url || ''}；新节点=${ev.to_url || ''}；任务=${ev.task_id ?? ''}；原因=${reasonLabel(ev.reason)}`
    ])
  }
  downloadCsv(`平台健康监控报表_${stamp}.csv`, header, rows)
  ElMessage.success('已导出监控报表（Excel 可直接打开）')
}

function exportEvents() {
  const stamp = new Date().toISOString().slice(0, 10)
  const header = ['故障发生时间', '故障原节点', '切换至新节点', '关联测试任务 ID', '故障触发原因']
  const rows = filteredEvents.value.map((ev) => [
    fmtTime(ev.at),
    ev.from_url || '',
    ev.to_url || '',
    ev.task_id ?? '',
    reasonLabel(ev.reason)
  ])
  downloadCsv(`执行器故障自愈日志_${stamp}.csv`, header, rows)
  ElMessage.success(`已导出 ${rows.length} 条故障日志`)
}

async function clearEvents() {
  try {
    await ElMessageBox.confirm('确定清空全部执行器故障自愈切换记录？', '清空记录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await monitorApi.clearExecutorEvents()
    events.value = []
    emit('updated', { monitor: monitor.value, events: [] })
    ElMessage.success('已清空故障记录')
  } catch (e) {
    ElMessage.error(e?.message || '清空失败')
  }
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function syncTimer() {
  stopTimer()
  if (autoRefresh.value && props.active) {
    timer = setInterval(() => { refresh() }, 30000)
  }
}

watch(autoRefresh, syncTimer)
watch(() => props.active, (v) => {
  if (v) {
    refresh()
    syncTimer()
  } else {
    stopTimer()
  }
})

onMounted(() => {
  if (props.active) refresh()
  syncTimer()
})

onUnmounted(stopTimer)

defineExpose({ refresh })
</script>

<style scoped>
.health-monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hm-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.hm-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.hm-sub {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  max-width: 640px;
}
.hm-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.auto-refresh {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #475569;
  padding: 4px 10px;
  background: #f8fafc;
  border-radius: 8px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
@media (max-width: 1100px) {
  .overview-grid { grid-template-columns: repeat(2, 1fr); }
}
.ov-card {
  position: relative;
  border-radius: 12px;
  padding: 16px 18px;
  min-height: 96px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.ov-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.ov-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}
.ov-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}
.ov-value.is-warn { color: #c2410c; }
.ov-corner {
  position: absolute;
  right: 14px;
  bottom: 12px;
  font-size: 12px;
  color: #64748b;
}
.ov-corner.warn { color: #c2410c; font-weight: 600; }
.tone-ok { background: #eff6ff; border-color: #bfdbfe; }
.tone-alert { background: #fff1f2; border-color: #fecdd3; }
.tone-green { background: #ecfdf5; border-color: #a7f3d0; }
.tone-cream { background: #fffbeb; border-color: #fde68a; }
.tone-gray { background: #f8fafc; border-color: #e2e8f0; }

.component-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
@media (max-width: 1100px) {
  .component-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 720px) {
  .component-grid { grid-template-columns: 1fr; }
}
.comp-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  min-height: 180px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.comp-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
}
.comp-card.is-bad {
  background: #fff1f2;
  border-color: #fecdd3;
}
.comp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.comp-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.comp-metrics {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
}
.comp-metrics li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #475569;
  padding: 4px 0;
  border-bottom: 1px dashed #e2e8f0;
}
.comp-metrics li:last-child { border-bottom: none; }
.m-label { color: #64748b; flex-shrink: 0; }
.m-value { text-align: right; color: #334155; word-break: break-all; }
.is-warn-text .m-value { color: #ea580c; font-weight: 600; }
.comp-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.comp-nav-btn {
  min-width: 120px;
  font-weight: 600;
}

.failover-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
}
.failover-head h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
}
.failover-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.empty-failover {
  text-align: center;
  padding: 28px 16px 12px;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 6px;
}
.empty-hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.hm-footer {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 12px;
}
@media (max-width: 960px) {
  .hm-footer { grid-template-columns: 1fr; }
}
.quick-links, .rule-panel {
  border-radius: 12px;
  padding: 14px 16px;
}
.quick-links {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.rule-panel {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}
.footer-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
  color: #0f172a;
}
.rule-panel .footer-title { color: #1e40af; }
.quick-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rule-block {
  margin-bottom: 10px;
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
}
.rule-block:last-child { margin-bottom: 0; }
.rule-h {
  font-weight: 700;
  color: #1e3a8a;
  margin-bottom: 4px;
}
.rule-block ul {
  margin: 4px 0;
  padding-left: 18px;
}
.rule-block p { margin: 4px 0; }
</style>
