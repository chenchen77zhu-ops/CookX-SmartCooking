export const MIN_TEMPERATURE = -50
export const MAX_TEMPERATURE = 500
export const TEMPERATURE_STALE_MS = 5000

export const createTemperatureStreamParser = (onTemperature) => {
  if (typeof onTemperature !== 'function') {
    throw new TypeError('温度数据回调必须是函数')
  }

  let buffer = ''

  return {
    append(chunk) {
      if (typeof chunk !== 'string' || chunk.length === 0) return

      buffer += chunk
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ''

      for (const line of lines) {
        const text = line.trim()
        if (!text) continue

        const temperature = Number(text)
        if (!Number.isFinite(temperature)) {
          console.debug('[Temperature] 丢弃非法数据:', text)
          continue
        }
        if (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
          console.debug('[Temperature] 丢弃超出范围的数据:', temperature)
          continue
        }

        onTemperature({ temperature, updatedAt: Date.now() })
      }
    },

    reset() {
      buffer = ''
    }
  }
}
