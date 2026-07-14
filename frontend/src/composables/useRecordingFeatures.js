import { ref } from 'vue'
import { recordingApi } from '@/api'

const features = ref({
  recording_v2: true,
  min_recognition_rate: 95,
  min_locator_hit_rate: 98,
  max_long_tasks_per_min: 2,
  runtime_overrides: false,
  source: 'yaml_default'
})
let loaded = false
let loadingPromise = null

export function invalidateRecordingFeatures() {
  loaded = false
}

export function useRecordingFeatures() {
  async function loadFeatures(force = false) {
    if (loaded && !force) return features.value
    if (loadingPromise && !force) return loadingPromise
    loadingPromise = recordingApi.features()
      .then(res => {
        features.value = { ...features.value, ...(res.data || {}) }
        loaded = true
        return features.value
      })
      .catch(() => features.value)
      .finally(() => { loadingPromise = null })
    return loadingPromise
  }

  return { features, loadFeatures, invalidateRecordingFeatures }
}
