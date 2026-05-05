<template>
  <div class="home-shell">
    <!-- 英雄区域 -->
    <section class="hero-section">
      <div class="hero-content">
        <div class="hero-text">
          <span class="hero-badge">AI 智能规划</span>
          <h2>让旅行规划变得简单</h2>
          <p>
            上传攻略资料、输入旅行需求，AI 助手会帮你生成个性化的旅行计划。
            结合实时搜索和智能分析，让每一次旅行都完美无忧。
          </p>
          <div class="hero-actions">
            <router-link to="/ai-plan" class="primary-btn">
              <span class="btn-icon">🚀</span> 立即开始规划
            </router-link>
            <button class="secondary-btn" @click="goToAIPlan">
              <span class="btn-icon">📋</span> 打开工作台
            </button>
          </div>
        </div>
        <div class="hero-visual">
          <div class="floating-card card-1">
            <div class="card-icon">🗺️</div>
            <span>智能路线规划</span>
          </div>
          <div class="floating-card card-2">
            <div class="card-icon">🏨</div>
            <span>酒店推荐</span>
          </div>
          <div class="floating-card card-3">
            <div class="card-icon">🍽️</div>
            <span>美食攻略</span>
          </div>
          <div class="floating-card card-4">
            <div class="card-icon">📷</div>
            <span>景点推荐</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 特色功能 -->
    <section class="features-section">
      <div class="section-header">
        <h3>核心功能</h3>
        <p>全方位满足您的旅行规划需求</p>
      </div>
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">📁</div>
          <h4>文件上传解析</h4>
          <p>支持多种格式的攻略文件上传，智能提取关键信息</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🔍</div>
          <h4>实时联网搜索</h4>
          <p>整合最新旅游资讯，确保行程信息的时效性</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🤖</div>
          <h4>AI 智能规划</h4>
          <p>基于大数据分析，生成最优化的旅行路线</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">📊</div>
          <h4>可视化输出</h4>
          <p>Markdown 格式输出，清晰展示每日行程安排</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🖼️</div>
          <h4>图片资源展示</h4>
          <p>自动搜索目的地图片，让行程更直观</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">💾</div>
          <h4>历史记录保存</h4>
          <p>自动保存生成的旅行计划，随时查看和修改</p>
        </div>
      </div>
    </section>

    <!-- 热门目的地 -->
    <section class="destinations-section">
      <div class="section-header">
        <h3>热门目的地</h3>
        <p>精选热门旅游城市，一键生成行程</p>
      </div>
      <div class="destinations-grid">
        <div 
          v-for="dest in destinations" 
          :key="dest.name"
          class="destination-card"
          @click="quickPlan(dest.name)"
        >
          <div class="dest-image" :style="{ backgroundImage: `url(${dest.image})` }">
            <div class="dest-overlay">
              <span class="dest-name">{{ dest.name }}</span>
              <span class="dest-desc">{{ dest.desc }}</span>
            </div>
          </div>
          <div class="dest-info">
            <div class="dest-meta">
              <span class="meta-item">🏙️ {{ dest.type }}</span>
              <span class="meta-item">⭐ {{ dest.rating }}</span>
            </div>
            <button class="dest-btn">生成 {{ dest.days }} 天计划</button>
          </div>
        </div>
      </div>
    </section>

    <!-- 用户评价 -->
    <section class="testimonials-section">
      <div class="section-header">
        <h3>用户好评</h3>
        <p>来自真实用户的反馈</p>
      </div>
      <div class="testimonials-grid">
        <div class="testimonial-card">
          <div class="testimonial-header">
            <div class="user-avatar">👤</div>
            <div class="user-details">
              <span class="user-name">张先生</span>
              <span class="user-location">北京</span>
            </div>
          </div>
          <p class="testimonial-content">
            "使用这个工具规划了我的日本之旅，行程安排非常合理，推荐的餐厅也都很棒！"
          </p>
          <div class="testimonial-rating">⭐⭐⭐⭐⭐</div>
        </div>
        <div class="testimonial-card">
          <div class="testimonial-header">
            <div class="user-avatar">👩</div>
            <div class="user-details">
              <span class="user-name">李女士</span>
              <span class="user-location">上海</span>
            </div>
          </div>
          <p class="testimonial-content">
            "AI 推荐的路线很贴心，避开了很多人流高峰，旅行体验大大提升！"
          </p>
          <div class="testimonial-rating">⭐⭐⭐⭐⭐</div>
        </div>
        <div class="testimonial-card">
          <div class="testimonial-header">
            <div class="user-avatar">👨</div>
            <div class="user-details">
              <span class="user-name">王先生</span>
              <span class="user-location">深圳</span>
            </div>
          </div>
          <p class="testimonial-content">
            "上传了一份攻略文档，系统自动整理出完美的行程，省时又省力！"
          </p>
          <div class="testimonial-rating">⭐⭐⭐⭐⭐</div>
        </div>
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="cta-section">
      <div class="cta-content">
        <h3>准备好开启你的下一次旅行了吗？</h3>
        <p>让 AI 助手为你规划完美行程</p>
        <router-link to="/ai-plan" class="primary-btn large">开始规划</router-link>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-section">
          <h4>旅行计划助手</h4>
          <p>AI 驱动的智能旅行规划平台</p>
        </div>
        <div class="footer-section">
          <h5>功能</h5>
          <ul>
            <li><router-link to="/ai-plan">AI 规划</router-link></li>
            <li><router-link to="/">首页</router-link></li>
          </ul>
        </div>
        <div class="footer-section">
          <h5>支持</h5>
          <ul>
            <li>帮助中心</li>
            <li>联系我们</li>
          </ul>
        </div>
      </div>
      <div class="footer-bottom">
        <p>© 2024 旅行计划助手. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const destinations = ref([
  {
    name: '东京',
    desc: '日本首都，现代与传统交融',
    type: '都市观光',
    rating: '4.8',
    days: 5,
    image: 'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=300&fit=crop'
  },
  {
    name: '新加坡',
    desc: '花园城市，多元文化汇聚',
    type: '亲子度假',
    rating: '4.9',
    days: 4,
    image: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=300&fit=crop'
  },
  {
    name: '巴黎',
    desc: '浪漫之都，艺术与时尚',
    type: '浪漫旅行',
    rating: '4.7',
    days: 6,
    image: 'https://images.unsplash.com/photo-1505761671935-60b3a7427bad?w=400&h=300&fit=crop'
  },
  {
    name: '京都',
    desc: '千年古都，和风韵味',
    type: '文化体验',
    rating: '4.8',
    days: 4,
    image: 'https://images.unsplash.com/photo-1513407030348-c983a97b98d8?w=400&h=300&fit=crop'
  },
  {
    name: '曼谷',
    desc: '天使之城，热情洋溢',
    type: '休闲度假',
    rating: '4.6',
    days: 5,
    image: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=300&fit=crop'
  },
  {
    name: '马尔代夫',
    desc: '海岛天堂，梦幻度假',
    type: '海岛度假',
    rating: '4.9',
    days: 7,
    image: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=300&fit=crop'
  }
])

const goToAIPlan = () => {
  router.push('/ai-plan')
}

const quickPlan = (destination) => {
  localStorage.setItem('quickDestination', destination)
  router.push('/ai-plan')
}

</script>

<style scoped>
.home-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f4ff 0%, #f8fafc 50%, #f0f4ff 100%);
  color: #162033;
  font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  padding-top: 60px;
}

/* 按钮样式 */
.ghost-btn,
.secondary-btn,
.primary-btn {
  border: none;
  border-radius: 999px;
  padding: 12px 20px;
  cursor: pointer;
  font-weight: 600;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.ghost-btn {
  background: transparent;
  color: #64748b;
  border: 1px solid #cbd5e1;
}

.ghost-btn:hover {
  background: #f1f5f9;
}

.secondary-btn {
  background: #eef2ff;
  color: #334155;
}

.secondary-btn:hover {
  background: #e0e7ff;
  transform: translateY(-2px);
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.3);
}

.primary-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(37, 99, 235, 0.4);
}

.primary-btn.large {
  padding: 16px 40px;
  font-size: 18px;
}

.btn-icon {
  font-size: 16px;
}

/* 英雄区域 */
.hero-section {
  margin: 20px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  border-radius: 32px;
  padding: 60px 40px;
  color: white;
  overflow: hidden;
  position: relative;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 600px;
  height: 600px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  filter: blur(60px);
}

.hero-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: center;
  position: relative;
  z-index: 1;
}

.hero-text {
  max-width: 500px;
}

.hero-badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 16px;
  display: inline-block;
}

.hero-text h2 {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 16px;
  line-height: 1.2;
}

.hero-text p {
  font-size: 16px;
  line-height: 1.8;
  opacity: 0.9;
  margin: 0 0 24px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

/* 浮动卡片 */
.hero-visual {
  position: relative;
  height: 320px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 20px;
  padding: 20px;
}

.floating-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #1e293b;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.3s ease;
}

.floating-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.18);
}

.card-icon {
  font-size: 40px;
}

.floating-card span {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  text-align: center;
}

@keyframes floatCard {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

/* 通用区块样式 */
.section-header {
  text-align: center;
  margin-bottom: 40px;
}

.section-header h3 {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #0f172a;
}

.section-header p {
  font-size: 16px;
  color: #64748b;
  margin: 0;
}

/* 功能特色 */
.features-section {
  padding: 60px 20px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.feature-card {
  background: white;
  border-radius: 20px;
  padding: 32px;
  text-align: center;
  border: 1px solid rgba(148, 163, 184, 0.1);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.04);
  transition: all 0.3s ease;
}

.feature-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.1);
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.feature-card h4 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 12px;
  color: #0f172a;
}

.feature-card p {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin: 0;
}

/* 热门目的地 */
.destinations-section {
  padding: 60px 20px;
  background: rgba(37, 99, 235, 0.03);
}

.destinations-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.destination-card {
  background: white;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.05);
  transition: all 0.3s ease;
}

.destination-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
}

.dest-image {
  height: 200px;
  background-size: cover;
  background-position: center;
  position: relative;
}

.dest-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  padding: 30px 20px 20px;
}

.dest-name {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: white;
  margin-bottom: 4px;
}

.dest-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.dest-info {
  padding: 20px;
}

.dest-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.meta-item {
  font-size: 13px;
  color: #64748b;
}

.dest-btn {
  width: 100%;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
  border: none;
  border-radius: 12px;
  padding: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.dest-btn:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3);
}

/* 用户评价 */
.testimonials-section {
  padding: 60px 20px;
}

.testimonials-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.testimonial-card {
  background: white;
  border-radius: 20px;
  padding: 24px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.04);
}

.testimonial-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.user-avatar {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
}

.user-location {
  font-size: 12px;
  color: #64748b;
}

.testimonial-content {
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
  margin: 0 0 12px;
}

.testimonial-rating {
  font-size: 18px;
}

/* CTA 区块 */
.cta-section {
  margin: 0 20px 40px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border-radius: 24px;
  padding: 60px;
  text-align: center;
  color: white;
}

.cta-content h3 {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 8px;
}

.cta-content p {
  font-size: 16px;
  opacity: 0.9;
  margin: 0 0 24px;
}

/* 页脚 */
.footer {
  background: #0f172a;
  color: white;
  padding: 40px 20px;
}

.footer-content {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 32px;
  max-width: 1200px;
  margin: 0 auto 32px;
}

.footer-section h4 {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 12px;
}

.footer-section h5 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  color: #94a3b8;
}

.footer-section p {
  font-size: 14px;
  color: #94a3b8;
  margin: 0;
}

.footer-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.footer-section li {
  margin-bottom: 8px;
}

.footer-section a {
  color: #94a3b8;
  text-decoration: none;
  font-size: 14px;
}

.footer-section a:hover {
  color: white;
}

.footer-bottom {
  border-top: 1px solid rgba(148, 163, 184, 0.1);
  padding-top: 20px;
  text-align: center;
}

.footer-bottom p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 900px) {
  .hero-content {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .hero-text h2 {
    font-size: 32px;
  }

  .hero-visual {
    height: 250px;
  }

  .floating-card {
    padding: 16px;
  }

  .cta-section {
    padding: 40px 20px;
  }

  .cta-content h3 {
    font-size: 24px;
  }
}
</style>