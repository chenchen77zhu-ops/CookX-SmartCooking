<template>
  <div class="ck-gauge" :style="{ width: `${size}px`, height: `${size * 0.9}px` }">
    <svg :viewBox="`0 0 ${size} ${size}`" class="ck-gauge__svg" aria-hidden="true">
      <defs>
        <linearGradient :id="gid" x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" stop-color="#FFC27A" />
          <stop offset="0.6" stop-color="#FF8A3D" />
          <stop offset="1" stop-color="#F0561C" />
        </linearGradient>
      </defs>
      <circle :cx="c" :cy="c" :r="r" fill="none" stroke="rgba(255,255,255,0.12)" :stroke-width="stroke" stroke-linecap="round" :stroke-dasharray="`${arc} ${circ}`" :transform="`rotate(135 ${c} ${c})`" />
      <circle v-if="progress > 0" :cx="c" :cy="c" :r="r" fill="none" :stroke="`url(#${gid})`" :stroke-width="stroke" stroke-linecap="round" :stroke-dasharray="`${arc * progress} ${circ}`" :transform="`rotate(135 ${c} ${c})`" class="ck-gauge__value-arc" />
      <circle v-if="progress > 0" :cx="knob.x" :cy="knob.y" :r="stroke * 0.62" fill="#fff" stroke="#FF8A3D" :stroke-width="stroke * 0.35" />
    </svg>
    <div class="ck-gauge__center">
      <span class="ck-gauge__label">{{ label }}</span>
      <div class="ck-gauge__num ck-num"><span :class="{ 'is-empty': value === null }">{{ display }}</span><sup v-if="value !== null">°C</sup></div>
      <span v-if="sub" class="ck-gauge__sub">{{ sub }}</span>
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: null },
  min: { type: Number, default: 0 },
  max: { type: Number, default: 250 },
  size: { type: Number, default: 260 },
  label: { type: String, default: '当前温度' },
  sub: { type: String, default: '' }
})

const gid = `ckgauge-${Math.random().toString(36).slice(2, 8)}`
const stroke = computed(() => Math.round(props.size * 0.045))
const c = computed(() => props.size / 2)
const r = computed(() => props.size / 2 - stroke.value)
const circ = computed(() => 2 * Math.PI * r.value)
const arc = computed(() => circ.value * 0.75)
const progress = computed(() => props.value === null ? 0 : Math.min(1, Math.max(0.02, (props.value - props.min) / (props.max - props.min))))
const knob = computed(() => {
  const angle = (135 + 270 * progress.value) * Math.PI / 180
  return { x: c.value + r.value * Math.cos(angle), y: c.value + r.value * Math.sin(angle) }
})
const display = computed(() => props.value === null ? '--' : Math.round(props.value))
</script>

<style scoped>
.ck-gauge { position: relative; margin: 0 auto; }
.ck-gauge__svg { position: absolute; inset: 0 0 auto; width: 100%; height: auto; overflow: visible; }
.ck-gauge__value-arc { filter: drop-shadow(0 0 10px rgba(255, 138, 61, 0.45)); transition: stroke-dasharray 0.6s ease; }
.ck-gauge__center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; padding-top: 6%; text-align: center; }
.ck-gauge__label { color: var(--ck-text-2); font-size: 14px; }
.ck-gauge__num { display: flex; align-items: flex-start; color: #fff; font-size: calc(var(--gauge-size, 1) * 64px); font-weight: 300; line-height: 1.05; }
.ck-gauge__num .is-empty { color: rgba(255, 255, 255, 0.5); font-size: 0.8em; letter-spacing: 0.08em; }
.ck-gauge__num sup { margin: 0.2em 0 0 3px; font-size: 0.36em; font-weight: 300; }
.ck-gauge__sub { margin-top: 2px; color: var(--ck-text-2); font-size: 13px; }
</style>
