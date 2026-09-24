<template><section aria-label="烹饪挑战"><h2>烹饪挑战</h2><p class="note">{{ note }}</p><p v-if="error" role="alert">{{ error }}</p><button v-if="pending" :disabled="busy" @click="retry">核对加入结果</button><button :disabled="busy" @click="load">刷新挑战进度</button><article v-for="c in items" :key="c.id" class="item"><h3>{{ c.name }}</h3><p>{{ c.description }}</p><p v-if="c.participation">{{ statuses[c.participation.status] }} · {{ Math.min(c.target,c.participation.progress) }} / {{ c.target }}<span v-if="c.participation.ends_at"> · 截止 {{ new Date(c.participation.ends_at).toLocaleString() }}</span></p><button v-else :disabled="busy||!!pending" @click="join(c)">加入{{ c.name }}</button></article></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import {businessApi,apiError} from '../api/business'
import {usePendingCommand} from '../services/businessCommands'
const items=ref([]),note=ref(''),statuses={active:'进行中',completed:'已完成',ended:'已结束'}
const {busy,error,pending,send,retry}=usePendingCommand('challenge',load)
async function load(){try{const result=await businessApi('/challenges');items.value=result.items;note.value=result.note}catch(e){error.value=apiError(e)}}
const join=c=>send('/challenges/'+c.id+'/join','post',{})
onMounted(load)
</script>
