<template>
  <section class="insight" aria-label="温度时序分析">
    <div class="insight-head"><strong>{{ assessment.phaseLabel }}</strong><span :class="assessment.quality">{{ qualityLabel }}</span></div>
    <p v-if="assessment.temperature !== null" class="current-reading">{{ assessment.temperature.toFixed(1) }} <small>℃</small></p>
    <p class="source">{{ replaying ? '物理仿真回放 · 非设备实测' : '设备测温 · 移动识别研究版' }}</p>
    <svg viewBox="0 0 320 100" role="img" aria-label="最近一分钟温度曲线">
      <path d="M0 85 H320 M0 45 H320 M0 5 H320" stroke="var(--ck-hairline)" fill="none"/>
      <polyline v-for="(line,i) in paths" :key="i" :points="line" fill="none" stroke="#FF8A3D" stroke-width="2"/>
      <text x="4" y="98" font-size="9" fill="rgba(246,243,238,0.45)">{{ extent[0].toFixed(0) }}–{{ extent[1].toFixed(0) }} ℃ · 60 秒</text>
    </svg>
    <p v-for="reason in assessment.reasons" :key="reason">{{ reason }}</p>
    <p v-if="assessment.suggestion" class="suggestion" :class="assessment.risk" role="status">{{ assessment.suggestion }}</p>
    <div class="event-actions">
      <button @click="$emit('confirm','ingredient_added')">刚投料</button>
      <button @click="$emit('confirm','probe_moved')">移动了探头</button>
      <button @click="$emit('confirm','heat_off')">已关火</button>
    </div>
    <details>
      <summary>算法与记录</summary>
      <p>{{ modelState }}</p>
      <label><input type="checkbox" :checked="experimental" @change="$emit('experimental',$event.target.checked)"> 启用实验模型（仿真训练）</label>
      <div v-if="prediction">
        <p>模型阶段估计：{{ prediction.phaseLabel }}。{{ prediction.note }}</p>
        <p v-for="item in prediction.forecast" :key="item.seconds">{{ item.seconds }} 秒后：{{ item.low.toFixed(0) }}–{{ item.high.toFixed(0) }} ℃</p>
      </div>
      <p v-else-if="experimental">证据不足或窗口正在恢复，暂不显示模型预测。</p>
      <div class="event-actions">
        <button v-if="!replaying" :disabled="connected" @click="$emit('replay')">仿真回放</button>
        <button v-else @click="$emit('stop-replay')">停止回放</button>
        <button @click="$emit('export',false)">导出本次</button>
        <button @click="$emit('export',true)">导出保存记录</button>
      </div>
      <p v-if="connected">回放需先断开设备，避免混淆数据来源。</p>
      <p v-if="storageMessage" role="status">{{ storageMessage }}</p>
    </details>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { QUALITY_LABELS } from '@/temperature/context'
const props=defineProps({assessment:Object,history:Array,prediction:Object,modelState:String,experimental:Boolean,replaying:Boolean,connected:Boolean,storageMessage:String})
defineEmits(['confirm','experimental','replay','stop-replay','export'])
const qualityLabel=computed(()=>QUALITY_LABELS[props.assessment.quality])
const extent=computed(()=>{
  const values=props.history.filter(s=>s.valid && Number.isFinite(s.temperature)).map(s=>s.temperature)
  return values.length?[Math.min(...values)-5,Math.max(...values)+5]:[0,100]
})
const paths=computed(()=>{
  const end=props.history.at(-1)?.updatedAt??0,[lo,hi]=extent.value
  let lines=[],line=[],previous=null
  for(const s of props.history){
    if(!s.valid || s.discontinuity || (previous && s.updatedAt-previous.updatedAt>1500)){if(line.length)lines.push(line.join(' '));line=[]}
    if(s.valid && Number.isFinite(s.temperature)) line.push(((s.updatedAt-end+60000)/60000*320).toFixed(1)+','+(85-(s.temperature-lo)/(hi-lo)*80).toFixed(1))
    previous=s
  }
  if(line.length)lines.push(line.join(' '))
  return lines
})
</script>
<style scoped>
.insight{margin-top:14px;border-top:1px solid var(--ck-hairline);padding-top:14px;color:var(--ck-text-2)}
.insight-head{display:flex;justify-content:space-between;gap:8px;align-items:center}
.insight-head strong{color:var(--ck-text);font-size:19px}.insight-head span{padding:4px 10px;border-radius:999px;background:var(--ck-fill-strong);font-size:12px}
.insight-head .invalid{opacity:.7}.insight-head .suspect{color:var(--ck-warn)}.source{font-size:11px!important;color:var(--ck-text-3)}
.current-reading{font-size:28px!important;color:var(--ck-text);font-weight:300}.current-reading small{font-size:14px}.insight p{font-size:12.5px;line-height:1.6;margin:8px 0}.insight svg{width:100%;height:112px}
.event-actions{display:flex;flex-wrap:wrap;gap:6px;margin:10px 0}
button{border:1px solid var(--ck-glass-border);border-radius:999px;background:var(--ck-fill-strong);color:var(--ck-text);font:inherit;font-size:13px;font-weight:600;min-height:38px;padding:0 14px;margin:4px 6px 4px 0;margin:0}
button:disabled{opacity:.45;cursor:default}.suggestion{padding:10px 12px;border-radius:12px;background:var(--ck-heat-soft);color:var(--ck-heat-text)}.danger{background:var(--ck-danger-soft);color:var(--ck-danger-text)}
details{font-size:12.5px;margin-top:10px}summary{cursor:pointer;padding:8px 0;color:var(--ck-text-2)}label{display:flex;align-items:center;gap:8px}
input[type=checkbox]{width:18px;height:18px;accent-color:var(--ck-heat-deep)}
</style>
