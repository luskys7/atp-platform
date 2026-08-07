<template>
  <div class="cse">
    <div class="cse-toolbar">
      <el-radio-group v-model="uiMode" size="small" @change="onUiModeChange">
        <el-radio-button value="simple">简易模式</el-radio-button>
        <el-radio-button value="pro">专业模式</el-radio-button>
      </el-radio-group>
      <div class="cse-toolbar-right">
        <el-button v-if="uiMode === 'simple'" size="small" @click="showTemplates = true">套用模板</el-button>
        <el-button size="small" @click="goPicker">控件拾取</el-button>
      </div>
    </div>

    <!-- 简易：三步 -->
    <div v-if="uiMode === 'simple'" class="cse-simple">
      <el-steps :active="wizardStep" finish-status="success" align-center class="cse-steps">
        <el-step title="基础信息" description="名称 / 类型 / 适用端" />
        <el-step title="执行配置" description="动作参数 / 控件" />
        <el-step title="高级与保存" description="可选展开" />
      </el-steps>

      <section v-show="wizardStep === 0" class="cse-card" data-guide="basic">
        <div class="cse-card-title">基础信息 <span class="hint">仅 3 项必填</span></div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="步骤名称" required :error="errors.name">
            <el-input
              v-model="simple.name"
              maxlength="80"
              show-word-limit
              placeholder="例：关闭广告弹窗、机器人回充"
              @input="onNameInput"
            />
            <div v-if="nameSuggestions.length" class="suggest-row">
              <span class="suggest-label">推荐模板：</span>
              <el-tag
                v-for="t in nameSuggestions"
                :key="t.id"
                size="small"
                class="suggest-tag"
                @click="applyTpl(t.id)"
              >{{ t.name }}</el-tag>
            </div>
          </el-form-item>
          <el-form-item label="步骤类型（大类）" required>
            <el-select v-model="simple.category" style="width:100%" @change="onCategoryChange">
              <el-option
                v-for="c in visibleCategories"
                :key="c.key"
                :label="c.label"
                :value="c.key"
              >
                <span>{{ c.label }}</span>
                <span class="opt-hint">{{ c.hint }}</span>
              </el-option>
            </el-select>
            <el-checkbox v-model="showAdvancedCats" style="margin-top:8px">显示高级自定义类</el-checkbox>
          </el-form-item>
          <el-form-item label="适用端" required>
            <el-radio-group v-model="simple.platform">
              <el-radio-button v-for="p in PLATFORMS" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </section>

      <section v-show="wizardStep === 1" class="cse-card" data-guide="action">
        <div class="cse-card-title">执行配置</div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="具体动作" required>
            <el-select v-model="simple.action_key" style="width:100%" filterable>
              <el-option
                v-for="a in currentActions"
                :key="a.key"
                :label="a.label + (a.hazardous ? '（高危）' : '')"
                :value="a.key"
              />
            </el-select>
          </el-form-item>

          <template v-if="fieldHas('locator')">
            <el-form-item label="定位方式">
              <div class="locator-row">
                <el-select v-model="simple.locator_type" style="width:120px">
                  <el-option label="id" value="id" />
                  <el-option label="text" value="text" />
                  <el-option label="xpath" value="xpath" />
                  <el-option label="desc" value="content_desc" />
                </el-select>
                <el-input v-model="simple.locator_value" placeholder="定位值，可从控件拾取回填" />
                <el-button type="primary" plain @click="goPicker">拾取</el-button>
              </div>
            </el-form-item>
            <el-form-item label="控件名称">
              <el-input v-model="simple.element_name" placeholder="可选，便于步骤可读" />
            </el-form-item>
          </template>

          <el-form-item v-if="fieldHas('text')" label="输入文本">
            <el-input v-model="simple.text" placeholder="支持 ${变量}" />
          </el-form-item>
          <el-form-item v-if="fieldHas('seconds')" label="等待秒数">
            <el-input-number v-model="simple.seconds" :min="0" :max="120" :step="0.5" />
          </el-form-item>
          <el-form-item v-if="fieldHas('timeout')" label="超时（秒）">
            <el-input-number v-model="simple.timeout" :min="1" :max="120" />
          </el-form-item>
          <el-form-item v-if="fieldHas('duration_ms')" label="长按时长(ms)">
            <el-input-number v-model="simple.duration_ms" :min="200" :max="5000" :step="100" />
          </el-form-item>
          <template v-if="fieldHas('swipe_coords')">
            <el-form-item label="滑动坐标">
              <div class="coords">
                <el-input-number v-model="simple.x1" /> → <el-input-number v-model="simple.x2" />
                <el-input-number v-model="simple.y1" /> → <el-input-number v-model="simple.y2" />
              </div>
            </el-form-item>
          </template>
          <el-form-item v-if="fieldHas('condition')" label="判断条件">
            <el-input v-model="simple.condition" />
          </el-form-item>
          <el-form-item v-if="fieldHas('branch_true')" label="成立分支说明">
            <el-input v-model="simple.branch_true" />
          </el-form-item>
          <el-form-item v-if="fieldHas('branch_false')" label="否则分支说明">
            <el-input v-model="simple.branch_false" />
          </el-form-item>
          <el-form-item v-if="fieldHas('loop_count')" label="循环次数">
            <el-input-number v-model="simple.loop_count" :min="1" :max="999" />
          </el-form-item>
          <el-form-item v-if="fieldHas('loop_body')" label="循环体说明">
            <el-input v-model="simple.loop_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('try_body')" label="try 主流程">
            <el-input v-model="simple.try_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('catch_body')" label="catch 兜底">
            <el-input v-model="simple.catch_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('robot_command')" label="机器人指令">
            <el-select v-model="simple.robot_command" style="width:100%" filterable>
              <el-option v-for="c in ROBOT_COMMANDS" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('wait_after')" label="执行后等待(秒)">
            <el-input-number v-model="simple.wait_after" :min="0" :max="60" />
          </el-form-item>
          <el-form-item v-if="fieldHas('app_package')" label="应用包名">
            <el-input v-model="simple.app_package" placeholder="com.example.app" />
          </el-form-item>
          <el-form-item v-if="fieldHas('save_path')" label="截图保存路径">
            <el-input v-model="simple.save_path" placeholder="可选" />
          </el-form-item>
          <el-form-item v-if="fieldHas('shell_cmd')" label="Shell 命令">
            <el-input v-model="simple.shell_cmd" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item v-if="fieldHas('expected')" label="预期结果">
            <el-input v-model="simple.expected" />
          </el-form-item>
          <el-form-item v-if="fieldHas('actual')" label="实际值/变量">
            <el-input v-model="simple.actual" />
          </el-form-item>
          <el-form-item v-if="fieldHas('compare_op')" label="比较方式">
            <el-select v-model="simple.compare_op" style="width:100%">
              <el-option label="相等" value="equals" />
              <el-option label="包含" value="contains" />
              <el-option label="不相等" value="not_equals" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('var_name')" label="变量名">
            <el-input v-model="simple.var_name" />
          </el-form-item>
          <el-form-item v-if="fieldHas('var_value')" label="变量值">
            <el-input v-model="simple.var_value" />
          </el-form-item>
          <el-form-item v-if="fieldHas('file_path')" label="文件路径">
            <el-input v-model="simple.file_path" />
          </el-form-item>
          <el-form-item v-if="fieldHas('http_method')" label="HTTP 方法">
            <el-select v-model="simple.http_method" style="width:140px">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('http_url')" label="URL">
            <el-input v-model="simple.http_url" />
          </el-form-item>
          <el-form-item v-if="fieldHas('http_body')" label="Body">
            <el-input v-model="simple.http_body" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item v-if="fieldHas('script')" label="脚本内容" :required="isHazardous">
            <el-input v-model="simple.script" type="textarea" :rows="8" class="mono" />
          </el-form-item>
          <el-alert
            v-if="isHazardous"
            type="warning"
            :closable="false"
            show-icon
            title="高危步骤：保存前需确认，将立刻入库可用"
            style="margin-bottom:12px"
          />
          <p class="defaults-hint">默认：重试 0 次 · 失败终止当前步骤 · 超时 {{ simple.timeout || 3 }}s</p>
        </el-form>
      </section>

      <section v-show="wizardStep === 2" class="cse-card">
        <div class="cse-card-title">高级配置 <span class="hint">默认折叠，新手可跳过</span></div>
        <el-collapse v-model="advancedOpen">
          <el-collapse-item title="前置说明 / 备注" name="adv">
            <el-input v-model="simple.remark" type="textarea" :rows="2" placeholder="写入描述，便于同事理解" />
            <el-input
              v-model="simple.description"
              type="textarea"
              :rows="2"
              placeholder="完整描述（可留空，保存时自动生成）"
              style="margin-top:8px"
            />
          </el-collapse-item>
        </el-collapse>
        <div class="preview-box">
          <div class="preview-title">将生成步骤预览</div>
          <pre>{{ stepsPreview }}</pre>
        </div>
      </section>

      <div class="cse-nav">
        <el-button v-if="wizardStep > 0" @click="wizardStep -= 1">上一步</el-button>
        <el-button v-if="wizardStep < 2" type="primary" @click="nextWizard">下一步</el-button>
        <el-button v-if="wizardStep === 2" type="primary" :loading="saving" @click="submit">保存入库</el-button>
        <el-button @click="emitCancel">取消</el-button>
      </div>
    </div>

    <!-- 专业模式 -->
    <div v-else class="cse-pro">
      <section class="cse-card">
        <el-form label-position="top">
          <el-form-item label="步骤名称" required :error="errors.name">
            <el-input v-model="proName" maxlength="80" show-word-limit @input="errors.name = ''" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="proDesc" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </section>
      <section class="cse-card">
        <div class="section-head">
          <div class="cse-card-title" style="margin:0">自动化执行步骤</div>
          <el-radio-group v-model="editMode" size="small" @change="onProModeChange">
            <el-radio-button value="visual">可视化</el-radio-button>
            <el-radio-button value="raw">原生 JSON</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="editMode === 'visual'" class="visual-layout">
          <aside class="palette">
            <div class="palette-title">步骤库</div>
            <div v-for="group in PRO_PALETTE" :key="group.key" class="palette-group">
              <div class="palette-group-name">{{ group.label }}</div>
              <button
                v-for="item in group.items"
                :key="item.action_key"
                type="button"
                class="palette-item"
                @click="addProStep(item)"
              >{{ item.label }}</button>
            </div>
          </aside>
          <div class="canvas">
            <div v-if="!visualSteps.length" class="canvas-empty">从左侧添加步骤</div>
            <div v-for="(s, i) in visualSteps" :key="s._uid" class="canvas-step">
              <el-tag size="small">{{ s.type }}</el-tag>
              <span class="canvas-step-desc">{{ s.element_name || s.name || s.command || s.expected || '' }}</span>
              <div class="canvas-step-actions">
                <el-button text size="small" @click="movePro(i, -1)">上</el-button>
                <el-button text size="small" @click="movePro(i, 1)">下</el-button>
                <el-button text size="small" type="danger" @click="removePro(i)">删</el-button>
              </div>
            </div>
          </div>
        </div>
        <el-input
          v-else
          v-model="rawContent"
          type="textarea"
          :rows="16"
          class="mono"
          @input="onRawInput"
        />
        <div v-if="errors.steps" class="field-error">{{ errors.steps }}</div>
      </section>
      <div class="cse-nav">
        <el-button type="primary" :loading="saving" @click="submit">保存入库</el-button>
        <el-button @click="emitCancel">取消</el-button>
      </div>
    </div>

    <el-dialog v-model="showTemplates" title="高频模板库" width="480px" append-to-body>
      <div class="tpl-list">
        <button
          v-for="t in TEMPLATES"
          :key="t.id"
          type="button"
          class="tpl-item"
          @click="applyTpl(t.id); showTemplates = false"
        >
          <strong>{{ t.name }}</strong>
          <span>{{ categoryLabel(t.category) }} · {{ t.keywords.join(' / ') }}</span>
        </button>
      </div>
    </el-dialog>

    <el-alert
      v-if="showGuide"
      class="guide-banner"
      type="info"
      show-icon
      closable
      title="新手三步：① 填名称/类型/适用端 → ② 选动作并拾取控件 → ③ 保存入库（高级可跳过）"
      @close="finishGuide"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commonStepApi } from '@/api'
import {
  CATEGORIES, PLATFORMS, ROBOT_COMMANDS, PRO_PALETTE, TEMPLATES,
  UI_MODE_KEY, GUIDE_SEEN_KEY,
  actionsByCategory, getAction, categoryLabel,
  blankSimpleForm, buildStepsContent, parseStepsContentFull,
  formFromRow, applyTemplate, resolveStepsForSave,
  suggestTemplatesByName, uniqueName, isHazardousAction, autoDescription
} from '@/config/commonStepCatalog'

const props = defineProps({
  editRow: { type: Object, default: null },
  returnTo: { type: String, default: '' },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['saved', 'cancel'])

const router = useRouter()
let uidSeq = 1

const uiMode = ref(localStorage.getItem(UI_MODE_KEY) || 'simple')
const wizardStep = ref(0)
const saving = ref(false)
const showTemplates = ref(false)
const showAdvancedCats = ref(false)
const advancedOpen = ref([])
const showGuide = ref(false)
const editMode = ref('visual')
const visualSteps = ref([])
const rawContent = ref('{"steps":[],"meta":{}}')
const proName = ref('')
const proDesc = ref('')
const existingNames = ref([])
const simple = reactive(blankSimpleForm())
const errors = reactive({ name: '', steps: '' })

const visibleCategories = computed(() =>
  CATEGORIES.filter(c => showAdvancedCats.value || !c.foldDefault || simple.category === c.key)
)
const currentActions = computed(() => actionsByCategory(simple.category))
const isHazardous = computed(() => isHazardousAction(simple.action_key))
const nameSuggestions = computed(() => suggestTemplatesByName(simple.name))
const currentAction = computed(() => getAction(simple.action_key))
const stepsPreview = computed(() => {
  try {
    return JSON.stringify(resolveStepsForSave({ ...simple }), null, 2)
  } catch {
    return '[]'
  }
})

function fieldHas(name) {
  return (currentAction.value?.fields || []).includes(name)
}

function withUid(step) {
  return { ...step, _uid: uidSeq++ }
}

function onUiModeChange(mode) {
  localStorage.setItem(UI_MODE_KEY, mode)
  if (mode === 'pro') syncSimpleToPro()
  else syncProToSimple()
}

function onCategoryChange() {
  const list = actionsByCategory(simple.category)
  if (!list.find(a => a.key === simple.action_key)) {
    simple.action_key = list[0]?.key || ''
    applyActionDefaults(simple.action_key)
  }
}

function applyActionDefaults(key) {
  const a = getAction(key)
  if (!a?.defaults) return
  Object.assign(simple, a.defaults)
}

watch(() => simple.action_key, (k, prev) => {
  if (k && k !== prev) applyActionDefaults(k)
})

function onNameInput() {
  errors.name = simple.name?.trim() ? '' : '请填写步骤名称'
}

function applyTpl(id) {
  const next = applyTemplate(id, { ...simple })
  Object.assign(simple, next)
  if (next._multiSteps) simple._multiSteps = next._multiSteps
  else delete simple._multiSteps
  ElMessage.success(`已套用模板`)
}

function nextWizard() {
  if (wizardStep.value === 0) {
    if (!simple.name?.trim()) {
      errors.name = '请填写步骤名称'
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (!simple.category || !simple.platform) {
      ElMessage.warning('请选择类型与适用端')
      return
    }
  }
  if (wizardStep.value === 1) {
    if (!simple.action_key) {
      ElMessage.warning('请选择具体动作')
      return
    }
    if (fieldHas('script') && !simple.script?.trim()) {
      ElMessage.warning('请填写脚本内容')
      return
    }
  }
  wizardStep.value += 1
}

function syncSimpleToPro() {
  proName.value = simple.name
  proDesc.value = simple.description || autoDescription(simple)
  const steps = resolveStepsForSave({ ...simple })
  visualSteps.value = steps.map(withUid)
  rawContent.value = buildStepsContent({ ...simple, ui_mode: 'pro' }, steps)
}

function syncProToSimple() {
  simple.name = proName.value || simple.name
  simple.description = proDesc.value
  const parsed = parseStepsContentFull(rawContent.value)
  if (parsed.ok && parsed.meta) {
    Object.assign(simple, formFromRow({
      name: proName.value,
      description: proDesc.value,
      steps_content: rawContent.value
    }))
  }
}

function addProStep(item) {
  visualSteps.value.push(withUid({ type: item.type, enabled: true, ...(item.defaults || {}) }))
  syncProRaw()
}

function movePro(i, d) {
  const t = i + d
  if (t < 0 || t >= visualSteps.value.length) return
  const arr = visualSteps.value
  ;[arr[i], arr[t]] = [arr[t], arr[i]]
  syncProRaw()
}

function removePro(i) {
  visualSteps.value.splice(i, 1)
  syncProRaw()
}

function syncProRaw() {
  const steps = visualSteps.value.map(({ _uid, ...rest }) => rest)
  rawContent.value = JSON.stringify({
    steps,
    meta: {
      category: simple.category,
      platform: simple.platform,
      action_key: simple.action_key,
      ui_mode: 'pro'
    }
  }, null, 2)
}

function onProModeChange(mode) {
  if (mode === 'visual') {
    const parsed = parseStepsContentFull(rawContent.value)
    if (!parsed.ok) {
      ElMessage.warning(parsed.error || 'JSON 有误')
      editMode.value = 'raw'
      return
    }
    visualSteps.value = parsed.steps.map(withUid)
  } else {
    syncProRaw()
  }
}

function onRawInput() {
  const parsed = parseStepsContentFull(rawContent.value)
  errors.steps = parsed.ok ? '' : (parsed.error || '解析失败')
}

function goPicker() {
  const returnTo = props.returnTo || router.currentRoute.value.fullPath
  router.push({ path: '/element-picker', query: { returnTo, forCommonStep: '1' } })
}

function emitCancel() {
  emit('cancel')
}

function finishGuide() {
  showGuide.value = false
  localStorage.setItem(GUIDE_SEEN_KEY, '1')
}

function loadRow(row) {
  errors.name = ''
  errors.steps = ''
  wizardStep.value = 0
  if (!row) {
    Object.assign(simple, blankSimpleForm())
    delete simple._multiSteps
    proName.value = ''
    proDesc.value = ''
    visualSteps.value = []
    rawContent.value = '{"steps":[],"meta":{}}'
    return
  }
  Object.assign(simple, formFromRow(row))
  delete simple._multiSteps
  proName.value = row.name || ''
  proDesc.value = row.description || ''
  const parsed = parseStepsContentFull(row.steps_content || '{}')
  visualSteps.value = (parsed.steps || []).map(withUid)
  rawContent.value = JSON.stringify({
    steps: parsed.steps || [],
    meta: parsed.meta || {}
  }, null, 2)
  if (parsed.meta?.ui_mode === 'pro') uiMode.value = 'pro'
}

async function loadExistingNames() {
  try {
    const list = (await commonStepApi.list()).data || []
    existingNames.value = list.map(s => s.name).filter(Boolean)
  } catch {
    existingNames.value = []
  }
}

async function submit() {
  let name = ''
  let description = ''
  let stepsContent = ''
  let hazardous = false

  if (uiMode.value === 'simple') {
    name = simple.name?.trim()
    if (!name) {
      errors.name = '请填写步骤名称'
      wizardStep.value = 0
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (!simple.action_key) {
      wizardStep.value = 1
      ElMessage.warning('请选择动作')
      return
    }
    hazardous = isHazardous.value
    const names = existingNames.value.filter(n => !props.editRow || n !== props.editRow.name)
    if (names.includes(name)) {
      const renamed = uniqueName(name, names)
      try {
        await ElMessageBox.confirm(
          `名称「${name}」已存在，是否自动重命名为「${renamed}」？`,
          '重名校验',
          { type: 'warning', confirmButtonText: '使用新名称', cancelButtonText: '返回修改' }
        )
        name = renamed
        simple.name = renamed
      } catch {
        return
      }
    }
    description = simple.description?.trim() || autoDescription(simple)
    if (simple.remark) description = `${description}；${simple.remark}`
    const steps = resolveStepsForSave({ ...simple })
    if (!steps.length) {
      ElMessage.warning('未生成可执行步骤')
      return
    }
    stepsContent = buildStepsContent({ ...simple, ui_mode: 'simple', name }, steps)
  } else {
    name = proName.value?.trim()
    if (!name) {
      errors.name = '请填写步骤名称'
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (editMode.value === 'visual') syncProRaw()
    const parsed = parseStepsContentFull(rawContent.value)
    if (!parsed.ok) {
      errors.steps = parsed.error
      ElMessage.warning(parsed.error || '步骤脚本错误')
      return
    }
    hazardous = parsed.steps.some(s =>
      ['custom_script', 'shell'].includes(s.type)
    )
    description = proDesc.value || ''
    stepsContent = JSON.stringify({
      steps: parsed.steps,
      meta: {
        ...(parsed.meta || {}),
        ui_mode: 'pro',
        category: parsed.meta?.category || simple.category,
        platform: parsed.meta?.platform || simple.platform
      }
    }, null, 2)
  }

  if (hazardous) {
    try {
      await ElMessageBox.confirm(
        '该步骤包含自定义脚本 / Shell / 接口调用等高级能力，保存后将立刻对全平台可用。请确认内容安全。',
        '高危步骤确认',
        { type: 'warning', confirmButtonText: '确认保存上架', cancelButtonText: '返回检查' }
      )
    } catch {
      return
    }
  }

  saving.value = true
  try {
    const payload = { name, description, steps_content: stepsContent }
    if (props.editRow?.id) {
      await commonStepApi.update(props.editRow.id, payload)
      ElMessage.success('公共步骤已更新')
      emit('saved', { name, id: props.editRow.id, updated: true })
    } else {
      const res = await commonStepApi.create({ ...payload, status: 'active' })
      ElMessage.success(hazardous ? '高危步骤已入库可用（请知会测试负责人抽查）' : '公共步骤已保存入库')
      const id = res?.data?.id
      let wentToCase = false
      try {
        await ElMessageBox.confirm('是否立即在用例中引用该步骤？', '立即引用', {
          confirmButtonText: '去用例编辑器',
          cancelButtonText: '稍后',
          type: 'success'
        })
        wentToCase = true
        if (props.returnTo) {
          const path = String(props.returnTo).split('?')[0]
          router.push({ path, query: { invokeCommon: name } })
        } else {
          router.push({ path: '/cases/editor', query: { invokeCommon: name } })
        }
      } catch { /* skip */ }
      emit('saved', { name, id, updated: false, wentToCase })
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.editRow, (row) => loadRow(row), { immediate: true })

onMounted(async () => {
  await loadExistingNames()
  const tpl = router.currentRoute.value.query.template
  if (!props.editRow && typeof tpl === 'string' && tpl) {
    applyTpl(tpl)
  }
  if (!props.editRow && !localStorage.getItem(GUIDE_SEEN_KEY) && uiMode.value === 'simple') {
    await nextTick()
    setTimeout(() => { showGuide.value = true }, 400)
  }
})

defineExpose({ loadRow, submit })
</script>

<style scoped>
.cse { display: flex; flex-direction: column; gap: 14px; }
.cse-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.cse-toolbar-right { display: flex; gap: 8px; }
.cse-steps { margin-bottom: 8px; }
.cse-card {
  padding: 16px 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.cse-card-title {
  font-weight: 700; margin-bottom: 12px; font-size: 15px;
}
.hint { font-weight: 400; color: #94a3b8; font-size: 12px; margin-left: 8px; }
.opt-hint { float: right; color: #94a3b8; font-size: 12px; }
.suggest-row { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.suggest-label { font-size: 12px; color: #64748b; }
.suggest-tag { cursor: pointer; }
.locator-row { display: flex; gap: 8px; width: 100%; }
.coords { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.defaults-hint { font-size: 12px; color: #94a3b8; margin: 0; }
.preview-box {
  margin-top: 14px; background: #0f172a; color: #e2e8f0;
  border-radius: 8px; padding: 10px 12px; max-height: 220px; overflow: auto;
}
.preview-title { font-size: 12px; color: #94a3b8; margin-bottom: 6px; }
.preview-box pre { margin: 0; font-size: 12px; white-space: pre-wrap; }
.cse-nav { display: flex; gap: 10px; flex-wrap: wrap; }
.mono :deep(textarea) { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.field-error { color: #dc2626; font-size: 12px; margin-top: 6px; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.visual-layout { display: grid; grid-template-columns: 200px 1fr; gap: 12px; min-height: 320px; }
.palette {
  background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px; overflow: auto; max-height: 420px;
}
.palette-title { font-weight: 600; font-size: 13px; margin-bottom: 8px; }
.palette-group { margin-bottom: 10px; }
.palette-group-name { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.palette-item {
  display: block; width: 100%; text-align: left; border: 1px solid #e2e8f0;
  background: #f8fafc; border-radius: 6px; padding: 6px 8px; margin-bottom: 4px;
  cursor: pointer; font-size: 12px;
}
.palette-item:hover { border-color: #93c5fd; background: #eff6ff; }
.canvas {
  background: #fff; border: 1px dashed #cbd5e1; border-radius: 8px;
  padding: 10px; overflow: auto; max-height: 420px;
}
.canvas-empty { color: #94a3b8; text-align: center; padding: 40px 12px; }
.canvas-step {
  display: flex; align-items: center; gap: 8px; padding: 8px;
  border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 6px;
}
.canvas-step-desc { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.canvas-step-actions { display: flex; }
.tpl-list { display: flex; flex-direction: column; gap: 8px; }
.tpl-item {
  text-align: left; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px 12px; background: #fff; cursor: pointer;
}
.tpl-item:hover { border-color: #93c5fd; background: #f8fafc; }
.tpl-item strong { display: block; margin-bottom: 4px; }
.tpl-item span { font-size: 12px; color: #64748b; }
.guide-banner { margin-bottom: 4px; }
</style>
