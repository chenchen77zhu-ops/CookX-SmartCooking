<template>
 <main class="business-page"><header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>{{ onlyFavorites?'我的收藏':'菜谱复刻' }}</h1></header><p class="note">复刻会创建独立副本并保留来源。核对食材后送到烹饪页，由你决定何时开始；不会直接计时或扣库存。</p>
 <p v-if="error" role="alert" class="error">{{ error }}</p><section v-if="pending"><p>操作结果未确认，请使用原凭证重试。</p><button :disabled="busy" @click="retry">重试原操作</button></section>
 <section><label>菜谱来源<select aria-label="菜谱来源" v-model="kind" @change="load"><option value="standard">标准菜谱</option><option value="history">我的菜谱历史</option><option value="favorite">我的收藏</option><option value="copy">我的独立副本</option></select></label><button :disabled="busy" @click="load">刷新菜谱</button><p v-if="!items.length" class="note">当前来源暂无可执行菜谱。</p></section>
 <section v-for="item in items" :key="item.id"><h2>{{ item.recipe.dish_name }}</h2><p class="note">{{ item.recipe.steps.length }} 个步骤 · {{ item.recipe.method||'烹饪方法未标注' }}</p><details><summary>查看步骤与来源</summary><ol><li v-for="step in item.recipe.steps" :key="step.id">{{ step.text }} <small>（{{ step.time_estimate==null?'时长未提供':step.time_estimate+' 秒' }}）</small></li></ol><p class="note">来源：{{ labels[item.source?.type||kind]||item.source?.type }} · 版本 {{ item.source?.version||item.source_version }}</p></details><div class="actions"><button class="primary" :disabled="busy||!!pending" @click="clone(item)">{{ kind==='copy'?'再创建独立副本':'一键复刻' }}</button><button v-if="kind!=='favorite'" :disabled="busy||!!pending" @click="favorite(item)">收藏此版本</button><button v-if="kind==='favorite'" :disabled="busy||!!pending" @click="removeFavorite(item)">取消收藏</button><button v-if="kind==='copy'" :disabled="busy" @click="check(item.id)">核对食材</button></div></section>
 <section v-if="checked" aria-label="复刻食材核对"><h2>核对：{{ checked.copy.recipe.dish_name }}</h2><p class="note">{{ checked.note }}</p><p v-if="!checked.ingredients.length" class="note">来源未提供结构化食材清单，请手动检查原菜谱。</p><div v-for="(item,index) in checked.ingredients" :key="index" class="item"><b>{{ item.name }} · {{ item.required_amount??'用量未知' }} {{ item.unit||'' }}</b><p class="note">{{ statuses[item.status] }}</p></div><button class="primary" @click="prepare">已核对，送到烹饪页</button></section>
 </main>
</template>
<script setup>
import {ref,onMounted} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
import {queueRecipeDraft} from '../services/recipeDraft'
import {readUserId} from '../services/recognitionDraft'
import '../assets/business.css'
const props=defineProps({onlyFavorites:Boolean}),router=useRouter(),route=useRoute(),kind=ref(props.onlyFavorites?'favorite':'standard'),items=ref([]),checked=ref(null)
const labels={standard:'标准菜谱',history:'我的历史',favorite:'我的收藏',copy:'独立副本',community:'社区菜谱',menu:'已保存菜单'},statuses={missing:'库存未找到该食材，请准备后再开始',needs_freshness_check:'存在同名库存，但日期或鲜度需核查，请检查实物',needs_quantity_check:'找到可评估批次，仍需手动核对数量与单位'}
const {pending,busy,error,send,retry}=usePendingCommand('recipes',async result=>{await load();if(result.copy)await check(result.copy.id)})
async function load(){items.value=[];try{items.value=(await businessApi('/recipes/sources?kind='+kind.value)).items}catch(e){error.value=apiError(e)}}
function source(item){return {source_type:kind.value,source_id:item.id,expected_source_version:String(item.source_version)}}
const clone=item=>send('/recipes/copies','post',source(item)),favorite=item=>send('/recipes/favorites','post',source(item)),removeFavorite=item=>send('/recipes/favorites/'+item.id,'delete',{expected_version:item.version})
async function check(id){checked.value=null;try{checked.value=await businessApi('/recipes/copies/'+id+'/check')}catch(e){error.value=apiError(e)}}
function prepare(){try{const copy=checked.value.copy;queueRecipeDraft(readUserId(),{...copy.recipe,cookx_copy_id:copy.id,source_meta:copy.source,copy_version:copy.version});router.push('/home?tab=AiChef')}catch(e){error.value=e.message}}
onMounted(async()=>{await load();if(route.query.copy)await check(String(route.query.copy))})
</script>
