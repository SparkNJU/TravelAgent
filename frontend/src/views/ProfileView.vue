<template>
  <div class="profile-page">
    <div class="profile-container">
      <!-- 用户信息卡片 -->
      <div class="user-card">
        <div class="user-avatar">
          <SvgIcon name="user" :size="36" />
        </div>
        <div class="user-info">
          <h1>{{ username }}</h1>
          <p>旅行计划助手用户</p>
        </div>
        <div class="user-stats">
          <div class="stat">
            <span class="num">{{ posts.length }}</span>
            <span class="label">帖子</span>
          </div>
          <div class="stat">
            <span class="num">{{ history.length }}</span>
            <span class="label">规划</span>
          </div>
        </div>
      </div>

      <!-- 标签切换 -->
      <div class="tabs-wrapper">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['tab-btn', { active: activeTab === tab.id }]"
          @click="activeTab = tab.id; loadData()"
        >
          {{ tab.name }}
        </button>
      </div>

      <!-- 内容区域 -->
      <div class="content-area">
        <!-- 旅行规划 -->
        <div v-if="activeTab === 'plans'" class="plans-section">
          <div v-if="loading" class="loading-box">
            <SvgIcon name="loader" :size="24" spin />
            <span>加载中...</span>
          </div>
          <div v-else-if="!history.length" class="empty-box">
            <SvgIcon name="compass" :size="40" />
            <p>暂无规划记录</p>
            <span>去 AI规划 页面生成你的第一份旅行计划</span>
          </div>
          <div v-else class="plans-list">
            <div v-for="item in history" :key="item.planId" class="plan-item">
              <div class="plan-icon">
                <SvgIcon name="map" :size="20" />
              </div>
              <div class="plan-content">
                <h3>{{ item.title || '未命名行程' }}</h3>
                <div class="plan-meta">
                  <span>{{ item.destination }}</span>
                  <span>{{ item.days }}天</span>
                </div>
              </div>
              <span class="plan-date">{{ formatDate(item.createdAt) }}</span>
              <div class="plan-actions">
                <button type="button" class="action-btn" @click="viewPlanDetail(item.planId)">
                  查看
                </button>
                <button type="button" class="action-btn primary" @click="addToWorkspace(item.planId)">
                  工作台
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 我的帖子 -->
        <div v-if="activeTab === 'posts'" class="posts-section">
          <div v-if="!posts.length" class="empty-box">
            <SvgIcon name="file-text" :size="40" />
            <p>暂无帖子</p>
            <span>去 发现 页面发布你的第一篇帖子</span>
          </div>
          <div v-else class="posts-grid">
            <article v-for="post in posts" :key="post.id" class="post-card">
              <div v-if="post.images?.length" class="card-img" :style="{ backgroundImage: `url(${post.images[0]})` }">
                <span v-if="post.originalPostId" class="repost-tag">转载</span>
              </div>
              <div class="card-body">
                <h3>{{ post.title }}</h3>
                <p>{{ post.description }}</p>
                <div class="card-footer">
                  <span v-for="tag in post.tags?.slice(0, 2)" :key="tag" class="tag">{{ tag }}</span>
                  <div class="card-stats">
                    <span><SvgIcon name="heart" :size="12" /> {{ post.likes }}</span>
                    <span><SvgIcon name="message" :size="12" /> {{ post.comments }}</span>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>

    <!-- 规划详情弹窗 -->
    <div v-if="showDetailModal" class="modal-overlay" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ selectedPlan?.title || '规划详情' }}</h3>
          <button class="close-btn" @click="closeModal">
            <SvgIcon name="x" :size="16" />
          </button>
        </div>
        <div class="modal-body">
          <div class="detail-row">
            <span class="detail-label">目的地</span>
            <span class="detail-value">{{ selectedPlan?.destination }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">天数</span>
            <span class="detail-value">{{ selectedPlan?.days }}天</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">预算</span>
            <span class="detail-value">¥{{ selectedPlan?.estimatedBudget || '未设置' }}</span>
          </div>
          <div class="detail-section">
            <h4>行程安排</h4>
            <div class="itinerary-content" v-html="formatItinerary(selectedPlan?.itinerary)"></div>
          </div>
          <div v-if="selectedPlan?.highlights?.length" class="detail-section">
            <h4>行程亮点</h4>
            <ul class="highlights-list">
              <li v-for="(highlight, index) in selectedPlan.highlights" :key="index">
                {{ highlight }}
              </li>
            </ul>
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn secondary" @click="closeModal">关闭</button>
          <button class="modal-btn primary" @click="addToWorkspace(selectedPlan?.planId); closeModal()">
            <SvgIcon name="edit" :size="14" />
            加到工作台
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'

const router = useRouter()
const username = ref(localStorage.getItem('username') || '用户')
const history = ref([])
const posts = ref([])
const activeTab = ref('plans')
const loading = ref(false)
const selectedPlan = ref(null)
const showDetailModal = ref(false)

const tabs = [
  { id: 'plans', name: '旅行规划' },
  { id: 'posts', name: '我的帖子' }
]

const formatDate = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

const loadData = async () => {
  if (activeTab.value === 'plans') {
    loading.value = true
    try {
      const userId = localStorage.getItem('userId') || '1'
      const response = await fetch(`/api/travel/plans/user/${userId}`)
      const data = await response.json()
      if (data.code === 200) {
        history.value = data.data
      } else {
        history.value = []
      }
    } catch (error) {
      console.error('加载规划失败:', error)
      history.value = []
    } finally {
      loading.value = false
    }
  } else if (activeTab.value === 'posts') {
    try {
      const stored = localStorage.getItem('travelPosts')
      if (stored) posts.value = JSON.parse(stored)
    } catch { posts.value = [] }
  }
}

const viewPlanDetail = async (planId) => {
  console.log('查看按钮点击了，planId:', planId)
  try {
    const response = await fetch(`/api/travel/plan/${planId}`)
    const data = await response.json()
    console.log('接口返回:', data)
    if (data.code === 200) {
      selectedPlan.value = data.data
      showDetailModal.value = true
      console.log('弹窗已显示')
    } else {
      alert('加载失败: ' + data.message)
    }
  } catch (error) {
    console.error('加载规划详情失败:', error)
    alert('加载失败: ' + error.message)
  }
}

const addToWorkspace = (planId) => {
  router.push(`/ai-plan?planId=${planId}`)
}

const closeModal = () => {
  showDetailModal.value = false
  selectedPlan.value = null
}

const formatItinerary = (itinerary) => {
  if (!itinerary) return '<p>暂无行程安排</p>'
  
  try {
    // 尝试解析JSON
    const parsed = JSON.parse(itinerary)
    
    if (Array.isArray(parsed)) {
      // 格式化行程数组
      let html = ''
      parsed.forEach((day) => {
        html += `<div class="itinerary-day"><strong>第${day.day}天</strong>`
        if (day.activities && Array.isArray(day.activities)) {
          day.activities.forEach((activity) => {
            html += `<div class="itinerary-item">`
            if (activity.time) {
              html += `<span class="itinerary-time">${activity.time}</span>`
            }
            html += `<span class="itinerary-location">${activity.location || ''}</span>`
            if (activity.description) {
              html += `<span class="itinerary-desc">${activity.description}</span>`
            }
            html += `</div>`
          })
        }
        html += `</div>`
      })
      return html
    }
  } catch (e) {
    // 如果不是JSON，按普通文本处理
  }
  
  // 将换行转换为 <br>，简单格式化显示
  return itinerary.replace(/\n/g, '<br>')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.profile-page {
  background: var(--color-bg);
  min-height: 100%;
  padding: 24px;
}

.profile-container {
  max-width: 720px;
  margin: 0 auto;
}

/* 用户信息卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 20px 24px;
  border: 1px solid var(--color-border);
  margin-bottom: 20px;
}

.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-info h1 {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 4px;
}

.user-info p {
  font-size: 13px;
  color: var(--color-hint);
  margin: 0;
}

.user-stats {
  display: flex;
  gap: 24px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat .num {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
}

.stat .label {
  font-size: 12px;
  color: var(--color-muted);
}

/* 标签切换 */
.tabs-wrapper {
  display: flex;
  background: var(--color-card);
  border-radius: var(--radius-pill);
  padding: 4px;
  border: 1px solid var(--color-border);
  margin-bottom: 20px;
}

.tab-btn {
  flex: 1;
  padding: 10px 16px;
  border: none;
  background: none;
  border-radius: calc(var(--radius-pill) - 4px);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-hint);
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-family);
}
.tab-btn:hover { color: var(--color-title); }
.tab-btn.active {
  background: var(--color-red);
  color: white;
}

/* 内容区域 */
.content-area {
  background: var(--color-card);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

/* 空状态 */
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  color: var(--color-muted);
}
.empty-box svg { margin-bottom: 12px; opacity: 0.4; }
.empty-box p {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-hint);
  margin: 0 0 6px;
}
.empty-box span { font-size: 13px; }

/* 旅行规划列表 */
.plans-list {
  padding: 8px;
}

.plan-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: var(--radius-input);
  background: var(--color-bg);
  margin-bottom: 8px;
  transition: background 0.2s;
  justify-content: flex-start;
}
.plan-item:hover { background: var(--color-border); }

.plan-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(230,57,70,0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-red-light);
  flex-shrink: 0;
}

.plan-content {
  flex: 1;
  min-width: 0;
}

.plan-content h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  margin: 0 0 4px;
}

.plan-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-hint);
}

.plan-date {
  font-size: 11px;
  color: var(--color-muted);
  flex-shrink: 0;
}

/* 帖子网格 */
.posts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  padding: 12px;
}

.post-card {
  background: var(--color-bg);
  border-radius: var(--radius-input);
  overflow: hidden;
  border: 1px solid var(--color-border);
  transition: all 0.2s;
}
.post-card:hover {
  border-color: var(--color-red);
  box-shadow: 0 2px 8px rgba(230,57,70,0.08);
}

.card-img {
  width: 100%;
  padding-bottom: 70%;
  background-size: cover;
  background-position: center;
  position: relative;
}

.repost-tag {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 3px 10px;
  background: rgba(230,57,70,0.9);
  color: white;
  font-size: 11px;
  font-weight: 500;
  border-radius: var(--radius-pill);
}

.card-body {
  padding: 12px;
}

.card-body h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  margin: 0 0 6px;
  line-clamp: 2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-body p {
  font-size: 12px;
  color: var(--color-secondary);
  margin: 0 0 10px;
  line-height: 1.5;
  line-clamp: 2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-footer .tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  background: rgba(230,57,70,0.12);
  color: var(--color-red-light);
}

.card-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-muted);
}

.card-stats span {
  display: flex;
  align-items: center;
  gap: 3px;
}

/* 规划项操作按钮 */
.plan-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  flex-shrink: 0;
  z-index: 10;
  align-items: center;
  margin-left: 0;
}

.plan-actions .action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 8px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-hint);
  background: var(--color-bg);
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-family);
  position: relative;
  margin: 0;
  width: 88px;
  min-width: 88px;
  box-sizing: border-box;
}

.plan-actions .action-btn:hover {
  border-color: var(--color-red);
  color: var(--color-red);
  background: rgba(230, 57, 70, 0.05);
}

.plan-actions .action-btn.primary {
  background: var(--color-red-light);
  border-color: var(--color-red-light);
  color: white;
}

.plan-actions .action-btn.primary:hover {
  background: var(--color-red);
  border-color: var(--color-red);
}

.plan-actions .action-btn.primary:hover {
  background: var(--color-red);
  border-color: var(--color-red);
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  min-height: 100vh;
}

.modal-content {
  background: var(--color-card);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
  width: 100%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0;
}

.modal-header .close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: var(--color-bg);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-hint);
  cursor: pointer;
  transition: all 0.2s;
}

.modal-header .close-btn:hover {
  background: var(--color-border);
  color: var(--color-title);
}

.modal-body {
  padding: 20px;
  max-height: 50vh;
  overflow-y: auto;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.detail-label {
  font-size: 13px;
  color: var(--color-hint);
}

.detail-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
}

.detail-section {
  margin-top: 16px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  margin: 0 0 10px;
}

.itinerary-content {
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-body);
  white-space: pre-wrap;
}

.highlights-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.highlights-list li {
  padding: 8px 0;
  font-size: 13px;
  color: var(--color-body);
  border-bottom: 1px dashed var(--color-border);
}

.highlights-list li:last-child {
  border-bottom: none;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}

.modal-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: var(--radius-pill);
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-title);
}

.modal-btn:hover {
  border-color: var(--color-red);
  color: var(--color-red);
}

.modal-btn.primary {
  background: var(--gradient-brand);
  border-color: var(--color-red-light);
  color: white;
}

.modal-btn.primary:hover {
  filter: brightness(1.1);
}

/* 加载状态 */
.loading-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  color: var(--color-muted);
}

.loading-box svg {
  margin-bottom: 12px;
}

.loading-box span {
  font-size: 13px;
  color: var(--color-hint);
}
</style>