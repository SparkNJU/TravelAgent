import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/overview' },
  {
    path: '/overview',
    name: 'Overview',
    component: () => import('../views/OverviewView.vue'),
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('../views/TasksView.vue'),
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('../views/RunHistoryView.vue'),
  },
  {
    path: '/runs/:runId(\\d+)',
    name: 'RunDetail',
    component: () => import('../views/RunDetailView.vue'),
    props: (route) => ({ runId: Number(route.params.runId) }),
  },
  {
    path: '/runs/:runId(\\d+)/monitor',
    name: 'Monitor',
    component: () => import('../views/MonitorView.vue'),
    props: (route) => ({ runId: Number(route.params.runId) }),
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('../views/ModelsView.vue'),
  },
  {
    path: '/datasets',
    name: 'Datasets',
    component: () => import('../views/DatasetsView.vue'),
  },
  {
    path: '/strategies',
    name: 'Strategies',
    component: () => import('../views/StrategiesView.vue'),
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
