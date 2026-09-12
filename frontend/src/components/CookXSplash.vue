<template>
  <Transition name="cookx-splash-fade">
    <div v-if="visible" class="cookx-splash" aria-label="CookX 正在启动">
      <div class="cookx-splash__halo"></div>
      <div class="cookx-splash__content">
        <span class="cookx-splash__mark"><img :src="cookxMark" alt="" /></span>
        <div class="cookx-splash__wordmark">Cook<strong>X</strong></div>
        <p class="cookx-splash__subtitle">AI 智能烹饪助手</p>
        <i></i>
        <p class="cookx-splash__slogan">感知每一度 · 智烹每一步</p>
      </div>
      <div class="cookx-splash__wave" aria-hidden="true"><span></span><span></span><span></span></div>
    </div>
  </Transition>
</template>

<script setup>
import cookxMark from '@/assets/brand/cookx-mark.svg'

defineProps({ visible: { type: Boolean, default: false } })
</script>

<style scoped>
.cookx-splash { position: fixed; z-index: 10000; inset: 0; display: grid; overflow: hidden; background: #f7f5ef; color: #173f35; place-items: center; }
.cookx-splash__halo { position: absolute; top: -22vw; right: -25vw; width: min(78vw, 560px); aspect-ratio: 1; border-radius: 50%; background: rgba(77,139,105,.08); }
.cookx-splash__content { position: relative; z-index: 2; display: flex; align-items: center; flex-direction: column; transform: translateY(-4vh); }
.cookx-splash__mark { display: grid; width: 132px; height: 132px; padding: 15px; border-radius: 34px; background: #173f35; box-shadow: 0 18px 45px rgba(23,63,53,.18); place-items: center; }
.cookx-splash__mark img { display: block; width: 100%; height: 100%; }
.cookx-splash__wordmark { margin-top: 20px; font-size: 42px; font-weight: 800; letter-spacing: -1.5px; line-height: 1; }
.cookx-splash__wordmark strong { color: #4d8b69; }
.cookx-splash__subtitle { margin: 14px 0 0; color: #737a75; font-size: 14px; letter-spacing: 3px; }
.cookx-splash__content > i { width: 34px; height: 3px; margin-top: 18px; border-radius: 99px; background: #e9a23b; }
.cookx-splash__slogan { margin: 15px 0 0; font-size: 15px; font-weight: 600; letter-spacing: 2px; }
.cookx-splash__wave { position: absolute; right: 0; bottom: max(34px, env(safe-area-inset-bottom)); left: 0; height: 70px; opacity: .16; }
.cookx-splash__wave span { position: absolute; right: -8%; left: -8%; height: 52px; border: 2px solid #4d8b69; border-color: #4d8b69 transparent transparent; border-radius: 50%; }
.cookx-splash__wave span:nth-child(2) { top: 14px; transform: scaleX(.82); }
.cookx-splash__wave span:nth-child(3) { top: 28px; transform: scaleX(.64); }
.cookx-splash-fade-leave-active { transition: opacity .28s ease; }
.cookx-splash-fade-leave-to { opacity: 0; }

@media (max-width: 390px) {
  .cookx-splash__mark { width: 116px; height: 116px; border-radius: 30px; }
  .cookx-splash__wordmark { font-size: 38px; }
}
</style>
