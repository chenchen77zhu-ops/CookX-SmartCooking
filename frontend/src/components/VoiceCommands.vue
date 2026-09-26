<template>
 <section class="voice-commands" aria-label="语音指令">
  <button :disabled="listening" @click="listen">{{ listening ? '正在听，请说一句…' : '点击说一句指令' }}</button>
  <button v-if="listening" @click="cancel">取消识别</button>
  <label>文字指令<input v-model="text" placeholder="例如：暂停计时" @keyup.enter="interpret(text, 1)" /></label>
  <button @click="interpret(text,1)">执行文字指令</button>
  <p role="status">{{ message }}</p>
  <div v-if="pending.length"><span>请确认要执行的操作：</span><button v-for="command in pending" :key="command" @click="execute(command)">{{ labels[command] }}</button><button @click="pending=[]">取消</button></div>
 </section>
</template>
<script setup>
import {ref,onBeforeUnmount,onMounted} from 'vue'
import {listenOnce,stopListening} from '../services/systemVoice.js'
import {parseVoiceCommand,commandLabels as labels} from '../services/voiceCommands.js'
const emit=defineEmits(['command','before-listen'])
const text=ref(''),message=ref('点击麦克风后识别一条指令。'),listening=ref(false),pending=ref([])
let version=0
function execute(command){pending.value=[];message.value=`执行：${labels[command]}`;emit('command',command)}
function interpret(value,confidence){const parsed=parseVoiceCommand(value,confidence);pending.value=[];if(!parsed.matches.length){message.value='未匹配支持的指令，请重说或使用按钮';return}if(parsed.confirmed)execute(parsed.matches[0]);else{pending.value=parsed.matches;message.value='识别结果需要确认，尚未执行'}}
async function listen(){if(listening.value)return;const own=++version;emit('before-listen');pending.value=[];listening.value=true;try{const result=await listenOnce();if(own!==version)return;text.value=result.text;interpret(result.text,result.confidence)}catch(error){if(own===version)message.value=error.message}finally{if(own===version)listening.value=false}}
function cancel(){version++;listening.value=false;stopListening()}
const visibility=()=>{if(document.hidden)cancel()}
onMounted(()=>document.addEventListener('visibilitychange',visibility))
onBeforeUnmount(()=>{cancel();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>
.voice-commands{margin:4px 0 12px;padding:14px;border-radius:16px;background:var(--ck-fill);color:var(--ck-text-2);font-size:13px;line-height:1.6}
button{border:1px solid var(--ck-glass-border);border-radius:999px;background:var(--ck-fill-strong);color:var(--ck-text);font:inherit;font-size:13px;font-weight:600;min-height:38px;padding:0 14px;margin:4px 6px 4px 0}
button:first-of-type{background:var(--ck-heat-soft);border-color:rgba(255,138,61,.35);color:#FFB27F}
button:disabled{opacity:.5}
label{display:grid;gap:4px;margin:8px 0 4px;color:var(--ck-text-3);font-size:12px}
input{box-sizing:border-box;min-height:42px;padding:0 12px;border:1px solid rgba(255,255,255,.1);border-radius:12px;background:rgba(255,255,255,.07);color:var(--ck-text);font:inherit;width:100%}
p{overflow-wrap:anywhere;margin:6px 0 0}
</style>
