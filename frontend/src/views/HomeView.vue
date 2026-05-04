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
  background: linear-gradient(180deg, #f3f7ff 0%, #f8fafc 100%);
  padding: 28px;
  color: #162033;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 24px 28px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
}

.topbar h1 {
  margin: 0;
  font-size: 28px;
}

.topbar p {
  margin: 6px 0 0;
  color: #64748b;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ghost-btn,
.secondary-btn,
.primary-btn {
  border: none;
  border-radius: 999px;
  padding: 12px 18px;
  cursor: pointer;
  font-weight: 600;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ghost-btn,
.secondary-btn {
  background: #eef2ff;
  color: #334155;
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
}

.home-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-top: 24px;
}

.hero-card,
.info-card {
  background: white;
  border-radius: 24px;
  padding: 28px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
}

.hero-card h2 {
  margin-top: 0;
  font-size: 30px;
}

.hero-card p,
.info-card li {
  color: #475569;
  line-height: 1.8;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.info-card ul {
  padding-left: 18px;
  margin: 0;
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


