<template>
  <el-dialog
    :model-value="modelValue"
    width="820px"
    top="3vh"
    class="data-factory-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑造数模板' : '新建造数模板' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础信息</div>
        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="自动化用例内一键调用，快速生成业务测试数据">模板名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="例：订单生成造数模板、用户账号创建模板"
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
            <span class="enable-desc">开启 = 用例可正常调用；关闭 = 模板全局禁用，无法发起造数</span>
          </div>
          <div v-if="form.enabled" class="field-ok">模板已启用，所有测试用例可引用生成测试数据</div>
          <div v-else class="field-muted">模板已停用，自动化流程无法调用</div>
        </div>
      </section>

      <!-- 分区 2：造数接口 -->
      <section class="section-card">
        <div class="section-title">生成测试数据接口</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="新增测试数据统一使用 POST 方式">请求方式</span>
          </div>
          <el-select v-model="form.method" style="width:100%">
            <el-option label="POST 新增" value="POST" />
            <el-option label="GET 查询" value="GET" />
            <el-option label="PUT 修改" value="PUT" />
            <el-option label="DELETE 删除" value="DELETE" />
          </el-select>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span>接口地址模板</span>
          </div>
          <el-input
            v-model="form.url_template"
            placeholder="支持引用全局参数 {{参数键}}，示例：https://api.test.{{域名}}/orders"
            :class="{ 'is-error-input': !!errors.url_template }"
            @input="onUrlInput"
          />
          <div class="field-hint"><span v-pre>{{变量名}}</span> 可读取全局参数、数据集、上一步提取变量</div>
          <div v-if="errors.url_template" class="field-error">{{ errors.url_template }}</div>
        </div>

        <div class="field-block">
          <div class="section-head sub-head">
            <div class="field-label" style="margin:0">请求头配置</div>
            <div class="mode-switch">
              <el-radio-group v-model="headerMode" size="small" @change="onHeaderModeChange">
                <el-radio-button value="visual">可视化键值编辑（推荐）</el-radio-button>
                <el-radio-button value="raw">原生文本脚本</el-radio-button>
              </el-radio-group>
              <el-dropdown trigger="click" @command="applyHeaderTemplate">
                <el-button size="small" class="btn-tpl">
                  通用请求头模板
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="token">登录 Token 鉴权</el-dropdown-item>
                    <el-dropdown-item command="json">JSON 内容类型</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <div v-if="headerMode === 'visual'" class="kv-editor">
            <div v-for="(row, idx) in headerRows" :key="'h'+idx" class="kv-row">
              <el-input v-model="row.key" placeholder="请求头名，例：Authorization" @input="syncHeadersFromKv" />
              <el-input v-model="row.value" placeholder="请求头值" @input="syncHeadersFromKv" />
              <el-button type="danger" plain circle @click="removeKv(headerRows, idx, syncHeadersFromKv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addKv(headerRows, syncHeadersFromKv)">
              <el-icon><Plus /></el-icon> 新增请求头
            </el-button>
          </div>
          <div v-else>
            <el-input
              v-model="form.headers_json"
              type="textarea"
              :rows="4"
              :class="{ 'is-error-input': !!errors.headers_json }"
              placeholder='{"Authorization":"Bearer {{LOGIN_AUTH_TOKEN}}","Content-Type":"application/json"}'
              @input="onHeadersRawInput"
            />
            <div v-if="errors.headers_json" class="field-error">{{ errors.headers_json }}</div>
          </div>
        </div>

        <div class="field-block">
          <div class="section-head sub-head">
            <div class="field-label" style="margin:0">请求体模板</div>
            <el-radio-group v-model="bodyMode" size="small" @change="onBodyModeChange">
              <el-radio-button value="visual">可视化表单</el-radio-button>
              <el-radio-button value="raw">JSON 脚本</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="bodyMode === 'visual'" class="kv-editor">
            <div v-for="(row, idx) in bodyRows" :key="'b'+idx" class="kv-row">
              <el-input v-model="row.key" placeholder="字段名，例：amount" @input="syncBodyFromKv" />
              <el-input v-model="row.value" placeholder="字段值，可写 {{变量}}" @input="syncBodyFromKv" />
              <el-button type="danger" plain circle @click="removeKv(bodyRows, idx, syncBodyFromKv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addKv(bodyRows, syncBodyFromKv)">
              <el-icon><Plus /></el-icon> 新增字段
            </el-button>
          </div>
          <div v-else>
            <el-input
              v-model="form.body_template"
              type="textarea"
              :rows="5"
              :class="{ 'is-error-input': !!errors.body_template }"
              placeholder='{"product_id":"P001","amount":99.0,"currency":"CNY"}'
              @input="onBodyRawInput"
            />
            <div v-if="errors.body_template" class="field-error">{{ errors.body_template }}</div>
          </div>
        </div>
      </section>

      <!-- 分区 3：变量提取 -->
      <section class="section-card">
        <div class="section-title">接口返回变量提取规则</div>
        <div class="field-block">
          <div class="section-head sub-head">
            <span class="field-hint" style="margin:0">变量名 → JSON 路径（JsonPointer，如 /data/id）</span>
            <el-radio-group v-model="extractMode" size="small" @change="onExtractModeChange">
              <el-radio-button value="visual">可视化编辑</el-radio-button>
              <el-radio-button value="raw">JSON 脚本</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="extractMode === 'visual'" class="kv-editor">
            <div v-for="(row, idx) in extractRows" :key="'e'+idx" class="kv-row">
              <el-input v-model="row.key" placeholder="变量名，例：order_id" @input="syncExtractFromKv" />
              <el-input v-model="row.value" placeholder="路径，例：/data/id" @input="syncExtractFromKv" />
              <el-button type="danger" plain circle @click="removeKv(extractRows, idx, syncExtractFromKv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addKv(extractRows, syncExtractFromKv)">
              <el-icon><Plus /></el-icon> 新增提取规则
            </el-button>
          </div>
          <div v-else>
            <el-input
              v-model="form.extract_json"
              type="textarea"
              :rows="3"
              :class="{ 'is-error-input': !!errors.extract_json }"
              placeholder='{"order_id":"/data/id"}'
              @input="onExtractRawInput"
            />
            <div v-if="errors.extract_json" class="field-error">{{ errors.extract_json }}</div>
          </div>
          <div class="field-hint">
            提取后的变量可在清理接口、后续页面步骤中通过 <span v-pre>{{变量名}}</span> 复用；
            示例：创建订单后提取 order_id，自动带入清理接口销毁订单
          </div>
        </div>
      </section>

      <!-- 分区 4：清理接口 -->
      <section class="section-card">
        <div class="section-title">后置清理销毁接口</div>
        <div class="field-block">
          <div class="field-label">清理请求方式</div>
          <el-select v-model="form.cleanup_method" style="width:100%">
            <el-option label="DELETE 删除（推荐）" value="DELETE" />
            <el-option label="PUT 作废" value="PUT" />
            <el-option label="POST 调用清理接口" value="POST" />
          </el-select>
        </div>
        <div class="field-block">
          <div class="field-label">清理接口地址</div>
          <el-input
            v-model="form.cleanup_url_template"
            placeholder="可引用造数接口提取的变量，示例：https://api.test/orders/{{order_id}}"
            :class="{ 'is-error-input': !!errors.cleanup_url }"
            @input="onCleanupUrlInput"
          />
          <div v-if="errors.cleanup_url" class="field-error">{{ errors.cleanup_url }}</div>
        </div>
        <div class="field-block">
          <div class="section-head sub-head">
            <div class="field-label" style="margin:0">清理请求体</div>
            <el-radio-group v-model="cleanupBodyMode" size="small" @change="onCleanupBodyModeChange">
              <el-radio-button value="visual">可视化键值</el-radio-button>
              <el-radio-button value="raw">JSON 脚本</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="cleanupBodyMode === 'visual'" class="kv-editor">
            <div v-for="(row, idx) in cleanupBodyRows" :key="'c'+idx" class="kv-row">
              <el-input v-model="row.key" placeholder="字段名" @input="syncCleanupBodyFromKv" />
              <el-input v-model="row.value" placeholder="字段值" @input="syncCleanupBodyFromKv" />
              <el-button type="danger" plain circle @click="removeKv(cleanupBodyRows, idx, syncCleanupBodyFromKv)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addKv(cleanupBodyRows, syncCleanupBodyFromKv)">
              <el-icon><Plus /></el-icon> 新增字段
            </el-button>
          </div>
          <div v-else>
            <el-input
              v-model="form.cleanup_body_template"
              type="textarea"
              :rows="3"
              :class="{ 'is-error-input': !!errors.cleanup_body }"
              placeholder='{"reason":"auto_cleanup"}'
              @input="onCleanupBodyRawInput"
            />
            <div v-if="errors.cleanup_body" class="field-error">{{ errors.cleanup_body }}</div>
          </div>
        </div>
      </section>

      <!-- 分区 5：说明 -->
      <section class="guide-panel">
        <div class="guide-title">造数模板使用说明</div>
        <div class="guide-block">
          <div class="guide-h">1. 完整执行流程</div>
          <ul>
            <li><strong>执行造数接口：</strong>调用配置的接口，自动生成订单 / 账号 / 商品等测试数据</li>
            <li><strong>提取返回变量：</strong>从接口响应中拿到数据唯一标识（order_id / user_id）</li>
            <li><strong>用例执行完毕自动调用清理接口：</strong>销毁测试数据，避免脏数据堆积环境</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">2. 变量引用规则</div>
          <ul>
            <li><strong>全局参数：</strong><code v-pre>{{全局参数键名}}</code>，读取平台全局配置域名、鉴权 token</li>
            <li><strong>接口提取变量：</strong>造数接口返回提取的字段，直接在清理 URL / Body 中复用</li>
            <li><strong>数据集变量：</strong><code v-pre>{{数据集.字段名}}</code>，读取参数化批量数据</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">3. 适用业务场景</div>
          <ul>
            <li>批量创建测试订单、测试用户、商品资源</li>
            <li>自动化回归前后自动生成 + 销毁数据，保障环境干净无脏数据</li>
            <li>多用例复用同一套造数逻辑，无需重复编写接口请求步骤</li>
          </ul>
        </div>
        <div class="guide-block">
          <div class="guide-h">4. 规范提示</div>
          <ul>
            <li>所有造数模板建议配套清理接口，避免测试环境长期堆积无效测试数据</li>
            <li>接口域名统一使用全局参数 <code v-pre>{{BASE_URL}}</code>，切换环境无需修改模板</li>
          </ul>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-dropdown trigger="click" @command="applyFactoryTemplate">
            <el-button class="btn-aux">
              填充通用造数模板
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="order">订单创建模板</el-dropdown-item>
                <el-dropdown-item command="user">用户注册模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button class="btn-aux" @click="goGlobalParams">查看全局参数</el-button>
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
import { Close, ArrowDown, Delete, Plus } from '@element-plus/icons-vue'
import { dataFactoryApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])
const router = useRouter()

const HEADER_TEMPLATES = {
  token: {
    Authorization: 'Bearer {{LOGIN_AUTH_TOKEN}}',
    'Content-Type': 'application/json'
  },
  json: {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  }
}

const FACTORY_TEMPLATES = {
  order: {
    name: '订单创建造数模板',
    method: 'POST',
    url_template: 'https://{{BASE_URL}}/orders',
    headers_json: JSON.stringify({
      Authorization: 'Bearer {{LOGIN_AUTH_TOKEN}}',
      'Content-Type': 'application/json'
    }, null, 2),
    body_template: JSON.stringify({
      product_id: 'P001',
      amount: 99.0,
      currency: 'CNY'
    }, null, 2),
    extract_json: JSON.stringify({ order_id: '/data/id' }, null, 2),
    cleanup_method: 'DELETE',
    cleanup_url_template: 'https://{{BASE_URL}}/orders/{{order_id}}',
    cleanup_body_template: '',
    enabled: true
  },
  user: {
    name: '用户注册造数模板',
    method: 'POST',
    url_template: 'https://{{BASE_URL}}/users/register',
    headers_json: JSON.stringify({
      'Content-Type': 'application/json'
    }, null, 2),
    body_template: JSON.stringify({
      username: 'auto_user_{{timestamp}}',
      phone: '13800000000',
      password: 'Pass@123'
    }, null, 2),
    extract_json: JSON.stringify({ user_id: '/data/user_id' }, null, 2),
    cleanup_method: 'DELETE',
    cleanup_url_template: 'https://{{BASE_URL}}/users/{{user_id}}',
    cleanup_body_template: '',
    enabled: true
  }
}

const saving = ref(false)
const snapshot = ref('')
const headerMode = ref('visual')
const bodyMode = ref('visual')
const extractMode = ref('visual')
const cleanupBodyMode = ref('visual')
const headerRows = ref([{ key: '', value: '' }])
const bodyRows = ref([{ key: '', value: '' }])
const extractRows = ref([{ key: '', value: '' }])
const cleanupBodyRows = ref([{ key: '', value: '' }])

const form = reactive(blankForm())
const errors = reactive({
  name: '',
  url_template: '',
  headers_json: '',
  body_template: '',
  extract_json: '',
  cleanup_url: '',
  cleanup_body: ''
})

const isEdit = computed(() => !!form.id)

function blankForm() {
  return {
    id: null,
    name: '',
    method: 'POST',
    url_template: '',
    headers_json: '{}',
    body_template: '',
    extract_json: '{}',
    cleanup_method: 'DELETE',
    cleanup_url_template: '',
    cleanup_body_template: '',
    enabled: true
  }
}

function clearErrors() {
  Object.keys(errors).forEach(k => { errors[k] = '' })
}

function takeSnapshot() {
  return JSON.stringify({
    form: { ...form },
    headerMode: headerMode.value,
    bodyMode: bodyMode.value,
    extractMode: extractMode.value,
    cleanupBodyMode: cleanupBodyMode.value
  })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  errors.name = form.name?.trim() ? '' : '请填写造数模板业务名称'
}

function findJsonError(text, emptyOk = true) {
  const v = String(text ?? '').trim()
  if (!v) return emptyOk ? null : { message: '内容不能为空' }
  try {
    JSON.parse(v)
    return null
  } catch (e) {
    const msg = String(e.message || '')
    const m = msg.match(/position\s+(\d+)/i)
    if (m) {
      const line = v.slice(0, Number(m[1])).split('\n').length
      return { message: `JSON 语法错误，约在第 ${line} 行` }
    }
    return { message: 'JSON 语法错误，请检查格式' }
  }
}

function validateUrlTemplate(raw, { required = false } = {}) {
  const v = String(raw || '').trim()
  if (!v) return required ? '请填写接口地址模板' : ''
  // 将 {{var}} 替换为占位，便于 URL 解析
  const normalized = v.replace(/\{\{[^}]+\}\}/g, 'placeholder')
  try {
    const u = new URL(normalized)
    if (!['http:', 'https:'].includes(u.protocol)) {
      return '地址格式错误：仅支持 http 或 https 协议'
    }
    return ''
  } catch {
    return '地址格式错误：请输入合法的 http/https 地址（可含 {{变量}}）'
  }
}

function onUrlInput() {
  const soft = validateUrlTemplate(form.url_template, { required: false })
  errors.url_template = form.url_template.trim()
    ? (soft || '')
    : '请填写造数接口地址模板'
}

function onCleanupUrlInput() {
  if (!form.cleanup_url_template?.trim()) {
    errors.cleanup_url = ''
    return
  }
  errors.cleanup_url = validateUrlTemplate(form.cleanup_url_template)
}

function parseKvFromJson(text) {
  try {
    const obj = JSON.parse(text || '{}')
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return [{ key: '', value: '' }]
    const rows = Object.entries(obj).map(([key, value]) => ({
      key,
      value: value == null ? '' : (typeof value === 'string' ? value : JSON.stringify(value))
    }))
    return rows.length ? rows : [{ key: '', value: '' }]
  } catch {
    return [{ key: '', value: '' }]
  }
}

function kvToJson(rows) {
  const obj = {}
  for (const row of rows) {
    const k = (row.key || '').trim()
    if (!k) continue
    const raw = row.value
    if (raw === 'true') obj[k] = true
    else if (raw === 'false') obj[k] = false
    else if (raw !== '' && !Number.isNaN(Number(raw)) && /^-?\d+(\.\d+)?$/.test(String(raw).trim())) {
      obj[k] = Number(raw)
    } else {
      try {
        if (typeof raw === 'string' && (raw.trim().startsWith('{') || raw.trim().startsWith('['))) {
          obj[k] = JSON.parse(raw)
        } else {
          obj[k] = raw
        }
      } catch {
        obj[k] = raw
      }
    }
  }
  return JSON.stringify(obj, null, 2)
}

function addKv(rowsRef, syncFn) {
  rowsRef.value.push({ key: '', value: '' })
  syncFn()
}

function removeKv(rowsRef, idx, syncFn) {
  rowsRef.value.splice(idx, 1)
  if (!rowsRef.value.length) rowsRef.value.push({ key: '', value: '' })
  syncFn()
}

function syncHeadersFromKv() {
  form.headers_json = kvToJson(headerRows.value)
  errors.headers_json = ''
}
function syncBodyFromKv() {
  form.body_template = kvToJson(bodyRows.value)
  errors.body_template = ''
}
function syncExtractFromKv() {
  form.extract_json = kvToJson(extractRows.value)
  errors.extract_json = ''
}
function syncCleanupBodyFromKv() {
  form.cleanup_body_template = kvToJson(cleanupBodyRows.value)
  errors.cleanup_body = ''
}

function onHeaderModeChange(mode) {
  if (mode === 'visual') {
    const err = findJsonError(form.headers_json || '{}')
    if (err) {
      ElMessage.warning('请求头 JSON 有误，请先修正')
      headerMode.value = 'raw'
      return
    }
    headerRows.value = parseKvFromJson(form.headers_json)
    syncHeadersFromKv()
  } else {
    syncHeadersFromKv()
  }
}
function onBodyModeChange(mode) {
  if (mode === 'visual') {
    const raw = form.body_template?.trim()
    if (raw) {
      const err = findJsonError(raw)
      if (err) {
        ElMessage.warning('请求体 JSON 有误，请先修正')
        bodyMode.value = 'raw'
        return
      }
      try {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) {
          ElMessage.warning('可视化模式仅支持对象结构，数组请使用 JSON 脚本')
          bodyMode.value = 'raw'
          return
        }
      } catch { /* handled */ }
    }
    bodyRows.value = parseKvFromJson(form.body_template || '{}')
    syncBodyFromKv()
  } else {
    syncBodyFromKv()
  }
}
function onExtractModeChange(mode) {
  if (mode === 'visual') {
    const err = findJsonError(form.extract_json || '{}')
    if (err) {
      ElMessage.warning('变量提取 JSON 有误，请先修正')
      extractMode.value = 'raw'
      return
    }
    extractRows.value = parseKvFromJson(form.extract_json)
    syncExtractFromKv()
  } else {
    syncExtractFromKv()
  }
}
function onCleanupBodyModeChange(mode) {
  if (mode === 'visual') {
    const raw = form.cleanup_body_template?.trim()
    if (raw) {
      const err = findJsonError(raw)
      if (err) {
        ElMessage.warning('清理请求体 JSON 有误，请先修正')
        cleanupBodyMode.value = 'raw'
        return
      }
    }
    cleanupBodyRows.value = parseKvFromJson(form.cleanup_body_template || '{}')
    syncCleanupBodyFromKv()
  } else {
    syncCleanupBodyFromKv()
  }
}

function onHeadersRawInput() {
  const err = findJsonError(form.headers_json || '{}')
  errors.headers_json = err ? err.message : ''
}
function onBodyRawInput() {
  if (!form.body_template?.trim()) { errors.body_template = ''; return }
  const err = findJsonError(form.body_template)
  errors.body_template = err ? err.message : ''
}
function onExtractRawInput() {
  const err = findJsonError(form.extract_json || '{}')
  errors.extract_json = err ? err.message : ''
}
function onCleanupBodyRawInput() {
  if (!form.cleanup_body_template?.trim()) { errors.cleanup_body = ''; return }
  const err = findJsonError(form.cleanup_body_template)
  errors.cleanup_body = err ? err.message : ''
}

function applyHeaderTemplate(cmd) {
  const tpl = HEADER_TEMPLATES[cmd]
  if (!tpl) return
  let current = {}
  try { current = JSON.parse(form.headers_json || '{}') || {} } catch { current = {} }
  form.headers_json = JSON.stringify({ ...current, ...tpl }, null, 2)
  if (headerMode.value === 'visual') headerRows.value = parseKvFromJson(form.headers_json)
  errors.headers_json = ''
  ElMessage.success('请求头模板已填充')
}

function applyFactoryTemplate(cmd) {
  const tpl = FACTORY_TEMPLATES[cmd]
  if (!tpl) return
  Object.assign(form, {
    name: form.name?.trim() ? form.name : tpl.name,
    method: tpl.method,
    url_template: tpl.url_template,
    headers_json: tpl.headers_json,
    body_template: tpl.body_template,
    extract_json: tpl.extract_json,
    cleanup_method: tpl.cleanup_method,
    cleanup_url_template: tpl.cleanup_url_template,
    cleanup_body_template: tpl.cleanup_body_template,
    enabled: true
  })
  headerRows.value = parseKvFromJson(form.headers_json)
  bodyRows.value = parseKvFromJson(form.body_template)
  extractRows.value = parseKvFromJson(form.extract_json)
  cleanupBodyRows.value = parseKvFromJson(form.cleanup_body_template || '{}')
  clearErrors()
  errors.name = ''
  ElMessage.success(cmd === 'order' ? '已填充订单创建模板' : '已填充用户注册模板')
}

function applyRow(row) {
  clearErrors()
  headerMode.value = 'visual'
  bodyMode.value = 'visual'
  extractMode.value = 'visual'
  cleanupBodyMode.value = 'visual'
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      method: row.method || 'POST',
      url_template: row.url_template || '',
      headers_json: row.headers_json || '{}',
      body_template: row.body_template || '',
      extract_json: row.extract_json || '{}',
      cleanup_method: row.cleanup_method || 'DELETE',
      cleanup_url_template: row.cleanup_url_template || '',
      cleanup_body_template: row.cleanup_body_template || '',
      enabled: row.enabled !== false
    })
  } else {
    Object.assign(form, blankForm())
  }
  try { form.headers_json = JSON.stringify(JSON.parse(form.headers_json || '{}'), null, 2) } catch { /* keep */ }
  try {
    if (form.body_template?.trim()) form.body_template = JSON.stringify(JSON.parse(form.body_template), null, 2)
  } catch { /* keep */ }
  try { form.extract_json = JSON.stringify(JSON.parse(form.extract_json || '{}'), null, 2) } catch { /* keep */ }
  try {
    if (form.cleanup_body_template?.trim()) {
      form.cleanup_body_template = JSON.stringify(JSON.parse(form.cleanup_body_template), null, 2)
    }
  } catch { /* keep */ }

  headerRows.value = parseKvFromJson(form.headers_json)
  bodyRows.value = parseKvFromJson(form.body_template || '{}')
  extractRows.value = parseKvFromJson(form.extract_json)
  cleanupBodyRows.value = parseKvFromJson(form.cleanup_body_template || '{}')
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  applyRow(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前模板配置未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前模板配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空模板全部接口、变量配置，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  headerRows.value = [{ key: '', value: '' }]
  bodyRows.value = [{ key: '', value: '' }]
  extractRows.value = [{ key: '', value: '' }]
  cleanupBodyRows.value = [{ key: '', value: '' }]
  clearErrors()
  ElMessage.success('已重置')
}

async function goGlobalParams() {
  if (isDirty()) {
    try {
      await ElMessageBox.confirm('当前模板配置未保存，是否确认关闭并前往全局参数？', '未保存确认', {
        type: 'warning',
        confirmButtonText: '仍要前往',
        cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
  router.push({ path: '/platform-config', query: { tab: 'global-params' } })
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写造数模板业务名称'
    ok = false
  }
  if (headerMode.value === 'visual') syncHeadersFromKv()
  if (bodyMode.value === 'visual') syncBodyFromKv()
  if (extractMode.value === 'visual') syncExtractFromKv()
  if (cleanupBodyMode.value === 'visual') syncCleanupBodyFromKv()

  const urlErr = validateUrlTemplate(form.url_template, { required: true })
  if (urlErr) {
    errors.url_template = urlErr
    ok = false
  }
  const hErr = findJsonError(form.headers_json || '{}')
  if (hErr) {
    errors.headers_json = hErr.message
    headerMode.value = 'raw'
    ok = false
  }
  if (form.body_template?.trim()) {
    const bErr = findJsonError(form.body_template)
    if (bErr) {
      errors.body_template = bErr.message
      bodyMode.value = 'raw'
      ok = false
    }
  }
  const eErr = findJsonError(form.extract_json || '{}')
  if (eErr) {
    errors.extract_json = eErr.message
    extractMode.value = 'raw'
    ok = false
  }
  if (form.cleanup_url_template?.trim()) {
    const cErr = validateUrlTemplate(form.cleanup_url_template)
    if (cErr) {
      errors.cleanup_url = cErr
      ok = false
    }
  }
  if (form.cleanup_body_template?.trim()) {
    const cbErr = findJsonError(form.cleanup_body_template)
    if (cbErr) {
      errors.cleanup_body = cbErr.message
      cleanupBodyMode.value = 'raw'
      ok = false
    }
  }
  return ok
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请完善必填项并修正标红字段')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      method: form.method || 'POST',
      url_template: form.url_template.trim(),
      headers_json: form.headers_json || '{}',
      body_template: form.body_template || '',
      extract_json: form.extract_json || '{}',
      cleanup_method: form.cleanup_method || 'DELETE',
      cleanup_url_template: form.cleanup_url_template || '',
      cleanup_body_template: form.cleanup_body_template || '',
      enabled: !!form.enabled
    }
    if (isEdit.value) {
      await dataFactoryApi.updateTemplate(form.id, payload)
      ElMessage.success('造数模板已保存')
    } else {
      await dataFactoryApi.createTemplate(payload)
      ElMessage.success('造数模板已创建')
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
.dlg-title { margin: 0; font-size: 18px; font-weight: 700; }
.dlg-close { margin-right: -6px; }

.dlg-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 74vh;
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
.section-head, .sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
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
.field-ok { margin-top: 8px; font-size: 12px; color: #16a34a; }
.field-muted { margin-top: 8px; font-size: 12px; color: #94a3b8; }
:deep(.is-error-input .el-input__wrapper),
:deep(.is-error-input .el-textarea__inner) {
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

.kv-editor { display: flex; flex-direction: column; gap: 8px; }
.kv-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr auto;
  gap: 8px;
  align-items: center;
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
.guide-h { font-weight: 700; margin-bottom: 4px; color: #1e3a8a; }
.guide-block ul { margin: 4px 0; padding-left: 18px; }
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
.footer-left, .footer-right {
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
