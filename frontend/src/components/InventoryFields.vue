<template>
  <fieldset class="inventory-fields" :disabled="disabled">
    <label>名称 <input v-model="model.name" placeholder="如：牛肉" required /></label>
    <label>数量 <input v-model="model.quantity" type="number" min="1" step="1" required /></label>
    <label>储存方式 <select v-model="model.storage_type" aria-label="储存方式"><option value="">未填写</option><option>常温</option><option>冷藏</option><option>冷冻</option><option v-if="model.storage_type && !['常温','冷藏','冷冻'].includes(model.storage_type)" :value="model.storage_type">{{ model.storage_type }}（原值）</option></select></label>
    <label>保质期（天，可未知） <input v-model="model.shelf_life" type="number" step="any" placeholder="未知" /></label>
    <template v-if="!editing">
      <label>购买时间（本地，可未知） <input v-model="model.purchase_time" type="datetime-local" /></label>
      <label>到期时间（本地，可未知） <input v-model="model.expiry_date" type="datetime-local" /></label>
      <p>留空代表未知；入库时间由服务器记录。</p>
    </template>
    <template v-else>
      <p>购买时间：{{ original.purchase_time || original.purchase_date || '未知' }}</p>
      <p>到期时间：{{ original.expiry_date || '未知' }}</p>
      <p>入库时间（服务端原值）：{{ original.add_time || '未知' }}</p>
      <p>本批暂不开放日期修改、字段清空与小数保质期编辑；未修改字段保持原值。</p>
    </template>
  </fieldset>
</template>
<script setup>
const model = defineModel({ required: true })
defineProps({ editing: Boolean, disabled: Boolean, original: { type: Object, default: () => ({}) } })
</script>
<style scoped>
.inventory-fields { border: 0; padding: 0; margin: 0; min-width: 0; text-align: left; }
label { display: grid; gap: 6px; margin: 12px 0; color: #315340; font-size: 14px; }
input,select { box-sizing: border-box; width: 100%; min-width: 0; padding: 11px; border: 1px solid #c9d8cf; border-radius: 8px; background: white; color: #243f31; font: inherit; }
p { font-size: 12px; color: #66746b; overflow-wrap: anywhere; line-height: 1.6; }
</style>
