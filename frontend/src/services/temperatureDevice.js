import { Capacitor, registerPlugin } from '@capacitor/core'
import { createTemperatureStreamParser } from './temperatureStream'

const TemperatureBluetooth = registerPlugin('TemperatureBluetooth')
let temperatureParser = null
const isAndroidNative = () => Capacitor.getPlatform() === 'android'
const unsupportedResult = () => ({
  status: 'unsupported',
  platform: Capacitor.getPlatform(),
  message: 'Bluetooth Classic SPP is only available in the Android app.'
})

export const connectTemperatureDevice = async (address) => {
  if (!isAndroidNative()) return unsupportedResult()
  const normalizedAddress = typeof address === 'string' ? address.trim().toUpperCase() : ''
  if (normalizedAddress && !/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/i.test(normalizedAddress)) throw new Error('蓝牙设备地址无效')

  resetTemperatureBuffer()
  try {
    return await TemperatureBluetooth.connect(normalizedAddress ? { address: normalizedAddress } : {})
  } catch (error) {
    resetTemperatureBuffer()
    throw error
  }
}

export const requestBluetoothPermissions = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.requestBluetoothPermissions()
}

export const getBluetoothState = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.checkBluetoothState()
}

export const scanTemperatureDevices = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.startDiscovery()
}

export const stopTemperatureDeviceScan = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.stopDiscovery()
}

export const getTemperatureConnectionState = async () => {
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.getConnectionState()
}

export const writeTemperatureHex = async (data) => TemperatureBluetooth.write({ type: 'hex', data })
export const writeTemperatureText = async (data) => TemperatureBluetooth.write({ type: 'text', data })

const nativeListener = async (eventName, callback) => {
  if (typeof callback !== 'function') throw new TypeError(`${eventName} 回调必须是函数`)
  if (!isAndroidNative()) return { remove: async () => {} }
  return TemperatureBluetooth.addListener(eventName, callback)
}

export const onTemperatureDeviceFound = (callback) => nativeListener('deviceFound', callback)
export const onTemperatureConnectionStateChanged = (callback) => nativeListener('connectionStateChanged', callback)
export const onTemperatureRawData = (callback) => nativeListener('dataReceived', callback)

export const disconnectTemperatureDevice = async () => {
  resetTemperatureBuffer()
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.disconnect()
}

export const resetTemperatureBuffer = () => {
  temperatureParser?.reset()
}

export const onTemperatureData = async (callback) => {
  if (typeof callback !== 'function') {
    throw new TypeError('温度数据回调必须是函数')
  }
  if (!isAndroidNative()) {
    return { remove: async () => {} }
  }

  temperatureParser = createTemperatureStreamParser(callback)
  const listener = await TemperatureBluetooth.addListener('temperatureData', ({ chunk }) => {
    temperatureParser?.append(chunk)
  })

  return {
    remove: async () => {
      temperatureParser?.reset()
      temperatureParser = null
      await listener.remove()
    }
  }
}

export const handleTemperatureUpdate = (temp, timestamp = Date.now()) => {
  const temperature = Number(temp)
  if (!Number.isFinite(temperature) || temperature < -50 || temperature > 500) {
    return null
  }

  const receivedAt = Number(timestamp)

  return {
    temperature,
    updatedAt: Number.isFinite(receivedAt) ? receivedAt : Date.now()
  }
}
