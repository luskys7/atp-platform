<template>
  <el-card
    shadow="hover"
    class="stat-card-wrap"
    :class="{
      'is-clickable': clickable,
      'is-alert': alert,
      'is-zero': isZero
    }"
    @click="onClick"
  >
    <div class="stat-card" :class="[`stat-card--${variant}`, toneClass]">
      <div class="stat-card__icon">
        <el-icon><component :is="icon" /></el-icon>
      </div>
      <div class="stat-card__body">
        <div class="stat-card__value" :class="{ 'is-risk': risk }">
          <slot>{{ value }}</slot>
        </div>
        <div class="stat-card__label">
          {{ label }}
          <slot name="extra" />
        </div>
        <div v-if="$slots.sub" class="stat-card__sub">
          <slot name="sub" />
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '' },
  icon: { type: String, default: 'DataLine' },
  variant: { type: String, default: 'primary' },
  /** case | device | pass | cover — 业务色调 */
  tone: { type: String, default: '' },
  clickable: { type: Boolean, default: false },
  /** 异常警示边框（在线=0、通过率过低等） */
  alert: { type: Boolean, default: false },
  /** 风险数值标红 */
  risk: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

const isZero = computed(() => {
  const v = props.value
  if (v === '' || v == null) return false
  const n = Number(String(v).replace('%', ''))
  return !Number.isNaN(n) && n === 0
})

const toneClass = computed(() => (props.tone ? `stat-card--tone-${props.tone}` : ''))

function onClick(e) {
  if (!props.clickable) return
  emit('click', e)
}
</script>

<style scoped lang="scss">
.stat-card-wrap {
  border: none !important;
  border-radius: var(--atp-radius-lg);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  --el-card-border-color: transparent;

  &:hover {
    transform: translateY(-2px);
  }

  &.is-clickable {
    cursor: pointer;

    &:hover {
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
      transform: translateY(-3px);
    }
  }

  &.is-zero :deep(.stat-card__value) {
    color: var(--atp-text-secondary);
  }
}

.stat-card-wrap :deep(.el-card__body) {
  padding: 20px 22px;
}

.stat-card__value.is-risk {
  color: var(--atp-danger) !important;
}

.stat-card__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--atp-text-secondary);
}

.stat-card--tone-case .stat-card__icon {
  background: #f1f5f9;
  color: #64748b;
}
.stat-card--tone-device .stat-card__icon {
  background: var(--atp-primary-bg, #e0f2fe);
  color: var(--atp-primary, #0284c7);
}
.stat-card--tone-pass .stat-card__icon {
  background: var(--atp-success-bg, #d1fae5);
  color: var(--atp-success, #059669);
}
.stat-card--tone-cover .stat-card__icon {
  background: var(--atp-warning-bg, #fef3c7);
  color: var(--atp-warning, #d97706);
}
</style>
