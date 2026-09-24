import axios from 'axios'
import {readUserId} from '../services/recognitionDraft.js'
import {API_BASE_URL} from '../config/backend'
import {readInventory} from './inventoryWrites.js'
import {createConsumption} from '../services/inventoryConsumption.js'
export const consumption=createConsumption({isCurrent:user=>readUserId()===user,read:readInventory,storage:localStorage,
 write:async(user,body)=>(await axios.post(`${API_BASE_URL}/inventory/consume`,body,{timeout:15000})).data,
 lookup:async(user,key)=>(await axios.get(`${API_BASE_URL}/inventory/consumption/${encodeURIComponent(key)}`,{params:{user_id:user},timeout:15000})).data
})
