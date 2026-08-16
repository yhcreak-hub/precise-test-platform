import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

// 顺序：pinia 先于 router（路由守卫中会使用 user store）
app.use(createPinia())
app.use(router)
// Element Plus 全量引入 + 中文语言包（简单起见，后续可按需引入优化体积）
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
