<template>
  <div class="home-shell">
    <header class="topbar">
      <div>
        <h1>旅行计划助手</h1>
        <p>上传资料、输入需求，快速进入 AI 规划</p>
      </div>
      <div class="user-area">
        <span>欢迎，{{ username }}</span>
        <button class="ghost-btn" @click="handleLogout">退出</button>
      </div>
    </header>

    <main class="home-grid">
      <section class="hero-card">
        <h2>把攻略、文件和想法交给 Agent</h2>
        <p>
          你可以直接进入聊天工作台，上传行程参考文件，然后让系统结合联网搜索生成旅行计划。
        </p>
        <div class="actions">
          <router-link to="/ai-plan" class="primary-btn">立即开始规划</router-link>
          <button class="secondary-btn" @click="goToAIPlan">打开计划工作台</button>
        </div>
      </section>

      <section class="info-card">
        <h3>当前版本包含</h3>
        <ul>
          <li>登录 / 注册</li>
          <li>文件上传</li>
          <li>Agent 联网搜索</li>
          <li>Markdown 旅行计划输出</li>
          <li>图片结果展示</li>
        </ul>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('User')

onMounted(() => {
  const storedUser = localStorage.getItem('username')
  if (storedUser) {
    username.value = storedUser
  }
})

const goToAIPlan = () => {
  router.push('/ai-plan')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('userId')
  router.push('/login')
}
</script>

<style scoped>
.home-shell {
  min-height: 100vh;
  background: linear-gradient(135deg, var(--color-white) 0%, var(--color-gray-50) 100%);
  padding: var(--space-6);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  background: var(--color-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}

.topbar h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--color-gray-900);
  letter-spacing: -0.03em;
}

.topbar p {
  margin: var(--space-1) 0 0;
  color: var(--color-gray-500);
  font-size: 14px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.ghost-btn,
.secondary-btn,
.primary-btn {
  border: none;
  border-radius: var(--radius-full);
  padding: 10px 20px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.ghost-btn,
.secondary-btn {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.ghost-btn:hover,
.secondary-btn:hover {
  background: var(--color-gray-200);
}

.primary-btn {
  background: var(--color-primary);
  color: var(--color-white);
  box-shadow: var(--shadow-primary);
}

.primary-btn:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.home-grid {
  display: grid;
  grid-template-columns: 5fr 2fr;
  gap: var(--space-5);
  margin-top: var(--space-5);
}

.hero-card,
.info-card {
  background: var(--color-white);
  border-radius: var(--radius-2xl);
  padding: var(--space-8);
  box-shadow: var(--shadow-sm);
}

.hero-card h2 {
  margin-top: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-gray-900);
  letter-spacing: -0.02em;
}

.hero-card p {
  color: var(--color-gray-600);
  line-height: 1.8;
  font-size: 15px;
}

.info-card h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-gray-900);
  margin-bottom: var(--space-4);
}

.info-card li {
  color: var(--color-gray-600);
  line-height: 2;
  font-size: 14px;
}

.info-card ul {
  padding-left: var(--space-4);
  margin: 0;
}

.info-card li::marker {
  color: var(--color-primary);
}

.actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-top: var(--space-6);
}

@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: 1fr;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


