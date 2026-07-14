<template>
  <div v-if="canRecord" class="quick-record-fab" @click="onClick">
    <el-icon :size="22"><VideoCamera /></el-icon>
    <span v-if="!compact" class="fab-label">一键录制</span>
  </div>

  <el-dialog v-model="showPicker" title="选择录制设备" width="480px">
    <el-table :data="onlineDevices" v-loading="loading" size="small" @row-click="pickDevice">
      <el-table-column prop="name" label="设备" min-width="120">
        <template #default="{ row }">{{ row.name || row.serial_number }}</template>
      </el-table-column>
      <el-table-column prop="platform" label="平台" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="success">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !onlineDevices.length" description="暂无在线设备" />
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { deviceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { activeScreenSessions } from '@/composables/useScreenStream'
import { operationRecordingState } from '@/composables/useOperationRecording'
import { markRecordingBoot } from '@/composables/useRecordingStartup'

const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'

defineProps({
  compact: { type: Boolean, default: false }
})

const router = useRouter()
const userStore = useUserStore()
const showPicker = ref(false)
const loading = ref(false)
const devices = ref([])

const canRecord = computed(() => userStore.canEdit && !userStore.isReadonly)

const onlineDevices = computed(() =>
  devices.value.filter(d => ['online', 'busy'].includes(d.status))
)

async function loadDevices() {
  loading.value = true
  try {
    const res = await deviceApi.list({ page: 1, page_size: 100 })
    devices.value = res.data.list || []
  } finally {
    loading.value = false
  }
}

function onClick() {
  markRecordingBoot()
  if (operationRecordingState.active && operationRecordingState.deviceId) {
    router.push(`/devices/${operationRecordingState.deviceId}/screen`)
    return
  }
  const lastId = localStorage.getItem(LAST_RECORD_DEVICE_KEY)
  if (lastId) {
    router.push(`/devices/${lastId}/screen?auto_record=1`)
    return
  }
  const active = activeScreenSessions.value[0]
  if (active) {
    router.push(`/devices/${active.deviceId}/screen?auto_record=1`)
    return
  }
  showPicker.value = true
  loadDevices()
}

function pickDevice(row) {
  showPicker.value = false
  localStorage.setItem(LAST_RECORD_DEVICE_KEY, String(row.id))
  router.push(`/devices/${row.id}/screen?auto_record=1`)
}

onMounted(loadDevices)
</script>

<style scoped>
.quick-record-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 1900;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(239, 68, 68, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  user-select: none;
}
.quick-record-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(239, 68, 68, 0.45);
}
.fab-label {
  font-size: 14px;
  font-weight: 600;
}
</style>
