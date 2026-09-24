<template>
 <main class="business-page">
  <header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>家庭共享冰箱</h1></header>
  <p class="note">个人冰箱保持私有。只有确认转入的批次，家庭成员才能查看和维护。</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <section v-if="pending"><p>操作结果尚未确认。请先刷新核对；重试将使用同一凭证，不会重复执行。</p><div class="actions"><button :disabled="busy" @click="retry">重试原操作</button><button @click="refresh">刷新核对</button><button @click="discard">已核对，清除待确认状态</button></div></section>
  <section><label>我的家庭<select aria-label="我的家庭" v-model="selected" @change="loadDetail"><option value="">请选择</option><option v-for="f in families" :key="f.id" :value="f.id">{{ f.name }}</option></select></label><button :disabled="busy" @click="refresh">刷新家庭</button></section>
  <section v-if="!detail"><h2>创建或加入家庭</h2><label>家庭名称<input v-model="name" maxlength="80" /></label><button :disabled="busy||!!pending||!name.trim()" @click="send('/households','post',{name})">创建家庭</button><label>家庭邀请码<input v-model="joinCode" autocomplete="off" /></label><button :disabled="busy||!!pending||!joinCode" @click="send('/households/join','post',{code:joinCode})">确认加入</button></section>
  <template v-if="detail">
   <section><h2>{{ detail.household.name }}</h2><p class="note">成员 {{ detail.members.length }} 人 · {{ detail.membership.role==='admin'?'你是管理员':'你是成员' }}</p>
    <template v-if="detail.membership.role==='admin'"><button :disabled="busy||!!pending" @click="send(`/households/${selected}/invite`,'post',{})">生成一次性邀请</button><p v-if="inviteCode">请私下分享邀请码：<code>{{ inviteCode }}</code></p><div v-for="invite in detail.invitations.filter(i=>i.active)" :key="invite.id" class="item"><small>有效至 {{ new Date(invite.expires_at*1000).toLocaleString() }}</small><button :disabled="busy||!!pending" @click="send(`/households/${selected}/invitations/${invite.id}`,'delete',{expected_version:invite.version})">撤销邀请</button></div></template>
    <div v-for="m in detail.members" :key="m.id" class="item"><small>{{ m.user_id===user?'我':m.display_name }} · {{ m.role==='admin'?'管理员':'成员' }}</small><div v-if="m.role!=='admin'" class="actions"><button v-if="detail.membership.role==='admin'||m.user_id===user" :disabled="busy||!!pending" @click="removeMember(m)">{{ m.user_id===user?'退出家庭':'移除成员' }}</button><button v-if="detail.membership.role==='admin'" :disabled="busy||!!pending" @click="transferAdmin(m)">移交管理员</button></div></div>
   </section>
   <section><h2>共享库存</h2><p v-if="!detail.inventory.length" class="note">还没有共享库存。</p><div v-for="item in detail.inventory" :key="item.id" class="item"><b>{{ item.name }} · {{ item.quantity }} {{ item.unit }}</b><p class="note">{{ item.storage_type||'储存方式未知' }}</p><FreshnessCard :detail="item.freshness" status="ready" /><div class="actions"><button :disabled="busy||!!pending" @click="edit(item)">编辑批次</button><button :disabled="busy||!!pending" @click="removeStock(item)">删除批次</button></div></div></section>
   <section><h2>{{ editing?'编辑共享批次':'新增共享库存' }}</h2><label>食材名称<input v-model="stock.name" maxlength="80" /></label><div class="two"><label>实际数量<input v-model.number="stock.quantity" type="number" min="0.001" step="any" /></label><label>计量单位<select aria-label="计量单位" v-model="stock.unit"><option v-for="u in units" :key="u">{{ u }}</option></select></label></div><p class="note">“库存计数”沿用旧版本的份数，不等同于克数。无法确定换算时请保留原单位。</p><label>储存方式<select aria-label="储存方式" v-model="stock.storage_type"><option :value="null">未知</option><option>常温</option><option>冷藏</option><option>冷冻</option></select></label><label>保质期（天，可小数或未知）<input v-model="stock.shelf_life" type="number" step="any" min="0.001" /></label><label>购买时间（可未知）<input v-model="purchaseInput" type="datetime-local" /></label><label>到期时间（可未知）<input v-model="expiryInput" type="datetime-local" /></label><div class="actions"><button class="primary" :disabled="busy||!!pending||!stock.name.trim()" @click="saveStock">{{ editing?'确认保存修改':'确认入库' }}</button><button v-if="editing" @click="resetStock">取消编辑</button></div></section>
   <section><h2>从我的冰箱转入</h2><p class="note">整批转入后会从个人冰箱移除；日期与原有库存计数保留。不会自动合并不同批次。</p><button @click="loadPersonal" :disabled="busy">读取我的库存</button><div v-for="item in personal.items" :key="item.id" class="item"><b>{{ item.name }} · {{ item.quantity }} 库存计数</b><div class="actions"><button :disabled="busy||!!pending" @click="transfer(item)">确认整批转入家庭</button></div></div></section>
  </template>
 </main>
</template>
<script setup>
import FreshnessCard from '../components/FreshnessCard.vue'
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import {businessApi,commandKey,apiError} from '../api/business'
import {readUserId} from '../services/recognitionDraft'
import '../assets/business.css'
const router=useRouter(),user=readUserId(),families=ref([]),selected=ref(''),detail=ref(null),error=ref(''),busy=ref(false),name=ref(''),joinCode=ref(''),inviteCode=ref(''),personal=ref({items:[],version:0})
const key=`cookx:family-pending:v1:${user}`,pending=ref(null)
try{pending.value=JSON.parse(localStorage.getItem(key)||'null')}catch{}
const units=['库存计数','克','千克','毫升','升','个','根','份'],stock=ref({name:'',quantity:1,unit:'库存计数',storage_type:null,shelf_life:''}),editing=ref(null),purchaseInput=ref(''),expiryInput=ref('')
async function refresh(){try{families.value=(await businessApi('/households')).items;if(selected.value&&!families.value.some(f=>f.id===selected.value))selected.value='';if(!selected.value&&families.value.length)selected.value=families.value[0].id;await loadDetail()}catch(e){error.value=apiError(e)}}
async function loadDetail(){detail.value=null;inviteCode.value='';if(!selected.value)return;try{detail.value=await businessApi(`/households/${selected.value}`)}catch(e){error.value=apiError(e)}}
async function loadPersonal(){try{personal.value=await businessApi('/personal-inventory')}catch(e){error.value=apiError(e)}}
async function send(path,method,body){if(busy.value||pending.value)return;pending.value={path,method,body:{...body,idempotency_key:commandKey()}};localStorage.setItem(key,JSON.stringify(pending.value));await retry()}
async function retry(){if(busy.value||!pending.value)return;busy.value=true;error.value='';try{const p=pending.value,result=await businessApi(p.path,p.method,p.body);pending.value=null;localStorage.removeItem(key);if(result.household)selected.value=result.household.id;await refresh();if(result.code)inviteCode.value=result.code;await loadPersonal();resetStock()}catch(e){error.value=String(apiError(e));if(e.response?.status>=400&&e.response.status<500){pending.value=null;localStorage.removeItem(key)}}finally{busy.value=false}}
async function discard(){try{await ElMessageBox.confirm('确认已核对家庭和个人库存？清除本地记录不会撤销已保存操作。','核对操作',{confirmButtonText:'已核对',cancelButtonText:'取消'});pending.value=null;localStorage.removeItem(key)}catch{}}
function resetStock(){editing.value=null;stock.value={name:'',quantity:1,unit:'库存计数',storage_type:null,shelf_life:''};purchaseInput.value='';expiryInput.value=''}
function localDate(s){if(!s)return '';const d=new Date(s);if(!Number.isFinite(+d))return '';return new Date(+d-d.getTimezoneOffset()*60000).toISOString().slice(0,16)}
function edit(item){editing.value=item;stock.value={name:item.name,quantity:item.quantity,unit:item.unit,storage_type:item.storage_type||null,shelf_life:item.shelf_life??''};purchaseInput.value=localDate(item.purchase_time);expiryInput.value=localDate(item.expiry_date)}
function dateValue(input,field){if(editing.value&&input===localDate(editing.value[field]))return editing.value[field]??null;return input?new Date(input).toISOString():null}
async function saveStock(){try{const body={...stock.value,shelf_life:stock.value.shelf_life===''?null:Number(stock.value.shelf_life),purchase_time:dateValue(purchaseInput.value,'purchase_time'),expiry_date:dateValue(expiryInput.value,'expiry_date')};if(editing.value){body.expected_version=editing.value.version;body.add_time=editing.value.add_time||null}await send(`/households/${selected.value}/inventory${editing.value?'/'+editing.value.id:''}`,editing.value?'put':'post',body)}catch(e){error.value=apiError(e)}}
async function confirm(text,action){try{await ElMessageBox.confirm(text,'确认操作',{confirmButtonText:'确认',cancelButtonText:'取消'});await action()}catch{}}
const transfer=item=>confirm(`将「${item.name}」整批转入家庭？个人冰箱将移除该批次。`,()=>send(`/households/${selected.value}/transfer`,'post',{item_id:String(item.id),expected_inventory_version:personal.value.version,confirmed:true}))
const removeStock=item=>confirm(`删除共享批次「${item.name}」？`,()=>send(`/households/${selected.value}/inventory/${item.id}`,'delete',{expected_version:item.version}))
const removeMember=m=>confirm('移除后将立即失去此家庭的访问权限，共享库存仍由家庭保留。',()=>send(`/households/${selected.value}/members/${m.user_id}`,'delete',{expected_version:m.version}))
const transferAdmin=m=>confirm('确认将管理员权限移交给这位成员？',()=>send(`/households/${selected.value}/administrator`,'post',{member_id:m.user_id,expected_member_version:m.version,expected_version:detail.value.membership.version}))
onMounted(refresh)
</script>
