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

    <details class="recommendation-options">
      <summary>预算、难度与营养目标（可不填）</summary>
      <fieldset :disabled="loading" @change="criteriaChanged">
        <label>整道菜预算（元）<input v-model="criteria.budget" type="number" min="0.01" step="any" /></label>
        <label>期望难度<select v-model="criteria.difficulty_target" aria-label="期望难度"><option value="">不限制</option><option value="easy">简单</option><option value="medium">中等</option><option value="hard">较难</option></select></label>
        <label v-for="[key,label] in nutritionFields" :key="key">{{ label }}<input v-model="criteria[key]" type="number" min="0.01" step="any" /></label>
      </fieldset>
      <p>预算按整道菜估算，营养目标按每份计算。缺少基础数据时对应分项不参与评分；目标关联当前账号，重新推荐时保存到后端。</p>
    </details>
    <p v-if="viewState === 'stale'" role="status">库存或目标已变化，旧推荐已失效，请重新获取推荐。</p>
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

      <p v-if="personalization">{{ personalization.reason }}</p><button class="secondary-button" @click="router.push('/learning')">管理偏好学习与反馈</button>
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
          <h3>{{ item.recipe_name }}</h3><p v-if="item.personalization" class="muted">偏好重排依据：{{ item.personalization.reasons.map(r=>r.tag+'（'+r.direction+'）').join('、') || '当前标签模型' }}。此分值不是准确率；原综合推荐分保持不变。</p>

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
            <div class="source-details">
              <p>预算匹配：{{ item.cost_status || '数据不足' }}；整道菜估价 {{ item.estimated_cost ?? '未知' }} {{ item.currency || 'CNY' }}</p>
              <p v-for="note in item.cost_data_notes" :key="'cost'+note">{{ note }}</p>
              <p>难度匹配：{{ item.difficulty_status || '数据不足' }}；菜谱难度 {{ item.recipe_difficulty || '未知' }}</p>
              <p v-for="note in item.difficulty_data_notes" :key="'difficulty'+note">{{ note }}</p>
              <p>营养匹配：{{ item.nutrition_status || '数据不足' }}；口径 {{ item.nutrition_basis==='per_serving'?'每份':item.nutrition_basis || '未知' }}</p>
              <p v-for="note in item.nutrition_data_notes" :key="'nutrition'+note">{{ note }}</p>
              <p v-if="item.nutrition_disclaimer">{{ item.nutrition_disclaimer }}</p>
            </div>
          </details>
        </article>
      </div>

      <p class="algorithm-note">综合推荐分及有效权重均采用服务端结果；缺少数据的分项不由前端补算。</p>
    </template>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {businessApi,commandKey} from '../api/business'
import {readSession} from '../services/authSession'
import { useRouter } from 'vue-router'
import { Box, DataAnalysis, Refresh, User, WarningFilled } from '@element-plus/icons-vue'
import { getMultiObjectiveRecommendations } from '@/api'
import { nutritionFields, loadRecommendationPreferences, serializeRecommendationPreferences, recommendationPreferenceKey } from '../services/recommendationPreferences.js'

defineEmits(['manage-inventory'])
const props = defineProps({ inventoryRevision: Number, inventoryReady: Boolean, userId: String })
let requestVersion = 0

const router = useRouter()
const loading = ref(false)
const hasRequested = ref(false)
const viewState = ref('initial')
const stateMessage = ref('')
const recommendations = ref([])
const eligibleCount = ref(0)
const filteredCount = ref(0)
const algorithmVersion = ref('multi_objective_v1')
const personalization=ref(null)
let requestController = null
let componentActive = true

const criteria = ref(loadRecommendationPreferences(localStorage,props.userId))
let preferenceVersion=null,criteriaDirty=false
async function loadRemotePreferences(){if(!readSession())return;const user=readUserId();const data=(await businessApi('/preferences')).preferences;if(!componentActive||user!==readUserId())return;preferenceVersion=data.version;if(data.version>0){localStorage.setItem(`cookx:preferences:v1:${user}`,JSON.stringify(data.values));if(!criteriaDirty){criteria.value=Object.fromEntries(Object.entries(data.recommendation).map(([k,v])=>[k,v??'']));localStorage.setItem(recommendationPreferenceKey(user),JSON.stringify(criteria.value))}}}
onMounted(()=>loadRemotePreferences().catch(error=>{stateMessage.value='账号偏好读取失败：'+error.message}))
function criteriaChanged(){criteriaDirty=true;requestVersion++;requestController?.abort();loading.value=false;resetResults();viewState.value=hasRequested.value?'stale':'initial'}
watch(()=>props.userId,user=>{criteria.value=loadRecommendationPreferences(localStorage,user)})
const metricLabels = { I: '食材匹配', F: '临期利用', P: '偏好匹配', W: '厨余减少', B:'预算匹配', D:'难度匹配', N:'营养匹配', M: '缺失惩罚' }

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
  const stored = safeJsonObject(`cookx:preferences:v1:${readUserId()}`)
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
  recommendations.value = [];personalization.value=null
  eligibleCount.value = 0
  filteredCount.value = 0
}

const fetchRecommendations = async () => {
  if (loading.value || !props.inventoryReady) return
  const userId = readUserId()
  if (!userId) {
    resetResults()
    hasRequested.value = true
    viewState.value = 'auth_required'
    stateMessage.value = '登录信息缺失或已失效，请重新登录后获取推荐。'
    return
  }

  const version = ++requestVersion
  try { sessionStorage.setItem(`cookx:recommendation-requested:${userId}`, '1') } catch {}
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  hasRequested.value = true
  viewState.value = 'loading'
  stateMessage.value = ''

  try {
    if(readSession()){
      if(preferenceVersion===null)await loadRemotePreferences()
      if(criteriaDirty){const recommendation=Object.fromEntries(Object.entries(criteria.value).map(([k,v])=>[k,v===''?null:k==='difficulty_target'?v:Number(v)]));const saved=await businessApi('/preferences','put',{idempotency_key:commandKey(),expected_version:preferenceVersion,recommendation});preferenceVersion=saved.preferences.version;criteriaDirty=false}
    }
    const constraints=serializeRecommendationPreferences(criteria.value)
    localStorage.setItem(recommendationPreferenceKey(userId),JSON.stringify(criteria.value))
    const response = await getMultiObjectiveRecommendations({
      user_id: userId,
      top_k: 5,
      ...constraints,
      preferences: readPreferences()
    }, requestController.signal)
    if (!componentActive || version !== requestVersion || readUserId() !== userId) return
    const data = response.data || {}
    algorithmVersion.value = data.algorithm_version || 'multi_objective_v1'
    eligibleCount.value = Number(data.eligible_recipe_count) || 0
    filteredCount.value = Number(data.filtered_recipe_count) || 0
    personalization.value=data.personalization||null
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
    if (!componentActive || version !== requestVersion || readUserId() !== userId || error?.code === 'ERR_CANCELED') return
    resetResults()
    viewState.value = error?.response?.status === 404 ? 'auth_required' : 'error'
    if (error?.response?.status === 404) stateMessage.value = '登录信息可能已失效，请重新登录。'
    else if (error?.response?.status === 422) stateMessage.value = '推荐参数暂时无法处理，请刷新页面后重试。'
    else stateMessage.value = error?.response ? '推荐服务暂时不可用，请检查网络或稍后重试。' : error.message || '请求失败，请重试。'
  } finally {
    if (componentActive && version === requestVersion) loading.value = false
  }
}

const formatScore = value => value != null && Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '--'
const formatPercent = value => value != null && Number.isFinite(Number(value)) ? `${Math.round(Number(value) * 100)}%` : '数据不足'
const metricLabel = key => metricLabels[key] || key
const scoreMetrics = item => ['I', 'F', 'P', 'W', 'B', 'D', 'N', 'M'].map(key => {
  const value = item.component_scores?.[key]
  return { key, label: metricLabel(key), value: value == null ? null : Number(value), display: value == null ? '数据不足' : formatPercent(value) }
})

watch(() => [props.inventoryRevision, props.inventoryReady, props.userId], () => {
  requestVersion++
  requestController?.abort()
  loading.value = false
  resetResults()
  try { hasRequested.value = sessionStorage.getItem(`cookx:recommendation-requested:${props.userId}`) === '1' } catch { hasRequested.value = false }
  viewState.value = hasRequested.value ? 'stale' : 'initial'
  if (hasRequested.value && props.inventoryReady) fetchRecommendations()
})

onBeforeUnmount(() => {
  componentActive = false
  requestController?.abort()
})
</script>

<style scoped>
.smart-recommendations { width: 100%; margin: 12px 0 0; padding: 16px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); color: var(--ck-text); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.recommendation-heading { display: flex; flex-direction: column; align-items: stretch; gap: 12px; }
.section-kicker { color: #FFB27F; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.recommendation-heading h2 { margin: 2px 0; font-size: 18px; font-weight: 600; }
.recommendation-heading p { color: var(--ck-text-3); font-size: 12.5px; line-height: 1.6; }
.recommend-button, .secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 44px; padding: 0 18px; border: 0; border-radius: 999px; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.recommend-button { width: 100%; background: var(--ck-heat-deep); color: #fff; }
.recommend-button:disabled { cursor: wait; opacity: .6; }
.secondary-button { margin: 8px 0; border: 1px solid var(--ck-glass-border); background: var(--ck-fill-strong); color: var(--ck-text); }
.spinning { animation: spin 1s linear infinite; }
.recommendation-options { margin: 12px 0 0; border-top: 1px solid var(--ck-hairline); line-height: 1.7; }
.recommendation-options summary { display: flex; align-items: center; min-height: 44px; color: var(--ck-text-2); font-size: 13.5px; cursor: pointer; }
.recommendation-options fieldset { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; padding: 4px 0 10px; border: 0; }
.recommendation-options label { display: grid; gap: 5px; color: var(--ck-text-2); font-size: 12.5px; }
.recommendation-options input, .recommendation-options select { min-width: 0; min-height: 42px; padding: 0 12px; border: 1px solid rgba(255,255,255,.1); border-radius: 12px; background: rgba(255,255,255,.07); color: var(--ck-text); font: inherit; }
.recommendation-options select option { background: #1E2220; }
.recommendation-options p, .source-details { color: var(--ck-text-3); font-size: 12px; overflow-wrap: anywhere; }
.smart-recommendations > p[role=status] { margin-top: 10px; color: var(--ck-warn); font-size: 13px; }
.state-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 14px; padding: 14px; border-radius: 16px; background: var(--ck-fill); }
.state-panel > div { flex: 1; min-width: 0; }
.state-panel h3 { margin: 0 0 4px; font-size: 15px; font-weight: 600; }
.state-panel p { color: var(--ck-text-3); font-size: 12.5px; line-height: 1.6; }
.state-panel .secondary-button { width: 100%; margin: 0; }
.state-icon { display: grid; flex: 0 0 42px; width: 42px; height: 42px; border-radius: 14px; background: var(--ck-heat-soft); color: var(--ck-heat); font-size: 20px; place-items: center; }
.attention-state .state-icon { background: var(--ck-warn-soft); color: var(--ck-warn); }
.error-state .state-icon { background: var(--ck-danger-soft); color: var(--ck-danger); }
.loading-ring { flex: 0 0 34px; width: 34px; height: 34px; border: 3px solid rgba(255,255,255,.12); border-top-color: var(--ck-heat); border-radius: 50%; animation: spin .8s linear infinite; }
.result-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 14px 0 10px; color: var(--ck-text-2); font-size: 12px; }
.result-summary span { padding: 5px 10px; border-radius: 999px; background: var(--ck-fill); }
.result-summary small { width: 100%; color: var(--ck-text-3); }
.smart-recommendations > p { color: var(--ck-text-2); font-size: 12.5px; }
.recommendation-list { display: grid; gap: 10px; }
.recommendation-card { min-width: 0; padding: 14px; border: 1px solid var(--ck-glass-border); border-radius: 18px; background: rgba(255,255,255,.04); }
.recommendation-card.winner { border-color: rgba(255,138,61,.45); background: linear-gradient(160deg, rgba(255,138,61,.14), rgba(255,255,255,.03) 60%); }
.card-topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rank-badge { padding: 4px 10px; border-radius: 999px; background: var(--ck-heat-soft); color: #FFB27F; font-size: 11px; font-weight: 700; letter-spacing: .5px; }
.score { display: flex; align-items: baseline; gap: 6px; color: var(--ck-text-3); }
.score strong { color: var(--ck-text); font-family: var(--ck-font-display); font-size: 26px; font-weight: 300; }
.score small { font-size: 11px; }
.recommendation-card h3 { margin: 6px 0 10px; font-size: 18px; font-weight: 600; }
.muted { color: var(--ck-text-3); font-size: 12px; }
.ingredient-groups { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; }
.ingredient-group { min-width: 0; padding: 10px 12px; border-radius: 12px; background: var(--ck-fill); }
.ingredient-group span { display: block; margin-bottom: 2px; color: var(--ck-text-3); font-size: 11.5px; font-weight: 600; }
.ingredient-group p { overflow-wrap: anywhere; color: var(--ck-text); font-size: 13px; line-height: 1.5; }
.ingredient-group.matched p, .ingredient-group .positive-copy { color: var(--ck-fresh); }
.ingredient-group.missing p:not(.positive-copy) { color: var(--ck-warn); }
.ingredient-group.expiring.has-expiring p { color: #FFB27F; }
.ingredient-group .muted { color: var(--ck-text-3); }
.unsafe-warning { display: flex; align-items: flex-start; gap: 7px; margin-top: 8px; padding: 10px 12px; border-radius: 12px; background: var(--ck-danger-soft); color: #FFB5AB; font-size: 12.5px; line-height: 1.5; }
.reason-block { margin-top: 10px; }
.reason-block > strong { color: var(--ck-text-2); font-size: 12.5px; }
.reason-block ul { display: grid; gap: 3px; margin: 4px 0 0; padding-left: 18px; color: var(--ck-text-2); font-size: 12.5px; line-height: 1.55; }
.score-details { margin-top: 10px; border-top: 1px solid var(--ck-hairline); }
.score-details summary { display: flex; align-items: center; min-height: 44px; color: var(--ck-text-2); font-size: 13px; font-weight: 600; cursor: pointer; }
.metric-list { display: grid; gap: 8px; margin-top: 4px; }
.metric-row { min-width: 0; }
.metric-row > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 4px; color: var(--ck-text-2); font-size: 12px; }
.metric-track { width: 100%; height: 6px; overflow: hidden; border-radius: 99px; background: rgba(255,255,255,.1); }
.metric-track i { display: block; height: 100%; border-radius: inherit; background: var(--ck-fresh); }
.metric-row.penalty .metric-track i { background: var(--ck-heat); }
.weight-copy { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 10px; }
.weight-copy span { padding: 3px 8px; border-radius: 8px; background: var(--ck-fill); color: var(--ck-text-3); font-size: 11px; }
.unavailable-copy, .algorithm-note { color: var(--ck-text-3); font-size: 11.5px; line-height: 1.55; }
.unavailable-copy { margin: 8px 0 0; }
.algorithm-note { margin: 12px 0 0; text-align: center; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
