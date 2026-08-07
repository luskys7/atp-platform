<template>
  <Transition name="dock-slide">
    <div v-if="sessions.length" class="screen-dock">
      <div class="dock-header">
        <el-icon><Monitor /></el-icon>
        <span>投屏进行中</span>
        <el-tag size="small" type="success" effect="dark">{{ sessions.length }}</el-tag>
      </div>
      <div class="dock-list">
        <button
          v-for="s in sessions"
          :key="s.deviceId"
          class="dock-item"
          :class="{ connecting: s.connecting }"
          @click="goScreen(s.deviceId)"
        >
          <span class="dock-dot" :class="{ live: s.connected }" />
          <span class="dock-name">{{ s.name }}</span>
          <span v-if="s.connected && s.fps" class="dock-meta">{{ s.fps }} FPS</span>
          <span v-else-if="s.connecting" class="dock-meta">连接中</span>
        </button>
      </div>
      <button class="dock-close-all" @click="confirmStopAll">全部断开</button>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { activeScreenSessions, stopAllScreenStreams, syncActiveScreenSessions } from '@/composables/useScreenStream'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const sessions = computed(() => activeScreenSessions.value)

function goScreen(deviceId) {
  router.push(`/devices/${deviceId}/screen`)
}

async function confirmStopAll() {
  await ElMessageBox.confirm('确定断开所有投屏连接？', '确认', { type: 'warning' })
  stopAllScreenStreams()
}

onMounted(() => syncActiveScreenSessions())
</script>

<style scoped lang="scss">
.screen-dock {
  position: fixed;
  right: 12px;
  bottom: 12px;
  z-index: 2000;
  width: 200px;
  background: rgba(12, 18, 34, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid var(--atp-dark-border);
  border-radius: 10px;
  box-shadow: var(--atp-shadow-lg);
  color: var(--atp-screen-text);
  overflow: hidden;
}

.dock-header {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  :deep(.el-icon) {
    font-size: 14px;
  }

  :deep(.el-tag) {
    height: 18px;
    padding: 0 5px;
    font-size: 11px;
    line-height: 16px;
  }
}

.dock-list {
  max-height: 140px;
  overflow-y: auto;
}

.dock-item {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;

  &:hover {
    background: var(--atp-primary-bg);
  }

  &.connecting {
    opacity: 0.85;
  }
}

.dock-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--atp-text-muted);
  flex-shrink: 0;

  &.live {
    background: var(--atp-success);
    box-shadow: 0 0 6px rgba(108, 212, 178, 0.55);
  }
}

.dock-name {
  flex: 1;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-meta {
  font-size: 10px;
  color: var(--atp-screen-text-muted);
}

.dock-close-all {
  width: 100%;
  padding: 5px;
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: transparent;
  color: var(--atp-danger);
  font-size: 11px;
  cursor: pointer;

  &:hover {
    background: var(--atp-danger-bg);
  }
}

.dock-slide-enter-active,
.dock-slide-leave-active {
  transition: all 0.25s ease;
}
.dock-slide-enter-from,
.dock-slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
