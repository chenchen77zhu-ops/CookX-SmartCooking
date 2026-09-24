<template>
<section class="reminders" aria-label="烹饪提醒">
 <label><input type="checkbox" :checked="enabled" @change="toggle($event.target.checked)" />开启烹饪提醒</label>
 <p role="status">{{ notificationMessage }}</p><button v-if="native" @click="configureExact">设置精确计时权限</button>
 <p v-if="error" role="status">{{ error }} <button @click="checkInventory">重试食材检查</button></p>
 <article v-for="event in store.state.value?.reminders || []" :key="event.key"><span>{{ event.text }}</span><small> {{ new Date(event.at).toLocaleTimeString() }}{{ event.dismissed?' · 已关闭提示':'' }}</small><button v-if="!event.dismissed" @click="dismiss(event.key)">关闭此提示</button></article>
</section>
</template>
<script setup>
import {ref,watch,onMounted,onBeforeUnmount} from 'vue'
import {Capacitor} from '@capacitor/core'
import {timerReminder,recordReminder} from '../services/cookingReminders.js'
import {remindersEnabled,setReminders,reconcileTimer,notifyEvent,notificationMessage,exactSettings} from '../services/cookingNotifications.js'
import {readInventory} from '../api/inventoryWrites.js'
import {getInventoryFreshness} from '../api/freshness.js'
import {canonicalName} from '../services/inventoryFields.js'
const props=defineProps({store:Object,assessment:Object,replaying:Boolean});const emit=defineEmits(['changed'])
const native=Capacitor.getPlatform()==='android',enabled=ref(remindersEnabled(props.store.engine.state.user)),error=ref('');let active=true,request=0,interval
function publish(event){if(!event)return;props.store.engine.persist();emit('changed');if(enabled.value && !(event.type==='timer' && props.store.engine.state.timers[props.store.engine.state.stepIndex].notificationKey===event.key))notifyEvent(props.store.engine.state.user,event)}
function tick(){if(props.store.ready)publish(timerReminder(props.store.engine.state))}
function dismiss(key){const event=props.store.engine.state.reminders.find(e=>e.key===key);if(event){event.dismissed=true;props.store.engine.persist();emit('changed')}}
async function toggle(value){try{enabled.value=await setReminders(props.store.engine.state.user,value)===true;await reconcileTimer(props.store)}catch(e){error.value=e.message}}
async function configureExact(){try{await exactSettings();await reconcileTimer(props.store)}catch(e){error.value=e.message}}
async function checkInventory(){
 const s=props.store.engine.state,sessionId=s.id,version=s.recipeVersion,own=++request;error.value=''
 const current=()=>active && own===request && props.store.engine.state.id===sessionId && props.store.engine.state.recipeVersion===version
 try{
  const rows=await readInventory(s.user);if(!current())return
  const present=new Set(rows.filter(r=>Number(r.quantity)>0).map(r=>canonicalName(r.name)))
  const required=[...new Set([...s.recipe.used_ingredients,...s.recipe.ingredients_list.map(i=>i.item),...s.recipe.missing])]
  const missing=required.filter(name=>!present.has(canonicalName(name)))
  if(missing.length)publish(recordReminder(s,{type:'missing',object:`recipe:${version}`,once:true,risk:'warning',text:`库存中未确认：${missing.join('、')}。请在继续前核对食材。`}))
  const result=await getInventoryFreshness(s.user);if(!current())return
  const ids=new Set(rows.map(r=>String(r.id)))
  for(const detail of result.items.filter(d=>ids.has(String(d.item_id)) && (d.expired||d.expiring_soon||d.critical))){const row=rows.find(r=>String(r.id)===String(detail.item_id));publish(recordReminder(s,{type:'freshness',object:String(detail.item_id),risk:detail.expired?'danger':'warning',text:`${row.name}：${detail.expired?'后端判定已过期':'后端提示临期'}。评估时间 ${result.evaluated_at || '未提供'}；请到库存页查看依据与免责声明。`}))}
 }catch(e){if(current())error.value=`食材检查未完成：${e.message}`}
}
watch(()=>[props.store.state.value?.id,props.store.state.value?.recipeVersion],checkInventory)
watch(()=>{const s=props.store.state.value;return [s?.id,s?.status,s?.stepIndex,s?.timers[s.stepIndex]?.deadline,s?.timers[s.stepIndex]?.round]},()=>{tick();reconcileTimer(props.store)})
watch(()=>props.assessment,a=>{if(props.replaying || !a || !props.store.ready)return;if(a.quality!=='usable'){publish(recordReminder(props.store.engine.state,{type:'temperature-quality',object:'sensor',text:'温度测量不可用或待稳定；暂停温度操作建议，请核对探头与连接。'}));return}if(['warning','danger'].includes(a.risk))publish(recordReminder(props.store.engine.state,{type:'temperature',object:'sensor',risk:a.risk,text:a.suggestion || a.reasons?.join('；') || '请核对温度状态'}))})
function foreground(){if(!document.hidden){tick();reconcileTimer(props.store)}}
onMounted(()=>{tick();checkInventory();reconcileTimer(props.store);interval=setInterval(tick,500);document.addEventListener('visibilitychange',foreground);window.addEventListener('cookx:foreground',foreground)})
onBeforeUnmount(()=>{active=false;request++;clearInterval(interval);document.removeEventListener('visibilitychange',foreground);window.removeEventListener('cookx:foreground',foreground)})
</script>
<style scoped>
.reminders{padding:14px;margin-top:12px;border-radius:12px;background:#f1f6f2;font-size:13px;line-height:1.6;overflow-wrap:anywhere}article{padding:8px;border-top:1px solid #d6e1d8}small{color:#636e65}button{margin:4px;padding:5px;border:1px solid #c7d5cb;border-radius:6px;background:white;color:#315340}
</style>
