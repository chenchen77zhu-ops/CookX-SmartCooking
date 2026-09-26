<template>
  <div :class="['ck-backdrop', `is-${variant}`]" aria-hidden="true">
    <img v-if="variant === 'kitchen'" :src="kitchenImage" alt="" class="ck-backdrop__photo" />
    <div class="ck-backdrop__glow"></div>
    <div class="ck-backdrop__fade"></div>
  </div>
</template>

<script setup>
import kitchenImage from '@/assets/backdrops/kitchen.webp'

defineProps({ variant: { type: String, default: 'warm' } })
</script>

<style scoped>
.ck-backdrop { position: fixed; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; background: var(--ck-bg); }
.ck-backdrop.is-kitchen { background: #0D100F; }
.is-plain .ck-backdrop__glow, .is-plain .ck-backdrop__fade { display: none; }
.ck-backdrop__photo {
  position: absolute; top: 0; left: 50%; width: max(100%, 720px); height: min(78vh, 720px);
  object-fit: cover; object-position: 62% 40%; transform: translateX(-50%);
  opacity: 0.92;
}
.ck-backdrop__glow, .ck-backdrop__fade { position: absolute; inset: 0; }
.is-kitchen .ck-backdrop__fade {
  background:
    linear-gradient(180deg, rgba(13,16,15,0.78) 0%, rgba(13,16,15,0.28) 26%, rgba(13,16,15,0.35) 48%, rgba(13,16,15,0.92) 72%, #0D100F 86%),
    linear-gradient(90deg, rgba(13,16,15,0.55) 0%, rgba(13,16,15,0) 60%);
}
.is-kitchen .ck-backdrop__glow { background: radial-gradient(60% 40% at 70% 36%, rgba(255,138,61,0.22), transparent 70%); }
.is-warm .ck-backdrop__glow {
  background:
    radial-gradient(90% 50% at 85% -5%, rgba(255,138,61,0.20), transparent 60%),
    radial-gradient(70% 40% at 0% 10%, rgba(120,70,40,0.25), transparent 65%);
}
.is-fresh .ck-backdrop__glow {
  background:
    radial-gradient(90% 45% at 90% -5%, rgba(61,214,140,0.18), transparent 60%),
    radial-gradient(70% 45% at -10% 15%, rgba(255,138,61,0.12), transparent 65%);
}
.is-plain .ck-backdrop__glow { display: none; }
.ck-backdrop__fade { background: linear-gradient(180deg, transparent 0%, transparent 45%, var(--ck-bg) 90%); }
</style>
