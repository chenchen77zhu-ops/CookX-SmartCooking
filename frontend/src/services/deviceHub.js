import {createTemperatureStreamParser,TEMPERATURE_STALE_MS} from './temperatureStream.js'
// One transport subscription for the app lifetime; page subscriptions never own the connection.
export function createDeviceHub({listen,readState,now=Date.now,limit=2000}) {
 const subscribers=new Map(),records=[];let starting=null,handles=[],state={state:'idle',connected:false},lastFrameAt=null,timedOut=false,info={}
 const emit=(event,data)=>{for(const callback of subscribers.get(event)||[])callback(data)}
 function record(type,data){records.push({at:now(),type,data});if(records.length>limit)records.splice(0,records.length-limit)}
 const parser=createTemperatureStreamParser(sample=>{lastFrameAt=now();timedOut=false;record('sample',sample);if(state.state==='connected')emit('sample',sample)},{now,onReject:reason=>record('rejected-frame',{reason})})
 function connection(next){state={...next,connected:next.connected??next.state==='connected'};if(next.connected===false && next.state==='connected')state.state='disconnected';if(state.state!=='connected'){parser.reset();lastFrameAt=null}record('connection',state);emit('connectionStateChanged',state)}
 async function start(){if(starting)return starting;starting=(async()=>{
  try{
   handles.push(await listen('temperatureData',({chunk})=>{record('raw-frame',{chunk:String(chunk).slice(0,4096)});parser.append(chunk)}))
   handles.push(await listen('connectionStateChanged',connection))
   handles.push(await listen('deviceFound',data=>{record('device-found',data);emit('deviceFound',data)}))
   handles.push(await listen('discoveryFinished',data=>{record('discovery-finished',data);emit('discoveryFinished',data)}))
   handles.push(await listen('dataReceived',data=>emit('dataReceived',data)))
   await reconcile()
  }catch(error){for(const handle of handles)await handle.remove();handles=[];starting=null;throw error}
 })();return starting}
 async function reconcile(){parser.reset();lastFrameAt=null;timedOut=false;record('foreground-check',{});try{const next=await readState();connection({...next,awaitingSample:true});return state}catch(e){connection({state:'error',connected:false,message:e.message});throw e}}
 async function subscribe(event,callback){if(typeof callback!=='function')throw new TypeError('监听回调必须是函数');if(!subscribers.has(event))subscribers.set(event,new Set());subscribers.get(event).add(callback);try{await start()}catch(error){subscribers.get(event).delete(callback);throw error}return {remove:async()=>subscribers.get(event)?.delete(callback)}}
 function tick(){if(state.state==='connected' && lastFrameAt!==null && now()-lastFrameAt>=TEMPERATURE_STALE_MS && !timedOut){timedOut=true;record('data-timeout',{lastFrameAt});emit('timeout',{lastFrameAt})}}
 return {start,subscribe,reconcile,tick,reset:()=>parser.reset(),record,setInfo:value=>{info=value},snapshot:()=>({schemaVersion:1,source:'native-device-diagnostics',exportedAt:now(),info,state,lastFrameAt,records:structuredClone(records),hardwareAcceptance:'待真机验收；日志不构成识别准确率结论'})}
}
