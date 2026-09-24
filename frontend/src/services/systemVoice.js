import { Capacitor, registerPlugin } from '@capacitor/core'
const native=registerPlugin('CookingVoice')
let recognition=null, generation=0, utterance=null, speechGeneration=0, speechListener=null
export async function stopListening() {generation++;if(Capacitor.getPlatform()==='android'){try{await native.stop()}catch{}}else{recognition?.abort();recognition=null}}
export async function listenOnce() {
 const stopping=stopListening();const own=generation;await stopping
 if(own!==generation)throw new Error('语音识别已取消')
 if(Capacitor.getPlatform()==='android') {
   const result=await native.start()
   if(own!==generation)throw new Error('语音识别已取消')
   return result
 }
 const Recognizer=window.SpeechRecognition||window.webkitSpeechRecognition
 if(!Recognizer)throw new Error('此设备没有可用语音识别，请使用文字指令或按钮')
 return new Promise((resolve,reject)=>{
   const instance=new Recognizer();recognition=instance;instance.lang='zh-CN';instance.continuous=false;instance.interimResults=false
   let settled=false
   const finish=(error,value)=>{if(settled)return;settled=true;clearTimeout(timeout);instance.onend=null;instance.onresult=null;instance.onerror=null;instance.abort();if(recognition===instance)recognition=null;error?reject(error):resolve(value)}
   const timeout=setTimeout(()=>finish(new Error('未收到识别结果，请再试一次')),20000)
   instance.onresult=event=>{const result=[...event.results].find(r=>r.isFinal);if(!result)return;if(own!==generation)return finish(new Error('语音识别已取消'));finish(null,{text:result[0].transcript,confidence:result[0].confidence})}
   instance.onerror=event=>finish(new Error(`语音识别不可用：${event.error}`))
   instance.onend=()=>finish(new Error('未识别到指令，请重试'))
   try{instance.start()}catch(error){finish(error)}
 })
}
export async function stopSystemSpeech(){speechGeneration++;speechListener?.remove();speechListener=null;if(Capacitor.getPlatform()==='android'){try{await native.stopSpeaking()}catch{}}else{window.speechSynthesis?.cancel();utterance=null}}
export async function speakSystem(text,onState=()=>{}) {
 const stopping=stopSystemSpeech();const own=speechGeneration;await stopping;await stopListening()
 if(own!==speechGeneration)return
 if(Capacitor.getPlatform()==='android') {
   let listener
   listener=await native.addListener('speechState',event=>{if(event.id===String(own)){if(own===speechGeneration)onState(event.state);if(['idle','error'].includes(event.state))listener?.remove()}})
   speechListener=listener
   if(own!==speechGeneration){listener.remove();return}
   try{await native.speak({text,id:String(own)});onState('playing')}catch(error){listener.remove();throw error}
   return
 }
 if(!window.speechSynthesis)throw new Error('系统播报不可用，可手动重试在线播报')
 const voices=window.speechSynthesis.getVoices().filter(v=>/^zh/i.test(v.lang))
 if(!voices.length)throw new Error('没有可用中文系统语音，可手动重试在线播报')
 utterance=new SpeechSynthesisUtterance(text);utterance.lang='zh-CN';utterance.voice=voices[0]
 utterance.onend=()=>{if(own===speechGeneration)onState('idle')};utterance.onerror=()=>{if(own===speechGeneration)onState('error')}
 window.speechSynthesis.speak(utterance);onState('playing')
}
