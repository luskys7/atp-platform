<template>
  <el-card :shadow="shadow" :class="['app-card', { 'no-hover': !hover }]">
    <template v-if="title || $slots.header" #header>
      <slot name="header">
        <span>{{ title }}</span>
      </slot>
    </template>
    <slot />
  </el-card>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  shadow: { type: String, default: 'hover' },
  hover: { type: Boolean, default: true }
})
</script>
