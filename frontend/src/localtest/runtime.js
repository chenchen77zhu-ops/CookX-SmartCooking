import {canonicalName} from '../services/inventoryFields.js'
export const LOCAL_STATE_KEY='cookx:localtest:v1'
export const TEST_USERS=[{id:'local-test-a',nickname:'本地测试 A',phone:'',avatar:''},{id:'local-test-b',nickname:'本地测试 B',phone:'',avatar:''}]
const clone=value=>JSON.parse(JSON.stringify(value))
const detailNote='本地固定示例，仅测试页面；未运行 FreshFusion，不代表真实鲜度或安全结论。'
export function createLocalTestRuntime({storage,now=Date.now,id=()=>globalThis.crypto.randomUUID()}) {
 const getState=()=>{const value=JSON.parse(storage.getItem(LOCAL_STATE_KEY)||'null');if(!value || value.version!==1 || !value.accounts)throw new Error('本地测试数据损坏，请在测试面板重置示例数据');return value}
 const save=state=>storage.setItem(LOCAL_STATE_KEY,JSON.stringify(state))
 const date=days=>new Date(now()+days*86400000).toISOString()
 const seed=()=>[
  {id:id(),name:'西红柿',quantity:2,storage_type:'冷藏',shelf_life:7,purchase_time:date(-1),add_time:date(-1),expiry_date:date(6),testCase:'normal'},
  {id:id(),name:'鸡蛋',quantity:3,storage_type:'冷藏',shelf_life:7,purchase_time:date(-2),add_time:date(-2),expiry_date:date(5),testCase:'partial'},
  {id:id(),name:'胡萝卜',quantity:1,storage_type:null,shelf_life:null,purchase_time:null,add_time:date(0),expiry_date:null,testCase:'unknown'},
  {id:id(),name:'牛奶',quantity:1,storage_type:'冷藏',shelf_life:3,purchase_time:date(-2),add_time:date(-2),expiry_date:date(1),testCase:'soon'},
  {id:id(),name:'豆腐',quantity:1,storage_type:'冷藏',shelf_life:2,purchase_time:date(-5),add_time:date(-5),expiry_date:date(-3),testCase:'zero'},
  {id:id(),name:'土豆',quantity:2,storage_type:'常温',shelf_life:null,purchase_time:null,add_time:date(-1),expiry_date:null,testCase:'unknown'},
  {id:id(),name:'土豆',quantity:1,storage_type:'冷藏',shelf_life:null,purchase_time:null,add_time:date(-2),expiry_date:null,testCase:'unknown'}
 ]
 function initialize(){if(!storage.getItem(LOCAL_STATE_KEY))save({version:1,currentUser:TEST_USERS[0].id,fault:'normal',accounts:Object.fromEntries(TEST_USERS.map(user=>[user.id,{user:clone(user),inventory:seed(),history:[]}]))});return currentUser()}
 function currentUser(){const s=getState();return clone(s.accounts[s.currentUser]?.user || TEST_USERS[0])}
 function selectUser(userId){const s=getState();if(!s.accounts[userId])throw new Error('请选择本地测试账号');s.currentUser=userId;save(s);storage.setItem('user',JSON.stringify(s.accounts[userId].user));return currentUser()}
 function resetCurrent(){const s=getState(),user=s.currentUser;s.accounts[user]={user:clone(TEST_USERS.find(u=>u.id===user)),inventory:seed(),history:[]};s.fault='normal';save(s)
  // Only the selected test account is cleared; never call localStorage.clear().
  for(const prefix of ['cookx:cooking:v1:','cookx:cooking-history:v1:','cookx:consumption:v1:','cookx:inventory-pending:v1:','cookx:recognition:v1:','cookx:reminders:v1:','cookx-temperature-session-'])storage.removeItem(prefix+user)
  storage.setItem('user',JSON.stringify(s.accounts[user].user))
 }
 function setFault(fault){const s=getState();if(!['normal','invalid-json','empty-recipe','business-error','timeout','consume-lost'].includes(fault))throw new Error('未知场景');s.fault=fault;save(s)}
 function freshness(rows){const evaluated_at=new Date(now()).toISOString();const items=rows.map(row=>{
  const kind=row.testCase||'unknown',known=kind!=='unknown',zero=kind==='zero';const parts={T:known?(zero?0:0.8):null,S:known&&kind!=='partial'?(zero?0:0.9):null,V:null,H:null}
  return {item_id:row.id,name:row.name,fresh_score:known?(zero?0:kind==='soon'?45:86):null,freshness_label:'示例值',confidence_score:known?0.55:null,confidence_level:known?'示例置信度':'数据不足',component_scores:parts,effective_weights:{T:known?(kind==='partial'?1:0.6):0,S:known&&kind!=='partial'?0.4:0,V:0,H:0},expired:zero,expiring_soon:kind==='soon',critical:zero,risk_flags:zero?['示例：已过期']:kind==='soon'?['示例：临期']:[],reasons:[detailNote],data_quality_notes:['V/H 没有数据，保持缺失。编辑或新增记录不生成鲜度分数。'],evaluated_at,algorithm_version:'local-fixture-v1',disclaimer:detailNote,time_details:{start_time:row.purchase_time,expiry_time:row.expiry_date,status:'本地字段，仅展示'},storage_details:{reason:'本地字段，仅展示'}}
 });return {status:'success',items,evaluated_at,total_count:items.length,unknown_count:items.filter(i=>i.fresh_score===null).length,evaluable_count:items.filter(i=>i.fresh_score!==null).length}}
 function recipe(prompt){return {schemaVersion:1,id:'local-guide',dish_name:'本地操作演练（非烹饪配方）',description:'预置练习，不是 AI 生成；只用于测试按钮、计时、语音和测温。无需点火。',test_source:'preset',ingredients_list:[{item:'西红柿',amount:'测试清单'},{item:'鸡蛋',amount:'测试清单'}],used_ingredients:['西红柿','鸡蛋'],missing:['盐'],steps:[{text:'保持灶具关闭，准备测试。探头可静止对准室内物体；本步骤未提供时长。',time_estimate:null},{text:'观察 10 秒倒计时，测试暂停、继续和重复播报。保持灶具关闭。',time_estimate:10},{text:'检查调整提示；模拟后续操作 15 秒，不判断食物熟度，也不要求加热。',time_estimate:15},{text:'演练完成核对，勾选本地测试库存后确认扣减。',time_estimate:null}],local_prompt:prompt}}
 function validateItem(row){if(typeof row.name!=='string'||!row.name.trim()||!Number.isSafeInteger(row.quantity)||row.quantity<=0)throw new Error('名称必填，数量必须是正整数');if(row.shelf_life!=null&&(!Number.isFinite(row.shelf_life)||row.shelf_life<=0))throw new Error('保质期必须为正数或未知');for(const key of ['purchase_time','add_time','expiry_date'])if(row[key]!=null&&!Number.isFinite(Date.parse(row[key])))throw new Error('日期格式无效');if(row.purchase_time&&Date.parse(row.purchase_time)>now())throw new Error('购买时间不能晚于现在');for(const start of [row.purchase_time,row.add_time])if(start&&row.expiry_date&&Date.parse(row.expiry_date)<=Date.parse(start))throw new Error('到期时间必须晚于购买和入库时间')}
 async function request({method='get',path,params={},body=null}){
  const s=getState();method=method.toLowerCase();const user=params.user_id||body?.user_id||s.currentUser;const account=s.accounts[user];if(!account)throw new Error('本地测试账号不存在')
  const result=data=>({status:200,data:clone(data)})
  if(path==='/inventory'&&method==='get')return result(account.inventory)
  if(/^\/users\/[^/]+\/inventory\/freshness$/.test(path)){const owner=decodeURIComponent(path.split('/')[2]);if(owner!==user || !s.accounts[owner])throw new Error('账号不匹配');return result(freshness(account.inventory))}
  if(path==='/add-to-inventory'&&method==='post'){
   if(!Array.isArray(body)||!body.length)throw new Error('入库清单不能为空');const additions=body.map(row=>{const item={...row,id:id(),add_time:date(0),testCase:'unknown'};validateItem(item);return item});account.inventory.push(...additions);save(s);return result({status:'success'})
  }
  if(path.startsWith('/inventory/')&&['put','delete'].includes(method)){
   const index=account.inventory.findIndex(r=>String(r.id)===decodeURIComponent(path.split('/').at(-1)));if(index<0)throw new Error('库存记录不存在')
   if(method==='delete')account.inventory.splice(index,1);else{const allowed=['name','quantity','purchase_time','add_time','expiry_date','shelf_life','storage_type'];const changes=Object.fromEntries(Object.entries(body||{}).filter(([k])=>allowed.includes(k)));const edited={...account.inventory[index],...changes,testCase:'unknown'};validateItem(edited);account.inventory[index]=edited}save(s);return result({status:'success'})
  }
  if(path==='/consume-ingredients'){
   const names=new Set((body||[]).map(canonicalName));for(const name of names)if(account.inventory.filter(r=>canonicalName(r.name)===name).length!==1)return result({status:'error',message:'同名多批次或记录已变化，请人工核对'})
   account.inventory=account.inventory.map(r=>names.has(canonicalName(r.name))?{...r,quantity:r.quantity-1}:r).filter(r=>r.quantity>0);const lost=s.fault==='consume-lost';if(lost)s.fault='normal';save(s);if(lost)throw Object.assign(new Error('模拟响应丢失：本地库存已写入，请只核对结果'),{code:'ECONNABORTED'});return result({status:'success'})
  }
  if(path==='/recommend-recipe'){
   const fault=s.fault;if(['invalid-json','empty-recipe','business-error','timeout'].includes(fault)){s.fault='normal';save(s);if(fault==='timeout')throw Object.assign(new Error('本地模拟：菜谱请求超时'),{code:'ECONNABORTED'});return result(fault==='business-error'?{status:'error',message:'本地模拟：菜谱生成失败'}:{status:'success',recipe:fault==='invalid-json'?'{invalid':{dish_name:'空示例',steps:[]}})}
   const value=recipe(params.user_prompt||'');if(params.save_history===true||params.save_history==='true'){account.history.push({role:'user',content:params.user_prompt||''},{role:'assistant',content:'预置操作演练，不是 AI 生成。',recipe:value});account.history=account.history.slice(-40);save(s)}return result({status:'success',recipe:value})
  }
  if(path==='/recommendations')return result({status:account.inventory.length?'success':'inventory_required',algorithm_version:'local-fixture-v1',eligible_recipe_count:account.inventory.length?1:0,filtered_recipe_count:0,recommendations:account.inventory.length?[{rank:1,recipe_id:'local-guide',recipe_name:'本地操作演练（示例推荐）',total_score:0,component_scores:{I:null,F:null,P:null,W:null,M:null},effective_weights:{},missing_required_ingredients:['盐'],matched_ingredients:account.inventory.filter(r=>['西红柿','鸡蛋'].includes(r.name)).map(r=>r.name),reasons:['固定展示样例；未运行真实推荐算法，请从下方“咨询教程”进入操作演练。'],unavailable_components:['I','F','P','W','M']}]:[]})
  if(path==='/analyze-fridge')return result({status:'success',detected:[{name:'西红柿',quantity:1,freshness_detail:{fresh_score:null,component_scores:{T:null,S:null,V:null,H:null},reasons:['手动载入的识别示例，没有分析图片。'],data_quality_notes:['没有视觉推理结果'],disclaimer:'本地示例，非真实识别。'}}]})
  if(path==='/chat-history')return result(account.history)
  if(path==='/notifications')return result([])
  if(path==='/notifications/read')return result({status:'success'})
  if(path.startsWith('/user/')){const target=s.accounts[decodeURIComponent(path.split('/').at(-1))];if(!target)throw new Error('测试账号不存在');if(method==='put'){target.user.nickname=String(params.nickname||target.user.nickname);target.user.phone=String(params.phone||'');save(s)}if(method==='delete')throw new Error('本地测试版请使用重置示例数据');return result({status:'success',user:target.user})}
  if(path==='/tts')throw new Error('本地测试版不调用在线播报，请使用系统中文语音或文字按钮')
  throw new Error('本地测试版未提供此在线功能：'+path)
 }
 return {initialize,currentUser,selectUser,resetCurrent,setFault,request,getState}
}
