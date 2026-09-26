<template>
  <div :class="['stage-backdrop', `is-${state}`, { 'is-cooking': cooking }]" aria-hidden="true">
    <img v-for="item in layers" :key="item.id" :src="item.src" alt="" :class="['stage-backdrop__photo', { active: item.id === state }]" />
    <CkSteam :intensity="steam" :smoke="state === 'overheat'" :origin-y="cooking ? 0.3 : 0.44" />
    <div class="stage-backdrop__shade"></div>
    <div class="stage-backdrop__alert"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import CkSteam from './CkSteam.vue'
import idle from '@/assets/backdrops/stage-idle.webp'
import preheat from '@/assets/backdrops/stage-preheat.webp'
import heating from '@/assets/backdrops/stage-heating.webp'
import sear from '@/assets/backdrops/stage-sear.webp'
import overheat from '@/assets/backdrops/stage-overheat.webp'

// 按锅温在 5 张烹饪状态背景之间柔和切换（1.6 秒交叉淡化）
const props = defineProps({
  state: { type: String, default: 'idle' },
  cooking: { type: Boolean, default: false }
})
const layers = [
  { id: 'idle', src: idle },
  { id: 'preheat', src: preheat },
  { id: 'heating', src: heating },
  { id: 'sear', src: sear },
  { id: 'overheat', src: overheat }
]
const steam = computed(() => ({ idle: 0, preheat: 0.04, heating: 0.18, sear: 0.3, overheat: 1 }[props.state] ?? 0.2))
</script>

<style scoped>
.stage-backdrop { position: fixed; inset: 0; z-index: -1; overflow: hidden; background: var(--ck-photo-tone-145); pointer-events: none; }
.stage-backdrop__photo {
  position: absolute; left: 50%; top: 0; width: 100%; height: 100%;
  object-fit: cover; object-position: 50% 50%; transform: translateX(-50%);
  opacity: 0; transition: opacity 1.6s ease;
}
.stage-backdrop__photo.active { opacity: 1; transform: translateX(-50%) scale(1); }
.is-cooking .stage-backdrop__photo { filter: blur(18px) brightness(0.7); transform: translateX(-50%) scale(1.12); }
.stage-backdrop__shade {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, var(--ck-photo-tone-146) 0%, var(--ck-photo-tone-147) 30%, transparent 42%, transparent 58%, var(--ck-photo-tone-146) 80%, var(--ck-photo-tone-145) 100%);
}
.is-cooking .stage-backdrop__shade { background: linear-gradient(180deg, var(--ck-photo-tone-148) 0%, var(--ck-photo-tone-146) 50%, var(--ck-photo-tone-149) 100%); }
.stage-backdrop__alert { position: absolute; inset: 0; opacity: 0; transition: opacity 0.6s ease; box-shadow: inset 0 0 120px 30px var(--ck-photo-tone-150); }
.is-overheat .stage-backdrop__alert { opacity: 1; animation: alert-pulse 1.6s ease-in-out infinite; }
@keyframes alert-pulse { 0%, 100% { opacity: 0.55; } 50% { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .is-overheat .stage-backdrop__alert { animation: none; } }
</style>
