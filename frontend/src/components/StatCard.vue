<template>
  <el-card shadow="hover" class="stat-card-wrap">
    <div class="stat-card" :class="`stat-card--${variant}`">
      <div class="stat-card__icon">
        <el-icon><component :is="icon" /></el-icon>
      </div>
      <div class="stat-card__body">
        <div class="stat-card__value">
          <slot>{{ value }}</slot>
        </div>
        <div class="stat-card__label">
          {{ label }}
          <slot name="extra" />
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '' },
  icon: { type: String, default: 'DataLine' },
  variant: { type: String, default: 'primary' }
})
</script>

<style scoped lang="scss">
.stat-card-wrap {
  border: 1px solid var(--atp-border-neutral);
  border-radius: var(--atp-radius-lg);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
  }
}

.stat-card-wrap :deep(.el-card__body) {
  padding: 20px 22px;
}
</style>
