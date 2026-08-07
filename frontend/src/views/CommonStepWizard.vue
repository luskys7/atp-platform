<template>
  <div class="page-container wizard-page">
    <PageHeader
      :title="isEdit ? '编辑公共步骤' : '快速新建公共步骤'"
      subtitle="简易三步即可入库复用 · 可切换专业模式"
    >
      <template #actions>
        <el-button @click="goBack">返回列表</el-button>
      </template>
    </PageHeader>
    <AppCard :hover="false" class="wizard-card">
      <div v-if="loading" class="loading">加载中…</div>
      <CommonStepEditor
        v-else
        :edit-row="editRow"
        :return-to="returnTo"
        @saved="onSaved"
        @cancel="goBack"
      />
    </AppCard>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import AppCard from '@/components/AppCard.vue'
import CommonStepEditor from '@/components/common-step/CommonStepEditor.vue'
import { commonStepApi } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const editRow = ref(null)

const isEdit = computed(() => !!route.params.id)
const returnTo = computed(() => (typeof route.query.returnTo === 'string' ? route.query.returnTo : ''))

function goBack() {
  if (returnTo.value) router.push(returnTo.value)
  else router.push({ path: '/platform-config', query: { tab: 'steps' } })
}

function onSaved(payload) {
  if (payload?.wentToCase) return
  goBack()
}

onMounted(async () => {
  if (!route.params.id) {
    editRow.value = null
    // 支持从 query 套模板
    return
  }
  loading.value = true
  try {
    const res = await commonStepApi.get(route.params.id)
    editRow.value = res.data
  } catch (e) {
    ElMessage.error(e?.message || '加载公共步骤失败')
    goBack()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.wizard-page { max-width: 1100px; }
.wizard-card { padding: 8px 4px 16px; }
.loading { padding: 40px; text-align: center; color: #94a3b8; }
</style>
