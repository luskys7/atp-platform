<template>
  <div class="page-container rq-page" v-loading="loading">
    <!-- 模块 1：标题 -->
    <div class="rq-header">
      <div>
        <h2 class="rq-title">录屏质量审计</h2>
        <p class="rq-sub">自动化录制用例指标审计，统一校验控件识别率、启动性能、CPU 负载，满足 PO 上线验收标准</p>
      </div>
      <div class="rq-actions">
        <el-select v-model="sampleLimit" style="width:130px" @change="loadAudit">
          <el-option :value="10" label="10 样本" />
          <el-option :value="20" label="20 样本" />
          <el-option :value="50" label="50 样本" />
          <el-option :value="100" label="100 样本" />
        </el-select>
        <el-button type="primary" plain @click="exportAll">导出全量审计报告</el-button>
        <el-button :loading="loading" @click="loadAudit">刷新数据</el-button>
        <el-button @click="openThresholdDlg">自定义验收阈值</el-button>
      </div>
    </div>

    <!-- 模块 2：验收门禁 -->
    <section v-if="p0" class="gate-card" :class="p0.overall_passed ? 'is-ok' : 'is-warn'">
      <div class="gate-head">
        <el-icon class="gate-icon" :size="22"><WarningFilled /></el-icon>
        <div>
          <div class="gate-title">
            {{ p0.overall_passed ? '验收门禁・当前指标全部达标' : '验收门禁・当前存在未达标指标' }}
          </div>
          <div class="gate-tags">
            <el-tag size="small" :type="audit.recognition_passed ? 'success' : 'danger'" effect="dark">
              {{ audit.recognition_passed ? '识别率达标' : '识别率未达标' }}
            </el-tag>
            <el-tag size="small" :type="p0.startup_passed ? 'success' : 'danger'" effect="dark">
              {{ p0.startup_passed ? '启动≤3s 达标' : '启动≤3s 未达标' }}
            </el-tag>
            <el-tag size="small" :type="p0.cpu_passed ? 'success' : 'danger'" effect="dark">
              {{ p0.cpu_passed ? 'CPU 采样达标' : 'CPU 采样未达标' }}
            </el-tag>
          </div>
        </div>
      </div>
      <div class="gate-stats">
        <div class="gs-item">
          <span class="gs-label">最大启动耗时</span>
          <span class="gs-value">{{ p0.max_startup_ms != null ? `${p0.max_startup_ms} ms` : '—' }}</span>
        </div>
        <div class="gs-item">
          <span class="gs-label">超 3 秒启动会话</span>
          <span class="gs-value">{{ audit.sessions_over_startup?.length || 0 }} 个</span>
        </div>
        <div class="gs-item">
          <span class="gs-label">≥5 分钟长录制会话</span>
          <span class="gs-value">{{ audit.long_session_count || 0 }} 个</span>
        </div>
        <div class="gs-item">
          <span class="gs-label">未通过项</span>
          <span class="gs-value warn">{{ failedChecksText }}</span>
        </div>
      </div>
      <div class="gate-actions">
        <el-button type="warning" plain size="small" @click="filterLowRecognition">查看所有低识别率会话</el-button>
      </div>
      <p v-if="optimizeHint" class="gate-hint">{{ optimizeHint }}</p>
    </section>

    <!-- 模块 3：统计卡片 -->
    <div class="overview-grid">
      <div class="ov-card tone-blue">
        <div class="ov-label">审计样本总数</div>
        <div class="ov-value">{{ audit.sample_count || 0 }}</div>
        <div class="ov-desc">本次参与质量校验的录制会话总量</div>
      </div>
      <div class="ov-card" :class="recognitionOk ? 'tone-cream' : 'tone-alert'">
        <div class="ov-label">平均控件识别率</div>
        <div class="ov-value" :class="{ 'is-warn': !recognitionOk }">
          {{ audit.avg_recognition_rate != null ? `${audit.avg_recognition_rate}%` : '—' }}
        </div>
        <div class="ov-desc">所有会话控件识别命中均值</div>
      </div>
      <div class="ov-card tone-white">
        <div class="ov-label">平均启动耗时</div>
        <div class="ov-value">{{ audit.avg_startup_ms != null ? `${audit.avg_startup_ms} ms` : '—' }}</div>
        <div class="ov-desc">应用冷启动平均加载时长</div>
      </div>
      <div class="ov-card" :class="cpuOk ? 'tone-green' : 'tone-alert'">
        <div class="ov-label">CPU 性能达标率</div>
        <div class="ov-value" :class="{ 'is-warn': !cpuOk }">
          {{ audit.cpu_ok_rate != null ? `${audit.cpu_ok_rate}%` : '—' }}
        </div>
        <div class="ov-desc">录制过程 CPU 负载满足标准会话占比</div>
      </div>
    </div>

    <!-- 模块 4：双栏 -->
    <div class="split-row">
      <section class="panel">
        <div class="panel-title">性能等级分布</div>
        <div ref="gradeChartRef" class="chart-box" />
        <div class="grade-legend">
          <button
            v-for="g in gradeLegend"
            :key="g.key"
            type="button"
            class="legend-btn"
            :class="[`tone-${g.key}`, { active: filters.grade === g.key }]"
            @click="toggleGradeFilter(g.key)"
          >
            <span class="dot" />
            {{ g.label }} {{ g.pct }}%
          </button>
        </div>
        <el-empty v-if="!hasGradeData" description="暂无性能采样数据" :image-size="64" />
      </section>
      <section class="panel">
        <div class="panel-title">验收阈值面板（点击可筛选）</div>
        <div class="threshold-list">
          <button type="button" class="th-row" @click="filterLowRecognition">
            <span>控件识别率阈值</span>
            <span :class="audit.recognition_passed ? 'ok' : 'bad'">
              ≥{{ audit.min_recognition_rate ?? thresholdForm.min_recognition_rate }}%
              {{ audit.recognition_passed ? '已达标' : '未达标' }}
            </span>
          </button>
          <button type="button" class="th-row" @click="onLocatorHint">
            <span>控件定位命中率阈值</span>
            <span>≥{{ audit.min_locator_hit_rate ?? thresholdForm.min_locator_hit_rate }}%（单会话审阅页逐一校验）</span>
          </button>
          <button type="button" class="th-row" @click="filters.passStatus = cpuOk ? 'all' : 'fail'; applyFilter()">
            <span>CPU 性能分级标准</span>
            <span :class="cpuOk ? 'ok' : 'bad'">长任务采样，分优 / 一般 / 差，整体达标率≥80%</span>
          </button>
          <button type="button" class="th-row" @click="filterSlowStartup">
            <span>应用启动耗时 PO 标准</span>
            <span :class="p0?.startup_passed ? 'ok' : 'bad'">≤3000ms（冷启动弹窗加载场景）</span>
          </button>
          <button type="button" class="th-row" @click="filterLowRecognition">
            <span>低于识别阈值预警线</span>
            <span :class="(audit.sessions_below_threshold?.length || 0) > 3 ? 'bad' : 'ok'">
              单次审计超过 3 个会话则判定整体不通过（当前 {{ audit.sessions_below_threshold?.length || 0 }} 个）
            </span>
          </button>
        </div>
      </section>
    </div>

    <!-- 模块 5：筛选 -->
    <section class="filter-bar">
      <el-select v-model="filters.module" clearable filterable size="small" placeholder="业务模块" style="width:150px">
        <el-option label="全部模块" value="" />
        <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
      </el-select>
      <el-select v-model="filters.rateRange" clearable size="small" placeholder="识别率区间" style="width:150px">
        <el-option label="全部识别率" value="" />
        <el-option label="0%~50%" value="0-50" />
        <el-option label="50%~100%" value="50-100" />
        <el-option label="100% 合格" value="100" />
      </el-select>
      <el-select v-model="filters.grade" clearable size="small" placeholder="性能等级" style="width:130px">
        <el-option label="全部" value="" />
        <el-option label="性能优" value="good" />
        <el-option label="性能一般" value="fair" />
        <el-option label="性能差" value="heavy" />
      </el-select>
      <el-select v-model="filters.duration" clearable size="small" placeholder="录制时长" style="width:150px">
        <el-option label="全部时长" value="" />
        <el-option label="短录制 (&lt;5min)" value="short" />
        <el-option label="长录制 (≥5min)" value="long" />
      </el-select>
      <el-select v-model="filters.passStatus" clearable size="small" placeholder="达标状态" style="width:130px">
        <el-option label="全部" value="" />
        <el-option label="PO 未达标" value="fail" />
        <el-option label="全部达标" value="pass" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置筛选</el-button>
      <el-button type="primary" size="small" @click="applyFilter">查询检索</el-button>
    </section>

    <!-- 模块 6：表格 -->
    <section class="table-panel">
      <div class="table-toolbar" v-if="selected.length">
        <span>已选 {{ selected.length }} 条</span>
        <el-button size="small" @click="batchExport">批量导出</el-button>
        <el-button size="small" type="warning" plain @click="batchMarkOptimize">批量标记待优化</el-button>
      </div>
      <el-table
        :data="pagedRows"
        stripe
        size="small"
        row-key="id"
        :row-class-name="rowClassName"
        max-height="480"
        @selection-change="rows => selected = rows"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="会话 ID" width="90" />
        <el-table-column label="业务模块" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.module_name || '未标注模块' }}</template>
        </el-table-column>
        <el-table-column label="绑定测试任务" width="120">
          <template #default="{ row }">
            <router-link v-if="row.task_id" :to="`/tasks/${row.task_id}`">任务 #{{ row.task_id }}</router-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="step_count" label="总步骤数" width="90" />
        <el-table-column label="控件识别率" width="120">
          <template #default="{ row }">
            <span v-if="row.recognition_rate != null" :class="rateClass(row.recognition_rate)">
              {{ formatRate(row.recognition_rate) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="定位命中率" width="110">
          <template #default="{ row }">
            {{ row.locator_hit_rate != null ? `${row.locator_hit_rate}%` : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="性能等级" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.performance_grade" size="small" :type="gradeType(row.performance_grade)">
              {{ gradeLabel(row.performance_grade) }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="冷启动耗时" width="120">
          <template #default="{ row }">
            <span v-if="row.startup_ms != null" :class="{ 'cell-warn': row.startup_ms > 3000 }">
              {{ row.startup_ms }} ms
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="录制总时长" width="120">
          <template #default="{ row }">
            <span :class="{ 'cell-long': (row.duration_seconds || 0) >= 300 }">
              {{ formatDuration(row.duration_seconds) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="单会话操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openReview(row)">审阅</el-button>
            <el-button type="primary" link size="small" @click="exportOne(row)">导出单会话报告</el-button>
            <el-button
              :type="isMarked(row.id) ? 'warning' : 'info'"
              link
              size="small"
              @click="toggleMark(row)"
            >{{ isMarked(row.id) ? '已标记' : '标记待优化' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 模块 7：分页 + 说明 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="filteredSessions.length"
        layout="total, sizes, prev, pager, next"
      />
      <span class="pager-extra">当前筛选 {{ filteredSessions.length }} 个会话</span>
    </div>

    <section class="guide-panel">
      <div class="guide-title">录屏质量审计标准说明</div>
      <div class="guide-grid">
        <div>
          <div class="guide-h">1. 各指标业务定义</div>
          <ul>
            <li>控件识别率：录制回放时自动匹配到拾取控件的步骤占比，PO 验收要求 100%</li>
            <li>定位命中率：页面元素唯一匹配成功率，动态弹窗、浮动广告会降低命中率</li>
            <li>CPU 性能：录制过程主线程卡顿采样，长卡顿判定为性能差，影响回放稳定性</li>
            <li>启动耗时：APP 冷启动至首页完全渲染完成时长，PO 上限 3000ms</li>
          </ul>
        </div>
        <div>
          <div class="guide-h">2. PO 验收通过规则</div>
          <p>全部会话识别率 100%、启动均≤3s、CPU 达标率≥80%、长录制会话无性能卡顿，整体审计判定通过；任意一项不满足则门禁拦截。</p>
        </div>
        <div>
          <div class="guide-h">3. 优化整改建议</div>
          <ul>
            <li>识别率低：重新拾取遮挡弹窗控件、减少动态浮窗、统一页面元素 ID</li>
            <li>启动超时：优化 APP 冷启动资源加载，录制前增加等待步骤</li>
          </ul>
        </div>
      </div>
    </section>

    <!-- 自定义阈值 -->
    <el-dialog v-model="thresholdVisible" title="自定义验收阈值" width="480px" destroy-on-close>
      <el-form label-width="140px">
        <el-form-item label="控件识别率 (%)">
          <el-input-number v-model="thresholdForm.min_recognition_rate" :min="50" :max="100" />
        </el-form-item>
        <el-form-item label="定位命中率 (%)">
          <el-input-number v-model="thresholdForm.min_locator_hit_rate" :min="50" :max="100" />
        </el-form-item>
        <el-form-item label="CPU 长任务上限">
          <el-input-number v-model="thresholdForm.max_long_tasks_per_min" :min="1" :max="20" />
          <span class="form-hint">次 / 分钟</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="thresholdVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingThreshold" @click="saveThreshold">保存并生效</el-button>
      </template>
    </el-dialog>

    <!-- 审阅弹窗 -->
    <el-dialog v-model="reviewVisible" title="单会话录屏审阅" width="820px" top="5vh" destroy-on-close>
      <div v-loading="reviewLoading">
        <div class="review-meta" v-if="reviewRow">
          <span>会话 #{{ reviewRow.id }}</span>
          <span>{{ reviewRow.module_name || '未标注模块' }}</span>
          <span>识别率 {{ reviewRow.recognition_rate != null ? reviewRow.recognition_rate + '%' : '—' }}</span>
          <span>步骤 {{ reviewRow.step_count ?? 0 }}</span>
        </div>
        <el-alert
          v-if="reviewRow && reviewRow.recognition_rate != null && reviewRow.recognition_rate < minRate"
          type="warning"
          :closable="false"
          show-icon
          title="该会话识别率未达验收阈值，请重点核查失败控件步骤"
          style="margin-bottom:12px"
        />
        <el-table :data="reviewSteps" size="small" max-height="420" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="type" label="步骤类型" width="100" />
          <el-table-column prop="element_name" label="控件名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="识别状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.recognized === false ? 'danger' : 'success'">
                {{ row.recognized === false ? '未识别' : '已识别' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="locator_type" label="定位类型" width="100" />
          <el-table-column prop="locator_value" label="定位表达式" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">关闭</el-button>
        <el-button type="primary" @click="goFullReview">打开完整审阅页</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { recordApi, recordingApi } from '@/api'
import { invalidateRecordingFeatures } from '@/composables/useRecordingFeatures'
import * as echarts from 'echarts'
import { CHART } from '@/utils/chartTheme'
import { ElMessage } from 'element-plus'

const MARK_KEY = 'atp_recording_optimize_tags'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const sampleLimit = ref(10)
const audit = ref({})
const gradeChartRef = ref()
let gradeChart = null

const page = ref(1)
const pageSize = ref(20)
const selected = ref([])
const markedIds = ref(loadMarks())

const filters = reactive({
  module: '',
  rateRange: '',
  grade: '',
  duration: '',
  passStatus: '',
  lowRecognition: false
})

const thresholdVisible = ref(false)
const savingThreshold = ref(false)
const thresholdForm = reactive({
  min_recognition_rate: 95,
  min_locator_hit_rate: 98,
  max_long_tasks_per_min: 2
})

const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewRow = ref(null)
const reviewSteps = ref([])

const p0 = computed(() => audit.value.p0_acceptance || null)
const minRate = computed(() => Number(audit.value.min_recognition_rate || thresholdForm.min_recognition_rate || 95))

const recognitionOk = computed(() => {
  const rate = audit.value.avg_recognition_rate
  if (rate == null) return true
  return rate >= minRate.value
})

const cpuOk = computed(() => {
  const rate = audit.value.cpu_ok_rate
  if (rate == null) return true
  return rate >= 80
})

const failedChecksText = computed(() => {
  const list = p0.value?.failed_checks || []
  if (!list.length) return '无'
  const map = {
    recognition_rate_below_threshold: '控件识别率低于验收阈值',
    startup_over_3000ms: '启动耗时超过 3000ms',
    cpu_ok_rate_below_80: 'CPU 达标率低于 80%'
  }
  return list.map((k) => map[k] || k).join('；')
})

const optimizeHint = computed(() => {
  const rate = audit.value.avg_recognition_rate
  if (rate == null || rate >= minRate.value) return ''
  return `当前识别率均值仅 ${rate}%，建议优化控件拾取、减少动态弹窗遮挡`
})

const sessions = computed(() => audit.value.sessions || [])

const moduleOptions = computed(() => {
  const set = new Set()
  for (const s of sessions.value) {
    if (s.module_name) set.add(s.module_name)
  }
  ;['登录模块', '首页', '支付模块'].forEach((m) => set.add(m))
  return [...set]
})

const gradeCounts = computed(() => audit.value.performance_grade_counts || { good: 0, fair: 0, heavy: 0 })
const gradeTotal = computed(() => {
  const c = gradeCounts.value
  return (c.good || 0) + (c.fair || 0) + (c.heavy || 0)
})
const hasGradeData = computed(() => gradeTotal.value > 0)

const gradeLegend = computed(() => {
  const total = gradeTotal.value || 1
  const c = gradeCounts.value
  return [
    { key: 'good', label: '性能优', pct: Math.round(((c.good || 0) * 1000) / total) / 10, count: c.good || 0 },
    { key: 'fair', label: '性能一般', pct: Math.round(((c.fair || 0) * 1000) / total) / 10, count: c.fair || 0 },
    { key: 'heavy', label: '性能差', pct: Math.round(((c.heavy || 0) * 1000) / total) / 10, count: c.heavy || 0 }
  ]
})

function sessionPass(row) {
  const rateOk = row.recognition_rate == null || row.recognition_rate >= minRate.value
  const startupOk = row.startup_ms == null || row.startup_ms <= 3000
  const cpuSessionOk = row.cpu_ok !== false && row.performance_grade !== 'heavy'
  return rateOk && startupOk && cpuSessionOk
}

const filteredSessions = computed(() => {
  return sessions.value.filter((row) => {
    if (filters.lowRecognition && !(row.recognition_rate != null && row.recognition_rate < minRate.value)) return false
    if (filters.module && row.module_name !== filters.module) return false
    if (filters.grade && row.performance_grade !== filters.grade) return false
    if (filters.duration === 'short' && (row.duration_seconds || 0) >= 300) return false
    if (filters.duration === 'long' && (row.duration_seconds || 0) < 300) return false
    if (filters.passStatus === 'pass' && !sessionPass(row)) return false
    if (filters.passStatus === 'fail' && sessionPass(row)) return false
    const rate = row.recognition_rate
    if (filters.rateRange === '0-50' && !(rate != null && rate < 50)) return false
    if (filters.rateRange === '50-100' && !(rate != null && rate >= 50 && rate < 100)) return false
    if (filters.rateRange === '100' && !(rate != null && rate >= 100)) return false
    return true
  })
})

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredSessions.value.slice(start, start + pageSize.value)
})

function loadMarks() {
  try {
    return JSON.parse(localStorage.getItem(MARK_KEY) || '[]')
  } catch {
    return []
  }
}

function saveMarks() {
  localStorage.setItem(MARK_KEY, JSON.stringify(markedIds.value))
}

function isMarked(id) {
  return markedIds.value.includes(id)
}

function gradeLabel(g) {
  return { good: '性能优', fair: '性能一般', heavy: '性能差' }[g] || g
}

function gradeType(g) {
  return { good: 'success', fair: 'warning', heavy: 'danger' }[g] || 'info'
}

function formatRate(rate) {
  if (rate == null) return '—'
  if (rate === 0) return '0%'
  return `${rate}%`
}

function rateClass(rate) {
  if (rate >= 100 || rate >= minRate.value) return 'rate-ok'
  return 'rate-bad'
}

function formatDuration(sec) {
  if (sec == null) return '—'
  if (sec < 60) return `${sec} 秒`
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return s ? `${m} 分 ${s} 秒` : `${m} 分钟`
}

function rowClassName({ row }) {
  return row.recognition_rate != null && row.recognition_rate < minRate.value ? 'risk-row' : ''
}

function applyFilter() {
  page.value = 1
}

function resetFilters() {
  filters.module = ''
  filters.rateRange = ''
  filters.grade = ''
  filters.duration = ''
  filters.passStatus = ''
  filters.lowRecognition = false
  page.value = 1
}

function filterLowRecognition() {
  filters.module = ''
  filters.grade = ''
  filters.duration = ''
  filters.rateRange = ''
  filters.passStatus = ''
  filters.lowRecognition = true
  page.value = 1
  ElMessage.success('已筛选所有低识别率会话')
}

function onLocatorHint() {
  ElMessage.info('可在单会话审阅页逐一校验定位命中率')
}

function filterSlowStartup() {
  filters.passStatus = 'fail'
  page.value = 1
  ElMessage.success('已筛选未达标会话，可在表格中查看启动超时项')
}

function toggleGradeFilter(key) {
  filters.grade = filters.grade === key ? '' : key
  page.value = 1
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

function sessionRowCsv(row) {
  return [
    row.id,
    row.module_name || '',
    row.task_id || '',
    row.step_count ?? '',
    row.recognition_rate ?? '',
    row.locator_hit_rate ?? '',
    gradeLabel(row.performance_grade),
    row.startup_ms ?? '',
    row.duration_seconds ?? '',
    isMarked(row.id) ? '待优化' : ''
  ]
}

const CSV_HEADER = ['会话ID', '业务模块', '绑定任务', '总步骤数', '控件识别率', '定位命中率', '性能等级', '冷启动耗时', '录制总时长', '整改标记']

function exportAll() {
  const list = filteredSessions.value
  downloadCsv(`录屏质量审计报告_${new Date().toISOString().slice(0, 10)}.csv`, CSV_HEADER, list.map(sessionRowCsv))
  ElMessage.success(`已导出 ${list.length} 条会话审计报告`)
}

function exportOne(row) {
  downloadCsv(`录屏会话_${row.id}_质量报告.csv`, CSV_HEADER, [sessionRowCsv(row)])
  ElMessage.success(`已导出会话 #${row.id}`)
}

function batchExport() {
  if (!selected.value.length) return
  downloadCsv(`录屏质量批量导出_${Date.now()}.csv`, CSV_HEADER, selected.value.map(sessionRowCsv))
  ElMessage.success(`已批量导出 ${selected.value.length} 条`)
}

function toggleMark(row) {
  const id = row.id
  if (isMarked(id)) {
    markedIds.value = markedIds.value.filter((x) => x !== id)
    ElMessage.success('已取消待优化标记')
  } else {
    markedIds.value = [...markedIds.value, id]
    ElMessage.success('已标记待优化')
  }
  saveMarks()
}

function batchMarkOptimize() {
  const ids = selected.value.map((r) => r.id)
  const set = new Set(markedIds.value)
  ids.forEach((id) => set.add(id))
  markedIds.value = [...set]
  saveMarks()
  ElMessage.success(`已批量标记 ${ids.length} 条待优化`)
}

async function openReview(row) {
  reviewRow.value = row
  reviewVisible.value = true
  reviewLoading.value = true
  reviewSteps.value = []
  try {
    const res = await recordApi.get(row.id)
    const data = res.data || {}
    let steps = []
    if (Array.isArray(data.steps)) steps = data.steps
    else if (data.steps_json) {
      try {
        const parsed = typeof data.steps_json === 'string' ? JSON.parse(data.steps_json) : data.steps_json
        steps = parsed.steps || []
      } catch { steps = [] }
    }
    reviewSteps.value = steps.map((s) => ({
      type: s.type || s.action || '—',
      element_name: s.element_name || s.name || '—',
      recognized: s.recognized !== false && s.control_id != null ? true : s.recognized,
      locator_type: s.locator_type || s.locator?.type || '—',
      locator_value: s.locator_value || s.locator?.value || s.xpath || '—'
    }))
  } catch (e) {
    ElMessage.error(e?.message || '加载会话详情失败')
  } finally {
    reviewLoading.value = false
  }
}

function goFullReview() {
  if (!reviewRow.value) return
  reviewVisible.value = false
  router.push(`/recordings/review/${reviewRow.value.id}`)
}

function openThresholdDlg() {
  thresholdForm.min_recognition_rate = Number(audit.value.min_recognition_rate || 95)
  thresholdForm.min_locator_hit_rate = Number(audit.value.min_locator_hit_rate || 98)
  thresholdVisible.value = true
}

async function saveThreshold() {
  savingThreshold.value = true
  try {
    await recordingApi.updateFeatures({
      min_recognition_rate: thresholdForm.min_recognition_rate,
      min_locator_hit_rate: thresholdForm.min_locator_hit_rate,
      max_long_tasks_per_min: thresholdForm.max_long_tasks_per_min
    })
    invalidateRecordingFeatures()
    ElMessage.success('验收阈值已更新')
    thresholdVisible.value = false
    await loadAudit()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingThreshold.value = false
  }
}

function renderGradeChart() {
  if (!gradeChartRef.value) return
  if (!gradeChart) gradeChart = echarts.init(gradeChartRef.value)
  const data = gradeLegend.value.map((g) => ({
    name: g.label,
    value: g.count,
    itemStyle: {
      color: g.key === 'good' ? '#22c55e' : g.key === 'fair' ? '#f59e0b' : '#ef4444'
    }
  }))
  gradeChart.setOption({
    color: CHART.device,
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>会话数量：${p.value}<br/>占比：${p.percent}%`
    },
    legend: { show: false },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      avoidLabelOverlap: true,
      label: {
        formatter: (p) => (p.value > 0 ? `${p.name}\n${p.percent}%` : ''),
        fontSize: 11
      },
      data: hasGradeData.value ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e2e8f0' }, label: { show: false } }]
    }]
  })
}

async function loadAudit() {
  loading.value = true
  try {
    const res = await recordApi.auditSummary(sampleLimit.value)
    audit.value = res.data || {}
    await nextTick()
    renderGradeChart()
  } catch (e) {
    audit.value = {}
    ElMessage.error(e?.message || '加载审计数据失败')
  } finally {
    loading.value = false
  }
}

watch(gradeLegend, () => renderGradeChart(), { deep: true })
watch(filteredSessions, () => {
  const maxPage = Math.max(1, Math.ceil(filteredSessions.value.length / pageSize.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

onMounted(async () => {
  await loadAudit()
  if (route.query.focus === 'low') {
    filterLowRecognition()
  }
  window.addEventListener('resize', () => gradeChart?.resize())
})

onUnmounted(() => {
  gradeChart?.dispose()
  gradeChart = null
})
</script>

<style scoped>
.rq-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rq-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  align-items: flex-start;
}
.rq-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}
.rq-sub {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  max-width: 680px;
}
.rq-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.gate-card {
  border-radius: 12px;
  padding: 16px 18px;
  border: 1px solid #fed7aa;
  background: #fff7ed;
}
.gate-card.is-ok {
  background: #ecfdf5;
  border-color: #a7f3d0;
}
.gate-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.gate-icon { color: #ea580c; margin-top: 2px; }
.gate-card.is-ok .gate-icon { color: #16a34a; }
.gate-title {
  font-size: 15px;
  font-weight: 700;
  color: #9a3412;
  margin-bottom: 8px;
}
.gate-card.is-ok .gate-title { color: #166534; }
.gate-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.gate-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 14px;
}
@media (max-width: 900px) {
  .gate-stats { grid-template-columns: repeat(2, 1fr); }
}
.gs-item {
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  padding: 10px 12px;
}
.gs-label { display: block; font-size: 12px; color: #78716c; margin-bottom: 4px; }
.gs-value { font-size: 14px; font-weight: 700; color: #1c1917; }
.gs-value.warn { color: #c2410c; }
.gate-actions { margin-top: 12px; }
.gate-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #9a3412;
  line-height: 1.5;
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
  border-radius: 12px;
  padding: 14px 16px;
  min-height: 100px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.ov-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.ov-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.ov-value { font-size: 26px; font-weight: 700; color: #0f172a; }
.ov-value.is-warn { color: #c2410c; }
.ov-desc { margin-top: 8px; font-size: 12px; color: #64748b; }
.tone-blue { background: #eff6ff; border-color: #bfdbfe; }
.tone-cream { background: #fffbeb; border-color: #fde68a; }
.tone-alert { background: #fff1f2; border-color: #fecdd3; }
.tone-white { background: #f8fafc; border-color: #e2e8f0; }
.tone-green { background: #ecfdf5; border-color: #a7f3d0; }

.split-row {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 12px;
}
@media (max-width: 960px) {
  .split-row { grid-template-columns: 1fr; }
}
.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
}
.panel-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
  color: #0f172a;
}
.chart-box { height: 240px; }
.grade-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.legend-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  color: #475569;
}
.legend-btn.active { border-color: #3b82f6; background: #eff6ff; color: #1d4ed8; }
.legend-btn .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}
.legend-btn.tone-good .dot { background: #22c55e; }
.legend-btn.tone-fair .dot { background: #f59e0b; }
.legend-btn.tone-heavy .dot { background: #ef4444; }

.threshold-list { display: flex; flex-direction: column; gap: 8px; }
.th-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  width: 100%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 12px;
  color: #334155;
  transition: background 0.15s ease;
}
.th-row:hover { background: #eff6ff; border-color: #bfdbfe; }
.th-row span:first-child { font-weight: 600; flex-shrink: 0; }
.th-row .ok { color: #16a34a; }
.th-row .bad { color: #c2410c; font-weight: 600; }

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.table-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #475569;
}
.rate-ok { color: #16a34a; font-weight: 600; }
.rate-bad { color: #ea580c; font-weight: 600; }
.cell-warn { color: #c2410c; font-weight: 700; }
.cell-long {
  background: #fef9c3;
  padding: 2px 6px;
  border-radius: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.pager-extra { font-size: 12px; color: #64748b; }

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
  margin-bottom: 10px;
}
.guide-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  font-size: 12px;
  color: #334155;
  line-height: 1.65;
}
@media (max-width: 960px) {
  .guide-grid { grid-template-columns: 1fr; }
}
.guide-h { font-weight: 700; color: #1e3a8a; margin-bottom: 4px; }
.guide-grid ul { margin: 4px 0; padding-left: 18px; }
.guide-grid p { margin: 4px 0; }

.form-hint { margin-left: 8px; font-size: 12px; color: #64748b; }
.review-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #475569;
}

:deep(.risk-row) { --el-table-tr-bg-color: #fff7ed; }
:deep(.el-table .risk-row td.el-table__cell) { background: #fff7ed !important; }
</style>
