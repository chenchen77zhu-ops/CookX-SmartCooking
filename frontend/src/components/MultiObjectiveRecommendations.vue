<template>
  <section class="smart-recommendations" aria-labelledby="smart-recommendations-title">
    <header class="recommendation-heading">
      <div>
        <span class="section-kicker">SMART RECOMMENDATION</span>
        <h2 id="smart-recommendations-title">智能菜谱推荐</h2>
        <p>根据当前库存、食材临期情况和饮食偏好综合排序</p>
      </div>
      <button
        type="button"
        class="recommend-button"
        :disabled="loading"
        @click="fetchRecommendations"
      >
        <el-icon :class="{ spinning: loading }"><Refresh /></el-icon>
        {{ loading ? '正在分析' : hasRequested ? '重新推荐' : '获取智能推荐' }}
      </button>
    </header>

    <div v-if="viewState === 'initial'" class="state-panel initial-state">
      <span class="state-icon"><el-icon><DataAnalysis /></el-icon></span>
      <div><h3>从真实库存中寻找更合适的一餐</h3><p>算法只会推荐至少匹配一项安全关键食材的标准菜谱。</p></div>
    </div>

    <div v-else-if="viewState === 'loading'" class="state-panel" role="status" aria-live="polite">
      <span class="loading-ring" aria-hidden="true"></span>
      <div><h3>正在计算综合推荐</h3><p>正在核对库存、临期情况和已保存偏好，请稍候。</p></div>
    </div>

    <div v-else-if="viewState === 'inventory_required' || viewState === 'no_eligible_recipes'" class="state-panel attention-state">
      <span class="state-icon"><el-icon><Box /></el-icon></span>
      <div><h3>{{ viewState === 'inventory_required' ? '需要可用库存' : '暂时没有合格菜谱' }}</h3><p>{{ stateMessage }}</p></div>
      <button type="button" class="secondary-button" @click="$emit('manage-inventory')">管理或识别食材</button>
    </div>

    <div v-else-if="viewState === 'auth_required'" class="state-panel attention-state">
      <span class="state-icon"><el-icon><User /></el-icon></span>
      <div><h3>请先登录</h3><p>{{ stateMessage }}</p></div>
      <button type="button" class="secondary-button" @click="router.push('/login')">前往登录</button>
    </div>

    <div v-else-if="viewState === 'error'" class="state-panel error-state" role="alert">
      <span class="state-icon"><el-icon><WarningFilled /></el-icon></span>
      <div><h3>推荐服务暂时不可用</h3><p>{{ stateMessage }}</p></div>
      <button type="button" class="secondary-button" @click="fetchRecommendations">重试</button>
    </div>

    <template v-else-if="viewState === 'success'">
      <div class="result-summary" aria-live="polite">
        <span>共 {{ eligibleCount }} 道合格候选</span>
        <span>已筛除 {{ filteredCount }} 道不匹配菜谱</span>
        <small>算法版本 {{ algorithmVersion }}</small>
      </div>

      <div class="recommendation-list">
        <article
          v-for="item in recommendations"
          :key="item.recipe_id"
          :class="['recommendation-card', { winner: item.rank === 1 }]"
        >
          <div class="card-topline">
            <span class="rank-badge">TOP {{ item.rank }}</span>
            <span class="score"><strong>{{ formatScore(item.total_score) }}</strong><small>综合推荐分</small></span>
          </div>
          <h3>{{ item.recipe_name }}</h3>

          <div class="ingredient-groups">
            <div class="ingredient-group matched">
              <span>已匹配食材</span>
              <p v-if="item.matched_ingredients?.length">{{ item.matched_ingredients.join(' · ') }}</p>
              <p v-else class="muted">暂无</p>
            </div>
            <div class="ingredient-group missing">
              <span>缺失关键食材</span>
              <p v-if="item.missing_required_ingredients?.length">{{ item.missing_required_ingredients.join(' · ') }}</p>
              <p v-else class="positive-copy">无需补充</p>
            </div>
            <div :class="['ingredient-group', 'expiring', { 'has-expiring': item.expiring_ingredients_used?.length }]">
              <span>临期食材</span>
              <p v-if="item.expiring_ingredients_used?.length">{{ item.expiring_ingredients_used.join(' · ') }}</p>
              <p v-else class="muted">无</p>
            </div>
          </div>

          <div v-if="item.unsafe_or_expired_ingredients?.length" class="unsafe-warning" role="alert">
            <el-icon><WarningFilled /></el-icon>
            <span>过期或不可用：{{ item.unsafe_or_expired_ingredients.join(' · ') }}</span>
          </div>

          <div class="reason-block">
            <strong>推荐理由</strong>
            <ul><li v-for="reason in item.reasons" :key="reason">{{ reason }}</li></ul>
          </div>

          <details class="score-details">
            <summary>查看评分依据</summary>
            <div class="metric-list">
              <div v-for="metric in scoreMetrics(item)" :key="metric.key" :class="['metric-row', { penalty: metric.key === 'M' }]">
                <div><span>{{ metric.label }}</span><strong>{{ metric.display }}</strong></div>
                <div
                  class="metric-track"
                  role="progressbar"
                  :aria-label="metric.label"
                  :aria-valuemin="0"
                  :aria-valuemax="100"
                  :aria-valuenow="metric.value == null ? undefined : Math.round(metric.value * 100)"
                  :aria-valuetext="metric.display"
                ><i v-if="metric.value != null" :style="{ width: `${Math.round(metric.value * 100)}%` }"></i></div>
              </div>
            </div>
            <div class="weight-copy">
              <span v-for="(weight, key) in item.effective_weights" :key="key">{{ metricLabel(key) }}权重 {{ formatPercent(weight) }}</span>
              <span>缺失惩罚权重 {{ formatPercent(item.missing_penalty_weight) }}</span>
            </div>
            <p v-if="item.unavailable_components?.length" class="unavailable-copy">未参与评分：{{ item.unavailable_components.join('、') }}</p>
          </details>
        </article>
      </div>

      <p class="algorithm-note">综合推荐分由食材匹配、临期利用、偏好匹配、厨余减少和缺失食材惩罚共同计算。</p>
    </template>
  </section>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Box, DataAnalysis, Refresh, User, WarningFilled } from '@element-plus/icons-vue'
import { getMultiObjectiveRecommendations } from '@/api'

defineEmits(['manage-inventory'])

const router = useRouter()
const loading = ref(false)
const hasRequested = ref(false)
const viewState = ref('initial')
const stateMessage = ref('')
const recommendations = ref([])
const eligibleCount = ref(0)
const filteredCount = ref(0)
const algorithmVersion = ref('multi_objective_v1')
let requestController = null
let componentActive = true

const weights = Object.freeze({ I: 0.35, F: 0.25, P: 0.20, W: 0.20, lambda: 0.15 })
const metricLabels = { I: '食材匹配', F: '临期利用', P: '偏好匹配', W: '厨余减少', M: '缺失惩罚' }

const safeJsonObject = (key) => {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '{}')
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch {
    return {}
  }
}

const splitIngredients = (value) => {
  if (Array.isArray(value)) return value.map(item => String(item).trim()).filter(Boolean)
  if (typeof value !== 'string') return []
  return value.split(/[，,、\n]/).map(item => item.trim()).filter(Boolean)
}

const readPreferences = () => {
  const stored = safeJsonObject('cookx_preferences')
  const preferences = {}
  if (stored.taste) preferences.taste = stored.taste
  if (stored.spice) preferences.spice = stored.spice
  if (stored.duration) preferences.duration = stored.duration
  const disliked = splitIngredients(stored.dislikedIngredients)
  if (disliked.length) preferences.disliked_ingredients = disliked
  return preferences
}

const readUserId = () => {
  const user = safeJsonObject('user')
  return typeof user.id === 'string' && user.id.trim() ? user.id.trim() : null
}

const resetResults = () => {
  recommendations.value = []
  eligibleCount.value = 0
  filteredCount.value = 0
}

const fetchRecommendations = async () => {
  if (loading.value) return
  const userId = readUserId()
  if (!userId) {
    resetResults()
    hasRequested.value = true
    viewState.value = 'auth_required'
    stateMessage.value = '登录信息缺失或已失效，请重新登录后获取推荐。'
    return
  }

  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  hasRequested.value = true
  viewState.value = 'loading'
  stateMessage.value = ''

  try {
    const response = await getMultiObjectiveRecommendations({
      user_id: userId,
      top_k: 5,
      weights,
      preferences: readPreferences()
    }, requestController.signal)
    if (!componentActive) return
    const data = response.data || {}
    algorithmVersion.value = data.algorithm_version || 'multi_objective_v1'
    eligibleCount.value = Number(data.eligible_recipe_count) || 0
    filteredCount.value = Number(data.filtered_recipe_count) || 0
    recommendations.value = Array.isArray(data.recommendations) ? data.recommendations : []
    stateMessage.value = data.message || ''

    if (data.status === 'inventory_required') viewState.value = 'inventory_required'
    else if (data.status === 'no_eligible_recipes') viewState.value = 'no_eligible_recipes'
    else if (data.status === 'success') viewState.value = 'success'
    else {
      resetResults()
      viewState.value = 'error'
      stateMessage.value = '推荐服务返回了无法识别的状态，请稍后重试。'
    }
  } catch (error) {
    if (!componentActive || error?.code === 'ERR_CANCELED') return
    resetResults()
    viewState.value = error?.response?.status === 404 ? 'auth_required' : 'error'
    if (error?.response?.status === 404) stateMessage.value = '登录信息可能已失效，请重新登录。'
    else if (error?.response?.status === 422) stateMessage.value = '推荐参数暂时无法处理，请刷新页面后重试。'
    else stateMessage.value = '推荐服务暂时不可用，请检查网络或稍后重试。'
  } finally {
    if (componentActive) loading.value = false
  }
}

const formatScore = value => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '--'
const formatPercent = value => Number.isFinite(Number(value)) ? `${Math.round(Number(value) * 100)}%` : '数据不足'
const metricLabel = key => metricLabels[key] || key
const scoreMetrics = item => ['I', 'F', 'P', 'W', 'M'].map(key => {
  const value = item.component_scores?.[key]
  return { key, label: metricLabel(key), value: value == null ? null : Number(value), display: value == null ? '数据不足' : formatPercent(value) }
})

onBeforeUnmount(() => {
  componentActive = false
  requestController?.abort()
})
</script>

<style scoped>
.smart-recommendations { box-sizing: border-box; width: 100%; max-width: 960px; margin: 20px auto 0; padding: 20px; border: var(--cookx-border); border-radius: 22px; background: #fff; box-shadow: 0 14px 35px rgba(29, 57, 48, .06); }
.recommendation-heading, .card-topline, .result-summary, .metric-row > div:first-child, .weight-copy { display: flex; align-items: center; }
.recommendation-heading { justify-content: space-between; gap: 20px; }
.section-kicker { color: var(--cookx-accent); font-size: 9px; font-weight: 800; letter-spacing: 1.4px; }
.recommendation-heading h2 { margin: 5px 0 4px; color: var(--cookx-primary); font-size: 22px; }
.recommendation-heading p { margin: 0; color: var(--cookx-text-secondary); font-size: 12px; line-height: 1.6; }
.recommend-button, .secondary-button { min-height: 44px; padding: 0 18px; border: 0; border-radius: 14px; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.recommend-button { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 7px; background: var(--cookx-primary); color: #fff; }
.recommend-button:disabled { cursor: wait; opacity: .65; }
.secondary-button { background: rgba(23, 63, 53, .09); color: var(--cookx-primary); }
.spinning { animation: spin 1s linear infinite; }
.state-panel { display: flex; align-items: center; gap: 14px; margin-top: 18px; padding: 18px; border-radius: 17px; background: #f6f8f5; }
.state-panel > div { flex: 1; min-width: 0; }
.state-panel h3 { margin: 0 0 5px; color: var(--cookx-primary); font-size: 15px; }
.state-panel p { margin: 0; color: var(--cookx-text-secondary); font-size: 12px; line-height: 1.6; }
.state-icon { display: grid; flex: 0 0 42px; width: 42px; height: 42px; border-radius: 14px; background: rgba(23, 63, 53, .1); color: var(--cookx-primary); font-size: 21px; place-items: center; }
.attention-state { background: #fff8eb; }
.attention-state .state-icon { background: #f8e8c9; color: #a36c18; }
.error-state { background: #fff2ef; }
.error-state .state-icon { background: #f9d9d2; color: #b44a3b; }
.loading-ring { flex: 0 0 34px; width: 34px; height: 34px; border: 3px solid rgba(23, 63, 53, .12); border-top-color: var(--cookx-primary); border-radius: 50%; animation: spin .8s linear infinite; }
.result-summary { flex-wrap: wrap; gap: 8px; margin: 16px 0 11px; color: var(--cookx-text-secondary); font-size: 11px; }
.result-summary span { padding: 6px 10px; border-radius: 999px; background: #f1f5f2; }
.result-summary small { margin-left: auto; }
.recommendation-list { display: grid; gap: 9px; }
.recommendation-card { min-width: 0; padding: 14px 16px; border: 1px solid rgba(23, 63, 53, .09); border-radius: 17px; background: #fbfcfa; }
.recommendation-card.winner { padding: 17px 18px; border-color: rgba(77, 139, 105, .34); background: linear-gradient(145deg, #f5faf6, #fff); box-shadow: 0 10px 26px rgba(48, 99, 75, .08); }
.card-topline { justify-content: space-between; gap: 12px; }
.rank-badge { padding: 5px 9px; border-radius: 999px; background: rgba(77, 139, 105, .12); color: #356d52; font-size: 9px; font-weight: 800; letter-spacing: .7px; }
.score { display: flex; align-items: baseline; gap: 6px; color: var(--cookx-text-secondary); }
.score strong { color: var(--cookx-primary); font-size: 23px; }
.score small { font-size: 10px; }
.recommendation-card h3 { margin: 7px 0 9px; color: #202b27; font-size: 18px; }
.ingredient-groups { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.ingredient-group { min-width: 0; padding: 8px 9px; border-radius: 11px; background: #f3f5f2; }
.ingredient-group span { display: block; margin-bottom: 4px; color: var(--cookx-text-secondary); font-size: 9px; font-weight: 700; }
.ingredient-group p { margin: 0; overflow-wrap: anywhere; color: #38423e; font-size: 11px; line-height: 1.45; }
.ingredient-group.matched { background: #edf7f0; }
.ingredient-group.matched p, .ingredient-group .positive-copy { color: #32704f; }
.ingredient-group.missing { background: #fff7e8; }
.ingredient-group.missing p:not(.positive-copy) { color: #95651f; }
.ingredient-group.expiring { background: #f3f5f2; }
.ingredient-group.expiring.has-expiring { background: #fff9ed; }
.ingredient-group.expiring.has-expiring p { color: #856221; }
.ingredient-group .muted { color: #797f7c; }
.unsafe-warning { display: flex; align-items: flex-start; gap: 7px; margin-top: 8px; padding: 8px 10px; border-radius: 11px; background: #fff0ed; color: #b24437; font-size: 11px; line-height: 1.5; }
.reason-block { margin-top: 9px; }
.reason-block > strong { color: var(--cookx-primary); font-size: 11px; }
.reason-block ul { display: grid; gap: 3px; margin: 5px 0 0; padding-left: 18px; color: #59615d; font-size: 11px; line-height: 1.5; }
.score-details { margin-top: 9px; border-top: var(--cookx-border); }
.score-details summary { display: flex; align-items: center; box-sizing: border-box; min-height: 44px; padding: 8px 6px 0; border-radius: 9px; color: var(--cookx-primary); font-size: 11px; font-weight: 700; cursor: pointer; outline: none; }
.score-details summary:focus { outline: none; }
.score-details summary:focus-visible { box-shadow: 0 0 0 3px rgba(77, 139, 105, .24); }
.metric-list { display: grid; gap: 7px; margin-top: 9px; }
.metric-row { min-width: 0; }
.metric-row > div:first-child { justify-content: space-between; gap: 10px; margin-bottom: 4px; color: #606864; font-size: 10px; }
.metric-track { box-sizing: border-box; width: 100%; height: 6px; overflow: hidden; border-radius: 99px; background: #e7ebe8; }
.metric-track i { display: block; height: 100%; border-radius: inherit; background: #4d8b69; }
.metric-row.penalty .metric-track i { background: #c68143; }
.weight-copy { flex-wrap: wrap; gap: 4px; margin-top: 9px; }
.weight-copy span { padding: 4px 7px; border-radius: 7px; background: #eef1ef; color: #69716d; font-size: 9px; }
.unavailable-copy, .algorithm-note { color: #858d89; font-size: 10px; line-height: 1.55; }
.unavailable-copy { margin: 8px 0 0; }
.algorithm-note { margin: 11px 0 0; text-align: center; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 620px) {
  .smart-recommendations { max-width: none; padding: 14px; border-radius: 18px; }
  .recommendation-heading { align-items: flex-start; flex-direction: column; }
  .recommend-button { width: 100%; justify-content: center; }
  .ingredient-groups { grid-template-columns: 1fr; }
  .recommendation-card { padding: 13px; }
  .recommendation-card.winner { padding: 15px; }
  .state-panel { align-items: flex-start; flex-wrap: wrap; }
  .state-panel .secondary-button { width: 100%; }
  .result-summary small { width: 100%; margin-left: 0; }
}
</style>
