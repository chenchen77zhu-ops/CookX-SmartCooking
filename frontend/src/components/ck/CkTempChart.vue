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
            <stop offset="0" stop-color="#FFC27A" />
            <stop offset="1" stop-color="#FF7A2E" />
          </linearGradient>
        </defs>
        <polyline v-if="linePoints" :points="linePoints" fill="none" :stroke="`url(#${gradientId})`" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <polyline v-if="predictionPoints" :points="predictionPoints" fill="none" stroke="rgba(246,243,238,0.55)" stroke-width="1.6" stroke-dasharray="4 4" vector-effect="non-scaling-stroke" />
      </svg>
      <span v-if="endPoint" class="ck-chart__dot" :style="{ left: `${endPoint.x}%`, top: `${endPoint.y}%` }"></span>
      <span v-if="endPoint && showValue" class="ck-chart__value" :style="{ left: `${Math.min(endPoint.x, 86)}%`, top: `${endPoint.y}%` }">{{ lastValue }}°C</span>
      <p v-if="!points.length" class="ck-chart__empty">{{ emptyText }}</p>
    </div>
    <div class="ck-chart__x">
      <span v-for="(label, i) in xLabels" :key="i">{{ label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

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
const span = computed(() => props.max - props.min)
const yTicks = computed(() => [props.max, Math.round((props.max + props.min) / 2), props.min])
const yPct = value => 100 - ((Math.min(props.max, Math.max(props.min, value)) - props.min) / span.value) * 100

const range = computed(() => {
  const all = [...props.points, ...props.prediction]
  if (!all.length) return null
  const start = all[0].at, end = all[all.length - 1].at
  return { start, end: Math.max(end, start + 60000) }
})
const xOf = at => ((at - range.value.start) / (range.value.end - range.value.start)) * 100
const toPoints = list => list.map(p => `${xOf(p.at).toFixed(2)},${yPct(p.t).toFixed(2)}`).join(' ')
const linePoints = computed(() => (props.points.length > 1 ? toPoints(props.points) : ''))
const predictionPoints = computed(() => {
  if (!props.prediction.length || !props.points.length) return ''
  return toPoints([props.points[props.points.length - 1], ...props.prediction])
})
const endPoint = computed(() => {
  const last = props.points[props.points.length - 1]
  return last ? { x: xOf(last.at), y: yPct(last.t) } : null
})
const lastValue = computed(() => {
  const last = props.points[props.points.length - 1]
  return last ? Math.round(last.t) : ''
})
const bandStyle = computed(() => {
  const [low, high] = props.band
  const top = yPct(high), bottom = yPct(low)
  return { top: `${top}%`, height: `${bottom - top}%` }
})
const clock = at => { const d = new Date(at); return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}` }
const xLabels = computed(() => {
  if (!range.value) return ['-6 分钟', '-4', '-2', '现在']
  const { start, end } = range.value
  return [0, 1, 2, 3].map(i => (i === 3 && !props.prediction.length ? '现在' : clock(start + ((end - start) * i) / 3)))
})
</script>

<style scoped>
.ck-chart { position: relative; display: grid; grid-template-columns: 30px minmax(0, 1fr); grid-template-rows: minmax(0, 1fr) 18px; column-gap: 8px; }
.ck-chart__axis { position: relative; }
.ck-chart__axis span { position: absolute; right: 0; color: var(--ck-text-3); font-size: 10.5px; font-variant-numeric: tabular-nums; transform: translateY(-50%); }
.ck-chart__plot { position: relative; min-width: 0; }
.ck-chart__grid { position: absolute; left: 0; right: 0; height: 1px; background: rgba(255, 255, 255, 0.07); }
.ck-chart__band { position: absolute; left: 0; right: 0; background: rgba(255, 138, 61, 0.1); border-block: 1px dashed rgba(255, 138, 61, 0.35); }
.ck-chart__svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.ck-chart__dot { position: absolute; width: 12px; height: 12px; border: 2.5px solid #FF8A3D; border-radius: 50%; background: #fff; box-shadow: 0 0 0 5px rgba(255, 138, 61, 0.22); transform: translate(-50%, -50%); }
.ck-chart__value { position: absolute; color: #FFB27F; font-size: 13px; font-weight: 700; transform: translate(-50%, -165%); white-space: nowrap; }
.ck-chart__empty { position: absolute; inset: 0; display: grid; place-items: center; margin: 0; color: var(--ck-text-3); font-size: 12px; }
.ck-chart__x { grid-column: 2; display: flex; justify-content: space-between; color: var(--ck-text-3); font-size: 10.5px; font-variant-numeric: tabular-nums; align-items: flex-end; }
</style>
