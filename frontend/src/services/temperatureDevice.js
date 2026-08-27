import { Capacitor, registerPlugin } from '@capacitor/core'

const TemperatureBluetooth = registerPlugin('TemperatureBluetooth')
const isAndroidNative = () => Capacitor.getPlatform() === 'android'
const unsupportedResult = () => ({
  status: 'unsupported',
  platform: Capacitor.getPlatform(),
  message: 'Bluetooth Classic SPP is only available in the Android app.'
})

export const connectTemperatureDevice = async (address) => {
  if (!isAndroidNative()) return unsupportedResult()
  if (typeof address !== 'string' || !/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/i.test(address.trim())) {
    throw new Error('请输入有效的蓝牙 MAC 地址')
  }

  return TemperatureBluetooth.connect({ address: address.trim().toUpperCase() })
}

export const disconnectTemperatureDevice = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.disconnect()
}

export const onTemperatureData = async (callback) => {
  if (typeof callback !== 'function') {
    throw new TypeError('温度数据回调必须是函数')
  }
  if (!isAndroidNative()) {
    return { remove: async () => {} }
  }

  return TemperatureBluetooth.addListener('temperatureData', callback)
}

export const handleTemperatureUpdate = (temp, timestamp = Date.now()) => {
  const temperature = Number(temp)
  if (!Number.isFinite(temperature) || temperature < -50 || temperature > 500) {
    return null
  }

  const receivedAt = Number(timestamp)

  return {
    temperature,
    timestamp: Number.isFinite(receivedAt) ? receivedAt : Date.now()
  }
}
