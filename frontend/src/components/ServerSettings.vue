<template><details class="server-settings"><summary>局域网后端地址</summary><p>手机与运行后端的电脑需处于同一网络。填写电脑的局域网 IP 和端口，例如 http://192.168.1.20:8000。</p><label>后端地址<input v-model="address" inputmode="url" placeholder="http://192.168.1.20:8000"></label><p v-if="error" role="alert">{{ error }}</p><button type="button" @click="apply">保存地址并在本机退出账号</button><p>更换地址后重新登录。正式版使用你指定的后端，空值恢复安装包默认配置。</p></details></template>
<script setup>
import {ref} from 'vue'
import {SERVER_KEY,normalizeServerOrigin} from '../services/serverAddress'
import {clearSession} from '../services/authSession'
import {BACKEND_BASE_URL} from '../config/backend'
const address=ref(localStorage.getItem(SERVER_KEY)||BACKEND_BASE_URL),error=ref('')
function apply(){try{const origin=normalizeServerOrigin(address.value);if(origin)localStorage.setItem(SERVER_KEY,origin);else localStorage.removeItem(SERVER_KEY);clearSession();location.hash='#/login';location.reload()}catch(e){error.value=e.message}}
</script>
<style scoped>.server-settings{margin:16px 0;padding:12px;background:#f2f7f3;border:1px solid #d9e5dc;border-radius:12px;color:#31473b;font-size:12px;line-height:1.7}.server-settings input{box-sizing:border-box;width:100%;padding:10px;margin:8px 0;border:1px solid #bfd0c4;border-radius:8px}.server-settings button{padding:9px;background:#e5f0e9;border:1px solid #bfd0c4;border-radius:8px}</style>
