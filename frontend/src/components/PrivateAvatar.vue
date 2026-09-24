<template><el-avatar :size="size" :src="url" :title="failed?'头像读取失败，可重新上传':''"><el-icon><UserFilled /></el-icon></el-avatar></template>
<script setup>
import {ref,watch,onBeforeUnmount} from 'vue'
import {UserFilled} from '@element-plus/icons-vue'
import axios from 'axios'
import {API_BASE_URL} from '../config/backend'
const props=defineProps({path:String,size:Number}),url=ref(''),failed=ref(false);let revision=0
watch(()=>props.path,async path=>{const own=++revision;if(url.value)URL.revokeObjectURL(url.value);url.value='';failed.value=false;if(!path)return
  const suffix=path.startsWith('/api/v3/profile/media/')?path.slice(4):path.startsWith('/static/uploads/avatars/')?'/v3/profile/avatar/legacy':null
  if(!suffix){failed.value=true;return}
  try{const response=await axios.get(API_BASE_URL+suffix,{responseType:'blob',timeout:15000});if(own===revision)url.value=URL.createObjectURL(response.data)}catch{if(own===revision)failed.value=true}
},{immediate:true})
onBeforeUnmount(()=>{revision++;if(url.value)URL.revokeObjectURL(url.value)})
</script>
