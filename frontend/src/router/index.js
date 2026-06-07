import { createRouter, createWebHistory } from 'vue-router'
import CommunityView from '../views/CommunityView.vue'
import AIPlanView from '../views/AIPlanView.vue'
import ProfileView from '../views/ProfileView.vue'
import ModelLeaderboardView from '../views/ModelLeaderboardView.vue'
import SettingsView from '../views/SettingsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'discover', component: CommunityView },
    { path: '/ai-plan', name: 'aiPlan', component: AIPlanView },
    { path: '/settings', name: 'settings', component: SettingsView },
    { path: '/skills', redirect: '/settings' },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/leaderboard', name: 'leaderboard', component: ModelLeaderboardView },
    { path: '/plan-workbench', name: 'planWorkbench', component: () => import('../views/PlanWorkbenchView.vue') },
    { path: '/login', redirect: '/' },
    { path: '/register', redirect: '/' },
  ]
})

export default router
