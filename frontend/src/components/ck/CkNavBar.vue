<template>
  <header :class="['ck-navbar', { 'is-solid': solid }]">
    <div class="ck-navbar__inner">
      <button v-if="back" type="button" class="ck-navbar__btn" :aria-label="backLabel" @click="goBack">
        <CkIcon name="chevron-left" :size="22" :stroke="2" />
      </button>
      <span v-else class="ck-navbar__spacer"></span>
      <h1 class="ck-navbar__title">{{ title }}</h1>
      <div class="ck-navbar__right"><slot name="right"><span class="ck-navbar__spacer"></span></slot></div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import CkIcon from './CkIcon.vue'

const props = defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true },
  backLabel: { type: String, default: '返回' },
  backTo: { type: [String, Object], default: null },
  solid: { type: Boolean, default: true },
  manual: { type: Boolean, default: false } // 只触发 back 事件，由页面自行处理返回
})
const emit = defineEmits(['back'])
const router = useRouter()
const goBack = () => {
  emit('back')
  if (props.manual) return
  if (props.backTo) router.push(props.backTo)
  else if (window.history.state?.back) router.back()
  else router.push('/home')
}
</script>

<style scoped>
.ck-navbar {
  position: sticky; top: 0; z-index: 20;
  margin: 0 calc(-1 * var(--ck-gutter));
  padding: var(--sat) var(--ck-gutter) 0;
}
.ck-navbar.is-solid {
  background: linear-gradient(180deg, var(--ck-nav-bg), var(--ck-nav-bg));
  -webkit-backdrop-filter: saturate(160%) blur(20px);
  backdrop-filter: saturate(160%) blur(20px);
  border-bottom: 1px solid var(--ck-hairline);
}
.ck-navbar__inner { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; align-items: center; gap: 8px; height: 52px; }
.ck-navbar__btn {
  display: grid; place-items: center; width: 38px; height: 38px; padding: 0;
  border: 1px solid var(--ck-glass-border); border-radius: 50%;
  background: var(--ck-fill); color: var(--ck-text);
}
.ck-navbar__btn:active { transform: scale(0.94); }
.ck-navbar__title { margin: 0; overflow: hidden; font-size: 17px; font-weight: 600; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.ck-navbar__right { display: flex; justify-content: flex-end; min-width: 44px; }
.ck-navbar__spacer { display: block; width: 38px; }
</style>
