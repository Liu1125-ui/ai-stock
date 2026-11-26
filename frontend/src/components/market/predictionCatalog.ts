import {inject, onBeforeUnmount, onMounted, provide, ref, type InjectionKey} from 'vue'
import {publishedModels, supportsFiveDayPrediction, type PublishedModel} from '../../api/predictions'
import type {Summary} from '../../api/market'

const forecastReadiness = {
  READY:'五日预测输入已就绪',
  CHECK_ON_SUBMIT:'行情已准备，提交时核验复权和交易日历',
  NO_DATA:'等待首份日线，行情就绪后可重新检查',
  INSUFFICIENT_HISTORY:'有效日线不足 60 个交易日或历史窗口不完整',
  STALE:'行情待更新，更新后可重新检查',
  CALENDAR_UNAVAILABLE:'交易日历暂无法核验',
  UNAVAILABLE:'预测行情暂不可用',
}

function createCatalog() {
  const models = ref<PublishedModel[]>([])
  const loading = ref(true)
  const failed = ref(false)
  let generation = 0
  async function reload() {
    const current = ++generation
    loading.value = true
    failed.value = false
    try {
      const result = await publishedModels()
      if (current === generation) models.value = result.items
    } catch {
      if (current === generation) failed.value = true
    } finally {
      if (current === generation) loading.value = false
    }
  }
  onMounted(() => void reload())
  onBeforeUnmount(() => {generation++})
  function supports(id: string) {
    return !loading.value && !failed.value && models.value.some(model => supportsFiveDayPrediction(model, id))
  }
  function canSubmit(id: string, quote?: Summary) {
    if (!supports(id) || quote?.state !== 'AVAILABLE') return false
    return !quote.forecastReadiness || quote.forecastReadiness === 'READY' || quote.forecastReadiness === 'CHECK_ON_SUBMIT'
  }
  function reason(id: string, quote?: Summary) {
    if (loading.value) return '正在检查预测能力'
    if (failed.value) return '暂无法确认预测模型'
    if (!supports(id)) return '暂无支持该股票的已发布五日模型'
    if (quote?.forecastReadiness) return forecastReadiness[quote.forecastReadiness]
    if (quote?.state !== 'AVAILABLE') return '暂缺已发布日线'
    return '支持五日预测，提交前核验行情'
  }
  return {failed, reload, supports, canSubmit, reason}
}

const homeCatalog: InjectionKey<ReturnType<typeof createCatalog>> = Symbol('home forecast catalog')
export function provideHomePredictionCatalog() {
  const catalog = createCatalog()
  provide(homeCatalog, catalog)
  return catalog
}
export function usePredictionCatalog() {
  return inject(homeCatalog, null) ?? createCatalog()
}

export function quoteAvailability(quote?: Summary) {
  if (quote?.state === 'UNAVAILABLE') return '行情暂不可用'
  if (quote?.state !== 'AVAILABLE') return '暂缺已发布日线'
  if (quote.freshness === 'STALE') return '可看日线 · 行情待更新'
  if (quote.freshness === 'UNKNOWN') return '可看日线 · 更新状态待确认'
  return '可看日线 · 已核验近期覆盖'
}
