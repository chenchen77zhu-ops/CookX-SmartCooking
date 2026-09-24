import axios, {AxiosError,CanceledError} from 'axios'
import {LOCAL_TEST_MODE} from '../config/buildMode.js'
import {createLocalTestRuntime} from './runtime.js'
export const localTestRuntime=LOCAL_TEST_MODE?createLocalTestRuntime({storage:localStorage}):null
document.documentElement.dataset.cookxMode=LOCAL_TEST_MODE?'local-test':'online'
if(LOCAL_TEST_MODE){
 try{localTestRuntime.initialize();localStorage.setItem('user',JSON.stringify(localTestRuntime.currentUser()))}catch(error){console.error(error.message)}
 // Install before all axios.create() modules. Unknown endpoints fail locally, never fall through to HTTP.
 axios.defaults.adapter=async config=>{
  if(config.signal?.aborted)throw new CanceledError()
  const url=new URL(config.url,config.baseURL?new URL(config.baseURL.endsWith('/')?config.baseURL:config.baseURL+'/',location.origin):location.origin)
  const path=url.pathname.replace(/^\/api/,'');const params={...Object.fromEntries(url.searchParams),...config.params};let body=config.data
  if(typeof body==='string'){try{body=JSON.parse(body)}catch{body=null}}
  try{const response=await localTestRuntime.request({method:config.method,path,params,body});if(config.signal?.aborted)throw new CanceledError();return {...response,statusText:'Local test',headers:{},config,request:{local:true}}}
  catch(error){if(error.code==='ERR_CANCELED')throw error;throw new AxiosError(error.message,error.code||'LOCAL_TEST_ERROR',config,undefined,error.response)}
 }
}
