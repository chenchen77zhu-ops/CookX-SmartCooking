import { reactive } from 'vue'

// 跨页面的界面状态：沉浸式烹饪时隐藏底栏
export const uiState = reactive({ immersive: false })
