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
.voice-commands{margin:12px 0;padding:12px;border-radius:12px;background:#edf5ef;color:#315340;font-size:13px;line-height:1.6}
button,input{margin:4px;padding:8px;max-width:100%;border:1px solid #c8d9ce;border-radius:8px;background:white;color:#234c3b}label{display:block}p{overflow-wrap:anywhere}
</style>
