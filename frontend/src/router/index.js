import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import CommunityView from '../views/CommunityView.vue'
import AIPlanView from '../views/AIPlanView.vue'
import ProfileView from '../views/ProfileView.vue'
import ModelLeaderboardView from '../views/ModelLeaderboardView.vue'
import GlobeExploreView from '../views/GlobeExploreView.vue'
import SettingsView from '../views/SettingsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/discover', name: 'discover', component: CommunityView },
    { path: '/explore', name: 'explore', component: GlobeExploreView },
    { path: '/ai-plan', name: 'aiPlan', component: AIPlanView },
    { path: '/settings', name: 'settings', component: SettingsView },
    { path: '/skills', redirect: '/settings' },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/leaderboard', name: 'leaderboard', component: ModelLeaderboardView },
    { path: '/plan/workbench', name: 'planWorkbench', component: () => import('../views/PlanWorkbenchView.vue') },
    { path: '/plan-workbench', redirect: to => ({ path: '/plan/workbench', query: to.query }) },
    { path: '/login', redirect: '/' },
    { path: '/register', redirect: '/' },
  ]
})

export default router
