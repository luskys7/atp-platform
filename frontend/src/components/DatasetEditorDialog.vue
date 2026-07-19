<template>
  <el-dialog
    :model-value="modelValue"
    width="780px"
    top="5vh"
    class="dataset-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="false"
    :before-close="handleBeforeClose"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ isEdit ? '编辑数据集' : '新建数据集' }}</h3>
        <el-button class="dlg-close" text @click="requestClose">
          <el-icon :size="18"><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div class="dlg-body" v-loading="loadingDetail">
      <!-- 分区 1：基础信息 -->
      <section class="section-card">
        <div class="section-title">基础信息</div>

        <div class="field-block">
          <div class="field-label">
            <span class="req">*</span>
            <span class="tip-label" title="用于参数化循环执行，一套数据可在多条用例复用">数据集名称</span>
          </div>
          <el-input
            v-model="form.name"
            placeholder="例：登录账号数据集、支付订单测试数据"
            maxlength="80"
            show-word-limit
            :class="{ 'is-error-input': !!errors.name }"
            @input="onNameInput"
          />
          <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
        </div>

        <div class="field-block">
          <div class="field-label">描述</div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="填写数据集用途说明，例：多组账号登录循环测试数据"
          />
        </div>
      </section>

      <!-- 分区 2：测试数据配置 -->
      <section class="section-card">
        <div class="section-head">
          <div class="section-title" style="margin:0">批量测试数据</div>
          <div class="mode-switch">
            <el-radio-group v-model="editMode" size="small" @change="onModeChange">
              <el-radio-button value="visual">可视化表单编辑（推荐）</el-radio-button>
              <el-radio-button value="raw">原生文本脚本编辑</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click" @command="applyTemplate">
              <el-button size="small" class="btn-tpl">
                填充通用模板
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="login">登录账号模板</el-dropdown-item>
                  <el-dropdown-item command="order">订单金额模板</el-dropdown-item>
                  <el-dropdown-item command="user">用户信息模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- 可视化：字段列 + 数据行 -->
        <div v-if="editMode === 'visual'" class="visual-editor">
          <div class="col-toolbar">
            <span class="col-label">字段列：</span>
            <el-tag
              v-for="(col, ci) in columns"
              :key="'c-' + ci"
              closable
              class="col-tag"
              @close="removeColumn(ci)"
            >
              <el-input
                v-model="columns[ci]"
                size="small"
                class="col-input"
                placeholder="字段名"
                @change="onColumnRename"
              />
            </el-tag>
            <el-button size="small" type="primary" plain @click="addColumn">
              <el-icon><Plus /></el-icon> 添加字段
            </el-button>
          </div>

          <div v-if="!columns.length" class="empty-hint">请先添加字段（如账号、密码、金额），再填写数据行</div>

          <div v-else class="data-table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="idx-col">#</th>
                  <th v-for="(col, ci) in columns" :key="'h-' + ci">{{ col || `字段${ci + 1}` }}</th>
                  <th class="act-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in visualRows" :key="'r-' + ri">
                  <td class="idx-col">{{ ri + 1 }}</td>
                  <td v-for="(col, ci) in columns" :key="'cell-' + ri + '-' + ci">
                    <el-input
                      v-model="row[col]"
                      size="small"
                      :placeholder="col || '值'"
                      @input="syncRawFromVisual"
                    />
                  </td>
                  <td class="act-col">
                    <el-button type="danger" plain size="small" @click="removeVisualRow(ri)">移除</el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="visual-actions">
            <el-button type="primary" plain :disabled="!columns.length" @click="addVisualRow">
              <el-icon><Plus /></el-icon> 添加数据行
            </el-button>
            <span class="preview-hint">已生成 {{ visualRows.length }} 组数据，保存时自动转为标准数组脚本</span>
          </div>
        </div>

        <!-- 原生文本 -->
        <div v-else class="raw-editor">
          <el-input
            v-model="form.rowsText"
            type="textarea"
            :rows="12"
            :class="{ 'is-error-input': !!errors.rows }"
            placeholder='[{"username":"test_user_01","password":"Pass@123"},{"username":"test_user_02","password":"Pass@456"}]'
            @input="onRawInput"
          />
          <div v-if="errors.rows" class="field-error">{{ errors.rows }}</div>
          <div v-else class="field-hint">数组中每一项对象代表一组循环执行入参</div>
        </div>
      </section>

      <!-- 分区 4：说明 -->
      <section class="hint-box">
        <div>填写规范：数据为数组格式，一组数据代表一次自动化执行入参</div>
        <div>使用场景：可在可视化用例、套件内开启数据循环，实现多组数据批量测试</div>
        <div>导入提示：支持 CSV 文件批量导入测试数据，快速生成数据集</div>
      </section>
    </div>

    <template #footer>
      <div class="dlg-footer">
        <div class="footer-left">
          <el-button class="btn-aux" @click="resetForm">重置表单</el-button>
          <el-button class="btn-aux" @click="openImportTemplate">导入数据模板</el-button>
          <el-button class="btn-aux" @click="triggerCsvPick">导入 CSV 文件</el-button>
          <input
            ref="csvInputRef"
            type="file"
            accept=".csv,text/csv"
            class="hidden-file"
            @change="onCsvFileChange"
          />
        </div>
        <div class="footer-right">
          <el-button @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>

  <!-- 导入已有数据集结构 -->
  <el-dialog
    v-model="showTplPicker"
    title="导入数据模板"
    width="480px"
    append-to-body
    destroy-on-close
  >
    <p class="tpl-tip">选择已有数据集，复用其字段结构与示例数据，快速新建同类数据集。</p>
    <el-select
      v-model="pickedTplId"
      filterable
      placeholder="请选择数据集"
      style="width:100%"
    >
      <el-option
        v-for="ds in datasetOptions"
        :key="ds.id"
        :label="`${ds.name}${ds.description ? ' · ' + ds.description : ''}`"
        :value="ds.id"
        :disabled="ds.id === form.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="showTplPicker = false">取消</el-button>
      <el-button type="primary" :loading="importingTpl" @click="applyImportedTemplate">导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { Close, ArrowDown, Plus } from '@element-plus/icons-vue'
import { datasetApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const TEMPLATES = {
  login: [
    { username: 'test_user_01', password: 'Pass@123' },
    { username: 'test_user_02', password: 'Pass@456' },
    { username: 'test_user_03', password: 'Pass@789' }
  ],
  order: [
    { order_id: 'ORD001', amount: '99.00', currency: 'CNY' },
    { order_id: 'ORD002', amount: '199.50', currency: 'CNY' },
    { order_id: 'ORD003', amount: '0.01', currency: 'CNY' }
  ],
  user: [
    { name: '张三', phone: '13800000001', email: 'zhangsan@example.com' },
    { name: '李四', phone: '13800000002', email: 'lisi@example.com' },
    { name: '王五', phone: '13800000003', email: 'wangwu@example.com' }
  ]
}

const saving = ref(false)
const loadingDetail = ref(false)
const editMode = ref('visual')
const snapshot = ref('')
const columns = ref([])
const visualRows = ref([])
const csvInputRef = ref(null)
const showTplPicker = ref(false)
const pickedTplId = ref(null)
const datasetOptions = ref([])
const importingTpl = ref(false)

const form = reactive(blankForm())
const errors = reactive({ name: '', rows: '' })

const isEdit = computed(() => !!form.id)

function blankForm() {
  return { id: null, name: '', description: '', rowsText: '[]' }
}

function clearErrors() {
  errors.name = ''
  errors.rows = ''
}

function takeSnapshot() {
  return JSON.stringify({
    form: { ...form },
    editMode: editMode.value,
    columns: columns.value,
    visualRows: visualRows.value
  })
}

function isDirty() {
  return takeSnapshot() !== snapshot.value
}

function onNameInput() {
  errors.name = form.name?.trim() ? '' : '请填写数据集名称'
}

function findJsonErrorLine(text) {
  try {
    JSON.parse(text || '[]')
    return null
  } catch (e) {
    const msg = String(e.message || '')
    const m = msg.match(/position\s+(\d+)/i) || msg.match(/at position\s+(\d+)/i)
    if (m) {
      const pos = Number(m[1])
      const before = (text || '').slice(0, pos)
      const line = before.split('\n').length
      return { line, message: `数据脚本格式错误，约在第 ${line} 行` }
    }
    return { line: null, message: '数据脚本格式错误，请检查 JSON 语法' }
  }
}

function parseRows(text) {
  const err = findJsonErrorLine(text)
  if (err) return { ok: false, error: err.message, rows: [] }
  try {
    const data = JSON.parse(text || '[]')
    if (!Array.isArray(data)) {
      return { ok: false, error: '数据脚本必须是数组格式，例：[{...},{...}]', rows: [] }
    }
    for (let i = 0; i < data.length; i++) {
      if (data[i] == null || typeof data[i] !== 'object' || Array.isArray(data[i])) {
        return { ok: false, error: `第 ${i + 1} 组数据必须是对象`, rows: [] }
      }
    }
    return { ok: true, error: '', rows: data }
  } catch {
    return { ok: false, error: '数据脚本格式错误', rows: [] }
  }
}

function collectColumns(rows) {
  const keys = []
  const seen = new Set()
  for (const row of rows) {
    for (const k of Object.keys(row || {})) {
      if (!seen.has(k)) {
        seen.add(k)
        keys.push(k)
      }
    }
  }
  return keys
}

function applyRowsToVisual(rows) {
  const cols = collectColumns(rows)
  columns.value = cols.length ? cols : []
  visualRows.value = (rows || []).map(r => {
    const obj = {}
    for (const c of columns.value) obj[c] = r?.[c] == null ? '' : String(r[c])
    return obj
  })
  if (!visualRows.value.length && columns.value.length) {
    visualRows.value = [emptyVisualRow()]
  }
}

function emptyVisualRow() {
  const obj = {}
  for (const c of columns.value) obj[c] = ''
  return obj
}

function syncRawFromVisual() {
  const rows = visualRows.value.map(row => {
    const obj = {}
    for (const c of columns.value) {
      const key = (c || '').trim()
      if (!key) continue
      obj[key] = row[c] ?? ''
    }
    return obj
  }).filter(obj => Object.keys(obj).length)
  form.rowsText = JSON.stringify(rows, null, 2)
  errors.rows = ''
}

function onColumnRename() {
  const unique = []
  const seen = new Set()
  for (const c of columns.value) {
    const t = (c || '').trim()
    if (!t) {
      unique.push(c)
      continue
    }
    if (seen.has(t)) unique.push(`${t}_${unique.length}`)
    else {
      seen.add(t)
      unique.push(c)
    }
  }
  columns.value = unique
  visualRows.value = visualRows.value.map(row => {
    const next = {}
    for (const c of columns.value) next[c] = row[c] ?? ''
    return next
  })
  syncRawFromVisual()
}

function addColumn() {
  let n = columns.value.length + 1
  let name = `field_${n}`
  while (columns.value.includes(name)) {
    n += 1
    name = `field_${n}`
  }
  columns.value.push(name)
  for (const row of visualRows.value) row[name] = ''
  if (!visualRows.value.length) visualRows.value.push(emptyVisualRow())
  syncRawFromVisual()
}

function removeColumn(ci) {
  const key = columns.value[ci]
  columns.value.splice(ci, 1)
  for (const row of visualRows.value) delete row[key]
  if (!columns.value.length) visualRows.value = []
  syncRawFromVisual()
}

function addVisualRow() {
  visualRows.value.push(emptyVisualRow())
  syncRawFromVisual()
}

function removeVisualRow(ri) {
  visualRows.value.splice(ri, 1)
  syncRawFromVisual()
}

function onRawInput() {
  const parsed = parseRows(form.rowsText)
  errors.rows = parsed.ok ? '' : parsed.error
}

function onModeChange(mode) {
  if (mode === 'visual') {
    const parsed = parseRows(form.rowsText)
    if (!parsed.ok) {
      ElMessage.warning('当前文本脚本有误，请先修正后再切换可视化')
      editMode.value = 'raw'
      return
    }
    applyRowsToVisual(parsed.rows)
    syncRawFromVisual()
  } else {
    syncRawFromVisual()
    onRawInput()
  }
}

function applyTemplate(cmd) {
  const tpl = TEMPLATES[cmd]
  if (!tpl) return
  form.rowsText = JSON.stringify(tpl, null, 2)
  applyRowsToVisual(tpl)
  errors.rows = ''
  const names = { login: '登录账号', order: '订单金额', user: '用户信息' }
  ElMessage.success(`已填充${names[cmd] || ''}模板，可继续修改`)
}

async function loadDetail(row) {
  clearErrors()
  editMode.value = 'visual'
  if (!row?.id) {
    Object.assign(form, blankForm())
    columns.value = []
    visualRows.value = []
    return
  }
  loadingDetail.value = true
  try {
    const res = await datasetApi.get(row.id)
    const ds = res.data?.dataset || res.data || row
    const rawRows = (res.data?.rows || []).map(r => {
      try { return JSON.parse(r.row_data_json || '{}') } catch { return {} }
    })
    Object.assign(form, {
      id: ds.id,
      name: ds.name || '',
      description: ds.description || '',
      rowsText: JSON.stringify(rawRows, null, 2)
    })
    applyRowsToVisual(rawRows)
    syncRawFromVisual()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载数据集失败')
    Object.assign(form, blankForm())
    columns.value = []
    visualRows.value = []
  } finally {
    loadingDetail.value = false
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  await loadDetail(props.editRow)
  await nextTick()
  snapshot.value = takeSnapshot()
})

async function handleBeforeClose(done) {
  if (!isDirty()) { done(); return }
  try {
    await ElMessageBox.confirm('当前填写内容未保存，是否确认关闭弹窗？', '未保存确认', {
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
    await ElMessageBox.confirm('当前填写内容未保存，是否确认关闭弹窗？', '未保存确认', {
      type: 'warning',
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续编辑'
    })
    emit('update:modelValue', false)
  } catch { /* stay */ }
}

async function resetForm() {
  try {
    await ElMessageBox.confirm('将清空名称、描述与全部测试数据，是否继续？', '重置表单', { type: 'warning' })
  } catch {
    return
  }
  const keepId = form.id
  Object.assign(form, blankForm(), { id: keepId })
  columns.value = []
  visualRows.value = []
  clearErrors()
  ElMessage.success('已重置')
}

async function openImportTemplate() {
  try {
    datasetOptions.value = (await datasetApi.list()).data || []
  } catch {
    datasetOptions.value = []
  }
  if (!datasetOptions.value.length) {
    ElMessage.warning('暂无可用数据集模板')
    return
  }
  pickedTplId.value = null
  showTplPicker.value = true
}

async function applyImportedTemplate() {
  if (!pickedTplId.value) {
    ElMessage.warning('请选择要导入的数据集')
    return
  }
  importingTpl.value = true
  try {
    const res = await datasetApi.get(pickedTplId.value)
    const ds = res.data?.dataset
    const rawRows = (res.data?.rows || []).map(r => {
      try { return JSON.parse(r.row_data_json || '{}') } catch { return {} }
    })
    if (!form.name?.trim() && ds?.name) {
      form.name = `${ds.name}_副本`
      errors.name = ''
    }
    if (!form.description?.trim() && ds?.description) {
      form.description = ds.description
    }
    form.rowsText = JSON.stringify(rawRows, null, 2)
    applyRowsToVisual(rawRows)
    syncRawFromVisual()
    errors.rows = ''
    showTplPicker.value = false
    ElMessage.success('已导入数据模板，请按需修改后保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导入失败')
  } finally {
    importingTpl.value = false
  }
}

function triggerCsvPick() {
  csvInputRef.value?.click()
}

function parseCsv(text) {
  const lines = String(text || '').replace(/^\uFEFF/, '').split(/\r?\n/).filter(l => l.trim())
  if (lines.length < 2) throw new Error('CSV 至少需要表头与一行数据')
  const headers = splitCsvLine(lines[0]).map(h => h.trim()).filter(Boolean)
  if (!headers.length) throw new Error('CSV 表头无效')
  const rows = []
  for (let i = 1; i < lines.length; i++) {
    const cells = splitCsvLine(lines[i])
    if (cells.every(c => !String(c || '').trim())) continue
    const obj = {}
    headers.forEach((h, idx) => { obj[h] = cells[idx] ?? '' })
    rows.push(obj)
  }
  if (!rows.length) throw new Error('CSV 中没有有效数据行')
  return rows
}

function splitCsvLine(line) {
  const result = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"'
        i += 1
      } else {
        inQuotes = !inQuotes
      }
    } else if (ch === ',' && !inQuotes) {
      result.push(cur)
      cur = ''
    } else {
      cur += ch
    }
  }
  result.push(cur)
  return result
}

async function onCsvFileChange(ev) {
  const file = ev.target?.files?.[0]
  ev.target.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const rows = parseCsv(text)
    form.rowsText = JSON.stringify(rows, null, 2)
    applyRowsToVisual(rows)
    syncRawFromVisual()
    errors.rows = ''
    ElMessage.success(`已从 CSV 导入 ${rows.length} 组测试数据`)
  } catch (e) {
    ElMessage.error(e?.message || 'CSV 解析失败')
  }
}

function validateAll() {
  clearErrors()
  let ok = true
  if (!form.name?.trim()) {
    errors.name = '请填写数据集名称'
    ok = false
  }
  if (editMode.value === 'visual') syncRawFromVisual()
  const parsed = parseRows(form.rowsText)
  if (!parsed.ok) {
    errors.rows = parsed.error
    if (editMode.value === 'visual') editMode.value = 'raw'
    ok = false
  }
  return { ok, rows: parsed.rows || [] }
}

async function submit() {
  const { ok, rows } = validateAll()
  if (!ok) {
    ElMessage.warning('请完善必填项并修正数据脚本错误')
    return
  }
  if (!rows.length) {
    try {
      await ElMessageBox.confirm(
        isEdit.value ? '当前数据集无测试数据，确认保存为空数据集吗？' : '当前数据集无测试数据，确认创建空数据集吗？',
        '空数据集确认',
        {
          type: 'warning',
          confirmButtonText: isEdit.value ? '仍要保存' : '仍要创建',
          cancelButtonText: '返回填写'
        }
      )
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description || '',
      rows
    }
    if (isEdit.value) {
      await datasetApi.update(form.id, payload)
      ElMessage.success('数据集已保存')
    } else {
      await datasetApi.create(payload)
      ElMessage.success('数据集已创建')
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
  max-height: 68vh;
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
:deep(.is-error-input .el-input__wrapper),
:deep(.is-error-input .el-textarea__inner) {
  box-shadow: 0 0 0 1px #f97316 inset !important;
}

.col-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.col-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.col-tag {
  height: auto;
  padding: 2px 4px 2px 6px;
  background: #fff;
}
.col-input {
  width: 96px;
}
.col-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
  padding: 0 4px;
}

.empty-hint {
  padding: 18px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}

.data-table-wrap {
  overflow: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 480px;
}
.data-table th,
.data-table td {
  padding: 8px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: middle;
}
.data-table th {
  background: #f1f5f9;
  font-size: 12px;
  color: #475569;
  font-weight: 600;
  white-space: nowrap;
}
.idx-col { width: 40px; color: #94a3b8; text-align: center !important; }
.act-col { width: 72px; white-space: nowrap; }

.visual-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.preview-hint {
  font-size: 12px;
  color: #64748b;
}

.hint-box {
  padding: 12px 14px;
  background: #f1f5f9;
  border-radius: 10px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.75;
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
.hidden-file { display: none; }
.tpl-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
</style>
