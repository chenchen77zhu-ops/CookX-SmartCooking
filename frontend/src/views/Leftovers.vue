<template>
<main class="business-page"><header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>剩菜改造</h1></header>
<p class="note">剩余原料继续使用原库存的日期与鲜度。熟食单独记录，信息不明、异常或过期时不提供再利用建议；记录通过筛选也不等于可食用性鉴定。</p>
<p v-if="error" class="error" role="alert">{{ error }}</p><section v-if="pending"><p>上次操作结果待确认。</p><button :disabled="busy" @click="retry">使用原凭证核对</button></section>
<details class="ck-disclosure"><summary>添加记录</summary><div class="ck-disclosure__body"><form @submit.prevent="create"><label>记录类型<select v-model="form.kind"><option value="raw">剩余原料</option><option value="cooked">熟食剩菜</option></select></label>
<label v-if="form.kind==='raw'">原库存批次<select v-model="form.inventory_id" required><option value="">请选择</option><option v-for="r in inventory" :key="r.id" :value="r.id">{{ r.name }} · {{ r.quantity }} 库存计数 · {{ r.add_time }}</option></select></label>
<template v-else><label>熟食名称<input v-model="form.name" required maxlength="80"></label><label>熟食类别<select v-model="form.cooked_type"><option value="other">其他 / 不确定</option><option value="rice">熟米饭</option><option value="noodles">熟面条</option><option value="vegetables">熟蔬菜</option><option value="meat">熟肉</option></select></label><label>实际数量<input v-model="form.quantity" type="number" min="0.000001" step="any"></label><label>单位<input v-model="form.unit" maxlength="16" placeholder="例如：份、克"></label><label>制作时间<input v-model="form.made_at" type="datetime-local"></label><label>首次低温储存时间<input v-model="form.stored_at" type="datetime-local"></label><label>记录的到期时间<input v-model="form.expiry_at" type="datetime-local"></label><label>储存方式<select v-model="form.storage_type"><option value="">不清楚</option><option>常温</option><option>冷藏</option><option>冷冻</option></select></label><label><input type="checkbox" v-model="form.cold_chain_confirmed">我已核对连续低温储存情况</label><label><input type="checkbox" v-model="form.abnormal">存在异常</label><p class="note">未知信息可以留空，保存后会显示需补充核查，不进入建议。</p></template>
<button class="primary" :disabled="busy||!!pending">保存剩余食材记录</button></form></div></details>
<button @click="load" :disabled="busy">刷新记录</button><section v-for="r in items" :key="r.id" class="leftover-card"><h2>{{ r.name }} · {{ r.kind==='raw'?'剩余原料':'熟食' }}</h2><p v-if="r.kind==='cooked'">{{ r.quantity??'数量未知' }} {{ r.unit||'' }} · {{ r.storage_type||'储存未知' }}<br>制作：{{ format(r.made_at) }}<br>首次储存：{{ format(r.stored_at) }}<br>到期：{{ format(r.expiry_at) }}</p><p v-else>保留原库存批次，使用量在库存核对。</p><p>{{ r.assessment.eligible?'记录符合本版建议筛选条件，请继续核查实物':r.assessment.reasons.join('；') }}</p><p class="note">{{ r.assessment.disclaimer }}</p>
<div v-if="!r.closed" class="actions"><button :disabled="!r.assessment.eligible||busy" @click="showIdeas(r)">查看再利用思路</button><button v-if="r.kind==='cooked'" :disabled="busy||!!pending" @click="event(r,'reheated')">记录已再加热</button><button v-if="r.kind==='cooked'&&!r.cold_chain_confirmed&&r.events.some(e=>e.type==='reheated')" :disabled="busy||!!pending" @click="event(r,'restored')">核对再加热后低温储存</button><button v-if="r.kind==='cooked'" :disabled="busy||!!pending" @click="use(r)">记录实际使用量</button><button :disabled="busy||!!pending" @click="event(r,'abnormal')">标记异常</button><button :disabled="busy||!!pending" @click="event(r,'discarded')">结束此记录</button></div>
<details v-if="r.events.length"><summary>处理记录</summary><p v-for="(e,i) in r.events" :key="i">{{ labels[e.type] }} · {{ format(e.at) }} {{ e.quantity??'' }}</p></details></section>
<section v-if="ideas" aria-label="再利用思路"><h2>再利用思路</h2><p>{{ ideas.assessment.reasons.join('；') }}</p><article v-for="(idea,i) in ideas.ideas" :key="i"><h3>{{ idea.name }}</h3><ol v-if="idea.steps"><li v-for="step in idea.steps" :key="step">{{ step }}</li></ol><button v-if="idea.type==='standard'" :disabled="busy||!!pending" @click="clone(idea)">创建菜谱副本并核对</button></article><p class="note">{{ ideas.assessment.disclaimer }}</p><a :href="ideas.assessment.guidance_url" target="_blank" rel="noopener">查看 USDA 储存与再加热依据</a></section>
</main>
</template>
<script setup>
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
import '../assets/business.css'
const router=useRouter(),items=ref([]),inventory=ref([]),ideas=ref(null),form=ref({kind:'cooked',cooked_type:'other',inventory_id:'',name:'',quantity:'',unit:'',made_at:'',stored_at:'',expiry_at:'',storage_type:'',cold_chain_confirmed:false,abnormal:false})
const labels={reheated:'已再加热',restored:'已核对低温储存',used:'实际使用',abnormal:'异常',discarded:'已结束'}
const {busy,pending,error,send,retry}=usePendingCommand('leftovers',async result=>{ideas.value=null;await load();if(result.copy)router.push('/recipes?copy='+result.copy.id)})
let revision=0
async function load(){const request=++revision;try{const [a,b]=await Promise.all([businessApi('/leftovers'),businessApi('/personal-inventory')]);if(request!==revision)return;items.value=a.items;inventory.value=b.items}catch(e){if(request===revision)error.value=apiError(e)}}
function format(t){return t?new Date(t).toLocaleString():'未提供'}
function iso(t){if(!t)return null;const d=new Date(t);if(!Number.isFinite(d.getTime()))throw Error('日期无效');return d.toISOString()}
async function create(){try{const f=form.value;await send('/leftovers','post',f.kind==='raw'?{kind:'raw',inventory_id:f.inventory_id}:{kind:'cooked',cooked_type:f.cooked_type,name:f.name,quantity:f.quantity===''?null:Number(f.quantity),unit:f.unit||null,made_at:iso(f.made_at),stored_at:iso(f.stored_at),expiry_at:iso(f.expiry_at),storage_type:f.storage_type||null,cold_chain_confirmed:f.cold_chain_confirmed,abnormal:f.abnormal})}catch(e){error.value=e.message}}
async function event(r,type){try{await ElMessageBox.confirm(type==='restored'?'确认再加热后一小时内已恢复冷藏或冷冻，且持续低温储存？原制作和到期时间不会重置。':'确认'+labels[type]+'？原制作与储存时间将保留。','核对处理记录');await send('/leftovers/'+r.id+'/events','post',{expected_version:r.version,type,cold_chain_confirmed:type==='restored'})}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message}}
async function use(r){try{const {value}=await ElMessageBox.prompt('实际使用数量（'+r.unit+'），最多 '+r.quantity,'核对使用量',{inputValidator:v=>Number.isFinite(Number(v))&&Number(v)>0&&Number(v)<=r.quantity||'请输入有效数量'});await send('/leftovers/'+r.id+'/events','post',{expected_version:r.version,type:'used',quantity:Number(value)})}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message}}
async function showIdeas(r){ideas.value=null;try{ideas.value=await businessApi('/leftovers/'+r.id+'/ideas')}catch(e){error.value=apiError(e)}}
const clone=r=>send('/recipes/copies','post',{source_type:'standard',source_id:r.id,expected_source_version:r.source_version})
onMounted(load)
</script>
