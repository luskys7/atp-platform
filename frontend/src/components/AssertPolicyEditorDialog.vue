<template>
  <el-dialog
    :model-value="modelValue"
    width="720px"
    top="5vh"
    class="assert-policy-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑断言策略' : '新建断言策略' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body">
      <!-- 分区 1：核心配置 -->
      <section class="section-card">
        <div class="section-title">断言策略核心配置</div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="白名单用于非关键弹窗、弱提示；黑名单用于核心业务校验失败拦截回归">规则类型</span>
          </div>
          <el-select v-model="form.rule_type" style="width:100%" placeholder="请选择规则类型">
            <el-option label="白名单 (失败跳过)" value="whitelist" />
            <el-option label="黑名单 (强制阻断)" value="blacklist" />
          </el-select>
          <div class="field-hint">{{ ruleHint }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="tip-label" title="断言类型匹配执行步骤；控件标识匹配页面弹出遮挡元素">目标类型</span>
          </div>
          <el-select v-model="form.target_type" style="width:100%" placeholder="请选择目标类型">
            <el-option label="断言类型" value="assert_type" />
            <el-option label="控件标识" value="element_name" />
          </el-select>
          <div class="field-hint">{{ targetHint }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span>匹配模式</span>
          </div>
          <el-input
            v-model="form.pattern"
            placeholder="支持通配符*模糊匹配，示例：吐司弹窗、全局弹窗、崩溃提示"
            :class="{ 'is-error-input': !!errors.pattern }"
            @input="onPatternInput"
          />
          <div class="field-hint">*代表任意字符，关键词*匹配前缀，*关键词匹配后缀；多关键词用英文逗号分隔</div>
          <div v-if="errors.pattern" class="field-error">{{ errors.pattern }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">说明</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="填写策略业务用途，例：弹窗弱提示断言失败自动跳过，不阻断回归"
          />
        </div>

        <div class="field-block">
          <div class="field-label">启用</div>
          <div class="enable-row">
            <el-switch v-model="form.enabled" />
            <span class="enable-desc">开启 = 策略全局生效；关闭 = 策略不参与自动化执行</span>
          </div>
          <div v-if="form.enabled" class="field-ok">当前策略已启用，所有自动化任务自动匹配拦截</div>
          <div v-else class="field-muted">策略已停用，不生效</div>
        </div>
      </section>

      <!-- 分区 2：使用说明 -->
      <section class="guide-panel">
        <div class="guide-title">断言策略使用说明</div>

        <div class="guide-block">
          <div class="guide-h">1. 黑白名单执行逻辑</div>
          <ul>
            <li><strong>白名单（失败跳过）：</strong>仅针对非关键弱提示、临时弹窗；匹配到断言失败时，仅记录日志，继续执行后续步骤</li>
            <li><strong>黑名单（强制阻断）：</strong>针对核心业务流程校验；匹配到断言失败时，立刻终止当前用例 / 套件，标记任务失败</li>
          </ul>
        </div>

        <div class="guide-block">
          <div class="guide-h">2. 目标类型区分</div>
          <ul>
            <li><strong>断言类型：</strong>匹配自动化脚本内的校验步骤（吐司提示、弹窗文案校验、弹框存在判断）</li>
            <li><strong>控件标识：</strong>匹配页面悬浮遮挡控件（广告弹窗、权限弹窗、升级弹窗），自动跳过遮挡元素</li>
          </ul>
        </div>

        <div class="guide-block">
          <div class="guide-h">3. 匹配模式语法规则</div>
          <ul>
            <li><strong>精确匹配：</strong>直接填写完整关键词，仅完全一致才命中</li>
            <li><strong>模糊通配匹配：</strong>使用 * 做模糊匹配</li>
            <li><code>弹窗*</code>：匹配所有以「弹窗」开头的断言 / 控件</li>
            <li><code>*吐司</code>：匹配所有以「吐司」结尾的断言 / 控件</li>
            <li><code>*提示*</code>：匹配任意包含「提示」的内容</li>
            <li><strong>多关键词分隔：</strong>多个匹配内容用英文逗号分隔，例：<code>吐司弹窗,*提示*,全局弹窗</code></li>
          </ul>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-dropdown trigger="click" @command="applyTemplate">
            <el-button class="btn-aux">
              填充常用模板
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="soft_toast">弱提示吐司跳过</el-dropdown-item>
                <el-dropdown-item command="ad_popup">广告弹窗遮挡</el-dropdown-item>
                <el-dropdown-item command="core_crash">核心崩溃阻断</el-dropdown-item>
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
import { Close, ArrowDown } from '@element-plus/icons-vue'
import { assertPolicyApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 允许：中文、字母数字、通配符*、英文逗号、下划线、横杠、空格、中文顿号 */
const ILLEGAL_RE = /[<>{}[\]\\|^=$`~!@#%&;'"?/+()]/

const TEMPLATES = {
  soft_toast: {
    rule_type: 'whitelist',
    target_type: 'assert_type',
    pattern: '吐司弹窗,*提示*,弱提示',
    description: '弹窗弱提示断言失败自动跳过，不阻断回归',
    enabled: true
  },
  ad_popup: {
    rule_type: 'whitelist',
    target_type: 'element_name',
    pattern: '广告弹窗,权限弹窗,升级弹窗,*遮罩*',
    description: '页面悬浮遮挡控件自动跳过',
    enabled: true
  },
  core_crash: {
    rule_type: 'blacklist',
    target_type: 'assert_type',
    pattern: '崩溃提示,*失败*,核心校验',
    description: '核心业务校验失败强制阻断',
    enabled: true
  }
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const snapshot = ref('')
const form = reactive(blankForm())
const errors = reactive({ pattern: '' })

const isEdit = computed(() => !!form.id)

const ruleHint = computed(() => {
  if (form.rule_type === 'whitelist') return '匹配到目标断言失败时，自动跳过不阻断整套用例'
  if (form.rule_type === 'blacklist') return '匹配到目标断言失败时，立即终止当前套件 / 用例执行'
  return ''
})

const targetHint = computed(() => {
  if (form.target_type === 'assert_type') return '匹配自动化内校验步骤（吐司、弹窗提示断言）'
  if (form.target_type === 'element_name') return '匹配页面控件定位 ID / 文本（弹窗遮挡类控件）'
  return ''
})

function blankForm() {
  return {
    id: null,
    rule_type: 'whitelist',
    target_type: 'assert_type',
    pattern: '',
    description: '',
    enabled: true
  }
}

function clearErrors() {
  errors.pattern = ''
}

function normalizeTarget(t) {
  if (t === 'toast_pattern') return 'assert_type'
  if (['assert_type', 'element_name'].includes(t)) return t
  return 'assert_type'
}

function takeSnapshot() {
  return JSON.stringify({ ...form })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function validatePattern(raw, { soft = false } = {}) {
  const v = String(raw ?? '').trim()
  if (!v) return soft ? '' : '请填写匹配模式'
  if (ILLEGAL_RE.test(v)) {
    return '匹配模式含非法特殊字符，仅支持中文关键词、字母数字、通配符 * 与英文逗号'
  }
  // 连续逗号 / 空段
  const parts = v.split(',').map(s => s.trim())
  if (parts.some(p => !p)) return '多关键词请用英文逗号分隔，且不能出现空段'
  for (const p of parts) {
    if (p.replace(/\*/g, '').length === 0) return '通配符 * 需配合关键词使用，不可单独填写'
  }
  return ''
}

function onPatternInput() {
  if (!form.pattern.trim()) {
    errors.pattern = '请填写匹配模式'
    return
  }
  errors.pattern = validatePattern(form.pattern)
}

function applyRow(row) {
  clearErrors()
  if (row) {
    Object.assign(form, {
      id: row.id,
      rule_type: row.rule_type === 'blacklist' ? 'blacklist' : 'whitelist',
      target_type: normalizeTarget(row.target_type),
      pattern: row.pattern || '',
      description: row.description || '',
      enabled: row.enabled !== false
    })
  } else {
    Object.assign(form, blankForm())
  }
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
    await ElMessageBox.confirm('当前策略配置未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前策略配置未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空当前策略配置，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  clearErrors()
  ElMessage.success('已重置')
}

function applyTemplate(cmd) {
  const tpl = TEMPLATES[cmd]
  if (!tpl) return
  Object.assign(form, {
    rule_type: tpl.rule_type,
    target_type: tpl.target_type,
    pattern: tpl.pattern,
    description: tpl.description,
    enabled: tpl.enabled
  })
  errors.pattern = ''
  const names = { soft_toast: '弱提示吐司跳过', ad_popup: '广告弹窗遮挡', core_crash: '核心崩溃阻断' }
  ElMessage.success(`已填充「${names[cmd] || ''}」模板`)
}

function validateAll() {
  clearErrors()
  const err = validatePattern(form.pattern)
  if (err) {
    errors.pattern = err
    return false
  }
  return true
}

async function submit() {
  if (!validateAll()) {
    ElMessage.warning('请修正标红字段后再保存')
    return
  }
  saving.value = true
  try {
    const payload = {
      rule_type: form.rule_type,
      target_type: form.target_type,
      pattern: form.pattern.trim(),
      description: form.description || '',
      enabled: !!form.enabled
    }
    if (isEdit.value) {
      await assertPolicyApi.update(form.id, payload)
      ElMessage.success('断言策略已保存')
    } else {
      await assertPolicyApi.create(payload)
      ElMessage.success('断言策略已创建')
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
  max-height: 70vh;
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
.field-ok {
  margin-top: 8px;
  font-size: 12px;
  color: #16a34a;
  line-height: 1.45;
}
.field-muted {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.45;
}
:deep(.is-error-input .el-input__wrapper) {
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
