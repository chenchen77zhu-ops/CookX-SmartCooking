<template>
  <el-alert v-if="message" :title="message" type="warning" :closable="false" />
  <div v-if="pending" class="pending-write">
    <p>上次写入结果待确认。再次点击保存只重新读取库存，不重复发送入库请求。请先核对库存，避免重复添加。</p>
    <el-button :disabled="disabled" @click="release">已核对库存，解除待确认</el-button>
  </div>
</template>
<script setup>
import { ElMessageBox } from 'element-plus'
import { dismissPendingWrite } from '../api/inventoryWrites.js'
const props = defineProps({ user: String, message: String, pending: Boolean, disabled: Boolean })
const emit = defineEmits(['released'])
async function release() {
  try {
    await ElMessageBox.confirm('仅解除本地防重复保护，不撤销或重发上次写入。确认已查看库存并核对数量和日期？', '核对保存结果', {confirmButtonText:'已核对',cancelButtonText:'继续核对'})
    dismissPendingWrite(props.user); emit('released')
  } catch { /* User cancelled. */ }
}
</script>
<style scoped>.pending-write { margin-top: 10px; color: var(--ck-warn-text); font-size: 13px; line-height: 1.6; }</style>
