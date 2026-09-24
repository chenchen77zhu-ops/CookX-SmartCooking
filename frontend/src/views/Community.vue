<template>
 <main class="business-page"><header><button @click="router.push('/home')" aria-label="返回首页">‹</button><h1>一起晒菜</h1></header><p class="note">仅已加入内测的账号可见。发帖由你主动确认，个人库存、家庭信息和温度日志不会自动公开。</p><p v-if="error" role="alert" class="error">{{ error }}</p><section v-if="pending"><p>操作结果未确认，请用原凭证重试。</p><button @click="retry" :disabled="busy">重试原操作</button></section>
 <section><h2>{{ editing?'编辑我的作品':'分享今天的作品' }}</h2><label>作品文字<textarea v-model="text" maxlength="3000" placeholder="记录这次做饭的尝试与心得" /></label><label>添加菜品照片（最多 4 张）<input type="file" accept="image/jpeg,image/png,image/webp" :disabled="uploading||images.length>=4" @change="upload" /></label><div v-for="id in images" :key="id"><PrivateImage :id="id" /><button @click="images=images.filter(i=>i!==id)">移除此图片</button></div><label>关联独立菜谱副本（可选）<select aria-label="关联独立菜谱副本" v-model="copyId"><option value="">不关联菜谱</option><option v-for="c in copies" :key="c.id" :value="c.id">{{ c.recipe.dish_name }}</option></select></label><p class="note">没有结构化菜谱的作品只作交流展示，不能作为可执行教程。</p><div class="actions"><button class="primary" :disabled="busy||!!pending||uploading||!text.trim()" @click="publish">{{ editing?'确认更新作品':'确认发布给内测成员' }}</button><button v-if="editing" @click="resetEditor">取消编辑</button></div></section>
 <section><div class="actions"><button :disabled="busy" @click="mine=false;load()">大家的作品</button><button :disabled="busy" @click="mine=true;load()">我的作品</button><button :disabled="busy" @click="load">刷新作品</button></div></section>
 <section v-for="post in posts" :key="post.id" class="post-card"><b>{{ post.author_name }}</b><p class="note">{{ new Date(post.created_at).toLocaleString() }}<span v-if="post.hidden"> · 已隐藏：{{ post.moderation_reason }}</span></p><p style="white-space:pre-wrap">{{ post.text }}</p><PrivateImage v-for="id in post.image_ids" :key="id" :id="id" /><div v-if="post.recipe"><h2>{{ post.recipe.dish_name }}</h2><p class="note">{{ post.recipe.steps.length }} 个结构化步骤</p><button v-if="!post.hidden" :disabled="busy||!!pending" @click="replicate(post)">复刻关联菜谱</button></div><div class="actions"><template v-if="post.owner===user.id"><button :disabled="busy||!!pending" @click="edit(post)">编辑作品</button><button :disabled="busy||!!pending" @click="remove(post)">删除作品</button></template><button v-if="!post.hidden" :disabled="busy||!!pending" @click="report(post)">举报内容</button><button v-if="user.role==='admin'" :disabled="busy||!!pending" @click="moderate(post,!post.hidden)">{{ post.hidden?'恢复展示':'隐藏内容' }}</button></div></section>
 <section v-if="!posts.length"><p class="note">暂无作品。只会展示成员主动发布的内容。</p></section>
 <section v-if="user.role==='admin'"><h2>内容管理</h2><button @click="loadReports">查看举报和隐藏内容</button><div v-for="r in reports" :key="r.id" class="item"><p>{{ r.reason }}</p><small>作品 {{ r.post_id }}</small></div><div v-for="p in hiddenPosts" :key="p.id" class="item"><p>{{ p.text }}</p><p class="note">{{ p.moderation_reason }}</p><button :disabled="busy||!!pending" @click="moderate(p,false)">恢复展示</button></div></section>
 </main>
</template>
<script setup>
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import axios from 'axios'
import {API_BASE_URL} from '../config/backend'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
import PrivateImage from '../components/PrivateImage.vue'
import '../assets/business.css'
const router=useRouter(),user=JSON.parse(localStorage.getItem('user')||'{}'),posts=ref([]),copies=ref([]),mine=ref(false),text=ref(''),images=ref([]),copyId=ref(''),uploading=ref(false),editing=ref(null),reports=ref([]),hiddenPosts=ref([])
const {pending,busy,error,send,retry}=usePendingCommand('community',async result=>{if(result.copy){router.push('/recipes?copy='+result.copy.id);return}resetEditor();await load()})
function resetEditor(){editing.value=null;text.value='';images.value=[];copyId.value=''}
async function load(){posts.value=[];try{const result=await businessApi('/community/posts?mine='+mine.value);posts.value=result.items;copies.value=(await businessApi('/recipes/sources?kind=copy')).items}catch(e){error.value=apiError(e)}}
async function upload(event){const file=event.target.files?.[0];if(!file)return;uploading.value=true;try{if(file.size>5*1024*1024)throw new Error('图片不能超过 5 MB');const form=new FormData();form.append('file',file);const response=await axios.post(API_BASE_URL+'/v3/community/media',form,{timeout:20000});if(JSON.parse(localStorage.getItem('user')||'{}').id===user.id)images.value.push(response.data.id)}catch(e){error.value=apiError(e)}finally{uploading.value=false;event.target.value=''}}
async function publish(){try{await ElMessageBox.confirm('发布后所有内测成员都能查看这些图片、文字及关联菜谱。确认发布？','确认分享',{confirmButtonText:'发布',cancelButtonText:'取消'});await send('/community/posts'+(editing.value?'/'+editing.value.id:''),editing.value?'put':'post',{text:text.value,image_ids:images.value,recipe_copy_id:copyId.value||null,confirmed:true,...(editing.value?{expected_version:editing.value.version}:{})})}catch{}}
function edit(post){editing.value=post;text.value=post.text;images.value=[...post.image_ids];copyId.value='';if(post.recipe)error.value='编辑时请重新选择要关联的菜谱副本；不选择会移除原关联。';window.scrollTo({top:0,behavior:'smooth'})}
async function remove(post){try{await ElMessageBox.confirm('删除后作品将退出展示和榜单。','删除作品',{confirmButtonText:'删除',cancelButtonText:'取消'});await send('/community/posts/'+post.id,'delete',{expected_version:post.version})}catch{}}
async function report(post){try{const {value}=await ElMessageBox.prompt('说明需要处理的内容（至少 3 个字）','举报',{inputValidator:v=>v?.trim().length>=3||'请补充原因',confirmButtonText:'提交',cancelButtonText:'取消'});await send('/community/posts/'+post.id+'/reports','post',{reason:value})}catch{}}
async function moderate(post,hidden){try{const {value}=await ElMessageBox.prompt('记录处理原因（至少 3 个字）','内容管理',{inputValidator:v=>v?.trim().length>=3||'请补充原因',confirmButtonText:'确认',cancelButtonText:'取消'});await send('/community/posts/'+post.id+'/moderation','post',{hidden,reason:value,expected_version:post.version});await loadReports()}catch{}}
async function loadReports(){try{const result=await businessApi('/community/moderation');reports.value=result.items;hiddenPosts.value=result.posts}catch(e){error.value=apiError(e)}}
const replicate=post=>send('/recipes/copies','post',{source_type:'community',source_id:post.id,expected_source_version:String(post.version)})
onMounted(load)
</script>
