<template>
  <div class="card">
    <h2>📦 冰箱当前库存</h2>
    <el-table :data="inventory" style="width: 100%">
      <el-table-column prop="name" label="食材名称" />
      <el-table-column prop="add_time" label="入库时间" width="180" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button type="danger" size="small" @click="removeItem(scope.row.id)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="inventory.length === 0" description="冰箱空空如也" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import {deleteInventory} from '../api/inventoryWrites'
import { API_BASE_URL } from '@/config/backend'

const inventory = ref([])
const userId = JSON.parse(localStorage.getItem('user') || '{}').id

const fetchInventory = async () => {
  if (!userId) return
  const res = await axios.get(`${API_BASE_URL}/inventory`, { params: { user_id: userId } })
  inventory.value = res.data
}

const removeItem = async (id) => {
  if (!userId) return
  await deleteInventory(userId,id,inventory.value.find(r=>r.id===id)?._revision)
  fetchInventory() // 刷新列表
}

onMounted(fetchInventory)
</script>
