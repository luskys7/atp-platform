<template>
  <el-dialog
    :model-value="modelValue"
    width="960px"
    top="4vh"
    class="common-step-editor-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dlg-header">
        <h3 class="dlg-title">{{ editRow ? '编辑公共步骤' : '快速新建公共步骤' }}</h3>
      </div>
    </template>
    <CommonStepEditor
      v-if="modelValue"
      :edit-row="editRow"
      :return-to="returnTo"
      embedded
      @saved="onSaved"
      @cancel="emit('update:modelValue', false)"
    />
  </el-dialog>
</template>

<script setup>
import CommonStepEditor from '@/components/common-step/CommonStepEditor.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  editRow: { type: Object, default: null },
  returnTo: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

function onSaved(payload) {
  emit('saved', payload)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.dlg-header { display: flex; align-items: center; justify-content: space-between; }
.dlg-title { margin: 0; font-size: 18px; font-weight: 700; }
</style>
