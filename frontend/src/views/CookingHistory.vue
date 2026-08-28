<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>烹饪与菜谱记录</h1><p>这里展示 AI 已为你生成过的真实菜谱，不代表已完成烹饪。</p></div></header>
    <main class="subpage-content">
      <section v-if="loading" class="subpage-card subpage-empty" v-loading="true"><h2>正在同步菜谱记录</h2></section>
      <section v-else-if="errorMessage" class="subpage-card subpage-empty"><span class="subpage-empty-icon"><el-icon><Warning /></el-icon></span><h2>暂时无法加载</h2><p>{{ errorMessage }}</p><button class="subpage-primary-button" type="button" @click="fetchHistory">重新加载</button></section>
      <section v-else-if="!records.length" class="subpage-card subpage-empty"><span class="subpage-empty-icon"><el-icon><Clock /></el-icon></span><h2>还没有菜谱记录</h2><p>在 AI 厨房生成菜谱后，真实记录会出现在这里。</p></section>
      <section v-else class="history-list">
        <article v-for="(record, index) in records" :key="`${record.time}-${index}`" class="subpage-card history-card">
          <div class="history-heading"><span><el-icon><Food /></el-icon></span><div><h2>{{ record.recipe.dish_name || '未命名菜谱' }}</h2><p>{{ formatHistoryTime(record.time) }}</p></div><b>{{ record.recipe.steps.length }} 步</b></div>
          <details><summary>查看已有步骤</summary><ol><li v-for="(step, stepIndex) in record.recipe.steps" :key="stepIndex">{{ typeof step === 'object' ? (step.text || step.content || '未提供步骤文字') : step }}</li></ol></details>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ArrowLeft, Clock, Food, Warning } from '@element-plus/icons-vue'
import { API_BASE_URL } from '@/config/backend'
import '@/assets/profile-pages.css'

const router = useRouter()
const records = ref([])
const loading = ref(true)
const errorMessage = ref('')

const fetchHistory = async () => {
  const userId = JSON.parse(localStorage.getItem('user') || '{}').id
  if (!userId) return router.push('/login')
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await axios.get(`${API_BASE_URL}/chat-history`, { params: { user_id: userId } })
    const history = Array.isArray(response.data) ? response.data : []
    records.value = history.filter(item => item?.recipe?.steps?.length).reverse()
  } catch (error) {
    console.error('加载菜谱记录失败', error)
    errorMessage.value = '请检查网络后重试'
  } finally {
    loading.value = false
  }
}

const formatHistoryTime = (value) => {
  if (!value) return '时间未记录'
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(fetchHistory)
</script>

<style scoped>
.history-list { display: grid; gap: 12px; }
.history-card { padding: 17px; }
.history-heading { display: flex; align-items: center; gap: 12px; }
.history-heading > span { display: grid; flex: 0 0 43px; width: 43px; height: 43px; border-radius: 14px; background: #eaf2ec; color: var(--cookx-primary); font-size: 20px; place-items: center; }
.history-heading > div { flex: 1; min-width: 0; }
.history-heading h2 { overflow: hidden; margin: 0 0 4px; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.history-heading p { margin: 0; color: var(--cookx-text-secondary); font-size: 10px; }
.history-heading > b { padding: 6px 9px; border-radius: 999px; background: #fff1e7; color: var(--cookx-accent); font-size: 10px; }
details { margin-top: 13px; padding-top: 12px; border-top: var(--cookx-border); }
summary { color: var(--cookx-primary); font-size: 11px; font-weight: 700; cursor: pointer; }
ol { margin: 12px 0 0; padding-left: 22px; color: #536059; font-size: 11px; line-height: 1.7; }
</style>
