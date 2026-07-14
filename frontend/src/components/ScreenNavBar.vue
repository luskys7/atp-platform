<template>
  <div class="android-nav-bar" :style="{ width: barWidth }">
    <button type="button" class="nav-btn" :disabled="disabled" title="返回" @click="$emit('key', 'back')">
      <el-icon :size="20"><Back /></el-icon>
    </button>
    <button type="button" class="nav-btn nav-home" :disabled="disabled" title="主屏幕" @click="$emit('key', 'home')">
      <el-icon :size="22"><HomeFilled /></el-icon>
    </button>
    <button type="button" class="nav-btn" :disabled="disabled" title="多任务" @click="$emit('key', 'recent')">
      <el-icon :size="20"><Grid /></el-icon>
    </button>
  </div>
</template>

<script setup>
defineProps({
  disabled: { type: Boolean, default: false },
  barWidth: { type: String, default: '100%' }
})
defineEmits(['key'])
</script>

<style scoped lang="scss">
.android-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 36px;
  padding: 0 12px;
  background: var(--atp-screen-bg);
  border-radius: 0 0 12px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--atp-screen-text-muted);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;

  &:hover:not(:disabled) {
    color: var(--atp-screen-text);
    background: rgba(255, 255, 255, 0.06);
  }

  &:active:not(:disabled) {
    background: rgba(255, 255, 255, 0.1);
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }

  &.nav-home {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 1.5px solid rgba(168, 155, 194, 0.5);
    color: var(--atp-brand-200);

    &:hover:not(:disabled) {
      border-color: rgba(237, 228, 255, 0.7);
      color: var(--atp-screen-text);
    }
  }
}
</style>
