<template>
  <aside class="sidebar">
    <div class="sidebar-logo" @click="$router.push('/')">
      <SvgIcon name="plane" :size="22" />
    </div>

    <nav class="sidebar-nav">
      <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
        <SvgIcon name="search" :size="20" />
        <span>发现</span>
      </router-link>
      <router-link to="/ai-plan" class="nav-item" :class="{ active: $route.path === '/ai-plan' }">
        <SvgIcon name="sparkles" :size="20" />
        <span>AI规划</span>
      </router-link>
      <router-link to="/publish" class="nav-item" :class="{ active: $route.path === '/publish' }">
        <SvgIcon name="plus" :size="20" />
        <span>发布</span>
      </router-link>
      <router-link to="/profile" class="nav-item" :class="{ active: $route.path === '/profile' }">
        <SvgIcon name="user" :size="20" />
        <span>我的</span>
      </router-link>
    </nav>

    <div class="sidebar-bottom">
      <button class="nav-item logout" @click="handleLogout">
        <SvgIcon name="logout" :size="18" />
        <span>退出</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { useRouter } from 'vue-router'
import SvgIcon from './SvgIcon.vue'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  router.push('/login')
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 72px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  z-index: 1000;
}

.sidebar-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  cursor: pointer;
  margin-bottom: 24px;
  flex-shrink: 0;
  transition: transform 0.2s;
}

.sidebar-logo:hover {
  transform: scale(1.08);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 56px;
  padding: 10px 0;
  border-radius: 12px;
  border: none;
  background: none;
  color: var(--color-hint);
  text-decoration: none;
  cursor: pointer;
  font-family: var(--font-family);
  transition: all 0.2s;
}

.nav-item span {
  font-size: 10px;
  font-weight: 500;
}

.nav-item:hover {
  color: var(--color-title);
  background: var(--color-card);
}

.nav-item.active {
  color: var(--color-red-light);
  background: rgba(230, 57, 70, 0.1);
}

.sidebar-bottom {
  flex-shrink: 0;
}

.logout:hover {
  color: var(--color-red-light);
}
</style>
