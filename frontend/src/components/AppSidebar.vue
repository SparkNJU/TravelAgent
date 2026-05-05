<template>
  <aside class="app-sidebar">
    <!-- Logo区域 -->
    <div class="logo-area">
      <span class="logo-icon">✈️</span>
      <div class="logo-text">
        <h1>旅行计划助手</h1>
        <p>智能旅行规划平台</p>
      </div>
    </div>

    <!-- 导航链接 -->
    <nav class="sidebar-nav">
      <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
        <span class="nav-icon">🏠</span>
        <span>首页</span>
      </router-link>
      <router-link to="/community" class="nav-item" :class="{ active: $route.path === '/community' }">
        <span class="nav-icon">👥</span>
        <span>社区</span>
      </router-link>
      <router-link to="/ai-plan" class="nav-item" :class="{ active: $route.path === '/ai-plan' }">
        <span class="nav-icon">🤖</span>
        <span>AI规划</span>
      </router-link>
    </nav>

    <!-- 底部用户操作 -->
    <div class="sidebar-footer">
      <button class="logout-btn" @click="handleLogout">
        <span class="nav-icon">🚪</span>
        <span>退出登录</span>
      </button>
    </div>
  </aside>
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
.app-sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 1000;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.05);
  margin-bottom: 24px;
}

.logo-icon {
  font-size: 32px;
}

.logo-text h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: white;
}

.logo-text p {
  margin: 2px 0 0;
  color: #94a3b8;
  font-size: 11px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 500;
  padding: 12px 14px;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.nav-item.active {
  color: white;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  font-weight: 600;
}

.nav-icon {
  font-size: 18px;
}

.sidebar-footer {
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  background: transparent;
  color: #94a3b8;
  border: none;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  color: #fca5a5;
  background: rgba(239, 68, 68, 0.1);
}

@media (max-width: 768px) {
  .app-sidebar {
    width: 64px;
    padding: 16px 8px;
  }
  
  .logo-text {
    display: none;
  }
  
  .nav-item span:not(.nav-icon),
  .logout-btn span:not(.nav-icon) {
    display: none;
  }
  
  .nav-item,
  .logout-btn {
    justify-content: center;
  }
}
</style>