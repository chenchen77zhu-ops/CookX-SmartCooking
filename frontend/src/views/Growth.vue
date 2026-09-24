<template><main class="business-page"><header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>厨艺成长</h1></header><p class="note">只统计你确认完成的烹饪。生成菜谱、暂停计时和扣库存都不会单独增加次数。</p><p v-if="error" class="error" role="alert">{{ error }}</p><section><button :disabled="busy" @click="load">刷新成长记录</button><p v-if="pendingCount">本机有 {{ pendingCount }} 次完成待同步，原会话凭证不会重复计次。</p><button v-if="pendingCount" :disabled="busy" @click="sync">同步待确认完成</button><button :disabled="busy" @click="importLocal">核对本机旧完成记录</button><p class="note">旧记录只从当前账号导入，并需你确认；不会公开到社区。</p></section><section v-if="summary"><h2>已确认完成 {{ summary.confirmed_count }} 次</h2><p>尝试 {{ summary.recipe_count }} 种菜谱</p><p>{{ summary.note }}</p><h3>烹饪方法</h3><p v-for="(count,method) in summary.methods" :key="method">{{ method }}：{{ count }} 次</p><details><summary>查看历史变化</summary><p v-for="(count,date) in summary.timeline" :key="date">{{ date }}：{{ count }} 次</p></details></section><CookingChallenges/><CookingBadges/><section v-for="r in summary?.history||[]" :key="r.id"><h2>{{ r.recipe.dish_name }}</h2><p>{{ new Date(r.completed_at).toLocaleString() }} · {{ r.provenance==='confirmed_local_import'?'已确认导入的本机记录':'烹饪会话确认' }}</p><p class="note">版本 {{ r.recipe_version }} · {{ r.recipe.method||'烹饪方法未标注' }}</p><button :disabled="busy" @click="clone(r)">复刻本次菜谱</button></section></main></template>
<script setup>
import CookingBadges from '../components/CookingBadges.vue'
import CookingChallenges from '../components/CookingChallenges.vue'
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import {businessApi,commandKey,apiError} from '../api/business'
import {completionSync} from '../api/completions'
import {readUserId} from '../services/recognitionDraft'
import {usePendingCommand} from '../services/businessCommands'
import '../assets/business.css'
const router=useRouter(),summary=ref(null),pendingCount=ref(0),user=readUserId()
const {busy,error,send}=usePendingCommand('growth-copy',r=>router.push('/recipes?copy='+r.copy.id))
async function load(){try{summary.value=await businessApi('/growth');pendingCount.value=completionSync.list(user).length}catch(e){error.value=apiError(e)}}
async function sync(){if(busy.value)return;busy.value=true;try{await completionSync.sync(user);await load()}catch(e){error.value=apiError(e)}finally{busy.value=false;pendingCount.value=completionSync.list(user).length}}
async function importLocal(){try{const records=JSON.parse(localStorage.getItem('cookx:cooking-history:v1:'+user)||'[]'),current=JSON.parse(localStorage.getItem('cookx:cooking:v1:'+user)||'null');if(current)records.push(current);const known=new Set((summary.value?.history||[]).map(r=>r.session_id)),eligible=[...new Map(records.filter(r=>r.user===user&&r.status==='completed'&&!known.has(r.id)).map(r=>[r.id,r])).values()];if(!eligible.length){error.value='当前账号没有尚未同步的本机完成记录';return}await ElMessageBox.confirm('导入当前账号 '+eligible.length+' 次已完成记录？将标记为本机旧记录导入。','核对旧记录');for(const r of eligible)completionSync.enqueue(r,'confirmed_local_import');pendingCount.value=completionSync.list(user).length;await sync()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message}}
async function clone(r){try{const rows=await businessApi('/recipes/sources?kind=history'),source=rows.items.find(s=>s.id===r.id);if(!source)throw Error('菜谱记录已变化，请刷新');await send('/recipes/copies','post',{source_type:'history',source_id:r.id,expected_source_version:source.source_version})}catch(e){error.value=apiError(e)}}
onMounted(load)
</script>
