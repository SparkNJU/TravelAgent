import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import CommunityView from '../views/CommunityView.vue'
import AIPlanView from '../views/AIPlanView.vue'
import PublishView from '../views/PublishView.vue'
import ProfileView from '../views/ProfileView.vue'

const requireAuth = (to, from, next) => {
  if (localStorage.getItem('token')) next()
  else next('/login')
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/', name: 'discover', component: CommunityView, beforeEnter: requireAuth },
    { path: '/ai-plan', name: 'aiPlan', component: AIPlanView, beforeEnter: requireAuth },
    { path: '/publish', name: 'publish', component: PublishView, beforeEnter: requireAuth },
    { path: '/profile', name: 'profile', component: ProfileView, beforeEnter: requireAuth },
  ]
})

export default router
