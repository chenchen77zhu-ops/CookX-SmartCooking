<template>
  <article :class="['sense', 'ck-dark', `is-${variant}`, { 'is-alert': alert?.level === 'danger', 'is-warn': alert?.level === 'warn' }]">
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
.sense { position: relative; overflow: hidden; border-radius: 26px; background: var(--ck-photo-tone-105); color: var(--ck-photo-tone-106); isolation: isolate; }
.sense__photo { position: absolute; top: 0; right: 0; width: 100%; height: 100%; object-fit: cover; object-position: 65% 50%; z-index: -2; }
.sense__shade { position: absolute; inset: 0; z-index: -1; background: linear-gradient(90deg, var(--ck-photo-tone-105) 30%, var(--ck-photo-tone-107) 70%, var(--ck-photo-tone-108) 100%), linear-gradient(180deg, var(--ck-photo-tone-108) 45%, var(--ck-photo-tone-109) 100%); }
.is-alert .sense__shade { background: linear-gradient(90deg, var(--ck-photo-tone-110) 30%, var(--ck-photo-tone-111) 70%, var(--ck-photo-tone-112) 100%), linear-gradient(180deg, var(--ck-photo-tone-112) 40%, var(--ck-photo-tone-113) 100%); }
.sense__body { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 16px 18px 0; }
.sense__brand { font-family: var(--ck-font-display); font-size: 20px; font-weight: 600; letter-spacing: -0.2px; }
.sense__brand b { color: var(--ck-chart-end); font-weight: inherit; }
.sense__status { display: flex; align-items: center; gap: 7px; color: var(--ck-photo-tone-05); font-size: 13px; }
.sense__status.is-off { color: var(--ck-photo-tone-114); }
.sense__label { margin-top: 12px; color: var(--ck-photo-tone-115); font-size: 13px; }
.sense__temp { display: flex; align-items: flex-start; font-size: 46px; font-weight: 600; line-height: 1.05; }
.sense__temp .is-empty, .mini__temp .is-empty { color: var(--ck-photo-tone-114); font-family: var(--ck-font); font-weight: 200; letter-spacing: 0.12em; }
.sense__temp sup { margin: 4px 0 0 3px; font-size: 20px; font-weight: 500; }
.is-alert .sense__temp { color: var(--ck-photo-tone-116); }
.sense__bar { position: relative; display: block; width: 136px; height: 6px; margin: 8px 0 10px; border-radius: 3px; background: var(--ck-photo-tone-117); overflow: hidden; }
.sense__bar b { position: absolute; inset: 0 auto 0 0; border-radius: 3px; background: var(--ck-heat-gradient); transition: width 0.6s ease; }
.is-alert .sense__bar b { background: linear-gradient(90deg, var(--ck-chart-end), var(--ck-photo-tone-118)); }
.sense__cooking { display: flex; align-items: center; gap: 10px; padding: 10px 16px 10px 12px; border: 1px solid var(--ck-photo-tone-119); border-radius: 16px; background: var(--ck-photo-tone-60); -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px); }
.sense__cooking .ck-icon { color: var(--ck-chart-end); }
.sense__cooking span { display: flex; flex-direction: column; line-height: 1.35; }
.sense__cooking small { color: var(--ck-photo-tone-120); font-size: 12px; }
.sense__cooking b { max-width: 150px; overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.sense__advice { display: flex; align-items: center; gap: 12px; margin: 12px; padding: 10px 12px 10px 14px; border: 1px solid var(--ck-photo-tone-121); border-radius: 18px; background: linear-gradient(120deg, var(--ck-photo-tone-122), var(--ck-photo-tone-123)); -webkit-backdrop-filter: blur(18px); backdrop-filter: blur(18px); }
.is-alert .sense__advice { border-color: var(--ck-photo-tone-124); background: linear-gradient(120deg, var(--ck-photo-tone-125), var(--ck-photo-tone-126)); }
.sense__advice > span:first-child { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1 1 auto; }
.sense__advice small { color: var(--ck-photo-tone-127); font-size: 12px; }
.sense__advice b { font-size: 16px; font-weight: 700; line-height: 1.35; }
.sense__advice em { color: var(--ck-photo-tone-128); font-size: 12.5px; font-style: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sense__go { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 50%; background: var(--ck-photo-tone-129); color: var(--ck-on-accent); box-shadow: 0 8px 20px var(--ck-photo-tone-130); }
.is-alert .sense__go { background: var(--ck-photo-tone-118); }

/* 悬浮小窗 */
.is-mini { width: 148px; border-radius: 20px; box-shadow: 0 16px 36px var(--ck-photo-tone-39); }
.is-mini .sense__photo { right: -30%; width: 110%; height: 100%; opacity: 0.55; }
.is-mini .sense__shade { background: linear-gradient(90deg, var(--ck-photo-tone-131) 30%, var(--ck-photo-tone-132) 100%); }
.is-mini.is-alert .sense__shade { background: linear-gradient(90deg, var(--ck-photo-tone-133) 30%, var(--ck-photo-tone-134) 100%); }
.mini { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px 12px; }
.mini__top { display: flex; align-items: center; justify-content: space-between; }
.mini .sense__brand { font-size: 14px; }
.mini__temp { display: flex; align-items: flex-start; font-size: 30px; font-weight: 600; line-height: 1.1; }
.mini__temp sup { margin: 3px 0 0 2px; font-size: 13px; }
.is-alert .mini__temp { color: var(--ck-photo-tone-116); }
.mini .sense__bar { width: 100%; height: 4px; margin: 6px 0 6px; }
.mini__line { overflow: hidden; color: var(--ck-photo-tone-135); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
@media (max-height: 800px) { .is-full .sense__body { padding-top: 12px; gap: 0; } .is-full .sense__temp { font-size: 40px; } .is-full .sense__bar { margin-block: 6px; } .is-full .sense__advice { padding-block: 8px; } .is-full .sense__cooking { padding-block: 6px; } .is-full .sense__label { margin-top: 8px; } .is-full .sense__advice { margin-top: 8px; } }
</style>
