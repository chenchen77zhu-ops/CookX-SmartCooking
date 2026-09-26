<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>偏好设置</h1><p>记录你的口味和烹饪习惯，偏好保存在当前账号的后端，其他设备登录后可继续使用。</p></div></header>
    <main class="subpage-content"><section class="subpage-card preference-form">
      <p v-if="error" role="alert">{{ error }}</p><button v-if="pending" :disabled="busy" @click="retry">核对上次保存结果</button><button :disabled="busy" @click="load">刷新已保存偏好</button><button v-if="legacyAvailable" :disabled="busy||!!pending" type="button" @click="importLegacy">确认将旧设备偏好导入当前账号</button>
      <label><b>口味偏好</b><el-select v-model="preferences.taste" placeholder="请选择" clearable><el-option v-for="option in tasteOptions" :key="option" :label="option" :value="option" /></el-select></label>
      <label><b>辣度</b><el-select v-model="preferences.spice" placeholder="请选择"><el-option v-for="option in spiceOptions" :key="option" :label="option" :value="option" /></el-select></label>
      <label><b>烹饪时长偏好</b><el-select v-model="preferences.duration" placeholder="请选择" clearable><el-option v-for="option in durationOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></label>
      <label><b>食材禁忌 / 不喜欢的食材</b><el-input v-model="preferences.dislikedIngredients" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="例如：花生、香菜（请按真实情况填写）" /></label>
      <button class="subpage-primary-button save-button" type="button" :disabled="busy||!!pending||!loaded" @click="savePreferences"><el-icon><CircleCheck /></el-icon>保存偏好</button>
    </section></main>
  </div>
</template>

<script setup>
import { reactive, ref, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import '@/assets/profile-pages.css'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'

const router = useRouter()
const userAtOpen=JSON.parse(localStorage.getItem('user')||'{}').id
const storageKey = `cookx:preferences:v1:${userAtOpen}`
const legacyAvailable=ref(!!localStorage.getItem('cookx_preferences'))
const validUser=()=>JSON.parse(localStorage.getItem('user')||'{}').id===userAtOpen
function changedUser(){if(!validUser())router.replace('/home')}
window.addEventListener('storage',changedUser);window.addEventListener('cookx:user-changed',changedUser)
onBeforeUnmount(()=>{window.removeEventListener('storage',changedUser);window.removeEventListener('cookx:user-changed',changedUser)})
async function importLegacy(){if(!validUser())return changedUser();try{const old=JSON.parse(localStorage.getItem('cookx_preferences')||'{}');for(const key of Object.keys(preferences))if(key in old)preferences[key]=old[key];await savePreferences();if(!pending.value&&!error.value)legacyAvailable.value=false}catch{ElMessage.error('旧偏好无法读取')}}
const tasteOptions = ['清淡', '咸香', '酸甜', '鲜香']
const spiceOptions = ['不辣', '微辣', '中辣', '较辣']
const durationOptions = [{ label: '30 分钟内', value: 'under_30' }, { label: '30–60 分钟', value: '30_to_60' }, { label: '不限时长', value: 'any' }]
let stored = {}
try { stored = JSON.parse(localStorage.getItem(storageKey) || '{}') } catch (error) { console.warn('偏好数据解析失败', error) }
const preferences = reactive({ taste: stored.taste || '', spice: stored.spice || '不辣', duration: stored.duration || '', dislikedIngredients: stored.dislikedIngredients || '' })
const loaded=ref(false),serverVersion=ref(0)
const {busy,error,pending,send,retry}=usePendingCommand('preferences',async()=>{await load();if(!error.value)ElMessage.success('偏好已保存到当前账号')})
async function load(){try{const data=(await businessApi('/preferences')).preferences;if(!validUser())return;serverVersion.value=data.version;loaded.value=true;if(data.version>0)Object.assign(preferences,data.values);localStorage.setItem(storageKey,JSON.stringify({...preferences}))}catch(e){error.value=apiError(e);loaded.value=false}}
async function savePreferences(){if(!validUser())return changedUser();if(!loaded.value)return;await send('/preferences','put',{expected_version:serverVersion.value,values:{...preferences}})}
onMounted(load)

</script>

<style scoped>
.preference-form { display: grid; gap: 18px; }
.preference-form label { display: grid; gap: 8px; }
.preference-form label > b { color: var(--ck-text-2); font-size: 13px; font-weight: 500; }
.preference-form > p[role=alert] { padding: 10px 12px; border-radius: 12px; background: var(--ck-danger-soft); color: #FFB5AB; font-size: 13px; }
.save-button { width: 100%; margin-top: 4px; }
</style>
