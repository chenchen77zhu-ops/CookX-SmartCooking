<template>
  <div :class="['ck-gauge', { 'is-alert': alert }]" :style="{ width: `${size}px`, height: `${size * 0.9}px` }">
    <svg :viewBox="`0 0 ${size} ${size}`" class="ck-gauge__svg" aria-hidden="true">
      <defs>
        <linearGradient :id="gid" x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" :stop-color="alert ? 'var(--ck-photo-tone-140)' : 'var(--ck-photo-tone-141)'" />
          <stop offset="0.55" :stop-color="alert ? 'var(--ck-photo-tone-118)' : 'var(--ck-photo-tone-142)'" />
          <stop offset="1" :stop-color="alert ? 'var(--ck-photo-tone-143)' : 'var(--ck-photo-tone-144)'" />
        </linearGradient>
      </defs>
      <circle :cx="c" :cy="c" :r="r" fill="none" stroke="var(--ck-photo-tone-117)" :stroke-width="stroke" stroke-linecap="round" :stroke-dasharray="`${arc} ${circ}`" :transform="`rotate(135 ${c} ${c})`" />
      <circle v-if="progress > 0" :cx="c" :cy="c" :r="r" fill="none" :stroke="`url(#${gid})`" :stroke-width="stroke" stroke-linecap="round" :stroke-dasharray="`${arc * progress} ${circ}`" :transform="`rotate(135 ${c} ${c})`" class="ck-gauge__value-arc" />
      <circle v-if="progress > 0" :cx="startKnob.x" :cy="startKnob.y" :r="stroke * 0.42" :fill="alert ? 'var(--ck-photo-tone-140)' : 'var(--ck-photo-tone-141)'" />
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
  sub: { type: String, default: '' },
  alert: { type: Boolean, default: false }
})

const gid = `ckgauge-${Math.random().toString(36).slice(2, 8)}`
const stroke = computed(() => Math.round(props.size * 0.055))
const c = computed(() => props.size / 2)
const r = computed(() => props.size / 2 - stroke.value)
const circ = computed(() => 2 * Math.PI * r.value)
const arc = computed(() => circ.value * 0.75)
const progress = computed(() => props.value === null ? 0 : Math.min(1, Math.max(0.02, (props.value - props.min) / (props.max - props.min))))
const startKnob = computed(() => {
  const angle = 135 * Math.PI / 180
  return { x: c.value + r.value * Math.cos(angle), y: c.value + r.value * Math.sin(angle) }
})
const display = computed(() => props.value === null ? '--' : Math.round(props.value))
</script>

<style scoped>
.ck-gauge { position: relative; margin: 0 auto; color: var(--ck-on-accent); }
.ck-gauge__svg { position: absolute; inset: 0 0 auto; width: 100%; height: auto; overflow: visible; }
.ck-gauge__value-arc { filter: drop-shadow(0 0 12px var(--ck-photo-tone-136)); transition: stroke-dasharray 0.8s ease; }
.is-alert .ck-gauge__value-arc { filter: drop-shadow(0 0 16px var(--ck-photo-tone-137)); }
.ck-gauge__center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; padding-top: 4%; text-align: center; }
.ck-gauge__label { color: var(--ck-photo-tone-25); font-size: 16px; }
.ck-gauge__num { display: flex; align-items: flex-start; font-size: 72px; font-weight: 600; line-height: 1.05; letter-spacing: -2px; text-shadow: 0 4px 24px var(--ck-photo-tone-138); }
.ck-gauge__num sup { margin: 0.14em 0 0 4px; font-size: 0.42em; font-weight: 500; letter-spacing: 0; }
.ck-gauge__num .is-empty { color: var(--ck-photo-tone-16); font-family: var(--ck-font); font-size: 0.7em; font-weight: 200; letter-spacing: 0.12em; }
.is-alert .ck-gauge__num { color: var(--ck-photo-tone-139); }
.ck-gauge__sub { margin-top: 2px; color: var(--ck-photo-tone-25); font-size: 15px; }
</style>
