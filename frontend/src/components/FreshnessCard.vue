<template>
  <section class="freshness-card" @click.stop aria-label="鲜度评估">
    <p v-if="status === 'loading'" role="status">鲜度评估中…</p>
    <p v-else-if="status === 'error'">鲜度评估失败，库存仍保留</p>
    <p v-else-if="!detail">鲜度未知，尚无可用评估</p>
    <template v-else>
      <strong>{{ summary.description }}</strong>
      <p>FreshScore：{{ hasScore(detail.fresh_score) ? detail.fresh_score : '数据不足' }} <span>{{ detail.freshness_label }}</span></p>
      <p>置信度：{{ hasScore(detail.confidence_score) ? `${Math.round(detail.confidence_score * 100)}%` : '数据不足' }} <span>{{ detail.confidence_level }}</span></p>
      <p>风险：{{ detail.risk_flags?.length ? detail.risk_flags.join('；') : '未返回风险标记（不代表安全）' }}</p>
      <ul v-if="detail.reasons?.length"><li v-for="reason in detail.reasons" :key="reason">{{ reason }}</li></ul>
      <small>评估时间：{{ formatTime(detail.evaluated_at || evaluatedAt) }}</small>
      <details class="freshness-details">
        <summary>查看鲜度依据</summary>
        <dl><template v-for="part in parts" :key="part.key">
          <dt>{{ part.key }} {{ part.label }}</dt>
          <dd>{{ hasScore(detail.component_scores?.[part.key]) ? `${detail.component_scores[part.key]}（0–1）` : '数据不足' }}；有效权重：{{ hasScore(detail.effective_weights?.[part.key]) && detail.effective_weights[part.key] > 0 ? `${Math.round(detail.effective_weights[part.key] * 100)}%` : '未参与' }}</dd>
        </template></dl>
        <p>权重和分项直接来自服务端；未提供的视觉与历史数据不作推测。</p>
        <p v-for="note in [...(detail.confidence_reasons || []), ...(detail.data_quality_notes || [])]" :key="note">{{ note }}</p>
        <p>起始时间：{{ formatTime(detail.time_details?.start_time) }}</p>
        <p>到期时间：{{ formatTime(detail.time_details?.expiry_time) }}</p>
        <p>时间依据：{{ detail.time_details?.status || '数据不足' }}</p>
        <p>储存依据：{{ detail.storage_details?.reason || detail.storage_details?.status || '数据不足' }}</p>
        <small>算法版本：{{ detail.algorithm_version || '未提供' }}</small>
      </details>
      <p class="disclaimer">{{ detail.disclaimer }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { hasScore, freshnessStatus } from '../services/inventoryFreshness.js'
const props = defineProps({ detail: Object, status: String, evaluatedAt: String })
const parts = [{key:'T',label:'时间'},{key:'S',label:'储存'},{key:'V',label:'视觉'},{key:'H',label:'历史'}]
const summary = computed(() => freshnessStatus(props.detail))
const formatTime = value => value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString('zh-CN') : '未知'
</script>
<style scoped>
.freshness-card { margin: 12px 0; padding: 12px; border-radius: 12px; background: #f1f7f3; color: #315340; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
summary { cursor: pointer; font-weight: 600; padding: 8px 0; } dt { font-weight: 600; } dd { margin: 0 0 8px; }
p { margin: 5px 0; } ul { padding-left: 18px; } small,.disclaimer { color: #59695d; font-size: 12px; }
</style>
