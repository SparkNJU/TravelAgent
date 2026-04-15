<template>
  <div class="home-container">
    <header class="header">
      <div class="logo">
        <h1>AgentLLM 旅游平台</h1>
      </div>
      <div class="user-info">
        <span>欢迎, {{ username }}</span>
        <button @click="handleLogout" class="logout-btn">退出</button>
      </div>
    </header>

    <main class="main-content">
      <aside class="sidebar">
        <ul class="nav-links">
          <li :class="{ active: currentNav === 'home' }" @click="currentNav = 'home'">首页发现</li>
          <li :class="{ active: currentNav === 'trips' }" @click="currentNav = 'trips'">我的行程</li>
          <li :class="{ active: currentNav === 'aiPlan' }" @click="goToAIPlan">AI 智能规划</li>
          <li :class="{ active: currentNav === 'orders' }" @click="currentNav = 'orders'">订单中心</li>
        </ul>
      </aside>

      <section class="content" v-if="currentNav === 'home'">
        <div class="hero">
          <h2>世界那么大，你想去哪里？</h2>
          <div class="search-bar">
            <input 
              v-model="searchKeyword" 
              type="text" 
              placeholder="探索目的地 / 酒店 / 景点..." 
              @keyup.enter="searchDestinations"
            />
            <button class="search-btn" @click="searchDestinations">搜索</button>
            <router-link to="/ai-plan" class="ai-btn">AI 帮我选</router-link>
          </div>
        </div>

        <div class="recommendations">
          <h3>热门目的地</h3>
          <div v-if="destinations.length > 0" class="cards">
            <div 
              v-for="dest in destinations" 
              :key="dest.id" 
              class="card"
            >
              <div class="card-img" :style="{ backgroundImage: 'url(' + dest.imageUrl + ')' }"></div>
              <div class="card-info">
                <h4>{{ dest.name }}</h4>
                <p class="rating">{{ dest.rating }} 分 ({{ dest.reviewCount }} 评价)</p>
                <p class="description">{{ dest.description.substring(0, 50) }}...</p>
                <button class="view-btn" @click="viewDestination(dest)">查看详情</button>
              </div>
            </div>
          </div>
          <div v-else class="loading">加载中...</div>
        </div>
      </section>

      <section class="content" v-else-if="currentNav === 'trips'">
        <div class="trips-placeholder">
          <h3>您还没有任何行程</h3>
          <p>点击 "AI 智能规划" 创建你的第一个行程吧！</p>
          <router-link to="/ai-plan" class="create-btn">创建行程</router-link>
        </div>
      </section>

      <section class="content" v-else-if="currentNav === 'orders'">
        <div class="orders-placeholder">
          <h3>您还没有任何订单</h3>
          <p>浏览热门目的地或使用 AI 规划来预订你的梦想之旅</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('User')
const currentNav = ref('home')
const searchKeyword = ref('')
const destinations = ref([])

onMounted(async () => {
  const storedUser = localStorage.getItem('username')
  if (storedUser) {
    username.value = storedUser
  }
  
  // Load popular destinations
  await loadDestinations()
})

const loadDestinations = async () => {
  try {
    const response = await fetch('/api/travel/destinations/popular')
    const data = await response.json()
    if (data.code === 200) {
      destinations.value = data.data
    }
  } catch (error) {
    console.error('Failed to load destinations:', error)
  }
}

const searchDestinations = async () => {
  if (!searchKeyword.value.trim()) {
    await loadDestinations()
    return
  }

  try {
    const response = await fetch(`/api/travel/destinations/search?keyword=${searchKeyword.value}`)
    const data = await response.json()
    if (data.code === 200) {
      destinations.value = data.data
    }
  } catch (error) {
    console.error('Search failed:', error)
  }
}

const viewDestination = (dest) => {
  console.log('Viewing destination:', dest)
  // Can implement detailed view later
}

const goToAIPlan = () => {
  router.push('/ai-plan')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<style scoped>
.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: #f5f7fa;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  background: white;
  color: #333;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  border-bottom: 1px solid #e8e8e8;
}

.logo h1 {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.logout-btn {
  background-color: #4CAF50;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: bold;
}

.logout-btn:hover {
  background-color: #45a049;
}

.main-content {
  display: flex;
  flex: 1;
}

.sidebar {
  width: 180px;
  background: white;
  padding: 20px 0;
  border-right: 1px solid #e8e8e8;
  position: sticky;
  top: 60px;
  height: calc(100vh - 60px);
  overflow-y: auto;
}

.nav-links {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-links li {
  padding: 12px 20px;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
  font-weight: 500;
  border-left: 3px solid transparent;
}

.nav-links li:hover {
  background-color: #f5f5f5;
}

.nav-links li.active {
  background-color: #e3f2fd;
  color: #1976d2;
  font-weight: bold;
  border-left: 3px solid #1976d2;
  padding-left: 20px;
}

.content {
  flex: 1;
  padding: 40px;
  background-color: #f5f7fa;
  overflow-y: auto;
}

.hero {
  background: white;
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  margin-bottom: 40px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.hero h2 {
  font-size: 28px;
  color: #333;
  margin: 0 0 30px 0;
  font-weight: 600;
}

.search-bar {
  display: flex;
  justify-content: center;
  gap: 10px;
  max-width: 700px;
  margin: 0 auto;
}

.search-bar input {
  flex: 1;
  padding: 12px 18px;
  font-size: 16px;
  border: 1px solid #ddd;
  border-radius: 4px;
  outline: none;
  transition: border-color 0.2s;
}

.search-bar input:focus {
  border-color: #4CAF50;
}

.search-btn, .ai-btn {
  padding: 12px 24px;
  font-size: 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: white;
  transition: all 0.2s;
  text-decoration: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.search-btn {
  background-color: #4CAF50;
}

.search-btn:hover {
  background-color: #1976d2;
}

.ai-btn {
  background-color: #1976d2;
}

.ai-btn:hover {
  background-color: #1565c0;
}

.recommendations {
  margin-top: 20px;
}

.recommendations h3 {
  font-size: 20px;
  color: #333;
  margin: 0 0 20px 0;
  font-weight: 600;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.card {
  background-color: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  transition: all 0.2s;
  cursor: pointer;
}

.card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}

.card-img {
  height: 160px;
  background-size: cover;
  background-position: center;
}

.card-info {
  padding: 15px;
}

.card-info h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #333;
  font-weight: 600;
}

.rating {
  color: #f57c00;
  font-weight: 600;
  margin: 5px 0;
  font-size: 14px;
}

.description {
  color: #999;
  font-size: 13px;
  margin: 8px 0;
  line-height: 1.4;
}

.view-btn {
  width: 100%;
  padding: 10px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
  font-size: 14px;
}

.view-btn:hover {
  background-color: #45a049;
}

.loading {
  background: white;
  border-radius: 8px;
  text-align: center;
  padding: 60px 40px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.emoji {
  font-size: 48px;
  margin-bottom: 20px;
}

.trips-placeholder, .orders-placeholder {
  background: white;
  border-radius: 8px;
  text-align: center;
  padding: 60px 40px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.trips-placeholder h3, .orders-placeholder h3 {
  color: #666;
  font-size: 20px;
  margin: 0 0 15px 0;
  font-weight: 600;
}

.trips-placeholder p, .orders-placeholder p {
  color: #999;
  margin: 0 0 25px 0;
  font-size: 14px;
}

.create-btn {
  display: inline-block;
  padding: 12px 30px;
  background-color: #4CAF50;
  color: white;
  border-radius: 4px;
  text-decoration: none;
  font-weight: 600;
  transition: all 0.2s;
}

.create-btn:hover {
  background-color: #45a049;
}
</style>
