<template>
<section class="completion" aria-label="完成核对">
 <h3>完成烹饪 · 实际使用清单</h3><p>请勾选实际使用的库存记录。每项按现有接口数量减一，不代表克数或份量换算。</p>
 <p v-if="message" role="status">{{ message }}</p>
 <button :disabled="busy" @click="load">重新读取库存</button>
 <label v-for="item in items" :key="item.id"><input v-model="selected" type="checkbox" :value="item.id" :disabled="busy || !!item.blocked || locked" />{{ item.name }}：{{ item.quantity }} → {{ item.after }} <span>{{ item.blocked }}</span></label>
 <button :disabled="busy" @click="finishOnly">{{ session.status==='completed'?'关闭核对':'仅记录完成，不扣库存' }}</button>
 <button v-if="!locked" :disabled="busy || !selected.length || !loaded" @click="submit">确认完成并扣减所选</button>
 <button v-if="uncertain" :disabled="busy" @click="reconcile">核对扣减结果（不重发）</button>
 <button v-if="session.status!=='completed'" :disabled="busy" @click="$emit('close')">继续烹饪</button>
</section>
</template>
<script setup>
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
import {readInventory} from '../api/inventoryWrites.js'
import {consumption} from '../api/consumption.js'
import {consumptionPreview} from '../services/inventoryConsumption.js'
import {readUserId} from '../services/recognitionDraft.js'
const props=defineProps({session:Object,engine:Object});const emit=defineEmits(['changed','close'])
const items=ref([]),selected=ref([]),busy=ref(false),loaded=ref(false),message=ref(''),transaction=ref(null);let active=true,version=0
const valid=()=>active && readUserId()===props.session.user
const uncertain=computed(()=>transaction.value && transaction.value.status!=='confirmed')
const locked=computed(()=>!!uncertain.value || transaction.value?.sessionId===props.session.id || props.session.consumption==='confirmed')
function readPending(){try{transaction.value=consumption.pending(props.session.user)}catch(e){transaction.value={status:'uncertain'};message.value=e.message}}
async function load(){const own=++version;busy.value=true;loaded.value=false;readPending();try{const rows=await readInventory(props.session.user);if(!valid() || own!==version)return;items.value=consumptionPreview(rows);selected.value=[];loaded.value=true}catch(e){if(valid())message.value=e.message}finally{if(valid() && own===version)busy.value=false}}
function completed(){if(props.engine.state.status!=='completed')props.engine.finish();emit('changed')}
function finishOnly(){if(!valid() || busy.value)return;completed();emit('close')}
function changed(){window.dispatchEvent(new CustomEvent('cookx:inventory-changed',{detail:{user:props.session.user}}))}
async function submit(){if(!valid()||busy.value||locked.value)return;busy.value=true;completed();try{await consumption.submit(props.session.user,props.session.id,items.value.filter(i=>selected.value.includes(i.id)));props.engine.state.consumption='confirmed';props.engine.persist();if(valid())message.value='库存扣减已回读确认。'}catch(e){if(valid())message.value=e.message+'；烹饪已记录完成。'}finally{changed();if(valid()){readPending();busy.value=false;emit('changed')}}}
async function reconcile(){if(!valid()||busy.value)return;busy.value=true;try{await consumption.reconcile(props.session.user);if(valid()){readPending();if(transaction.value.sessionId===props.session.id){props.engine.state.consumption='confirmed';props.engine.persist()}message.value='当前库存与预期扣减结果一致；若同期在其他设备修改过库存，请再人工核对。';emit('changed');changed()}}catch(e){if(valid())message.value=e.message}finally{if(valid())busy.value=false}}
onMounted(load);onBeforeUnmount(()=>{active=false;version++})
</script>
<style scoped>
.completion{padding:18px;margin:12px 0;background:white;border:1px solid #b8cbbf;border-radius:16px;line-height:1.7}.completion label{display:block;margin:8px 0}.completion button{padding:8px;margin:5px;border:1px solid #b8cbbf;border-radius:8px;background:#edf5ef;color:#244c36}.completion span{color:#97543a}
</style>
