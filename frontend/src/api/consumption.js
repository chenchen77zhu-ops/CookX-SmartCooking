import axios from 'axios'
import {readUserId} from '../services/recognitionDraft.js'
import {API_BASE_URL} from '../config/backend'
import {readInventory} from './inventoryWrites.js'
import {createConsumption} from '../services/inventoryConsumption.js'
export const consumption=createConsumption({isCurrent:user=>readUserId()===user,read:readInventory,storage:localStorage,write:async(user,names)=>{
 const {data}=await axios.post(`${API_BASE_URL}/consume-ingredients`,names,{params:{user_id:user},timeout:15000});return data
}})
