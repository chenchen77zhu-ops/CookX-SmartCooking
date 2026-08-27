<template>
  <div class="card">
    <h2>📸 识别食材入库</h2>
    <el-upload
      class="upload-demo"
      drag
      :action="`${API_BASE_URL}/analyze-fridge`"
      :on-success="handleSuccess"
      :show-file-list="false"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">将食材照片拖到此处，或 <em>点击上传</em></div>
    </el-upload>

    <div v-if="detectedItems.length > 0" style="margin-top: 20px;">
      <h3>✅ 识别成功并自动入库：</h3>
      <el-tag v-for="item in detectedItems" :key="item" style="margin-right: 10px">{{ item }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { API_BASE_URL } from '@/config/backend'

const detectedItems = ref([])

const handleSuccess = (response) => {
  detectedItems.value = response.detected
  // 提醒用户识别成功
  alert('识别到的食材已自动加入库存！')
}
</script>
