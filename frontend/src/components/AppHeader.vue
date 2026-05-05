<template>
  <header class="app-header">
    <div class="header-content">
      <!-- Logo区域 -->
      <div class="logo-area">
        <span class="logo-icon">✈️</span>
        <span class="logo-text">旅行计划助手</span>
      </div>

      <!-- 导航链接 -->
      <nav class="header-nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">首页</router-link>
        <router-link to="/community" class="nav-item" :class="{ active: $route.path === '/community' }">社区</router-link>
        <router-link to="/ai-plan" class="nav-item" :class="{ active: $route.path === '/ai-plan' }">AI规划</router-link>
      </nav>

      <!-- 用户操作区域 -->
      <div class="header-actions">
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  width: 100%;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 14px 24px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 60px;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 28px;
}

.logo-area h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-area p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 12px;
}

.header-nav {
  display: flex;
  gap: 32px;
}

.nav-item {
  text-decoration: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
}

.nav-item.active {
  color: white;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-right: 8px;
}

.greeting {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}

.user-role {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
  padding: 2px 8px;
  border-radius: 999px;
}

.logout-btn {
  background: transparent;
  color: #64748b;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 16px;
  font-weight: 500;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

@media (max-width: 900px) {
  .header-content {
    flex-wrap: wrap;
    padding: 12px 16px;
  }

  .header-nav {
    order: 3;
    width: 100%;
    justify-content: center;
    gap: 16px;
    padding-top: 12px;
    border-top: 1px solid rgba(148, 163, 184, 0.1);
  }

  .user-info {
    align-items: center;
    margin-right: 0;
  }

  .logo-area h1 {
    font-size: 18px;
  }
}
</style>