<template>
  <article :class="['sense', `is-${variant}`, { 'is-alert': alert?.level === 'danger', 'is-warn': alert?.level === 'warn' }]">
    <img :src="panImage" alt="" class="sense__photo" />
    <div class="sense__shade"></div>

    <template v-if="variant === 'full'">
      <div class="sense__body">
        <div class="sense__brand">Cook<b>X</b> Sense</div>
        <div :class="['sense__status', { 'is-off': !live.connected && !live.simulated }]"><span :class="['ck-dot', dotClass]"></span>{{ statusText }}</div>
        <span class="sense__label">当前锅内温度</span>
        <div class="sense__temp ck-num"><span :class="{ 'is-empty': temperature === null }">{{ tempText }}</span><sup v-if="temperature !== null">°C</sup></div>
        <i class="sense__bar"><b :style="{ width: `${barPercent}%` }"></b></i>
        <div class="sense__cooking">
          <CkIcon name="flame" :size="20" />
          <span><small>{{ session ? '正在烹饪中' : '待机中' }}</small><b>{{ session ? session.dish : '选择一道菜开始' }}</b></span>
        </div>
      </div>
      <div class="sense__advice">
        <span><small>{{ alert ? alert.title : '下一步建议' }}</small><b>{{ adviceTitle }}</b><em v-if="adviceText">{{ adviceText }}</em></span>
        <span class="sense__go"><CkIcon name="chevron-right" :size="20" :stroke="2.4" /></span>
      </div>
    </template>

    <template v-else>
      <div class="mini">
        <div class="mini__top"><span class="sense__brand">Cook<b>X</b></span><span :class="['ck-dot', dotClass]"></span></div>
        <div class="mini__temp ck-num"><span :class="{ 'is-empty': temperature === null }">{{ tempText }}</span><sup v-if="temperature !== null">°C</sup></div>
        <i class="sense__bar"><b :style="{ width: `${barPercent}%` }"></b></i>
        <p class="mini__line">{{ alert ? alert.title : (session ? `${session.dish} · ${session.stepIndex + 1}/${session.total}` : statusText) }}</p>
      </div>
    </template>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import CkIcon from './CkIcon.vue'
import panImage from '@/assets/backdrops/kitchen.webp'
import { live, liveAdvice } from '@/services/liveKitchen.js'


defineProps({ variant: { type: String, default: 'full' } })

const temperature = computed(() => live.temperature)
const session = computed(() => live.session)
const alert = computed(() => live.alert)
const tempText = computed(() => (temperature.value === null ? '--' : Math.round(temperature.value)))
const barPercent = computed(() => (temperature.value === null ? 0 : Math.max(4, Math.min(100, (temperature.value / 250) * 100))))
const dotClass = computed(() => (alert.value?.level === 'danger' ? 'is-alert' : live.connected || live.simulated ? 'is-on' : ''))
const statusText = computed(() => {
  if (live.simulated) return '仿真回放'
  if (live.connected) return '已连接'
  return live.supported ? '未连接' : '需 Android App'
})
const advice = computed(() => liveAdvice())
const adviceTitle = computed(() => {
  if (alert.value) return alert.value.text
  if (session.value) return session.value.nextTitle ? `接下来：${session.value.nextTitle}` : '最后一步，完成后记得核对库存'
  return advice.value?.text || '进入厨房连接测温设备'
})
const adviceText = computed(() => {
  if (alert.value) return ''
  if (session.value) return advice.value?.text || session.value.stepTitle
  return live.connected ? '' : '连接后实时查看锅温与火候建议'
})
</script>

<style scoped>
.sense { position: relative; overflow: hidden; border-radius: 26px; background: #0E0F0E; color: #F6F3EE; isolation: isolate; }
.sense__photo { position: absolute; top: 0; right: -18%; width: 88%; height: 76%; object-fit: cover; object-position: 30% 50%; z-index: -2; }
.sense__shade { position: absolute; inset: 0; z-index: -1; background: linear-gradient(90deg, #0E0F0E 30%, rgba(14, 15, 14, 0.2) 70%, rgba(14, 15, 14, 0) 100%), linear-gradient(180deg, rgba(14, 15, 14, 0) 45%, rgba(40, 22, 10, 0.92) 100%); }
.is-alert .sense__shade { background: linear-gradient(90deg, #1A0B09 30%, rgba(26, 11, 9, 0.2) 70%, rgba(26, 11, 9, 0) 100%), linear-gradient(180deg, rgba(26, 11, 9, 0) 40%, rgba(120, 18, 12, 0.92) 100%); }
.sense__body { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 20px 20px 0; }
.sense__brand { font-family: var(--ck-font-display); font-size: 20px; font-weight: 600; letter-spacing: -0.2px; }
.sense__brand b { color: #FF8A3D; font-weight: inherit; }
.sense__status { display: flex; align-items: center; gap: 7px; color: #7EE6A4; font-size: 13px; }
.sense__status.is-off { color: rgba(246, 243, 238, 0.6); }
.sense__label { margin-top: 18px; color: rgba(246, 243, 238, 0.72); font-size: 13px; }
.sense__temp { display: flex; align-items: flex-start; font-size: 46px; font-weight: 600; line-height: 1.05; }
.sense__temp .is-empty, .mini__temp .is-empty { color: rgba(246, 243, 238, 0.6); font-family: var(--ck-font); font-weight: 200; letter-spacing: 0.12em; }
.sense__temp sup { margin: 4px 0 0 3px; font-size: 20px; font-weight: 500; }
.is-alert .sense__temp { color: #FF6152; }
.sense__bar { position: relative; display: block; width: 136px; height: 6px; margin: 10px 0 16px; border-radius: 3px; background: rgba(255, 255, 255, 0.16); overflow: hidden; }
.sense__bar b { position: absolute; inset: 0 auto 0 0; border-radius: 3px; background: var(--ck-heat-gradient); transition: width 0.6s ease; }
.is-alert .sense__bar b { background: linear-gradient(90deg, #FF8A3D, #FF3B30); }
.sense__cooking { display: flex; align-items: center; gap: 10px; padding: 10px 16px 10px 12px; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; background: rgba(255, 255, 255, 0.08); -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px); }
.sense__cooking .ck-icon { color: #FF8A3D; }
.sense__cooking span { display: flex; flex-direction: column; line-height: 1.35; }
.sense__cooking small { color: rgba(246, 243, 238, 0.7); font-size: 12px; }
.sense__cooking b { max-width: 150px; overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.sense__advice { display: flex; align-items: center; gap: 12px; margin: 16px 12px 12px; padding: 14px 14px 14px 18px; border: 1px solid rgba(255, 170, 110, 0.28); border-radius: 18px; background: linear-gradient(120deg, rgba(150, 72, 24, 0.62), rgba(110, 52, 20, 0.5)); -webkit-backdrop-filter: blur(18px); backdrop-filter: blur(18px); }
.is-alert .sense__advice { border-color: rgba(255, 110, 90, 0.4); background: linear-gradient(120deg, rgba(170, 30, 20, 0.7), rgba(120, 20, 14, 0.6)); }
.sense__advice > span:first-child { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1 1 auto; }
.sense__advice small { color: rgba(255, 230, 210, 0.75); font-size: 12px; }
.sense__advice b { font-size: 18px; font-weight: 700; line-height: 1.35; }
.sense__advice em { color: rgba(255, 230, 210, 0.72); font-size: 12.5px; font-style: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sense__go { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 50%; background: #FF8A1F; color: #fff; box-shadow: 0 8px 20px rgba(255, 120, 30, 0.4); }
.is-alert .sense__go { background: #FF3B30; }

/* 悬浮小窗 */
.is-mini { width: 148px; border-radius: 20px; box-shadow: 0 16px 36px rgba(0, 0, 0, 0.35); }
.is-mini .sense__photo { right: -30%; width: 110%; height: 100%; opacity: 0.55; }
.is-mini .sense__shade { background: linear-gradient(90deg, rgba(14, 15, 14, 0.92) 30%, rgba(14, 15, 14, 0.45) 100%); }
.is-mini.is-alert .sense__shade { background: linear-gradient(90deg, rgba(90, 14, 10, 0.94) 30%, rgba(90, 14, 10, 0.6) 100%); }
.mini { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px 12px; }
.mini__top { display: flex; align-items: center; justify-content: space-between; }
.mini .sense__brand { font-size: 14px; }
.mini__temp { display: flex; align-items: flex-start; font-size: 30px; font-weight: 600; line-height: 1.1; }
.mini__temp sup { margin: 3px 0 0 2px; font-size: 13px; }
.is-alert .mini__temp { color: #FF6152; }
.mini .sense__bar { width: 100%; height: 4px; margin: 6px 0 6px; }
.mini__line { overflow: hidden; color: rgba(246, 243, 238, 0.78); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
</style>
