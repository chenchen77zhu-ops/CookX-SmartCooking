<template><section aria-label="我的徽章"><h2>我的徽章</h2><p class="note">{{ note }}</p><p v-if="error" role="alert">{{ error }}</p><button @click="load">刷新徽章</button><article v-for="b in items" :key="b.id" class="item"><h3>{{ b.award?'✓':'○' }} {{ b.name }}</h3><p>{{ b.description }}</p><p>{{ b.award?'已获得 · '+new Date(b.award.awarded_at).toLocaleString():'尚未获得' }}</p><details v-if="b.award"><summary>查看获奖依据</summary><p class="note">规则版本 {{ b.award.rule_version }} · 服务端记录 {{ b.award.evidence_ids.length }} 项</p></details></article></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import {businessApi,apiError} from '../api/business'
const items=ref([]),note=ref(''),error=ref('')
async function load(){try{const result=await businessApi('/badges');items.value=result.items;note.value=result.note}catch(e){error.value=apiError(e)}}
onMounted(load)
</script>
