import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getEngineeringStatus, type EngineeringStatus } from '../api/engineering'

export const useEngineeringStore = defineStore('engineering', () => {
  const status = ref<EngineeringStatus | null>(null)
  const traceId = ref('')
  const loading = ref(false)
  const error = ref('')

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    status.value = null
    try {
      const result = await getEngineeringStatus()
      status.value = result.data
      traceId.value = result.traceId
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '检查失败'
    } finally {
      loading.value = false
    }
  }

  return { status, traceId, loading, error, load }
})
