import { Capacitor, registerPlugin } from '@capacitor/core'
import {createDeviceHub} from './deviceHub.js'

const TemperatureBluetooth = registerPlugin('TemperatureBluetooth')
const deviceHub=createDeviceHub({listen:(event,callback)=>TemperatureBluetooth.addListener(event,callback),readState:async()=>{
 const [bluetooth,connection]=await Promise.all([TemperatureBluetooth.checkBluetoothState(),TemperatureBluetooth.getConnectionState()])
 return {...connection,state:!bluetooth.supported?'unsupported':!bluetooth.permissionsGranted?'permission-denied':!bluetooth.enabled?'bluetooth-off':connection.state,connected:bluetooth.enabled && bluetooth.permissionsGranted && connection.connected}
}})
let initialized=false,frameClock=null
export async function initializeTemperatureDevice(){
 if(!isAndroidNative())return unsupportedResult()
 await deviceHub.start()
 if(!initialized){initialized=true;frameClock=setInterval(()=>deviceHub.tick(),1000);window.addEventListener('cookx:foreground',()=>reconcileTemperatureDevice().catch(()=>{}));document.addEventListener('visibilitychange',()=>{if(!document.hidden)reconcileTemperatureDevice().catch(()=>{});else deviceHub.record('background',{continuousCollectionGuaranteed:false})});try{deviceHub.setInfo(await TemperatureBluetooth.getDiagnosticsInfo())}catch(error){deviceHub.record('info-unavailable',{message:error.message})}}
}
export async function reconcileTemperatureDevice(){if(!isAndroidNative())return unsupportedResult();await initializeTemperatureDevice();return deviceHub.reconcile()}
export async function exportTemperatureDiagnostics(){
 await initializeTemperatureDevice();const data=isAndroidNative()?deviceHub.snapshot():{schemaVersion:1,source:'browser-no-hardware',exportedAt:Date.now(),records:[],hardwareAcceptance:'浏览器不支持真实 SPP 采集，待真机验收'}
 if(isAndroidNative()){await TemperatureBluetooth.exportDiagnostics({json:JSON.stringify(data,null,2),filename:`cookx-device-diagnostics-${Date.now()}.json`});return data}
 const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download=`cookx-device-diagnostics-${Date.now()}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);return data
}
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

  await initializeTemperatureDevice()
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
  await initializeTemperatureDevice()
  return deviceHub.subscribe(eventName, callback)
}

export const onTemperatureDeviceFound = (callback) => nativeListener('deviceFound', callback)
export const onTemperatureConnectionStateChanged = (callback) => nativeListener('connectionStateChanged', callback)
export const onTemperatureRawData = (callback) => nativeListener('dataReceived', callback)

export const disconnectTemperatureDevice = async () => {
  resetTemperatureBuffer()
  if (!isAndroidNative()) return unsupportedResult()
  return TemperatureBluetooth.disconnect()
}

export const resetTemperatureBuffer = () => deviceHub.reset()
export const onTemperatureData = callback => nativeListener('sample',callback)

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
