<template>
  <canvas ref="canvas" class="ck-steam" aria-hidden="true"></canvas>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 锅上方升起的蒸汽 / 烟雾。intensity 0–1 决定粒子数量，smoke 为过热时的灰色烟雾。
const props = defineProps({
  intensity: { type: Number, default: 0.4 },
  smoke: { type: Boolean, default: false },
  originY: { type: Number, default: 0.42 } // 锅口相对画面高度
})

const canvas = ref(null)
let ctx = null, raf = 0, particles = [], width = 0, height = 0, last = 0, running = false
const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

function resize() {
  const el = canvas.value
  if (!el) return
  width = el.clientWidth; height = el.clientHeight
  el.width = Math.round(width * 0.5); el.height = Math.round(height * 0.5) // 半分辨率，配合模糊更柔和也更省电
  ctx = el.getContext('2d')
  ctx.setTransform(0.5, 0, 0, 0.5, 0, 0)
}

function spawn() {
  const spread = width * 0.62
  return {
    x: width / 2 + (Math.random() - 0.5) * spread,
    y: height * props.originY + Math.random() * 40,
    r: 24 + Math.random() * 46,
    vx: (Math.random() - 0.5) * 10,
    vy: -(18 + Math.random() * 26) * (props.smoke ? 1.25 : 1),
    life: 0,
    ttl: 4 + Math.random() * 4
  }
}

function frame(time) {
  if (!running) return
  const dt = Math.min(0.05, (time - (last || time)) / 1000)
  last = time
  const target = Math.round(6 + props.intensity * 34)
  while (particles.length < target && Math.random() < 0.35) particles.push(spawn())
  ctx.clearRect(0, 0, width, height)
  const tone = props.smoke ? '150, 140, 136' : '255, 250, 244'
  particles = particles.filter(p => {
    p.life += dt
    p.x += (p.vx + Math.sin((p.life + p.r) * 0.9) * 8) * dt
    p.y += p.vy * dt
    p.r += dt * 14
    const k = p.life / p.ttl
    if (k >= 1) return false
    const alpha = Math.sin(Math.PI * k) * (props.smoke ? 0.22 : 0.12) * (0.4 + props.intensity)
    const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r)
    g.addColorStop(0, `rgba(${tone}, ${alpha})`)
    g.addColorStop(1, `rgba(${tone}, 0)`)
    ctx.fillStyle = g
    ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fill()
    return true
  })
  raf = requestAnimationFrame(frame)
}

function start() { if (running || reduced) return; running = true; last = 0; raf = requestAnimationFrame(frame) }
function stop() { running = false; cancelAnimationFrame(raf) }
const onVisibility = () => (document.hidden ? stop() : start())

onMounted(() => { resize(); start(); window.addEventListener('resize', resize); document.addEventListener('visibilitychange', onVisibility) })
onBeforeUnmount(() => { stop(); window.removeEventListener('resize', resize); document.removeEventListener('visibilitychange', onVisibility) })
watch(() => props.intensity, value => { if (value <= 0) particles = particles.slice(0, 4) })
</script>

<style scoped>
.ck-steam { position: absolute; inset: 0; width: 100%; height: 100%; filter: blur(6px); pointer-events: none; }
</style>
