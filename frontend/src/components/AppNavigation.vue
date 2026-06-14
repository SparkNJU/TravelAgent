<template>
  <aside class="app-sidebar" aria-label="主导航">
    <button class="sidebar-brand" title="返回首页" @click="goHome">
      <img src="/logo.svg" alt="TravelMind" class="brand-logo" />
      <span>TravelMind</span>
    </button>

    <nav class="sidebar-nav">
      <router-link
        to="/discover"
        class="sidebar-link"
        :class="{ active: isDiscoverActive }"
      >
        <SvgIcon name="search" :size="22" />
        <span>发现</span>
      </router-link>

      <router-link
        to="/ai-plan"
        class="sidebar-link"
        :class="{ active: isActive('/ai-plan') }"
      >
        <SvgIcon name="sparkles" :size="22" />
        <span>AI规划</span>
      </router-link>

      <router-link
        :to="workbenchLink"
        class="sidebar-link"
        :class="{ active: isActive('/plan/workbench') || isActive('/plan-workbench') }"
      >
        <SvgIcon name="map-pin" :size="22" />
        <span>工作台</span>
      </router-link>

      <router-link
        to="/leaderboard"
        class="sidebar-link"
        :class="{ active: isActive('/leaderboard') }"
      >
        <SvgIcon name="trophy" :size="22" />
        <span>模型排行</span>
      </router-link>

      <router-link
        to="/settings"
        class="sidebar-link"
        :class="{ active: isActive('/settings') }"
      >
        <SvgIcon name="settings" :size="22" />
        <span>模型个性化</span>
      </router-link>
    </nav>

    <div class="sidebar-bottom">
      <button class="identity-card" :class="{ active: isActive('/profile') }" @click="goProfile">
        <span class="avatar-shell">
          <img v-if="avatar" :src="avatar" alt="" />
          <span v-else>{{ avatarLetter }}</span>
        </span>
        <span class="identity-copy">
          <strong>{{ displayName }}</strong>
          <small>{{ isLoggedIn ? '我的主页' : '点击登录' }}</small>
        </span>
      </button>

      <button class="theme-row" :title="themeTitle" @click="toggleTheme">
        <SvgIcon :name="theme === 'light' ? 'moon' : 'sun'" :size="20" />
        <span>{{ theme === 'light' ? '深色模式' : '浅色模式' }}</span>
      </button>

      <button class="sidebar-action danger" @click="handleLogout">
        <SvgIcon name="logout" :size="20" />
        <span>{{ isLoggedIn ? '退出登录' : '登录' }}</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SvgIcon from './SvgIcon.vue'
import { useAuth } from '../composables/useAuth'
import { useTheme } from '../composables/useTheme'

const route = useRoute()
const router = useRouter()
const showLogin = inject('showLoginModal')
const { isLoggedIn, username, avatar, logout } = useAuth()
const { theme, toggleTheme } = useTheme()

const isDiscoverActive = computed(() =>
  route.path === '/' || (route.path.startsWith('/discover') && route.query.publish !== '1'),
)

// Smart workbench link: pass planId if available so PlanWorkbenchView loads instantly
const workbenchLink = computed(() => {
  // If already on workbench, keep current query
  if (route.path.startsWith('/plan/workbench') && route.query.planId) {
    return { path: '/plan/workbench', query: route.query }
  }
  // Check localStorage for a conversation with a saved workbenchPlanId
  try {
    const raw = localStorage.getItem('travel_conversations')
    if (raw) {
      const convs = JSON.parse(raw)
      const withPlan = convs.find(c => c.result?.workbenchPlanId)
      if (withPlan) {
        return { path: '/plan/workbench', query: { planId: withPlan.result.workbenchPlanId } }
      }
    }
  } catch {}
  return '/plan/workbench'
})
const displayName = computed(() => (isLoggedIn.value ? username.value || '旅行者' : '未登录'))
const avatarLetter = computed(() => {
  const name = displayName.value || 'U'
  return name.charAt(0).toUpperCase()
})
const themeTitle = computed(() => theme.value === 'light' ? '切换深色模式' : '切换浅色模式')

function isActive(path) {
  return route.path.startsWith(path)
}

function goHome() {
  router.push('/')
}

function goProfile() {
  if (!isLoggedIn.value) {
    showLogin?.()
    return
  }
  router.push('/profile')
}

function handleLogout() {
  if (!isLoggedIn.value) {
    showLogin?.()
    return
  }
  logout()
  router.push('/')
}
</script>

<style scoped>
.app-sidebar {
  position: relative;
  z-index: 1200;
  display: flex;
  width: var(--sidebar-width);
  height: 100vh;
  flex-shrink: 0;
  flex-direction: column;
  padding: 28px 20px 18px;
  border-right: 1px solid rgba(17, 24, 39, 0.08);
  background: color-mix(in srgb, var(--color-card) 96%, transparent);
  box-shadow: 10px 0 40px rgba(17, 24, 39, 0.04);
}

.sidebar-brand,
.sidebar-link,
.identity-card,
.theme-row,
.sidebar-action {
  font-family: var(--font-family);
}

.sidebar-brand {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 48px;
  border: 0;
  background: transparent;
  color: var(--color-title);
  font-size: 18px;
  font-weight: 950;
  letter-spacing: 0;
}

.brand-logo {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 14px;
  object-fit: cover;
}

.sidebar-nav {
  display: grid;
  gap: 10px;
  margin-top: 44px;
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 50px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-title);
  text-align: left;
  text-decoration: none;
  font-size: 16px;
  font-weight: 900;
  transition:
    color 0.16s ease,
    background 0.16s ease,
    transform 0.16s ease;
}

.sidebar-link:hover {
  background: var(--color-card-hover);
  transform: translateX(2px);
}

.sidebar-link.active {
  background: var(--color-soft-red);
  color: var(--color-red);
}

.sidebar-bottom {
  display: grid;
  gap: 10px;
  margin-top: auto;
  padding-top: 18px;
}

.identity-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 58px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  color: var(--color-title);
  text-align: left;
}

.identity-card.active {
  border-color: rgba(255, 36, 66, 0.28);
  background: var(--color-soft-red);
}

.identity-card:hover,
.theme-row:hover,
.sidebar-action:hover {
  border-color: rgba(255, 36, 66, 0.28);
  background: var(--color-soft-red);
}

.avatar-shell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  background: var(--gradient-brand);
  color: #ffffff;
  font-size: 14px;
  font-weight: 950;
}

.avatar-shell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.identity-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.identity-copy strong,
.identity-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-copy strong {
  color: var(--color-title);
  font-size: 13px;
  font-weight: 950;
}

.identity-copy small {
  color: var(--color-hint);
  font-size: 11px;
  font-weight: 800;
}

.theme-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 42px;
  padding: 0 13px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 900;
}

.theme-row.active {
  background: var(--color-soft-red);
  color: var(--color-red);
  border-color: rgba(255, 36, 66, 0.22);
}

.sidebar-action {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 42px;
  padding: 0 13px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 900;
  text-align: left;
}

.sidebar-action.active {
  border-color: rgba(255, 36, 66, 0.22);
  background: var(--color-soft-red);
  color: var(--color-red);
}

.sidebar-action.danger {
  color: #b91c1c;
}

:root[data-theme="dark"] .app-sidebar {
  border-right-color: rgba(255, 255, 255, 0.08);
  background: rgba(10, 10, 10, 0.96);
  box-shadow: 10px 0 40px rgba(0, 0, 0, 0.35);
}

:root[data-theme="dark"] .identity-card,
:root[data-theme="dark"] .theme-row,
:root[data-theme="dark"] .sidebar-action {
  background: var(--color-card);
  border-color: var(--color-border);
}

:root[data-theme="dark"] .sidebar-action.danger {
  color: #ff7a8d;
}

@media (max-width: 860px) {
  .app-sidebar {
    width: var(--sidebar-width-compact);
    padding: 16px 10px 12px;
    align-items: center;
  }

  .sidebar-brand {
    justify-content: center;
  }

  .sidebar-brand span,
  .sidebar-link span,
  .identity-copy,
  .theme-row span,
  .sidebar-action span {
    display: none;
  }

  .brand-logo {
    width: 42px;
    height: 42px;
  }

  .sidebar-nav {
    width: 100%;
    gap: 8px;
    margin-top: 34px;
  }

  .sidebar-link {
    justify-content: center;
    min-height: 52px;
    padding: 0;
    border-radius: 18px;
  }

  .sidebar-link:hover {
    transform: none;
  }

  .sidebar-bottom {
    width: 100%;
    justify-items: center;
  }

  .identity-card,
  .theme-row,
  .sidebar-action {
    justify-content: center;
    width: 52px;
    min-height: 52px;
    padding: 0;
    border-radius: 18px;
  }

  .avatar-shell {
    width: 34px;
    height: 34px;
  }
}
</style>
