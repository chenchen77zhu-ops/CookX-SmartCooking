<template><details class="server-settings"><summary>局域网后端地址</summary><p>手机与运行后端的电脑需处于同一网络。填写电脑的局域网 IP 和端口，例如 http://192.168.1.20:8000。</p><label>后端地址<input v-model="address" inputmode="url" placeholder="http://192.168.1.20:8000"></label><p v-if="error" role="alert">{{ error }}</p><button type="button" @click="apply">保存地址并在本机退出账号</button><p>更换地址后重新登录。正式版使用你指定的后端，空值恢复安装包默认配置。</p></details></template>
<script setup>
import {ref} from 'vue'
import {SERVER_KEY,normalizeServerOrigin} from '../services/serverAddress'
import {clearSession} from '../services/authSession'
import {BACKEND_BASE_URL} from '../config/backend'
const address=ref(localStorage.getItem(SERVER_KEY)||BACKEND_BASE_URL),error=ref('')
function apply(){try{const origin=normalizeServerOrigin(address.value);if(origin)localStorage.setItem(SERVER_KEY,origin);else localStorage.removeItem(SERVER_KEY);clearSession();location.hash='#/login';location.reload()}catch(e){error.value=e.message}}
</script>
<style scoped>.server-settings{margin:16px 0 0;padding:0 14px;border:1px solid var(--ck-glass-border);border-radius:16px;background:var(--ck-input-bg);color:var(--ck-text-2);font-size:12.5px;line-height:1.7;text-align:left}.server-settings summary{display:flex;align-items:center;min-height:46px;color:var(--ck-text-2);font-size:13px;cursor:pointer}.server-settings p{margin:0 0 8px}.server-settings label{display:grid;gap:4px;color:var(--ck-text-3)}.server-settings input{box-sizing:border-box;min-height:42px;padding:0 12px;border:1px solid var(--ck-input-border);border-radius:12px;background:var(--ck-input-bg);color:var(--ck-text);font:inherit;width:100%;margin:4px 0 8px}.server-settings button{border:1px solid var(--ck-glass-border);border-radius:999px;background:var(--ck-fill-strong);color:var(--ck-text);font:inherit;font-size:13px;font-weight:600;min-height:38px;padding:0 14px;margin:4px 6px 4px 0;width:100%;margin:4px 0 8px}</style>
