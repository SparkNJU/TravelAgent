<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="logo">
        <SvgIcon name="plane" :size="20" />
        <span class="logo-text">旅行计划助手</span>
      </div>
      <div class="header-right">
        <span class="username">{{ username }}</span>
        <button class="logout-btn" @click="handleLogout">
          <SvgIcon name="logout" :size="14" />
          退出
        </button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from './SvgIcon.vue'

const router = useRouter()
const username = ref(localStorage.getItem('username') || '用户')

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: var(--header-height);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-inner {
  max-width: var(--max-width);
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-red-light);
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.username {
  font-size: 12px;
  color: var(--color-hint);
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 5px 12px;
  font-size: 12px;
  color: var(--color-hint);
  background: none;
  cursor: pointer;
  font-family: var(--font-family);
  transition: all 0.2s;
}

.logout-btn:hover {
  color: var(--color-red-light);
  border-color: var(--color-red);
  background: rgba(230, 57, 70, 0.08);
}

@media (max-width: 768px) {
  .username { display: none; }
}
</style>
