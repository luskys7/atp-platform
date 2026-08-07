<template>
  <div class="page-container ai-case-page">
    <PageHeader title="AI 生成用例">
      <template #actions>
        <el-tag v-if="statusTip" size="small" effect="plain">{{ statusTip }}</el-tag>
      </template>
    </PageHeader>

    <el-alert
      v-if="!enabled"
      type="warning"
      :closable="false"
      show-icon
      title="AI 用例功能已关闭（atp.ai-case.enabled=false），请联系管理员开启。"
      style="margin-bottom: 16px"
    />
    <template v-else>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <AppCard title="需求输入" :hover="false">
            <el-form label-width="96px" size="default">
              <el-form-item label="文档上传">
                <div class="doc-tools">
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept=".txt,.md,.markdown,.docx,.pdf,.csv,.log"
                    :on-change="onFileChange"
                  >
                    <el-button :loading="parsing">选择并解析</el-button>
                  </el-upload>
                  <span class="doc-hint">支持 txt / md / docx / pdf，解析后写入下方文本框</span>
                </div>
                <div v-if="docMeta" class="doc-meta">{{ docMeta }}</div>
              </el-form-item>

            <el-form-item label="Confluence">
              <div class="cf-block">
                <el-input
                  v-model="confluence.page_url"
                  placeholder="页面 URL（含 pages/{id}）或仅填 pageId"
                  clearable
                />
                <el-input
                  v-model="confluence.page_id"
                  placeholder="pageId（可选，URL 可解析时不必填）"
                  clearable
                  style="margin-top: 8px"
                />
                <el-row :gutter="8" style="margin-top: 8px">
                  <el-col :span="12">
                    <el-input
                      v-model="confluence.email"
                      placeholder="邮箱（Cloud API Token 推荐）"
                      clearable
                    />
                  </el-col>
                  <el-col :span="12">
                    <el-input
                      v-model="confluence.token"
                      type="password"
                      show-password
                      placeholder="Token（可留空用服务端配置）"
                      clearable
                    />
                  </el-col>
                </el-row>
                <el-button
                  style="margin-top: 8px"
                  :loading="fetchingCf"
                  :disabled="!confluence.page_url.trim() && !confluence.page_id.trim()"
                  @click="fetchConfluence"
                >
                  拉取页面正文
                </el-button>
                <span v-if="confluenceConfigured" class="doc-hint">服务端已配置 Confluence</span>
              </div>
            </el-form-item>

            <el-form-item label="需求/PRD" required>
              <el-input
                v-model="form.prd_text"
                type="textarea"
                :rows="14"
                placeholder="粘贴完整需求/PRD 原文。将生成标准功能测试用例（非自动化），并尽量覆盖端到端主路径"
              />
            </el-form-item>
            <el-form-item label="知识库">
              <div class="kb-block">
                <el-tag size="small" :type="ragReady ? 'success' : 'info'" effect="plain">
                  {{ ragReady ? 'RAG 就绪' : 'RAG 未就绪' }}
                </el-tag>
                <el-tag size="small" :type="testbrainReady ? 'success' : 'info'" effect="plain" style="margin-left: 6px">
                  {{ testbrainReady ? 'TestBrain 在线' : 'TestBrain 离线' }}
                </el-tag>
                <el-button
                  style="margin-left: 10px"
                  :loading="syncingKb"
                  :disabled="!form.prd_text.trim() || !testbrainReady"
                  @click="syncPrdToKnowledge"
                >
                  同步当前需求到知识库
                </el-button>
                <span class="doc-hint">需远程 TestBrain（默认 http://10.0.98.20:8000）在线；生成时 provider=testbrain 可走 RAG</span>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="generating" :disabled="!form.prd_text.trim()" @click="generate">
                生成用例
              </el-button>
              <el-button type="success" :loading="importing" :disabled="!drafts.length" @click="saveDrafts">
                可选：保存为草稿用例
              </el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>
      <el-col :xs="24" :md="12">
        <AppCard title="用例预览" :hover="false">
          <el-empty v-if="!drafts.length" description="生成后在此预览标准测试用例" :image-size="80" />
          <div v-else class="ai-drafts">
            <div class="ai-drafts-head">
              共 {{ drafts.length }} 条用例
              <span v-if="qualityTip" class="quality-tip">{{ qualityTip }}</span>
            </div>
            <el-alert
              v-if="reviewTip"
              type="warning"
              :closable="false"
              show-icon
              style="margin-bottom: 10px"
              :title="reviewTip"
            />
            <el-collapse>
              <el-collapse-item
                v-for="(d, i) in drafts"
                :key="i"
                :name="String(i)"
              >
                <template #title>
                  <div class="draft-title">
                    <span class="draft-name">{{ d.name || `用例 ${i + 1}` }}</span>
                    <el-tag size="small" effect="light" :type="priorityTagType(d.priority)">
                      {{ priorityLabel(d.priority) }}
                    </el-tag>
                    <el-tag size="small" type="info" effect="plain">{{ d.case_type || '功能' }}</el-tag>
                    <el-tag size="small" type="success" effect="plain">{{ (d.standard_steps || []).length || d.step_count || 0 }} 步</el-tag>
                    <el-tag v-if="(d.review_issues || []).length" size="small" type="warning" effect="light">
                      {{ d.review_issues.length }} 项待完善
                    </el-tag>
                  </div>
                </template>
                <div v-if="(d.review_issues || []).length" class="review-box">
                  <div class="review-title">完整性提示</div>
                  <ul>
                    <li v-for="(iss, ii) in d.review_issues" :key="ii">{{ iss }}</li>
                  </ul>
                </div>
                <div class="case-meta">
                  <div class="meta-row">
                    <span class="meta-k">模块</span>
                    <span class="meta-v">{{ d.module_name || '—' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-k">前置条件</span>
                    <span class="meta-v">{{ d.preconditions || '—' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-k">测试数据</span>
                    <span class="meta-v">{{ d.test_data || '—' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-k">总体预期</span>
                    <span class="meta-v">{{ d.expected_result || '—' }}</span>
                  </div>
                </div>
                <el-table :data="standardStepRows(d)" size="small" border stripe class="step-table">
                  <el-table-column type="index" label="#" width="44" />
                  <el-table-column label="操作步骤" min-width="160">
                    <template #default="{ row }">
                      <div class="cell-wrap">{{ row.step }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="预期结果" min-width="160">
                    <template #default="{ row }">
                      <div class="cell-wrap">{{ row.expected }}</div>
                    </template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </div>
        </AppCard>
      </el-col>
    </el-row>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { aiCaseApi } from '@/api'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import AppCard from '@/components/AppCard.vue'

const form = reactive({
  prd_text: ''
})
const confluence = reactive({
  page_url: '',
  page_id: '',
  email: '',
  token: ''
})
const drafts = ref([])
const qualityTip = ref('')
const reviewTip = ref('')
const generating = ref(false)
const importing = ref(false)
const parsing = ref(false)
const fetchingCf = ref(false)
const syncingKb = ref(false)
const statusTip = ref('')
const enabled = ref(true)
const confluenceConfigured = ref(false)
const testbrainReady = ref(false)
const ragReady = ref(false)
const docMeta = ref('')

function priorityLabel(p) {
  const n = Number(p)
  if (n === 0) return 'P0'
  if (n === 1) return 'P1'
  if (n === 2) return 'P2'
  if (n === 3) return 'P3'
  return 'P2'
}

/** Element Plus tag type：P0 危险红 / P1 警告橙 / P2 信息蓝 / P3 默认灰 */
function priorityTagType(p) {
  const n = Number(p)
  if (n === 0) return 'danger'
  if (n === 1) return 'warning'
  if (n === 2) return 'primary'
  if (n === 3) return 'info'
  return 'info'
}

function standardStepRows(d) {
  const list = d.standard_steps
  if (Array.isArray(list) && list.length) {
    return list.map((s) => ({
      step: s.step || s.action || '—',
      expected: s.expected || '—'
    }))
  }
  try {
    const visual = JSON.parse(d.steps_content || '{}')
    if (Array.isArray(visual.standard_steps) && visual.standard_steps.length) {
      return visual.standard_steps.map((s) => ({
        step: s.step || '—',
        expected: s.expected || '—'
      }))
    }
  } catch { /* ignore */ }
  return []
}

function applyPrdText(data) {
  const text = data?.prd_text || ''
  form.prd_text = text
  const parts = []
  if (data?.source) parts.push(`来源：${data.source}`)
  if (data?.char_count != null) parts.push(`${data.char_count} 字`)
  if (data?.truncated) parts.push('已截断')
  if (data?.title) parts.push(`标题：${data.title}`)
  docMeta.value = parts.join(' · ')
}

async function loadStatus() {
  try {
    const res = await aiCaseApi.status()
    const s = res.data || {}
    enabled.value = !!s.enabled
    confluenceConfigured.value = !!s.confluence_configured
    const scope = s.scope || {}
    testbrainReady.value = !!scope.testbrain_deployed
    ragReady.value = !!scope.rag_knowledge_base
    if (!s.enabled) {
      statusTip.value = '功能已关闭'
      return
    }
    const keyHint = s.has_llm_key ? `LLM(${s.llm_model})` : 'offline 标准用例'
    const ragHint = scope.rag_knowledge_base ? ' · RAG' : ''
    const tbHint = scope.testbrain_deployed ? ' · TestBrain' : ''
    statusTip.value = `提供方：${s.provider} · ${keyHint}${tbHint}${ragHint}`
  } catch {
    enabled.value = false
    statusTip.value = '无法读取 AI 状态'
  }
}

async function syncPrdToKnowledge() {
  if (!form.prd_text.trim()) return
  syncingKb.value = true
  try {
    let title = 'PRD-' + Date.now()
    if (docMeta.value && docMeta.value.includes('标题：')) {
      const t = docMeta.value.split('标题：')[1]?.split(' · ')[0]?.trim()
      if (t) title = t
    }
    const res = await aiCaseApi.ingestPrd({
      prd_text: form.prd_text,
      title
    })
    if (res.data?.success === false) {
      ElMessage.error(res.data?.message || '知识库入库失败')
      return
    }
    ElMessage.success(res.data?.message || `已同步到知识库（id=${res.data?.knowledge_id ?? '-'}）`)
    await loadStatus()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '同步知识库失败'
    ElMessage.error(String(msg).slice(0, 300))
  } finally {
    syncingKb.value = false
  }
}

async function onFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  parsing.value = true
  try {
    const res = await aiCaseApi.parseDocument(raw)
    applyPrdText(res.data || {})
    ElMessage.success('文档已解析并填入需求文本')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '文档解析失败')
  } finally {
    parsing.value = false
  }
}

async function fetchConfluence() {
  fetchingCf.value = true
  try {
    const res = await aiCaseApi.fetchConfluence({
      page_url: confluence.page_url || undefined,
      page_id: confluence.page_id || undefined,
      email: confluence.email || undefined,
      token: confluence.token || undefined
    })
    applyPrdText(res.data || {})
    ElMessage.success('Confluence 正文已填入')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || 'Confluence 拉取失败')
  } finally {
    fetchingCf.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const res = await aiCaseApi.generate({
      platform: 'android',
      app_package: '',
      prd_text: form.prd_text
    })
    drafts.value = res.data?.drafts || []
    const q = res.data?.quality || {}
    const rv = res.data?.review_summary || {}
    qualityTip.value = q.assert_count != null
      ? `完整用例 ${q.ready_for_editor ?? drafts.value.length} · 步骤预期 ${q.assert_count}`
      : ''
    reviewTip.value = rv.cases_with_issues
      ? `${rv.cases_with_issues} 条用例存在完整性提示，请展开查看`
      : ''
    if (res.data?.note) statusTip.value = res.data.note
    const used = res.data?.provider_used
    if (used) {
      statusTip.value = (statusTip.value ? statusTip.value + ' · ' : '') + `本次：${used}`
    }
    if (!drafts.value.length) {
      ElMessage.warning('未生成到用例，请检查需求文本后重试')
    } else {
      ElMessage.success(`已生成 ${drafts.value.length} 条标准用例` + (used ? `（${used}）` : ''))
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '生成失败'
    if (/timeout|exceeded|ECONNABORTED/i.test(String(msg))) {
      ElMessage.error('生成超时：请重启前端（vite 代理已加长超时）后重试')
    } else {
      ElMessage.error(msg)
    }
  } finally {
    generating.value = false
  }
}

async function saveDrafts() {
  importing.value = true
  try {
    const res = await aiCaseApi.importDrafts({
      drafts: drafts.value
    })
    const n = res.data?.imported || 0
    ElMessage.success(`已单向落库 ${n} 条草稿用例（未跳转其他页面）`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadStatus()
})
</script>

<style scoped>
.ai-drafts {
  max-height: calc(100vh - 320px);
  overflow: auto;
}
.ai-drafts :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  line-height: 1.45;
  padding: 10px 12px 10px 0;
  white-space: normal;
  align-items: flex-start;
}
.ai-drafts :deep(.el-collapse-item__arrow) {
  margin-top: 4px;
}
.ai-drafts-head {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.quality-tip {
  font-weight: 400;
  font-size: 12px;
  color: #64748b;
}
.draft-title {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 6px;
  padding-right: 8px;
  white-space: normal;
  line-height: 1.45;
}
.draft-name {
  flex: 1 1 200px;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.step-table {
  width: 100%;
}
.step-table :deep(.el-table__cell) {
  vertical-align: top;
}
.cell-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.5;
}
.review-box {
  margin-bottom: 10px;
  padding: 8px 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  font-size: 12px;
  color: #92400e;
}
.review-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.review-box ul {
  margin: 0;
  padding-left: 18px;
}
.case-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #334155;
  line-height: 1.55;
}
.meta-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.meta-k {
  flex: 0 0 64px;
  color: #64748b;
}
.meta-v {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.muted {
  color: #94a3b8;
}
.doc-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.doc-hint {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}
.doc-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
}
.cf-block {
  width: 100%;
}
.kb-block {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  width: 100%;
}
</style>
