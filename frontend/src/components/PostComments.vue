<template><div class="comments"><button @click="load" :disabled="busy">{{ opened?'刷新评论':'查看评论' }}</button><template v-if="opened"><p v-if="error" role="alert" class="error">{{ error }}</p><button v-if="pending" @click="retry" :disabled="busy">重试原评论操作</button><article v-for="c in items" :key="c.id" class="item"><b>{{ c.author_name }}</b><p>{{ c.text }}</p><small>{{ new Date(c.created_at).toLocaleString() }}</small><button v-if="c.owner===user.id||user.role==='admin'" :disabled="busy||!!pending" @click="remove(c)">删除评论</button></article><label>写一条评论<textarea v-model="text" maxlength="1000" /></label><button :disabled="busy||!!pending||!text.trim()" @click="send(root,'post',{text})">发表评论</button></template></div></template>
<script setup>
import {ref,computed} from 'vue'
import {ElMessageBox} from 'element-plus'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
const props=defineProps({postId:String}),user=JSON.parse(localStorage.getItem('user')||'{}'),opened=ref(false),items=ref([]),text=ref('')
const root=computed(()=>'/community/posts/'+props.postId+'/comments')
const {pending,busy,error,send,retry}=usePendingCommand('comments-'+props.postId,async()=>{text.value='';await load()})
async function load(){opened.value=true;items.value=[];try{items.value=(await businessApi(root.value)).items}catch(e){error.value=apiError(e)}}
async function remove(c){try{await ElMessageBox.confirm('确认删除这条评论？','删除评论',{confirmButtonText:'删除',cancelButtonText:'取消'});await send(root.value+'/'+c.id,'delete',{expected_version:c.version})}catch{}}
</script>
<style scoped>.comments{margin-top:14px;padding-top:12px;border-top:1px solid var(--ck-hairline)}.comments p{white-space:pre-wrap;overflow-wrap:anywhere}.comments small{display:block}.comments article.item{padding:10px 12px;margin:8px 0;border:0;border-radius:14px;background:var(--ck-fill)}</style>
