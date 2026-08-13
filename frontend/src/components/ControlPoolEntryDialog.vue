<template>
  <el-dialog
    :model-value="modelValue"
    width="640px"
    top="5vh"
    class="control-entry-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑控件池条目' : '添加控件池条目' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 模块 2：基础归属 -->
      <section class="section-card">
        <div class="section-title">基础归属信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="控件所在业务页面，建议使用中文页面名">页面名称</span>
          </div>
          <el-input
            v-model="form.page_name"
            placeholder="填写控件所属页面名称，例：登录页、支付首页"
          />
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="业务可读名称即可，无格式限制">控件名称</span>
          </div>
          <el-input
            v-model="form.element_name"
            placeholder="填写控件名称，例：定时任务，列表页右下角加号"
            maxlength="256"
            show-word-limit
            :class="{ 'is-field-error': errors.element_name }"
            @input="onNameInput"
          />
          <div v-if="errors.element_name" class="field-error">{{ errors.element_name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="选择控件适配的移动端平台">平台</span>
          </div>
          <el-select v-model="form.platform" style="width:100%">
            <el-option label="安卓" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="双端" value="both" />
          </el-select>
        </div>
      </section>

      <!-- 模块 3：定位核心 -->
      <section class="section-card">
        <div class="section-title">定位核心配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="资源 ID 稳定性最高，优先选用">定位类型</span>
          </div>
          <el-select
            v-model="form.locator_type"
            style="width:100%"
            :class="{ 'is-field-error': errors.locator_type }"
            @change="onLocatorTypeChange"
          >
            <el-option label="ID 定位" value="id" />
            <el-option label="文案定位" value="accessibility" />
            <el-option label="文本定位" value="text" />
            <el-option label="xpath" value="xpath" />
            <el-option label="坐标定位" value="bounds" />
          </el-select>
          <div v-if="errors.locator_type" class="field-error">{{ errors.locator_type }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="从控件拾取页面抓取后可自动回填">定位表达式</span>
          </div>
          <div class="locator-row">
            <el-input
              v-model="form.locator_value"
              type="textarea"
              :rows="4"
              placeholder="粘贴从控件拾取页面抓取的定位表达式"
              :class="{ 'is-field-error': errors.locator_value }"
              @input="onLocatorInput"
            />
            <el-button type="primary" class="btn-pick" @click="goPickerFill">打开控件拾取</el-button>
          </div>
          <div v-if="errors.locator_value" class="field-error">{{ errors.locator_value }}</div>
          <div v-else-if="locatorWarn" class="field-warn">{{ locatorWarn }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="按手机型号配置设备侧控件原始值（content-desc / text 等）；可多行，* 表示通用默认">设备关联元素值</span>
          </div>
          <el-table :data="form.device_bindings" border size="small" class="device-bind-table">
            <el-table-column label="关联设备" min-width="180">
              <template #default="{ row }">
                <el-select
                  v-model="row.device_model"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="选择或输入手机型号"
                  style="width:100%"
                >
                  <el-option label="通用默认 (*)" value="*" />
                  <el-option
                    v-for="m in deviceModelOptions"
                    :key="m"
                    :label="m"
                    :value="m"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="控件元素值" min-width="160">
              <template #default="{ row }">
                <el-input
                  v-model="row.element_value"
                  placeholder="例：定时"
                  maxlength="128"
                  clearable
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88" align="center">
              <template #default="{ $index }">
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="form.device_bindings.length <= 1"
                  @click="removeDeviceBinding($index)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="bind-actions">
            <el-button size="small" @click="addDeviceBinding">添加一行</el-button>
            <span class="bind-hint">可从设备列表选择型号，也可手动输入；保存后按型号匹配元素值</span>
          </div>
        </div>
      </section>

      <!-- 模块 4：版本环境 -->
      <section class="section-card">
        <div class="section-title">版本 & 环境标签</div>

        <div class="field-block">
          <div class="field-label">版本标签</div>
          <el-input
            v-model="form.version_tag"
            placeholder="填写适配 App 版本，多版本用逗号分隔"
          />
        </div>

        <div class="field-block">
          <div class="field-label">环境标签</div>
          <el-input
            v-model="form.env_tag"
            placeholder="测试环境、预发环境、生产环境，多环境逗号分隔"
          />
          <div class="tag-quick">
            <span class="tag-quick-label">快捷标签：</span>
            <el-button
              v-for="t in ENV_QUICK_TAGS"
              :key="t"
              size="small"
              round
              :type="hasEnvTag(t) ? 'primary' : 'default'"
              plain
              @click="toggleEnvTag(t)"
            >{{ t }}</el-button>
          </div>
        </div>
      </section>

      <!-- 模块 5：高级管控 -->
      <section class="section-card">
        <div class="section-title">高级管控配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="静态控件页面固定存在；动态控件仅特定场景弹出">控件分级</span>
          </div>
          <el-select v-model="form.control_tag" style="width:100%">
            <el-option
              v-for="t in CONTROL_GRADE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label">核心控件</div>
          <div class="switch-row">
            <el-switch v-model="form.is_core" />
            <span class="switch-desc">
              勾选后标记为生产核心控件，仅管理员账号支持编辑 / 修改，普通测试人员仅可查看
            </span>
          </div>
          <div v-if="form.is_core" class="field-warn">已标记为核心控件，修改权限受限</div>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-muted" @click="resetForm">重置表单</el-button>
          <el-button class="btn-muted" @click="goPickerFill">从拾取页面回填</el-button>
        </div>
        <div class="footer-right">
          <el-button class="btn-muted" @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">确认</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { controlApi, deviceApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { validateControlDisplayName } from '@/utils/locatorAssist'

const FILL_FLAG = 'atp_fill_control_form'
const FILL_PAYLOAD = 'atp_control_pool_form_fill'
const DRAFT_KEY = 'atp_control_pool_form_draft'

const ENV_QUICK_TAGS = ['测试环境', '预发环境', '生产环境']
const CONTROL_GRADE_OPTIONS = [
  { value: 'static', label: '静态控件' },
  { value: 'popup', label: '动态弹窗控件' },
  { value: 'list', label: '列表循环控件' }
]

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传入则进入编辑模式 */
  editRow: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const router = useRouter()
const saving = ref(false)
const snapshot = ref('')
const nameHint = ref('')
const locatorWarn = ref('')
const deviceModelOptions = ref([])

const form = reactive(blankForm())
const errors = reactive({
  app_package: '',
  element_name: '',
  locator_type: '',
  locator_value: ''
})

const isEdit = computed(() => !!(props.editRow?.id || form.id))

function emptyBinding(model = '', value = '') {
  return { device_model: model, element_value: value }
}

function blankForm() {
  return {
    id: null,
    app_package: '',
    page_name: '',
    element_name: '',
    platform: 'android',
    locator_type: 'id',
    locator_value: '',
    device_bindings: [emptyBinding('*', '')],
    version_tag: '',
    env_tag: '',
    control_tag: 'static',
    is_core: false
  }
}

function clearErrors() {
  errors.app_package = ''
  errors.element_name = ''
  errors.locator_type = ''
  errors.locator_value = ''
  nameHint.value = ''
  locatorWarn.value = ''
}

function takeSnapshot() {
  return JSON.stringify({ ...form })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function parseDeviceBindings(row) {
  const list = []
  let raw = row?.device_element_bindings
  if (typeof raw === 'string' && raw.trim()) {
    try { raw = JSON.parse(raw) } catch { raw = null }
  }
  if (Array.isArray(raw)) {
    for (const item of raw) {
      if (!item) continue
      list.push(emptyBinding(
        item.device_model || item.deviceModel || '*',
        item.element_value || item.elementValue || ''
      ))
    }
  }
  if (!list.length && row?.device_element_value) {
    list.push(emptyBinding('*', row.device_element_value))
  }
  if (!list.length) list.push(emptyBinding('*', ''))
  return list
}

function addDeviceBinding() {
  form.device_bindings.push(emptyBinding('', ''))
}

function removeDeviceBinding(idx) {
  if (form.device_bindings.length <= 1) return
  form.device_bindings.splice(idx, 1)
}

function serializeBindings() {
  return form.device_bindings
    .map(r => ({
      device_model: (r.device_model || '').trim() || '*',
      element_value: (r.element_value || '').trim()
    }))
    .filter(r => r.element_value || r.device_model === '*')
}

function defaultElementValueFromBindings(bindings) {
  const star = bindings.find(b => b.device_model === '*' && b.element_value)
  if (star) return star.element_value
  const first = bindings.find(b => b.element_value)
  return first?.element_value || ''
}

function applyRow(row) {
  Object.assign(form, blankForm(), {
    id: row.id,
    app_package: row.app_package || '',
    page_name: row.page_name || '',
    element_name: row.element_name || '',
    platform: row.platform || 'android',
    locator_type: normalizeLocatorType(row.locator_type),
    locator_value: row.locator_value || '',
    device_bindings: parseDeviceBindings(row),
    version_tag: row.version_tag || '',
    env_tag: toChineseEnv(row.env_tag || ''),
    control_tag: normalizeGrade(row.control_tag),
    is_core: !!row.is_core
  })
}

function normalizeLocatorType(t) {
  const map = {
    id: 'id',
    resource_id: 'id',
    accessibility: 'accessibility',
    accessibility_id: 'accessibility',
    content_desc: 'accessibility',
    desc: 'accessibility',
    text: 'text',
    xpath_text: 'text',
    ocr: 'text',
    xpath: 'xpath',
    xpath_desc: 'xpath',
    xpath_desc_contains: 'xpath',
    absolute_xpath: 'xpath',
    relative_xpath: 'xpath',
    class_name: 'xpath',
    uiselector: 'xpath',
    bounds: 'bounds',
    coordinate: 'bounds',
    xy: 'bounds',
    screen_ratio: 'bounds'
  }
  return map[t] || 'id'
}

function normalizeGrade(tag) {
  if (['static', 'popup', 'list'].includes(tag)) return tag
  if (tag === 'dynamic') return 'popup'
  return 'static'
}

function toChineseEnv(raw) {
  const s = String(raw || '').trim()
  if (!s) return ''
  return s
    .split(/[,，]/)
    .map(x => x.trim())
    .filter(Boolean)
    .map(x => {
      const k = x.toLowerCase()
      if (['test', '测试', '测试环境'].includes(k) || x === '测试环境') return '测试环境'
      if (['staging', 'pre', '预发', '预发环境'].includes(k) || x === '预发环境') return '预发环境'
      if (['prod', 'production', '生产', '生产环境'].includes(k) || x === '生产环境') return '生产环境'
      return x
    })
    .join(',')
}

function hasEnvTag(t) {
  return String(form.env_tag || '').split(/[,，]/).map(x => x.trim()).includes(t)
}

function toggleEnvTag(t) {
  const parts = String(form.env_tag || '').split(/[,，]/).map(x => x.trim()).filter(Boolean)
  const idx = parts.indexOf(t)
  if (idx >= 0) parts.splice(idx, 1)
  else parts.push(t)
  form.env_tag = parts.join(',')
}

function onNameInput() {
  errors.element_name = ''
  nameHint.value = ''
}

function validateLocatorExpr(type, value, { soft = false } = {}) {
  const v = String(value || '').trim()
  if (!v) return soft ? '' : '请填写定位表达式'
  if ((type === 'id') && (v.startsWith('//') || v.startsWith('/hierarchy') || v.startsWith('xpath='))) {
    return 'ID 定位表达式格式异常，请勿填写路径'
  }
  if (type === 'xpath') {
    if (!(v.startsWith('/') || v.startsWith('(') || v.startsWith('./') || v.startsWith('.//'))) {
      return '路径定位表达式格式异常，通常以 /、// 或 ( 开头'
    }
  }
  if (type === 'bounds') {
    if (!/\[\d+,\d+\]\[\d+,\d+\]/.test(v) && !/^\d+\s*,\s*\d+$/.test(v)) {
      return soft
        ? '坐标建议格式：[x1,y1][x2,y2] 或 x,y'
        : '坐标定位表达式格式异常，请使用 [x1,y1][x2,y2] 或 x,y'
    }
  }
  if (type === 'text' && v.length > 200) {
    return '文本定位过长，请检查是否粘贴错误'
  }
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(v)) {
    return '定位表达式包含非法控制字符'
  }
  return ''
}

function onLocatorInput() {
  errors.locator_value = ''
  locatorWarn.value = validateLocatorExpr(form.locator_type, form.locator_value, { soft: true })
  const guessed = guessDeviceElementValue(form.locator_type, form.locator_value)
  if (!guessed) return
  // 仅填充空的通用行，避免覆盖已配置的多型号值
  const star = form.device_bindings.find(r => (r.device_model || '*') === '*' && !(r.element_value || '').trim())
  if (star) star.element_value = guessed
  else if (form.device_bindings.length === 1 && !(form.device_bindings[0].element_value || '').trim()) {
    form.device_bindings[0].device_model = form.device_bindings[0].device_model || '*'
    form.device_bindings[0].element_value = guessed
  }
}

function guessDeviceElementValue(type, expr) {
  const raw = String(expr || '').trim()
  if (!raw) return ''
  const patterns = [
    /@content-desc=["']([^"']+)["']/,
    /content-desc=["']([^"']+)["']/,
    /@text=["']([^"']+)["']/,
    /text=["']([^"']+)["']/,
    /description\(["']([^"']+)["']\)/,
    /resource-id=["'][^"']*?\/([^/"']+)["']/i,
    /:id\/([A-Za-z0-9_.-]+)/
  ]
  for (const re of patterns) {
    const m = raw.match(re)
    if (m?.[1]) return m[1].trim()
  }
  if (type === 'text' || type === 'accessibility') return raw.slice(0, 64)
  if (type === 'id' && !raw.includes('/') && !raw.includes('[')) return raw.slice(0, 64)
  return ''
}

function onLocatorTypeChange() {
  errors.locator_type = ''
  onLocatorInput()
}

function validateAll() {
  clearErrors()
  let ok = true
  const nameErr = validateControlDisplayName(form.element_name)
  if (nameErr) {
    errors.element_name = nameErr
    ok = false
  }
  if (!form.locator_type) {
    errors.locator_type = '请选择定位类型'
    ok = false
  }
  const locErr = validateLocatorExpr(form.locator_type, form.locator_value)
  if (locErr) {
    errors.locator_value = locErr
    ok = false
  }
  return ok
}

async function loadDeviceModels() {
  try {
    const res = await deviceApi.list({ page: 1, page_size: 200 })
    const list = res.data?.list || res.data?.items || res.data || []
    const set = new Set()
    for (const d of list) {
      const m = (d.model || d.name || '').trim()
      if (m) set.add(m)
    }
    deviceModelOptions.value = [...set].sort((a, b) => a.localeCompare(b, 'zh'))
  } catch {
    deviceModelOptions.value = []
  }
}

async function initDialog() {
  clearErrors()
  await loadDeviceModels()
  if (props.editRow?.id) {
    applyRow(props.editRow)
  } else {
    Object.assign(form, blankForm())
    tryConsumeFillPayload()
  }
  await nextTick()
  snapshot.value = takeSnapshot()
}

watch(() => props.modelValue, (v) => {
  if (v) initDialog()
})

onMounted(() => {
  if (props.modelValue) initDialog()
})

function tryConsumeFillPayload() {
  try {
    const raw = sessionStorage.getItem(FILL_PAYLOAD) || localStorage.getItem(FILL_PAYLOAD)
    if (!raw) {
      // 恢复草稿
      const draft = sessionStorage.getItem(DRAFT_KEY)
      if (draft) Object.assign(form, blankForm(), JSON.parse(draft))
      return
    }
    const data = JSON.parse(raw)
    sessionStorage.removeItem(FILL_PAYLOAD)
    localStorage.removeItem(FILL_PAYLOAD)
    sessionStorage.removeItem(DRAFT_KEY)
    Object.assign(form, blankForm(), {
      app_package: data.app_package || form.app_package || '',
      page_name: data.page_name || '',
      element_name: data.element_name || '',
      platform: data.platform || 'android',
      locator_type: normalizeLocatorType(data.locator_type || 'id'),
      locator_value: data.locator_value || '',
      device_bindings: parseDeviceBindings({
        device_element_bindings: data.device_element_bindings,
        device_element_value: data.device_element_value || data.display_name || data.element_name || ''
      }),
      version_tag: data.version_tag || '',
      env_tag: toChineseEnv(data.env_tag || ''),
      control_tag: normalizeGrade(data.control_tag),
      is_core: !!data.is_core
    })
    // 兼容历史错误回填：类型写成 xpath 但值不是路径
    if (form.locator_type === 'xpath' && form.locator_value) {
      const v = String(form.locator_value).trim()
      if (!(v.startsWith('/') || v.startsWith('(') || v.startsWith('./') || v.startsWith('.//'))) {
        if (/^\[\d+,\d+\]\[\d+,\d+\]/.test(v) || /^\d+\s*,\s*\d+$/.test(v)) form.locator_type = 'bounds'
        else form.locator_type = 'text'
      }
    }
    ElMessage.success('已从控件拾取回填定位信息')
    onNameInput()
    onLocatorInput()
  } catch { /* ignore */ }
}

async function handleBeforeClose(done) {
  if (!isDirty()) {
    emit('update:modelValue', false)
    done()
    return
  }
  try {
    await ElMessageBox.confirm('当前内容尚未保存，确定关闭吗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
    done()
  } catch { /* stay */ }
}

async function requestClose() {
  if (!isDirty()) {
    emit('update:modelValue', false)
    return
  }
  try {
    await ElMessageBox.confirm('当前内容尚未保存，确定关闭吗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  await ElMessageBox.confirm('将清空当前所有输入内容，是否继续？', '重置表单', { type: 'warning' })
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  clearErrors()
  ElMessage.success('已重置')
}

function goPickerFill() {
  sessionStorage.setItem(FILL_FLAG, '1')
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ ...form }))
  emit('update:modelValue', false)
  router.push({ path: '/element-picker', query: { return_fill: '1' } })
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  saving.value = true
  try {
    const bindings = serializeBindings()
    const payload = {
      app_package: (form.app_package || '').trim(),
      page_name: form.page_name.trim(),
      element_name: form.element_name.trim(),
      platform: form.platform,
      locator_type: form.locator_type,
      locator_value: form.locator_value.trim(),
      device_element_value: defaultElementValueFromBindings(bindings),
      device_element_bindings: bindings,
      version_tag: form.version_tag.trim(),
      env_tag: form.env_tag.trim(),
      control_tag: form.control_tag,
      isCore: form.is_core,
      controlTag: form.control_tag
    }
    if (form.id) {
      await controlApi.updatePool(form.id, {
        element_name: payload.element_name,
        locator_type: payload.locator_type,
        locator_value: payload.locator_value,
        device_element_value: payload.device_element_value || null,
        device_element_bindings: payload.device_element_bindings,
        page_name: payload.page_name || null,
        version_tag: payload.version_tag,
        env_tag: payload.env_tag,
        is_core: form.is_core,
        control_tag: form.control_tag || null,
        reason: '编辑控件池条目',
        propagate_bindings: true
      })
      ElMessage.success('控件池条目已更新')
    } else {
      await controlApi.createPool(payload)
      ElMessage.success('控件池条目已添加')
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
.dlg-close { color: #64748b; }

.dlg-body {
  max-height: min(68vh, 680px);
  overflow-y: auto;
  padding-right: 4px;
}

.section-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
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
}
.req { color: #ef4444; }
.tip-label {
  cursor: help;
  border-bottom: 1px dashed rgba(100, 116, 139, 0.45);
}

.field-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ef4444;
}
.field-warn {
  margin-top: 6px;
  font-size: 12px;
  color: #ea580c;
}

:deep(.is-field-error .el-input__wrapper),
:deep(.is-field-error .el-textarea__inner),
:deep(.el-select.is-field-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px #ef4444 inset !important;
}

.locator-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.locator-row .el-textarea { flex: 1; }
.btn-pick { flex-shrink: 0; margin-top: 2px; }

.device-bind-table {
  width: 100%;
}
.device-bind-table :deep(.el-table__cell) {
  padding: 6px 8px;
  vertical-align: middle;
}
.bind-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.bind-hint {
  font-size: 12px;
  color: var(--atp-text-secondary, #64748b);
  line-height: 1.4;
}

.tag-quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.tag-quick-label { font-size: 12px; color: var(--atp-text-secondary); }

.switch-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.switch-desc {
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

.dlg-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-muted {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
}
</style>
