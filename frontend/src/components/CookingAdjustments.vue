<template>
<section class="adjustments" aria-label="烹饪调整">
 <b>遇到情况，先检查再调整</b>
 <div><button v-for="(rule,key) in adjustmentRules" :key="key" @click="preview(key)">{{ rule.label }}</button></div>
 <p v-if="error" role="alert">{{ error }}</p>
 <div v-if="proposal" class="preview">
  <strong>{{ proposal.label }} · 修改预览</strong><p>{{ proposal.reason }}</p>
  <div v-for="patch in proposal.patches" :key="patch.index"><p>第 {{ patch.index+1 }} 步原文：{{ patch.before }}</p><p>确认后：{{ patch.after }}</p></div>
  <p v-if="!proposal.patches.length">没有可修改的后续步骤。{{ proposal.advice }} 可使用上方手动计时。</p>
  <button v-if="proposal.patches.length" @click="apply">确认调整</button><button @click="proposal=null">取消预览</button>
 </div>
 <p>菜谱版本 {{ session.recipeVersion }}；当前和已进入的步骤保留原文。</p>
 <div v-for="record in session.adjustments" :key="record.id"><span>{{ record.label }} · 版本 {{ record.version }}{{ record.undone?' · 已撤销':'' }}</span><button v-if="canUndo(record)" @click="undo(record.id)">撤销{{ record.label }}</button></div>
</section>
</template>
<script setup>
import {ref} from 'vue'
import {adjustmentRules,previewAdjustment} from '../services/cookingAdjustments.js'
const props=defineProps({session:Object,engine:Object});const emit=defineEmits(['changed'])
const proposal=ref(null),error=ref('')
function preview(type){error.value='';try{proposal.value=previewAdjustment(props.session,type)}catch(e){error.value=e.message}}
function apply(){try{props.engine.adjust(proposal.value);proposal.value=null;emit('changed')}catch(e){error.value=e.message;proposal.value=null}}
function canUndo(record){return !record.undone && record.patches.every(p=>!props.session.timers[p.index].visited && p.index>props.session.stepIndex && props.session.recipe.steps[p.index].text===p.after)}
function undo(id){try{props.engine.undo(id);emit('changed')}catch(e){error.value=e.message}}
</script>
<style scoped>
.adjustments{padding:14px;margin-top:16px;background:#faf5ec;border-radius:14px;font-size:13px;line-height:1.7;overflow-wrap:anywhere}.preview{border-top:1px solid #d6c7ad;margin-top:8px}button{padding:8px;margin:4px;border:1px solid #d6c7ad;border-radius:8px;background:white;color:#65482a}p{white-space:pre-line}
</style>
