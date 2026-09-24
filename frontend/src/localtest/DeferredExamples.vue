<template><main class="business-page"><header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>多人功能离线样例</h1></header>
<p class="notice">这是本机交互演练，所有数据标为示例。同一手机可切换 A/B 角色；不代表真实跨设备同步、FreshFusion 评估、CP-SAT 求解或学习训练。真实接口与算法请使用正式版连接局域网后端。</p>
<p>当前角色：{{ actor }} · 样例更改仅保存在本机</p>
<section><h2>1—3 家庭、采购与复刻</h2><p>示例家庭成员：本地测试 A / 本地测试 B。个人库存不会自动转入。</p><p>共享示例大米：{{ lab.rice }} 克</p><label>采购实际量（克）<input type="number" v-model.number="quantity" min="1" max="10000"></label><button @click="addPurchase">添加示例采购</button><p v-for="p in lab.shopping" :key="p.id">{{ p.quantity }} 克 · {{ p.state }} · {{ p.actor }} <button v-if="p.state==='待认领'" @click="changePurchase(p,'已认领')">认领</button><button v-if="p.state==='已认领'&&p.actor===actor" @click="changePurchase(p,'已购买')">标记购买</button><button v-if="p.state==='已购买'" @click="stock(p)">确认实际入库</button></p><button @click="copyRecipe">复刻操作演练到指导页</button><p>复刻只预览练习步骤，需主动启动，不自动计时或扣减。</p></section>
<section><h2>4—7 晒菜、评论、点赞与热门</h2><label>示例作品文字<textarea v-model="postText" maxlength="500"></textarea></label><button @click="publish">确认发布到本机样例</button><p>本机样例不上传图片或日志。正式版支持经验证的图片上传。</p><article v-for="p in ranked" :key="p.id"><h3>{{ p.text }}</h3><p>{{ p.author }} · 示例点赞 {{ p.likes.length }} · {{ p.comments.length }} 条单层评论</p><button @click="like(p)">{{ p.likes.includes(actor)?'取消示例赞':'点赞示例' }}</button><button v-if="p.author===actor" @click="remove(p)">删除样例</button><label>评论<input v-model="comments[p.id]" maxlength="200"></label><button @click="comment(p)">发表本机评论</button><p v-for="(c,i) in p.comments" :key="i">{{ c.author }}：{{ c.text }}</p></article><p>列表按近七天本机样例点赞人数排序，评论人数和发布时间处理同分。</p></section>
<section><h2>8 剩菜改造</h2><p>剩余原料：引用原批次；熟食：单独记录制作、储存与再加热。时间未知或异常的记录不能推荐。</p><label>样例条件<select v-model="leftover"><option value="unknown">制作时间未知</option><option value="abnormal">状态异常</option><option value="complete">信息完整示意</option></select></label><p>{{ leftover==='complete'?'仅展示信息已填写，不据此宣布可食用；请用正式后端评估并检查实物。':'样例拒绝推荐：信息不足或状态异常。' }}</p></section>
<section><h2>9—11 成长、挑战与徽章</h2><p>当前角色演练完成次数：{{ mine.completed }}</p><button @click="join">加入首次演练挑战</button><button @click="finish">记录一次示例完成</button><p>挑战：{{ mine.joined?(mine.completed>mine.baseline?'已完成':'进行中'):'未加入' }}</p><p>示例徽章：{{ mine.completed?'首次演练（仅一枚，不是专业技能认证）':'尚未获得' }}</p></section>
<section><h2>12 七日菜单</h2><p>下面是固定布局样例，不是求解结果，不验证预算、营养、忌口或库存约束。正式版提供独立 CP-SAT 求解与逐项核算。</p><button @click="showMenu=!showMenu">{{ showMenu?'收起':'显示' }}七日布局样例</button><p v-for="day in showMenu?7:0" :key="day">第 {{ day }} 天：午餐 · 主菜示意＋主食示意；晚餐 · 主菜示意＋主食示意</p></section>
<section><h2>13 个性化反馈</h2><label><input type="checkbox" v-model="mine.learning" @change="save">主动开启本机反馈演练</label><button :disabled="!mine.learning" @click="feedback(true)">喜欢示例菜谱</button><button :disabled="!mine.learning" @click="feedback(false)">不喜欢示例菜谱</button><button @click="clearFeedback">清除本机反馈</button><p>明确反馈：{{ mine.vote===null?'未填写':mine.vote?'喜欢':'不喜欢' }}。本页不训练模型，也不改变真实排序。</p></section>
<p v-if="message" role="status">{{ message }}</p></main></template>
<script setup>
import {ref,computed,onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import {localTestRuntime} from './bootstrap'
import '../assets/business.css'
const router=useRouter(),key='cookx:offline-deferred-examples:v1',actor=ref(localTestRuntime.currentUser().id),quantity=ref(100),postText=ref('今天的本机演练'),comments=ref({}),leftover=ref('unknown'),showMenu=ref(false),message=ref('')
const empty=()=>({rice:100,shopping:[],posts:[],accounts:{}})
let initial;try{initial=JSON.parse(localStorage.getItem(key)||'null')}catch{}const lab=ref(initial?.accounts?initial:empty())
const personal=()=>({completed:0,joined:false,baseline:0,learning:false,vote:null})
const mine=computed(()=>lab.value.accounts[actor.value]??(lab.value.accounts[actor.value]=personal()))
const ranked=computed(()=>[...lab.value.posts].filter(p=>Date.now()-p.at<=7*86400000).sort((a,b)=>b.likes.length-a.likes.length||new Set(b.comments.map(c=>c.author)).size-new Set(a.comments.map(c=>c.author)).size||b.at-a.at))
function save(){localStorage.setItem(key,JSON.stringify(lab.value))}
function addPurchase(){if(!Number.isFinite(quantity.value)||quantity.value<=0||quantity.value>10000){message.value='请填写 0—10000 克之间的正数';return}lab.value.shopping.push({id:crypto.randomUUID(),quantity:quantity.value,state:'待认领',actor:actor.value});save()}
function changePurchase(p,state){p.state=state;p.actor=actor.value;save()}
function stock(p){if(p.state!=='已购买')return;p.state='已入库';lab.value.rice+=p.quantity;save();message.value='仅本机样例库存已更新；重复点击不重复入库。'}
async function copyRecipe(){const response=await localTestRuntime.request({path:'/recommend-recipe',params:{user_prompt:'练习',save_history:true}});message.value='独立练习已写入当前角色的本机历史，请在指导页主动选择。';await router.push('/home?tab=AiChef')}
function publish(){if(!postText.value.trim())return;lab.value.posts.push({id:crypto.randomUUID(),text:postText.value.trim(),author:actor.value,at:Date.now(),likes:[],comments:[]});postText.value='';save()}
function like(p){p.likes=p.likes.includes(actor.value)?p.likes.filter(a=>a!==actor.value):[...p.likes,actor.value];save()}
function comment(p){const text=comments.value[p.id]?.trim();if(!text)return;p.comments.push({author:actor.value,text});comments.value[p.id]='';save()}
function remove(p){lab.value.posts=lab.value.posts.filter(r=>r.id!==p.id);save()}
function join(){if(!mine.value.joined){mine.value.joined=true;mine.value.baseline=mine.value.completed;save()}}
function finish(){mine.value.completed++;save()}
function feedback(liked){mine.value.vote=liked;save()}
function clearFeedback(){mine.value.vote=null;save()}
function changed(){actor.value=localTestRuntime.currentUser().id}window.addEventListener('storage',changed);onBeforeUnmount(()=>window.removeEventListener('storage',changed))
</script>
<style scoped>.notice{background:#fff3cf;padding:14px;border-radius:12px;line-height:1.8}article{border-top:1px solid #ddd;padding:12px 0}button{margin:5px}input[type=checkbox]{width:auto}</style>
