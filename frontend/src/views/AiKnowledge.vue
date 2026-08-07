<template>
  <div class="page-container">
    <PageHeader title="知识库">
      <template #actions>
        <el-tag size="small" :type="ragReady ? 'success' : 'info'" effect="plain">
          {{ ragReady ? 'RAG 就绪' : 'RAG 未就绪' }}
        </el-tag>
        <el-tag size="small" :type="testbrainReady ? 'success' : 'info'" effect="plain" style="margin-left: 6px">
          {{ testbrainReady ? 'TestBrain 在线' : 'TestBrain 离线' }}
        </el-tag>
        <el-button style="margin-left: 10px" :loading="loading" @click="loadList">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="!testbrainReady"
      type="warning"
      :closable="false"
      show-icon
          title="TestBrain 未就绪：请检查远程服务 http://10.0.98.20:8000 是否可访问。"
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16">
      <el-col :xs="24" :md="10">
        <AppCard title="录入知识" :hover="false">
          <el-form label-width="72px">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" placeholder="如：某某模块 PRD 摘要" clearable />
            </el-form-item>
            <el-form-item label="正文" required>
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="12"
                placeholder="粘贴需求、接口说明、历史用例要点等，将向量化写入 Milvus"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                :disabled="!form.title.trim() || !form.content.trim() || !testbrainReady"
                @click="save"
              >
                写入知识库
              </el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </el-col>
      <el-col :xs="24" :md="14">
        <AppCard title="已入库条目" :hover="false">
          <el-empty v-if="!items.length && !loading" description="暂无知识条目" :image-size="72" />
          <el-table v-else v-loading="loading" :data="items" size="small" stripe border max-height="520">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="content" label="内容摘要" min-width="200" show-overflow-tooltip />
            <el-table-column prop="created_at" label="时间" width="170" />
          </el-table>
        </AppCard>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiCaseApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AppCard from '@/components/AppCard.vue'

const form = reactive({ title: '', content: '' })
const items = ref([])
const loading = ref(false)
const saving = ref(false)
const testbrainReady = ref(false)
const ragReady = ref(false)

async function loadStatus() {
  try {
    const res = await aiCaseApi.status()
    const scope = res.data?.scope || {}
    testbrainReady.value = !!scope.testbrain_deployed
    ragReady.value = !!scope.rag_knowledge_base
  } catch {
    testbrainReady.value = false
    ragReady.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await aiCaseApi.listKnowledge()
    items.value = res.data?.knowledge_items || []
    if (res.data?.success === false && res.data?.message) {
      ElMessage.warning(res.data.message)
    }
  } catch (e) {
    items.value = []
    ElMessage.error(e?.response?.data?.message || e?.message || '加载知识库失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const res = await aiCaseApi.ingestKnowledge({
      title: form.title.trim(),
      content: form.content.trim()
    })
    if (res.data?.success === false) {
      ElMessage.error(res.data?.message || '入库失败')
      return
    }
    ElMessage.success(res.data?.message || `已入库 id=${res.data?.knowledge_id ?? '-'}`)
    form.title = ''
    form.content = ''
    await loadList()
    await loadStatus()
  } catch (e) {
    ElMessage.error(String(e?.response?.data?.message || e?.message || '入库失败').slice(0, 300))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadStatus()
  await loadList()
})
</script>
