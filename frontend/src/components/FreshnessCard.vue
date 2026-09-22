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
      <p class="disclaimer">{{ detail.disclaimer }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { hasScore, freshnessStatus } from '../services/inventoryFreshness.js'
const props = defineProps({ detail: Object, status: String, evaluatedAt: String })
const summary = computed(() => freshnessStatus(props.detail))
const formatTime = value => value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString('zh-CN') : '未知'
</script>
<style scoped>
.freshness-card { margin: 12px 0; padding: 12px; border-radius: 12px; background: #f1f7f3; color: #315340; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
p { margin: 5px 0; } ul { padding-left: 18px; } small,.disclaimer { color: #59695d; font-size: 12px; }
</style>
