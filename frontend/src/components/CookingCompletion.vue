<template>
<SharedCookingCompletion v-if="session.recipe?.inventory_scope?.family_id" :session="session" :engine="engine" @changed="$emit('changed')" @close="$emit('close')"/><section v-else class="completion" aria-label="完成核对">
 <h3>完成烹饪 · 实际使用清单</h3><p>请勾选实际使用的库存记录。按批次填写实际使用的库存计数，不代表克数或份量换算。</p>
 <p v-if="message" role="status">{{ message }}</p>
 <button :disabled="busy" @click="load">重新读取库存</button>
 <label v-for="item in items" :key="item.id"><input v-model="selected" type="checkbox" :value="item.id" :disabled="busy || !!item.blocked || locked" />{{ item.name }}：{{ item.quantity }} → {{ Math.max(0,item.quantity-Number(item.consumeQuantity)) }} · 批次 {{ String(item.id).slice(-6) }} · {{ item.storage_type || '储存未知' }} · 保质期 {{ item.shelf_life ?? '未知' }} 天 · 入库 {{ displayDate(item.add_time) }} · 到期 {{ displayDate(item.expiry_date) }} <span>{{ item.blocked }}</span><input v-model.number="item.consumeQuantity" type="number" min="1" :max="item.quantity" step="1" :aria-label="item.name+'使用数量（批次 '+item.id+'）'" :disabled="busy || locked || !!item.blocked" /></label>
 <button :disabled="busy" @click="finishOnly">{{ session.status==='completed'?'关闭核对':'仅记录完成，不扣库存' }}</button>
 <button v-if="!locked" :disabled="busy || !selected.length || !loaded" @click="submit">确认完成并扣减所选</button>
 <button v-if="uncertain" :disabled="busy" @click="reconcile">核对扣减结果（不重发）</button>
 <button v-if="uncertain && transaction.schemaVersion===2" :disabled="busy" @click="retry">使用原凭证重试（不会重复扣减）</button>
 <button v-if="session.status!=='completed'" :disabled="busy" @click="$emit('close')">继续烹饪</button>
</section>
</template>
<script setup>
import SharedCookingCompletion from './SharedCookingCompletion.vue'
import {completionSync} from '../api/completions'
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
import {readInventory} from '../api/inventoryWrites.js'
import {consumption} from '../api/consumption.js'
import {consumptionPreview} from '../services/inventoryConsumption.js'
import {readUserId} from '../services/recognitionDraft.js'
const displayDate=value=>value && Number.isFinite(Date.parse(value))?new Date(value).toLocaleString('zh-CN',{hour12:false}):'未知'
const props=defineProps({session:Object,engine:Object});const emit=defineEmits(['changed','close'])
const items=ref([]),selected=ref([]),busy=ref(false),loaded=ref(false),message=ref(''),transaction=ref(null);let active=true,version=0
const valid=()=>active && readUserId()===props.session.user
const uncertain=computed(()=>transaction.value && !['confirmed','rejected'].includes(transaction.value.status))
const locked=computed(()=>!!uncertain.value || (transaction.value?.status!=='rejected' && transaction.value?.sessionId===props.session.id) || props.session.consumption==='confirmed')
function readPending(){try{transaction.value=consumption.pending(props.session.user)}catch(e){transaction.value={status:'uncertain'};message.value=e.message}}
async function load(){const own=++version;busy.value=true;loaded.value=false;readPending();try{const rows=await readInventory(props.session.user);if(!valid() || own!==version)return;items.value=consumptionPreview(rows);selected.value=[];loaded.value=true}catch(e){if(valid())message.value=e.message}finally{if(valid() && own===version)busy.value=false}}
function completed(){if(props.engine.state.status!=='completed')props.engine.finish();try{completionSync.enqueue(props.engine.state);completionSync.sync(props.session.user).catch(()=>{if(valid())message.value='本地完成已保留，成长记录待同步，可在厨艺成长页核对。'})}catch(e){if(valid())message.value='本地完成已保留，成长同步未提交：'+e.message}emit('changed')}
function finishOnly(){if(!valid() || busy.value)return;completed();emit('close')}
function changed(){window.dispatchEvent(new CustomEvent('cookx:inventory-changed',{detail:{user:props.session.user}}))}
async function submit(){if(!valid()||busy.value||locked.value)return;busy.value=true;completed();try{await consumption.submit(props.session.user,props.session.id,items.value.filter(i=>selected.value.includes(i.id)));props.engine.state.consumption='confirmed';props.engine.persist();if(valid())message.value='库存扣减已回读确认。'}catch(e){if(valid())message.value=e.message+'；烹饪已记录完成。'}finally{changed();if(valid()){readPending();busy.value=false;emit('changed')}}}
async function retry(){if(!valid()||busy.value)return;busy.value=true;try{await consumption.retry(props.session.user);message.value='原凭证已确认，库存已重新读取。';if(valid()){props.engine.state.consumption='confirmed';props.engine.persist();changed()}}catch(e){if(valid())message.value=e.message}finally{if(valid()){readPending();busy.value=false;emit('changed')}}}
async function reconcile(){if(!valid()||busy.value)return;busy.value=true;try{await consumption.reconcile(props.session.user);if(valid()){readPending();if(transaction.value.sessionId===props.session.id){props.engine.state.consumption='confirmed';props.engine.persist()}message.value='服务端原凭证已确认，当前库存已重新读取；其他设备后续修改不会触发再次扣减。';emit('changed');changed()}}catch(e){if(valid())message.value=e.message}finally{if(valid())busy.value=false}}
onMounted(()=>{if(!props.session.recipe?.inventory_scope?.family_id)load()});onBeforeUnmount(()=>{active=false;version++})
</script>
<style scoped>
.completion{margin:0;padding:16px;border:1px solid var(--ck-glass-border);border-radius:var(--ck-radius-lg);background:var(--ck-glass);color:var(--ck-text-2);font-size:13.5px;line-height:1.7;-webkit-backdrop-filter:var(--ck-blur);backdrop-filter:var(--ck-blur)}
.completion h3{margin-bottom:6px;color:var(--ck-text);font-size:17px}
.completion label{display:block;margin:10px 0;padding:12px;border-radius:14px;background:var(--ck-fill);color:var(--ck-text)}
.completion input[type=checkbox]{width:20px;height:20px;margin-right:8px;vertical-align:-4px;accent-color:var(--ck-heat-deep)}
.completion button{border:1px solid var(--ck-glass-border);border-radius:999px;background:var(--ck-fill-strong);color:var(--ck-text);font:inherit;font-size:13px;font-weight:600;min-height:38px;padding:0 14px;margin:4px 6px 4px 0}
.completion button:disabled{opacity:.45}
.completion span{color:#FFB27F}
.completion input[type=number]{box-sizing:border-box;min-height:42px;padding:0 12px;border:1px solid rgba(255,255,255,.1);border-radius:12px;background:rgba(255,255,255,.07);color:var(--ck-text);font:inherit;display:block;width:110px;margin:8px 0 0}
</style>
