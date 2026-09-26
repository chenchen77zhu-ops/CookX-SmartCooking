<template><div class="likes"><button :disabled="busy||!!pending||!loaded" :aria-pressed="value.liked" @click="send(root,'put',{liked:!value.liked,expected_version:value.version})">{{ value.liked?'取消点赞':'点赞' }} · {{ value.count }}</button><p v-if="error" class="error" role="alert">{{ error }}</p><button v-if="error&&!pending" @click="load">刷新点赞状态</button><button v-if="pending" :disabled="busy" @click="retry">核对原点赞操作</button></div></template>
<script setup>
import {ref,computed,onMounted} from 'vue'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
const props=defineProps({postId:String}),root=computed(()=>'/community/posts/'+props.postId+'/likes'),value=ref({count:0,liked:false,version:0}),loaded=ref(false)
const {pending,busy,error,send,retry}=usePendingCommand('like-'+props.postId,load)
async function load(){loaded.value=false;try{value.value=await businessApi(root.value);loaded.value=true;error.value=''}catch(e){error.value=apiError(e)}}
onMounted(load)
</script>
<style scoped>.likes{margin-top:12px}.likes button[aria-pressed=true]{border-color:rgba(255,138,61,.45);background:var(--ck-heat-soft);color:var(--ck-heat-text)}</style>
