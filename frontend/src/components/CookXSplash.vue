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
.cookx-splash { position: fixed; z-index: 10000; inset: 0; display: grid; overflow: hidden; background: #0D100F; color: #F6F3EE; place-items: center; }
.cookx-splash__halo { position: absolute; top: 18%; left: 50%; width: min(120vw, 620px); aspect-ratio: 1; border-radius: 50%; background: radial-gradient(circle, rgba(255,138,61,.28), rgba(255,138,61,0) 62%); transform: translateX(-50%); }
.cookx-splash__content { position: relative; z-index: 2; display: flex; align-items: center; flex-direction: column; transform: translateY(-4vh); }
.cookx-splash__mark { display: grid; width: 112px; height: 112px; padding: 14px; border: 1px solid rgba(255,255,255,.1); border-radius: 30px; background: rgba(255,255,255,.06); place-items: center; }
.cookx-splash__mark img { display: block; width: 100%; height: 100%; }
.cookx-splash__wordmark { margin-top: 22px; font-size: 40px; font-weight: 700; letter-spacing: -1.2px; line-height: 1; }
.cookx-splash__wordmark strong { color: #FF8A3D; }
.cookx-splash__subtitle { margin: 12px 0 0; color: rgba(246,243,238,.6); font-size: 13px; letter-spacing: 4px; }
.cookx-splash__content > i { width: 28px; height: 3px; margin-top: 18px; border-radius: 99px; background: #FF8A3D; }
.cookx-splash__slogan { margin: 14px 0 0; color: rgba(246,243,238,.8); font-size: 14px; letter-spacing: 3px; }
.cookx-splash__wave { display: none; }
.cookx-splash-fade-leave-active { transition: opacity .28s ease; }
.cookx-splash-fade-leave-to { opacity: 0; }
</style>
