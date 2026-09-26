<template>
  <div class="ck-chart" :style="{ height: `${height}px` }">
    <div class="ck-chart__axis">
      <span v-for="tick in yTicks" :key="tick" :style="{ top: `${yPct(tick)}%` }">{{ tick }}</span>
    </div>
    <div class="ck-chart__plot">
      <span v-for="tick in yTicks" :key="'g' + tick" class="ck-chart__grid" :style="{ top: `${yPct(tick)}%` }"></span>
      <div v-if="band" class="ck-chart__band" :style="bandStyle"></div>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" class="ck-chart__svg">
        <defs>
          <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="var(--ck-chart-start)" />
            <stop offset="1" stop-color="var(--ck-chart-end)" />
          </linearGradient>
        </defs>
        <path v-if="linePoints" :d="linePoints" fill="none" :stroke="`url(#${gradientId})`" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <path v-if="predictionPoints" :d="predictionPoints" fill="none" stroke="var(--ck-chart-prediction)" stroke-width="1.6" stroke-dasharray="4 4" vector-effect="non-scaling-stroke" />
        <path v-if="geometry.measured.length < 2" d="M 3 73 Q 23 58 43 66 T 93 51" fill="none" stroke="var(--ck-text-3)" opacity=".2" stroke-width="1.5" vector-effect="non-scaling-stroke" />
      </svg>
      <span v-if="endPoint" class="ck-chart__dot" :style="{ left: `${endPoint.x}%`, top: `${endPoint.y}%` }"></span>
      <span v-if="endPoint && showValue" class="ck-chart__value" :style="{ left: `${Math.min(Math.max(endPoint.x, 24), 93)}%`, top: `${endPoint.y}%` }">{{ lastValue }}°C</span>
      <p v-if="geometry.measured.length < 2" class="ck-chart__empty">{{ geometry.measured.length ? '正在积累温度记录' : emptyText }}</p>
    </div>
    <div class="ck-chart__x">
      <span v-for="(label, i) in xLabels" :key="i">{{ label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { chartGeometry, smoothChartPath } from '@/services/chartGeometry.js'

const props = defineProps({
  points: { type: Array, default: () => [] }, // [{ at, t }]
  prediction: { type: Array, default: () => [] },
  min: { type: Number, default: 50 },
  max: { type: Number, default: 250 },
  band: { type: Array, default: null }, // [low, high] 目标温区
  height: { type: Number, default: 140 },
  showValue: { type: Boolean, default: true },
  emptyText: { type: String, default: '连接 CookX Sense 后显示实时曲线' }
})

const gradientId = `ckg-${Math.random().toString(36).slice(2, 8)}`
const geometry = computed(() => chartGeometry(props.points, props.prediction, props.min, props.max))
const yTicks = computed(() => [geometry.value.high, Math.round((geometry.value.high + geometry.value.low) / 2), geometry.value.low])
const yPct = value => geometry.value.y(Math.min(geometry.value.high, Math.max(geometry.value.low, value)))
const linePoints = computed(() => smoothChartPath(geometry.value.coordinates(geometry.value.measured)))
const predictionPoints = computed(() => {
  const g = geometry.value
  return g.forecast.length ? smoothChartPath(g.coordinates([g.measured.at(-1), ...g.forecast])) : ''
})
const endPoint = computed(() => geometry.value.endpoint)
const lastValue = computed(() => endPoint.value?.value ?? '')
const bandStyle = computed(() => {
  const [low, high] = props.band
  const top = yPct(high), bottom = yPct(low)
  return { top: `${top}%`, height: `${bottom - top}%` }
})
const clock = at => { const d = new Date(at); const text = `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`; return geometry.value.end - geometry.value.start < 180000 ? `${text}:${String(d.getSeconds()).padStart(2, '0')}` : text }
const xLabels = computed(() => {
  const g = geometry.value
  if (!g.measured.length) return ['—', '—', '—', '等待测量']
  return [0, 1, 2, 3].map(i => clock(g.start + (g.end - g.start) * i / 3))
})
</script>

<style scoped>
.ck-chart { position: relative; display: grid; grid-template-columns: 30px minmax(0, 1fr); grid-template-rows: minmax(0, 1fr) 18px; column-gap: 8px; }
.ck-chart__axis { position: relative; }
.ck-chart__axis span { position: absolute; right: 0; color: var(--ck-text-3); font-size: 10.5px; font-variant-numeric: tabular-nums; transform: translateY(-50%); }
.ck-chart__plot { position: relative; min-width: 0; }
.ck-chart__grid { position: absolute; left: 0; right: 0; height: 1px; background: var(--ck-hairline); }
.ck-chart__band { position: absolute; left: 0; right: 0; background: var(--ck-photo-tone-151); border-block: 1px dashed var(--ck-photo-tone-152); }
.ck-chart__svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.ck-chart__dot { position: absolute; width: 12px; height: 12px; border: 2.5px solid var(--ck-chart-end); border-radius: 50%; background: var(--ck-on-accent); box-shadow: 0 0 0 5px var(--ck-photo-tone-153); transform: translate(-50%, -50%); }
.ck-chart__value { position: absolute; color: var(--ck-photo-tone-154); font-size: 13px; font-weight: 700; transform: translate(-85%, -115%); white-space: nowrap; }
.ck-chart__empty { position: absolute; inset: 8px 0 0; display: grid; place-items: center; margin: 0; color: var(--ck-text-3); font-size: 12px; }
.ck-chart__x { grid-column: 2; display: flex; justify-content: space-between; color: var(--ck-text-3); font-size: 10.5px; font-variant-numeric: tabular-nums; align-items: flex-end; }
</style>
