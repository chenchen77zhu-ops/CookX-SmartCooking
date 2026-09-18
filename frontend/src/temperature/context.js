export function adaptCookingContext(recipe = {}, step = {}, index = 0) {
  const value = step?.temperature
  let targetRange = null
  if (Array.isArray(value) && value.length === 2 && value.every(Number.isFinite)) targetRange = [...value]
  else if (typeof value === 'string') {
    const match = value.trim().match(/^(\d+(?:\.\d+)?)\s*[-–~～至]\s*(\d+(?:\.\d+)?)\s*(?:℃|°C|摄氏度)$/i)
    if (match) targetRange = [Number(match[1]), Number(match[2])]
  }
  if (targetRange && (targetRange[0] < 0 || targetRange[1] > 300 || targetRange[0] >= targetRange[1])) targetRange = null
  return { schemaVersion: 1, recipeId: String(recipe?.id ?? recipe?.dish_name ?? ''), stepId: String(index),
    method: typeof step?.method === 'string' ? step.method : 'unknown', targetRange }
}
export const PHASES = ['preheat', 'drop', 'recovery', 'steady', 'cooling', 'unknown']
export const PHASE_LABELS = { preheat: '预热', drop: '降温过渡', recovery: '回温', steady: '稳定加热', cooling: '冷却', unknown: '不确定' }
export const QUALITY_LABELS = { usable: '测量可用', suspect: '测量待确认', invalid: '测量失效' }
