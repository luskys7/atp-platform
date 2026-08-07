<template>
  <div class="page-testbrain">
    <iframe
      v-if="showFrame"
      :key="frameKey"
      class="tb-frame"
      src="/testbrain/"
      title="TestBrain"
      @load="onFrameLoad"
    />
    <div v-else class="tb-offline">
      <el-empty description="TestBrain 未就绪（请检查 http://10.0.98.20:8000 是否可访问）">
        <el-button type="primary" @click="retry">重试</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { aiCaseApi } from '@/api'

const showFrame = ref(true)
const frameKey = ref(0)
const loadedOk = ref(false)

async function check() {
  // 默认始终尝试加载 iframe；健康检查仅用于诊断，不阻断嵌入
  showFrame.value = true
  try {
    await aiCaseApi.status()
  } catch {
    /* ignore */
  }
}

function retry() {
  frameKey.value += 1
  showFrame.value = true
  check()
}

function onFrameLoad() {
  loadedOk.value = true
}

onMounted(check)
</script>

<style scoped>
.page-testbrain {
  height: calc(100vh - 56px);
  min-height: 480px;
  background: #fff;
  overflow: hidden;
}
.tb-frame {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}
.tb-offline {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
