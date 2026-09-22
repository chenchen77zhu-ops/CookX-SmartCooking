import { shallowRef } from 'vue'
import { createCookingSession } from './cookingSession.js'
const stores=new Map()
export function getCookingStore(user) {
  if(!stores.has(user)) {
    const engine=createCookingSession(user,{storage:localStorage})
    const state=shallowRef(engine.state), store={engine,state,ready:false,refresh(){state.value=engine.state?structuredClone(engine.state):null}}
    stores.set(user,store)
  }
  return stores.get(user)
}
export function suspendCookingStores(){for(const store of stores.values())store.ready=false}
