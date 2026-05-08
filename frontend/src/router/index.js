import { createRouter, createWebHistory } from 'vue-router'
import CommunityView from '../views/CommunityView.vue'
import AIPlanView from '../views/AIPlanView.vue'
import ProfileView from '../views/ProfileView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'discover', component: CommunityView },
    { path: '/ai-plan', name: 'aiPlan', component: AIPlanView },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/login', redirect: '/' },
    { path: '/register', redirect: '/' },
  ]
})

export default router
