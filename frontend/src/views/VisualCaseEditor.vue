<template>
  <div class="page-container">
    <PageHeader
      :title="taskId ? '编辑可视化用例' : '新建可视化用例'"
      subtitle="拖拽式步骤编排，自动生成 Python 脚本"
    >
      <template #actions>
        <el-button v-if="isAssetMode && taskId" @click="openDebugWorkbench">同屏调试</el-button>
        <el-button @click="previewScript">预览 Python</el-button>
        <el-button v-if="isAssetMode" type="warning" :loading="debugging" @click="debugRun">调试执行</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="10">
        <AppCard title="用例信息" :hover="false">
          <el-form :model="meta" label-width="90px">
            <el-form-item label="名称" required>
              <el-input v-model="meta.name" />
            </el-form-item>
            <el-form-item label="平台">
              <el-select v-model="meta.platform" style="width:100%">
                <el-option label="Android" value="android" />
                <el-option label="iOS" value="ios" />
              </el-select>
            </el-form-item>
            <el-form-item label="应用包名">
              <el-input v-model="meta.app_package" />
            </el-form-item>
            <el-form-item label="超时(秒)">
              <el-input-number v-model="meta.timeout_seconds" :min="60" :max="7200" style="width:100%" />
            </el-form-item>
            <el-form-item label="录屏">
              <el-switch v-model="meta.enable_recording" />
            </el-form-item>
            <el-form-item label="真人模拟">
              <el-switch v-model="meta.human_delay" />
            </el-form-item>
            <el-form-item label="等待模板">
              <el-select v-model="meta.wait_template" clearable placeholder="标准" style="width:100%">
                <el-option label="冒烟（快）" value="smoke" />
                <el-option label="标准" value="standard" />
                <el-option label="弱网（慢）" value="weak_network" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!isAssetMode" label="账号池互斥">
              <el-switch v-model="meta.use_account_pool" />
            </el-form-item>
            <el-form-item v-if="isAssetMode" label="用例状态">
              <el-select v-model="meta.case_status" style="width:100%">
                <el-option label="草稿" value="draft" />
                <el-option label="待评审" value="review" />
                <el-option label="生效" value="active" />
              </el-select>
            </el-form-item>
          </el-form>
        </AppCard>

        <AppCard title="添加步骤" :hover="false" style="margin-top:20px">
          <div class="quick-step-bar">
            <span class="quick-step-label">快捷添加：</span>
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'disk' })">清理磁盘缓存</el-button>
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'memory' })">杀进程</el-button>
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'all' })">清理全部缓存</el-button>
            <el-button size="small" @click="quickAddStep('force_stop')">强制停止应用</el-button>
          </div>
          <el-form :model="newStep" label-width="90px" size="small" style="margin-top:12px">
            <el-form-item label="类型">
              <el-select v-model="newStep.type" filterable style="width:100%" placeholder="选择或搜索步骤类型">
                <el-option-group label="基础操作">
                  <el-option label="等待" value="wait" />
                  <el-option label="点击控件" value="click" />
                  <el-option label="输入文本" value="input" />
                  <el-option label="滑动" value="swipe" />
                </el-option-group>
                <el-option-group label="应用生命周期">
                  <el-option label="启动应用" value="launch" />
                  <el-option label="清理缓存" value="clear_cache" />
                  <el-option label="强制停止应用" value="force_stop" />
                  <el-option label="切换 WebView 上下文" value="switch_context" />
                  <el-option label="回收运行时权限" value="revoke_permissions" />
                </el-option-group>
                <el-option-group label="系统操作">
                  <el-option label="系统按键" value="press_key" />
                  <el-option label="设置剪贴板" value="clipboard_set" />
                  <el-option label="亮屏" value="wake_screen" />
                  <el-option label="锁屏" value="lock_screen" />
                </el-option-group>
                <el-option-group label="断言校验">
                  <el-option label="断言文本" value="assert_text" />
                  <el-option label="断言控件存在" value="assert_exists" />
                  <el-option label="OCR 文本断言" value="assert_ocr" />
                  <el-option label="OCR 点击" value="tap_ocr" />
                  <el-option label="断言 Toast" value="assert_toast" />
                  <el-option label="HTTP 接口断言" value="assert_http" />
                  <el-option label="埋点校验" value="assert_analytics" />
                  <el-option label="组合断言" value="assert_composite" />
                  <el-option label="页面异常检测" value="check_anomaly" />
                  <el-option label="进程存活校验" value="assert_process" />
                  <el-option label="关闭系统弹窗" value="dismiss_popup" />
                  <el-option label="剪贴板断言" value="clipboard_assert" />
                  <el-option label="屏幕状态断言" value="assert_screen" />
                  <el-option label="按键响应断言" value="assert_key" />
                  <el-option label="音量断言" value="assert_volume" />
                  <el-option label="音量变更断言" value="assert_volume_change" />
                  <el-option label="图像相似度断言" value="assert_image" />
                </el-option-group>
                <el-option-group label="账号">
                  <el-option label="标记使用账号池" value="use_account_pool" />
                </el-option-group>
                <el-option-group label="弱网专项">
                  <el-option label="弱网模拟" value="network_profile" />
                  <el-option label="恢复网络" value="reset_network" />
                  <el-option label="崩溃捕获" value="capture_crash" />
                </el-option-group>
                <el-option-group label="专项测试">
                  <el-option label="切换语言" value="set_locale" />
                  <el-option label="性能采集" value="collect_performance" />
                  <el-option label="冷启动断言" value="assert_cold_start" />
                </el-option-group>
                <el-option-group label="流程控制">
                  <el-option label="人工介入等待" value="manual_wait" />
                </el-option-group>
                <el-option-group label="复用">
                  <el-option label="调用公共步骤" value="invoke_common" />
                  <el-option label="动态造数" value="data_factory" />
                </el-option-group>
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'wait'" label="秒数">
              <el-input-number v-model="newStep.seconds" :min="1" :max="60" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'click'" label="控件名">
              <el-input v-model="newStep.element_name" placeholder="login_button" />
            </el-form-item>
            <el-form-item v-if="['assert_text','assert_ocr','tap_ocr'].includes(newStep.type)" label="控件名">
              <el-input v-model="newStep.element_name" placeholder="可选" />
            </el-form-item>
            <el-form-item v-if="['assert_text','assert_ocr','tap_ocr','assert_toast'].includes(newStep.type)" label="期望文本">
              <el-input v-model="newStep.expected" placeholder="登录成功" />
            </el-form-item>
            <el-form-item label="失败重试">
              <el-input-number v-model="newStep.retry_count" :min="0" :max="5" />
            </el-form-item>
            <el-form-item label="失败策略">
              <el-select v-model="newStep.on_fail" style="width:100%">
                <el-option label="失败终止" value="fail" />
                <el-option label="跳过继续" value="skip" />
                <el-option label="重启应用后重试" value="restart_app" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'input'" label="文本">
              <el-input v-model="newStep.text" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'launch'" label="包名">
              <el-input v-model="newStep.app_package" :placeholder="meta.app_package" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'press_key' || newStep.type === 'assert_key'" label="按键">
              <el-select v-model="newStep.key" style="width:100%">
                <el-option label="返回 Back" value="back" />
                <el-option label="Home" value="home" />
                <el-option label="多任务 Recent" value="recent" />
                <el-option label="电源 Power" value="power" />
                <el-option label="音量+" value="volume_up" />
                <el-option label="音量-" value="volume_down" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'clipboard_set'" label="剪贴板文本">
              <el-input v-model="newStep.text" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'clipboard_assert'" label="期望内容">
              <el-input v-model="newStep.expected" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_screen'" label="屏幕状态">
              <el-select v-model="newStep.expected" style="width:100%">
                <el-option label="亮屏 on" value="on" />
                <el-option label="灭屏 off" value="off" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume'" label="期望音量">
              <el-input-number v-model="newStep.expected" :min="0" :max="15" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume'" label="音频流">
              <el-select v-model="newStep.stream" style="width:100%">
                <el-option label="媒体 music" value="music" />
                <el-option label="铃音 ring" value="ring" />
                <el-option label="闹钟 alarm" value="alarm" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume'" label="容差">
              <el-input-number v-model="newStep.tolerance" :min="0" :max="3" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume_change'" label="变更方向">
              <el-select v-model="newStep.direction" style="width:100%">
                <el-option label="音量增大 up" value="up" />
                <el-option label="音量减小 down" value="down" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume_change'" label="触发按键">
              <el-select v-model="newStep.key" style="width:100%">
                <el-option label="自动（按方向）" value="" />
                <el-option label="音量+" value="volume_up" />
                <el-option label="音量-" value="volume_down" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_volume_change'" label="音频流">
              <el-select v-model="newStep.stream" style="width:100%">
                <el-option label="媒体 music" value="music" />
                <el-option label="铃音 ring" value="ring" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'set_locale'" label="语言">
              <el-select v-model="newStep.locale" style="width:100%">
                <el-option label="简体中文" value="zh_cn" />
                <el-option label="繁体中文" value="zh_tw" />
                <el-option label="English" value="en_us" />
                <el-option label="日本語" value="ja_jp" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_cold_start'" label="最大耗时(ms)">
              <el-input-number v-model="newStep.max_ms" :min="500" :max="30000" :step="500" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'network_profile'" label="网络档位">
              <el-select v-model="newStep.profile" style="width:100%">
                <el-option label="2G 弱网" value="2g" />
                <el-option label="高延迟" value="high_latency" />
                <el-option label="丢包" value="lossy" />
                <el-option label="断网" value="offline" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'manual_wait'" label="提示语">
              <el-input v-model="newStep.prompt" type="textarea" :rows="2" placeholder="请完成验证码输入后点击继续" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'data_factory'" label="造数模板">
              <el-select v-model="newStep.template_id" filterable placeholder="选择模板" style="width:100%">
                <el-option v-for="t in dataFactoryTemplates" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_image'" label="模板路径">
              <el-input v-model="newStep.template_path" placeholder="./data/templates/home.png" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_image'" label="相似度阈值">
              <el-input-number v-model="newStep.threshold" :min="0.5" :max="1" :step="0.05" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'invoke_common'" label="公共步骤">
              <el-select v-model="newStep.common_step" filterable placeholder="选择公共步骤" style="width:100%">
                <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'invoke_common'" label="入参 JSON">
              <el-input v-model="newStep.input_params_json" type="textarea" :rows="2" placeholder='{"username":"test1","password":"123456"}' />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'check_anomaly'" label="检测项">
              <el-select v-model="newStep.check_types" style="width:100%">
                <el-option label="全部（黑屏/白屏/闪退）" value="all" />
                <el-option label="仅闪退" value="crash" />
                <el-option label="仅黑屏" value="black" />
                <el-option label="仅白屏" value="white" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'clear_cache'" label="清理模式">
              <el-select v-model="newStep.mode" style="width:100%">
                <el-option label="磁盘缓存" value="disk" />
                <el-option label="内存（杀进程）" value="memory" />
                <el-option label="全部" value="all" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'switch_context'" label="上下文">
              <el-select v-model="newStep.mode" style="width:100%">
                <el-option label="自动检测" value="auto" />
                <el-option label="原生 Native" value="native" />
                <el-option label="WebView / H5" value="webview" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_toast'" label="Toast 文本">
              <el-input v-model="newStep.expected" placeholder="登录成功" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_toast'" label="超时(秒)">
              <el-input-number v-model="newStep.timeout" :min="1" :max="30" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_http'" label="方法">
              <el-select v-model="newStep.method" style="width:100%">
                <el-option label="GET" value="GET" /><el-option label="POST" value="POST" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_http'" label="URL">
              <el-input v-model="newStep.url" placeholder="https://api.example.com/health" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_http'" label="期望状态码">
              <el-input-number v-model="newStep.expected_status" :min="100" :max="599" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_http'" label="Body 包含">
              <el-input v-model="newStep.body_contains" placeholder="可选" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_analytics'" label="事件名">
              <el-input v-model="newStep.event_name" placeholder="page_view" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_analytics'" label="属性 JSON">
              <el-input v-model="newStep.props_json" type="textarea" :rows="2" placeholder='{"page":"home"}' />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_analytics'" label="校验 URL">
              <el-input v-model="newStep.verify_url" placeholder="可选，埋点 mock 接口" />
            </el-form-item>
            <el-form-item v-if="newStep.type === 'assert_composite'" label="条件 JSON">
              <el-input v-model="newStep.conditions" type="textarea" :rows="4"
                placeholder='[{"type":"text","value":"首页"},{"type":"process"}]' />
            </el-form-item>
            <el-button type="primary" @click="addStep">添加步骤</el-button>
          </el-form>
        </AppCard>
      </el-col>

      <el-col :span="14">
        <AppCard :hover="false">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>步骤列表 ({{ steps.length }})</span>
              <el-tag type="info">拖拽排序用 ↑↓ 按钮</el-tag>
            </div>
          </template>
          <el-empty v-if="!steps.length" description="请添加测试步骤" />
          <div v-for="(step, idx) in steps" :key="step.id" class="step-item" :class="{ disabled: step.enabled === false }">
            <div class="step-index">{{ idx + 1 }}</div>
            <div class="step-body">
              <el-switch v-model="step.enabled" size="small" />
              <el-tag size="small">{{ stepTypeLabel(step.type) }}</el-tag>
              <span class="step-desc">{{ stepSummary(step) }}</span>
              <span v-if="stepLocator(step)" class="step-locator">{{ stepLocator(step) }}</span>
              <el-tag v-if="step.enabled === false" size="small" type="info">{{ step.disable_reason || '已禁用' }}</el-tag>
            </div>
            <div class="step-actions">
              <el-button size="small" plain @click="editDisable(step)">禁用说明</el-button>
              <el-button size="small" plain :disabled="idx === 0" @click="moveStep(idx, -1)">↑</el-button>
              <el-button size="small" plain :disabled="idx === steps.length - 1" @click="moveStep(idx, 1)">↓</el-button>
              <el-button size="small" type="danger" plain @click="removeStep(idx)">删</el-button>
            </div>
          </div>
        </AppCard>
      </el-col>
    </el-row>

    <el-dialog v-model="showPreview" title="生成的 Python 脚本" width="720px">
      <pre class="preview-code">{{ previewCode }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, caseApi, commonStepApi, dataFactoryApi } from '@/api'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id || null
const folderId = route.query.folder_id ? Number(route.query.folder_id) : null
const isAssetMode = route.query.asset === '1' || route.path.startsWith('/cases/')
const saving = ref(false)
const debugging = ref(false)
const showPreview = ref(false)
const previewCode = ref('')
const commonSteps = ref([])
const dataFactoryTemplates = ref([])
let stepSeq = 1

const meta = reactive({
  name: '可视化用例',
  platform: 'android',
  app_package: '',
  timeout_seconds: 3600,
  enable_recording: true,
  human_delay: false,
  wait_template: '',
  use_account_pool: false,
  case_status: 'draft',
  folder_id: null
})

const steps = ref([])

const newStep = reactive({
  type: 'wait',
  seconds: 2,
  element_name: '',
  text: '',
  app_package: '',
  expected: '',
  x1: 500, y1: 800, x2: 500, y2: 400,
  common_step: '', input_params_json: '', check_types: 'all', mode: 'disk',
  method: 'GET', url: '', expected_status: 200, body_contains: '', timeout: 5,
  event_name: '', props_json: '{}', verify_url: '',
  conditions: '[{"type":"text","value":"首页"}]',
  enabled: true, disable_reason: '', disable_mode: '',
  retry_count: 0, on_fail: 'fail', key: 'back', template_id: null, template_path: '', threshold: 0.85,
  prompt: '', profile: '2g', locale: 'zh_cn', max_ms: 5000
})

const typeLabels = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', launch: '启动', swipe: '滑动',
  assert_text: '断言', assert_exists: '断言存在', invoke_common: '公共步骤',
  check_anomaly: '异常检测', assert_process: '进程校验', clear_cache: '清理缓存', force_stop: '强制停止',
  assert_toast: 'Toast断言', assert_http: 'HTTP断言', assert_analytics: '埋点校验', assert_composite: '组合断言', dismiss_popup: '关弹窗',
  switch_context: '切换上下文', revoke_permissions: '权限回收',
  assert_ocr: 'OCR断言', tap_ocr: 'OCR点击',
  press_key: '系统按键', clipboard_set: '剪贴板', clipboard_assert: '剪贴板断言',
  wake_screen: '亮屏', lock_screen: '锁屏', assert_screen: '屏幕断言', assert_key: '按键断言',
  assert_volume: '音量断言', assert_volume_change: '音量变更断言',
  assert_image: '图像断言', data_factory: '动态造数', use_account_pool: '账号池',
  manual_wait: '人工介入', network_profile: '弱网模拟', reset_network: '恢复网络', capture_crash: '崩溃捕获',
  set_locale: '切换语言', collect_performance: '性能采集', assert_cold_start: '冷启动'
}

function stepTypeLabel(t) { return typeLabels[t] || t }

function stepLocator(step) {
  return formatStepLocator(step)
}

function stepSummary(step) {
  const target = formatStepTarget(step)
  switch (step.type) {
    case 'wait': return `${step.seconds}s`
    case 'click':
    case 'tap_xy':
    case 'long_press':
    case 'tap_ocr':
      return target || stepTypeLabel(step.type)
    case 'input': return step.text
    case 'launch': return step.app_package || meta.app_package
    case 'swipe': return target || `${step.x1},${step.y1}→${step.x2},${step.y2}`
    case 'assert_text': return `${step.element_name || ''} = ${step.expected}`
    case 'invoke_common': return step.common_step || '(未选)'
    case 'check_anomaly': return step.check_types || 'all'
    case 'assert_process': return meta.app_package || '当前应用'
    case 'clear_cache': return { disk: '磁盘缓存', memory: '内存', all: '全部' }[step.mode] || step.mode
    case 'force_stop': return meta.app_package || '当前应用'
    case 'assert_toast': return step.expected
    case 'assert_http': return `${step.method || 'GET'} ${step.url}`
    case 'assert_analytics': return step.event_name || '(未填事件)'
    case 'assert_composite': return '多条件'
    case 'dismiss_popup': return '系统弹窗'
    case 'switch_context': return { auto: '自动', native: 'Native', webview: 'WebView' }[step.mode] || step.mode
    case 'revoke_permissions': return 'pm revoke'
    case 'assert_ocr': return step.expected
    case 'tap_ocr': return step.expected
    case 'press_key': case 'assert_key': return step.key || 'back'
    case 'clipboard_set': return step.text
    case 'clipboard_assert': return step.expected
    case 'wake_screen': return '唤醒'
    case 'lock_screen': return '锁屏'
    case 'assert_screen': return step.expected || 'on'
    case 'assert_volume': return `${step.stream || 'music'}=${step.expected ?? 0}`
    case 'assert_volume_change': return `${step.direction || 'up'} ${step.key || 'auto'}`
    case 'assert_image': return `${step.template_path} ≥${step.threshold || 0.85}`
    case 'data_factory': return dataFactoryTemplates.value.find(t => t.id === step.template_id)?.name || `#${step.template_id}`
    case 'manual_wait': return step.prompt || '等待人工处理'
    case 'network_profile': return step.profile || '2g'
    case 'reset_network': return '恢复正常'
    case 'capture_crash': return '抓取崩溃日志'
    case 'set_locale': return step.locale || 'zh_cn'
    case 'collect_performance': return '内存/性能'
    case 'assert_cold_start': return `≤${step.max_ms || 5000}ms`
    default: return target || step.element_name || step.expected || stepTypeLabel(step.type)
  }
}

function quickAddStep(type, extra = {}) {
  const step = {
    id: stepSeq++, enabled: true, disable_reason: '', disable_mode: '', type,
    seconds: 2, element_name: '', text: '', app_package: '', expected: '',
    x1: 500, y1: 800, x2: 500, y2: 400, common_step: '', check_types: 'all',
    mode: 'disk', method: 'GET', url: '', expected_status: 200, body_contains: '',
    timeout: 5, conditions: '[{"type":"text","value":"首页"}]', retry_count: 0, on_fail: 'fail',
    key: 'back', ...extra
  }
  steps.value.push(step)
  ElMessage.success(`已添加「${stepTypeLabel(type)}」`)
}

function addStep() {
  const step = { id: stepSeq++, enabled: true, disable_reason: '', disable_mode: '', ...JSON.parse(JSON.stringify(newStep)) }
  if (step.type === 'invoke_common' && step.input_params_json) {
    try {
      step.input_params = JSON.parse(step.input_params_json)
    } catch {
      ElMessage.warning('入参 JSON 格式错误')
      return
    }
    delete step.input_params_json
  }
  if (step.type === 'clear_cache' && !step.mode) step.mode = 'disk'
  steps.value.push(step)
}

async function editDisable(step) {
  const { value } = await ElMessageBox.prompt('填写禁用原因（可选）', '步骤禁用说明', {
    inputValue: step.disable_reason || '',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (value !== null) {
    step.disable_reason = value
    step.disable_mode = 'temporary'
  }
}

function removeStep(idx) {
  steps.value.splice(idx, 1)
}

function moveStep(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= steps.value.length) return
  const arr = steps.value
  ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
}

function buildVisualJson() {
  const payload = {
    version: 1,
    human_delay: meta.human_delay,
    steps: steps.value.map(({ id, ...rest }) => rest)
  }
  if (meta.wait_template) payload.wait_template = meta.wait_template
  return JSON.stringify(payload)
}

function buildCasePayload() {
  const payload = {
    name: meta.name,
    platform: meta.platform,
    script_type: 'visual',
    steps_content: buildVisualJson(),
    app_package: meta.app_package,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    case_status: meta.case_status
  }
  const fid = meta.folder_id ?? folderId
  if (fid) payload.folder_id = fid
  return payload
}

function buildPayload() {
  return {
    name: meta.name,
    platform: meta.platform,
    script_type: 'visual',
    script_content: buildVisualJson(),
    app_package: meta.app_package,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    parallel_count: 1,
    use_account_pool: meta.use_account_pool
  }
}

async function debugRun() {
  if (!meta.name) {
    ElMessage.warning('请填写用例名称')
    return
  }
  if (!steps.value.length) {
    ElMessage.warning('请至少添加一个步骤')
    return
  }
  debugging.value = true
  try {
    let caseId = taskId
    if (caseId) {
      await caseApi.update(caseId, buildCasePayload())
    } else {
      const res = await caseApi.create(buildCasePayload())
      caseId = res.data.id
      router.replace(`/cases/editor/${caseId}?asset=1`)
    }
    const runRes = await caseApi.run(caseId)
    ElMessage.success(`调试任务 #${runRes.data.id} 已提交`)
    router.push(`/cases/${caseId}/debug?taskId=${runRes.data.id}`)
  } finally {
    debugging.value = false
  }
}

function openDebugWorkbench() {
  if (!taskId) return
  router.push(`/cases/${taskId}/debug`)
}

async function saveTask() {
  if (!meta.name) {
    ElMessage.warning('请填写用例名称')
    return
  }
  saving.value = true
  try {
    if (isAssetMode) {
      if (taskId) {
        await caseApi.update(taskId, buildCasePayload())
        ElMessage.success('用例已更新')
      } else {
        const res = await caseApi.create(buildCasePayload())
        ElMessage.success('用例已创建')
        router.replace(`/cases/editor/${res.data.id}?asset=1`)
      }
    } else if (taskId) {
      await taskApi.update(taskId, buildPayload())
      ElMessage.success('任务已更新')
    } else {
      const res = await taskApi.create(buildPayload())
      ElMessage.success('任务已创建')
      router.replace(`/cases/editor/${res.data.id}`)
    }
  } finally {
    saving.value = false
  }
}

async function previewScript() {
  const res = await taskApi.previewVisual(buildVisualJson())
  previewCode.value = res.data.script
  showPreview.value = true
}

async function loadTask() {
  if (!taskId) return
  if (isAssetMode) {
    const res = await caseApi.get(taskId)
    const c = res.data
    meta.name = c.name
    meta.platform = c.platform
    meta.app_package = c.app_package || ''
    meta.timeout_seconds = c.timeout_seconds
    meta.enable_recording = c.enable_recording
    meta.case_status = c.case_status || 'draft'
    meta.folder_id = c.folder_id || null
    try {
      const parsed = JSON.parse(c.steps_content)
      meta.human_delay = parsed.human_delay || false
      meta.wait_template = parsed.wait_template || ''
      steps.value = (parsed.steps || []).map(s => ({ id: stepSeq++, enabled: s.enabled !== false, disable_reason: s.disable_reason || '', ...s }))
    } catch { steps.value = [] }
    return
  }
  const res = await taskApi.get(taskId)
  const task = res.data
  meta.name = task.name
  meta.platform = task.platform
  meta.app_package = task.app_package || ''
  meta.timeout_seconds = task.timeout_seconds
  meta.enable_recording = task.enable_recording
  meta.use_account_pool = task.use_account_pool || false
  try {
    const parsed = JSON.parse(task.script_content)
    steps.value = (parsed.steps || []).map(s => ({ id: stepSeq++, ...s }))
  } catch {
    steps.value = []
  }
}

onMounted(async () => {
  try { commonSteps.value = (await commonStepApi.list()).data } catch { /* ignore */ }
  try { dataFactoryTemplates.value = (await dataFactoryApi.listTemplates()).data } catch { /* ignore */ }
  loadTask()
})
</script>

<style scoped>
.step-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--atp-border-light, #eee);
}
.step-item.disabled {
  opacity: 0.55;
}
.step-item:last-child {
  border-bottom: none;
}
.step-index {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--atp-primary), var(--atp-brand-400));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  margin-right: 12px;
  flex-shrink: 0;
}
.step-body {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.step-desc {
  color: var(--atp-text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-locator {
  color: var(--el-color-info);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
}
.preview-code {
  background: var(--atp-code-bg);
  color: var(--atp-screen-text);
  padding: 20px;
  border-radius: var(--atp-radius-md, 12px);
  max-height: 480px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}
.quick-step-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--atp-border-light, #eee);
}
.quick-step-label { font-size: 12px; color: var(--atp-text-secondary); }
</style>
