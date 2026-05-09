import './assets/variables.css'
import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Apply theme before first paint to prevent flash
import { useTheme } from './composables/useTheme'
useTheme()

const app = createApp(App)

app.use(router)

app.mount('#app')
