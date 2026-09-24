export const commandLabels={next:'下一步',previous:'上一步',repeat:'重复播报',startTimer:'开始计时',pauseTimer:'暂停计时',resumeTimer:'继续计时',temperature:'查询温度'}
const phrases={next:['下一步','下一个步骤'],previous:['上一步','上一个步骤'],repeat:['重复','重复播报','再说一遍','重新播报'],startTimer:['开始计时'],pauseTimer:['暂停计时','停止计时'],resumeTimer:['继续计时','恢复计时'],temperature:['查询温度','现在多少度','现在的温度','温度是多少']}
export function parseVoiceCommand(text,confidence) {
  const normalized=String(text||'').replace(/[，。！？、,.!?\s]/g,'')
  const exact=Object.entries(phrases).filter(([,values])=>values.includes(normalized)).map(([key])=>key)
  const matches=exact.length?exact:Object.entries(phrases).filter(([,values])=>values.some(value=>normalized.includes(value))).map(([key])=>key)
  const trustworthy=typeof confidence==='number' && Number.isFinite(confidence) && confidence>=0.8 && confidence<=1
  return {text:String(text||''),matches,confirmed:exact.length===1 && trustworthy}
}
