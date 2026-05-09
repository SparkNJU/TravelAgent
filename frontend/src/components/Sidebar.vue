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
      <router-link to="/profile" class="nav-item" :class="{ active: $route.path === '/profile' }">
        <SvgIcon name="user" :size="20" />
        <span>我的</span>
      </router-link>
    </nav>

    <div class="sidebar-bottom">
      <button class="nav-item theme-toggle" @click="toggleTheme" :title="theme === 'light' ? '切换深色模式' : '切换浅色模式'">
        <SvgIcon :name="theme === 'light' ? 'moon' : 'sun'" :size="18" />
        <span>{{ theme === 'light' ? '深色' : '浅色' }}</span>
      </button>
      <template v-if="isLoggedIn">
        <img v-if="avatar" :src="avatar" class="user-avatar-img" :title="username" />
        <div v-else class="user-avatar" :title="username">{{ avatarLetter }}</div>
        <button class="nav-item logout" @click="handleLogout">
          <SvgIcon name="logout" :size="18" />
          <span>退出</span>
        </button>
      </template>
      <button v-else class="nav-item login-btn" @click="showLogin">
        <SvgIcon name="user" :size="18" />
        <span>登录</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { inject, computed } from 'vue'
import SvgIcon from './SvgIcon.vue'
import { useAuth } from '../composables/useAuth'
import { useTheme } from '../composables/useTheme'

const { isLoggedIn, username, avatar, logout } = useAuth()
const { theme, toggleTheme } = useTheme()
const showLogin = inject('showLoginModal')

const avatarLetter = computed(() => {
  const name = username.value || 'U'
  return name.charAt(0).toUpperCase()
})

const handleLogout = () => {
  logout()
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
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 700;
}

.user-avatar-img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.logout:hover {
  color: var(--color-red-light);
}

.login-btn {
  color: var(--color-red-light);
}
.login-btn:hover {
  background: rgba(230, 57, 70, 0.1);
  color: var(--color-red-light);
}

.theme-toggle {
  margin-bottom: 4px;
}
</style>
