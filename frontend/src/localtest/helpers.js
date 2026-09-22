import {localTestRuntime} from './bootstrap.js'
import {saveDraft,recognitionItem} from '../services/recognitionDraft.js'
export async function loadRecognitionExample(router) {
 const user=localTestRuntime.currentUser().id
 const result=await localTestRuntime.request({path:'/analyze-fridge'})
 saveDraft(user,result.data.detected.map(recognitionItem))
 await router.push('/capture-confirm')
}
