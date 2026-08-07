<template>
  <el-tag v-if="connected && fps > 0" type="info" size="small" style="margin-left:8px">
    {{ fps }} FPS
  </el-tag>
</template>

<script setup>
import { useScreenStream } from '@/composables/useScreenStream'

const props = defineProps({
  deviceId: { type: [String, Number], required: true }
})

// 独立订阅 fps，避免父页面（含沉重结果面板）随帧统计重渲染
const { connected, fps } = useScreenStream(() => props.deviceId)
</script>
