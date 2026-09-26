<template>
  <fieldset class="inventory-fields" :disabled="disabled">
    <label>名称 <input v-model="model.name" placeholder="如：牛肉" required /></label>
    <label>数量 <input v-model="model.quantity" type="number" min="1" step="1" required /></label>
    <label>储存方式 <select v-model="model.storage_type" aria-label="储存方式"><option value="">未填写</option><option>常温</option><option>冷藏</option><option>冷冻</option><option>阴凉干燥</option><option v-if="model.storage_type && !['常温','冷藏','冷冻','阴凉干燥'].includes(model.storage_type)" :value="model.storage_type">{{ model.storage_type }}（原值）</option></select></label>
    <label>保质期（天，可未知） <input v-model="model.shelf_life" type="number" step="any" placeholder="未知" /></label>
    <label>购买时间（本地，可未知） <input v-model="model.purchase_time" type="datetime-local" /></label>
    <label>入库时间（本地，可未知） <input v-model="model.add_time" type="datetime-local" /></label>
    <label>到期时间（本地，可未知） <input v-model="model.expiry_date" type="datetime-local" /></label>
    <p v-if="!editing">{{ LOCAL_TEST_MODE ? '购买/到期留空代表未知；入库时间留空时记录当前手机时间。' : '留空代表未知；入库时间由服务器记录。' }}</p>
    <template v-else>
      <p>原购买时间：{{ original.purchase_time || original.purchase_date || '未知' }}</p>
      <p>原到期时间：{{ original.expiry_date || '未知' }}</p>
      <p>{{ LOCAL_TEST_MODE ? '入库时间（本地原值）：' : '入库时间（服务端原值）：' }}{{ original.add_time || '未知' }}</p>
      <p>只保存修改过的字段；清空代表未知。旧字段会同步清理。</p>
    </template>
  </fieldset>
</template>
<script setup>
import {LOCAL_TEST_MODE} from '../config/buildMode.js'
const model = defineModel({ required: true })
defineProps({ editing: Boolean, disabled: Boolean, original: { type: Object, default: () => ({}) } })
</script>
<style scoped>
.inventory-fields { min-width: 0; margin: 0; padding: 0; border: 0; text-align: left; }
label { display: grid; gap: 6px; margin: 12px 0; color: var(--ck-text-2); font-size: 13px; }
input, select { box-sizing:border-box;min-height:42px;padding:0 12px;border:1px solid var(--ck-input-border);border-radius:12px;background:var(--ck-input-bg);color:var(--ck-text);font:inherit; width: 100%; min-width: 0; min-height: 46px; font-size: 15px; }
input:focus, select:focus { outline: none; border-color: var(--ck-heat); }
select { -webkit-appearance: none; appearance: none; }
select option { background: var(--ck-surface-strong); }
input[type=datetime-local] { color-scheme: dark; }
p { color: var(--ck-text-3); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
</style>
