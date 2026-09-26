import './services/authSession.js'
import { createApp } from 'vue'
import App from './App.vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/theme.css'
import router from './router'
import { applyTheme } from './services/theme.js'

applyTheme()

const app = createApp(App)
app.use(ElementPlus)
app.use(router)
app.mount('#app')
