<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>偏好设置</h1><p>记录你的口味和烹饪习惯，数据仅保存在当前设备。</p></div></header>
    <main class="subpage-content"><section class="subpage-card preference-form">
      <label><b>口味偏好</b><el-select v-model="preferences.taste" placeholder="请选择" clearable><el-option v-for="option in tasteOptions" :key="option" :label="option" :value="option" /></el-select></label>
      <label><b>辣度</b><el-select v-model="preferences.spice" placeholder="请选择"><el-option v-for="option in spiceOptions" :key="option" :label="option" :value="option" /></el-select></label>
      <label><b>烹饪时长偏好</b><el-select v-model="preferences.duration" placeholder="请选择" clearable><el-option v-for="option in durationOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></label>
      <label><b>食材禁忌 / 不喜欢的食材</b><el-input v-model="preferences.dislikedIngredients" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="例如：花生、香菜（请按真实情况填写）" /></label>
      <button class="subpage-primary-button save-button" type="button" @click="savePreferences"><el-icon><CircleCheck /></el-icon>保存偏好</button>
    </section></main>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import '@/assets/profile-pages.css'

const router = useRouter()
const storageKey = 'cookx_preferences'
const tasteOptions = ['清淡', '咸香', '酸甜', '鲜香']
const spiceOptions = ['不辣', '微辣', '中辣', '较辣']
const durationOptions = [{ label: '30 分钟内', value: 'under_30' }, { label: '30–60 分钟', value: '30_to_60' }, { label: '不限时长', value: 'any' }]
let stored = {}
try { stored = JSON.parse(localStorage.getItem(storageKey) || '{}') } catch (error) { console.warn('偏好数据解析失败', error) }
const preferences = reactive({ taste: stored.taste || '', spice: stored.spice || '不辣', duration: stored.duration || '', dislikedIngredients: stored.dislikedIngredients || '' })
const savePreferences = () => {
  localStorage.setItem(storageKey, JSON.stringify({ ...preferences }))
  ElMessage.success('偏好已保存在当前设备')
}
</script>

<style scoped>
.preference-form { display: grid; gap: 20px; }
.preference-form label { display: grid; gap: 9px; }
.preference-form label > b { font-size: 13px; }
.save-button { width: 100%; margin-top: 2px; }
</style>
