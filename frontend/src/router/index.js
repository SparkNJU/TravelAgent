import { createRouter, createWebHistory } from 'vue-router'

// 首页首屏加载，保持静态导入；其余视图按需懒加载
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/discover', name: 'discover', component: () => import('../views/CommunityView.vue') },
    { path: '/explore', name: 'explore', component: () => import('../views/GlobeExploreView.vue') },
    { path: '/ai-plan', name: 'aiPlan', component: () => import('../views/AIPlanView.vue') },
    { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue') },
    { path: '/skills', redirect: '/settings' },
    { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
    { path: '/leaderboard', name: 'leaderboard', component: () => import('../views/ModelLeaderboardView.vue') },
    { path: '/plan/workbench', name: 'planWorkbench', component: () => import('../views/PlanWorkbenchView.vue') },
    { path: '/plan-workbench', redirect: to => ({ path: '/plan/workbench', query: to.query }) },
    { path: '/login', redirect: '/' },
    { path: '/register', redirect: '/' },
  ]
})

export default router
