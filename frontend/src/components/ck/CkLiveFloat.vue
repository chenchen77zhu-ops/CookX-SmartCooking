<template>
  <Transition name="float">
    <div
      v-if="visible"
      ref="root"
      class="live-float"
      :class="{ 'is-left': side === 'left', dragging }"
      :style="{ top: `${top}px` }"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
    >
      <button type="button" class="live-float__open" aria-label="回到实时厨房" @click="open">
        <CkSenseCard variant="mini" />
      </button>
      <button type="button" class="live-float__close" aria-label="收起悬浮窗" @click.stop="dismiss"><CkIcon name="x" :size="12" :stroke="2.6" /></button>
    </div>
  </Transition>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CkIcon from './CkIcon.vue'
import CkSenseCard from './CkSenseCard.vue'
import { live } from '@/services/liveKitchen.js'
import { uiState } from '@/services/uiState.js'
import { getCookingStore } from '@/services/cookingStore.js'
import { readUserId } from '@/services/recognitionDraft.js'

// 在其他页面显示的实时厨房小窗：有进行中的烹饪或已连接测温设备时出现
const route = useRoute()
const router = useRouter()
const root = ref(null)
const side = ref('right')
const top = ref(Math.max(90, (window.innerHeight || 700) - 300))
const dragging = ref(false)
const dismissedKey = ref('')

const key = computed(() => `${live.session?.id || ''}:${live.connected}`)
const onKitchen = computed(() => route.name === 'Home' && route.query.tab === 'AiChef')
const hiddenRoute = computed(() => ['Login', 'Register'].includes(route.name) || onKitchen.value || uiState.immersive)
const visible = computed(() => (!!live.session || live.connected) && !hiddenRoute.value && dismissedKey.value !== key.value)
watch(key, () => { dismissedKey.value = '' })

let start = null, moved = false
function onDown(event) {
  if (event.target.closest('.live-float__close')) return
  start = { x: event.clientX, y: event.clientY, top: top.value }
  moved = false
  root.value?.setPointerCapture?.(event.pointerId)
}
function onMove(event) {
  if (!start) return
  const dx = event.clientX - start.x, dy = event.clientY - start.y
  if (!moved && Math.hypot(dx, dy) < 6) return
  moved = true
  dragging.value = true
  const maxTop = (window.innerHeight || 700) - 180
  top.value = Math.min(maxTop, Math.max(60, start.top + dy))
  if (Math.abs(dx) > 40) side.value = event.clientX < (window.innerWidth || 390) / 2 ? 'left' : 'right'
}
function onUp() {
  start = null
  setTimeout(() => { dragging.value = false }, 0)
}
function open() {
  if (moved) { moved = false; return }
  const uid = readUserId()
  if (uid && live.session) getCookingStore(uid).ready = true // 回到厨房后自动恢复烹饪导航
  router.push({ path: '/home', query: { tab: 'AiChef' } })
}
function dismiss() { dismissedKey.value = key.value }
</script>

<style scoped>
.live-float { position: fixed; right: calc(10px + var(--sar)); z-index: 900; touch-action: none; transition: top 0.25s ease, left 0.25s ease, right 0.25s ease; }
.live-float.is-left { right: auto; left: calc(10px + var(--sal)); }
.live-float.dragging { transition: none; }
.live-float__open { display: block; padding: 0; border: 0; background: none; border-radius: 20px; }
.live-float__close { position: absolute; top: -6px; left: -6px; display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 1.5px solid rgba(255, 255, 255, 0.85); border-radius: 50%; background: rgba(30, 30, 28, 0.92); color: #fff; }
.live-float.is-left .live-float__close { left: auto; right: -6px; }
.float-enter-active, .float-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.float-enter-from, .float-leave-to { opacity: 0; transform: scale(0.85); }
</style>
