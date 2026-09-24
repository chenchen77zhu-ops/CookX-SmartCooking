<template><img v-if="url" :src="url" :alt="alt" class="private-image" /><small v-else>{{ failed?'图片读取失败':'图片加载中…' }}</small></template>
<script setup>
import {ref,watch,onBeforeUnmount} from 'vue'
import axios from 'axios'
import {API_BASE_URL} from '../config/backend'
const props=defineProps({id:String,alt:{type:String,default:'成员主动分享的菜品图片'}}),url=ref(''),failed=ref(false);let revision=0
watch(()=>props.id,async id=>{const own=++revision;if(url.value)URL.revokeObjectURL(url.value);url.value='';failed.value=false;try{const response=await axios.get(`${API_BASE_URL}/v3/community/media/${encodeURIComponent(id)}`,{responseType:'blob',timeout:15000});if(own===revision)url.value=URL.createObjectURL(response.data)}catch{if(own===revision)failed.value=true}},{immediate:true})
onBeforeUnmount(()=>{revision++;if(url.value)URL.revokeObjectURL(url.value)})
</script>
<style scoped>.private-image{display:block;width:100%;max-height:350px;object-fit:cover;border-radius:14px;margin:10px 0}</style>
