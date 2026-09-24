<template>
 <aside class="local-test-panel" aria-label="本地测试工具">
  <details><summary>本地测试版 · 无服务器 / 数据库 · 展开测试工具</summary>
   <p>数据只存于本机。识别、鲜度、推荐和多人场景为固定示例，不运行服务端菜单或学习算法，也不跨设备同步；新增或编辑食材不生成鲜度评分。温度模块保持原实现。</p>
   <label>测试账号 <select aria-label="测试账号" :value="user" @change="switchUser($event.target.value)" :disabled="busy"><option v-for="u in TEST_USERS" :key="u.id" :value="u.id">{{ u.nickname }}</option></select></label>
   <button :disabled="busy" @click="recognize">载入识别示例</button><button :disabled="busy" @click="router.push('/home?tab=AiChef')">进入烹饪演练</button>
   <label>下一次请求场景 <select aria-label="下一次请求场景" v-model="fault" @change="applyFault"><option value="normal">正常示例</option><option value="invalid-json">菜谱 JSON 无效</option><option value="empty-recipe">菜谱步骤为空</option><option value="business-error">菜谱业务失败</option><option value="timeout">菜谱超时</option><option value="consume-lost">扣减成功但响应丢失</option></select></label>
   <button @click="router.push('/offline-examples')">多人功能离线样例</button>
   <button :disabled="busy" @click="reset">重置当前测试账号</button>
   <p>异常场景只生效一次。语音依赖手机系统服务与中文语音包；不可用时使用文字指令和按钮。无需点火即可完成演练。</p>
   <p v-if="message" role="status">{{ message }}</p>
  </details>
 </aside>
</template>
<script setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessageBox} from 'element-plus'
import {localTestRuntime} from './bootstrap.js'
import {TEST_USERS} from './runtime.js'
import {loadRecognitionExample} from './helpers.js'
import {stopListening,stopSystemSpeech} from '../services/systemVoice.js'
import {setReminders} from '../services/cookingNotifications.js'
const router=useRouter(),user=ref(localTestRuntime.currentUser().id),fault=ref(localTestRuntime.getState().fault),busy=ref(false),message=ref('')
async function stop(){await setReminders(user.value,false);await stopListening();await stopSystemSpeech()}
async function switchUser(next){busy.value=true;try{await stop();localTestRuntime.selectUser(next);user.value=next;window.dispatchEvent(new Event('storage'));await router.push('/home');message.value='已切换本地账号；会话需要手动恢复。'}catch(e){message.value=e.message}finally{busy.value=false}}
async function recognize(){try{await loadRecognitionExample(router)}catch(e){message.value=e.message}}
function applyFault(){try{localTestRuntime.setFault(fault.value);message.value='已设置场景，只影响对应类型的下一次请求。'}catch(e){message.value=e.message}}
async function reset(){try{await ElMessageBox.confirm('清除当前测试账号的库存、草稿与烹饪记录并重建示例？另一个测试账号保持不变。','重置示例',{confirmButtonText:'确认重置',cancelButtonText:'取消'});busy.value=true;await stop();localTestRuntime.resetCurrent();window.location.hash='#/home';window.location.reload()}catch(e){if(e instanceof Error)message.value=e.message}finally{busy.value=false}}
</script>
<style scoped>
.local-test-panel{background:#fff3cf;color:#55451f;padding:10px 16px;font-size:13px;border-bottom:1px solid #e8d591;overflow-wrap:anywhere}.local-test-panel summary{cursor:pointer;font-weight:600;min-height:24px}.local-test-panel button,.local-test-panel select{min-height:40px;max-width:100%;border:1px solid #c6bc9d;background:#fff;border-radius:8px;padding:7px;margin:4px;font:inherit}.local-test-panel label{display:inline-block}.local-test-panel p{line-height:1.6}
</style>
