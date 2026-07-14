<template>
  <transition name="slide-up">
    <div v-if="state.active" class="recording-status-bar">
      <span class="status-dot" :class="{ paused: state.paused, error: state.error }" />
      <span class="status-text">{{ statusLabel }}</span>
      <span class="status-meta">{{ formattedDuration }} · {{ formattedSize }} · {{ state.stepCount }} 步</span>
      <span v-if="state.effectiveFps" class="status-meta">· {{ state.effectiveFps }}fps</span>
      <span v-if="state.performanceGrade" class="status-meta">· {{ perfLabel(state.performanceGrade) }}</span>
      <span v-if="state.segmentCount" class="status-meta">· {{ state.segmentCount }} 切点</span>
      <el-button v-if="!state.paused" size="small" plain @click="$emit('pause')">暂停</el-button>
      <el-button v-else size="small" type="warning" plain @click="$emit('resume')">继续</el-button>
      <el-button size="small" plain @click="$emit('mark-segment')">标记切点</el-button>
      <el-button size="small" type="primary" plain @click="$emit('finish')">结束</el-button>
      <el-button size="small" type="danger" plain @click="$emit('cancel')">取消</el-button>
    </div>
  </transition>
</template>

<script setup>
import { computed } from 'vue'
import { useOperationRecording } from '@/composables/useOperationRecording'

defineEmits(['pause', 'resume', 'mark-segment', 'finish', 'cancel'])

const { state, statusLabel } = useOperationRecording()

const formattedDuration = computed(() => {
  const ms = state.durationMs
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const formattedSize = computed(() => {
  const bytes = state.fileSizeBytes
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
})

function perfLabel(g) {
  return { good: '性能优', fair: '性能中', heavy: '高负载' }[g] || g
}
</script>

<style scoped>
.recording-status-bar {
  position: fixed;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--atp-bg-elevated, #fff);
  border: 1px solid var(--atp-border, #e2e8f0);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: pulse 1.2s infinite;
}
.status-dot.paused {
  background: #f59e0b;
  animation: none;
}
.status-dot.error {
  background: #64748b;
  animation: none;
}
.status-text {
  font-weight: 600;
  font-size: 13px;
}
.status-meta {
  font-size: 12px;
  color: var(--atp-text-muted);
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.slide-up-enter-active, .slide-up-leave-active {
  transition: all 0.25s ease;
}
.slide-up-enter-from, .slide-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-12px);
}
</style>
