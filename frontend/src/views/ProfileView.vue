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
          <div v-if="!history.length" class="empty-box">
            <SvgIcon name="compass" :size="40" />
            <p>暂无规划记录</p>
            <span>去 AI规划 页面生成你的第一份旅行计划</span>
          </div>
          <div v-else class="plans-list">
            <div v-for="item in history" :key="item.id" class="plan-item">
              <div class="plan-icon">
                <SvgIcon name="map" :size="20" />
              </div>
              <div class="plan-content">
                <h3>{{ item.result?.title || '未命名行程' }}</h3>
                <div class="plan-meta">
                  <span>{{ item.result?.destination }}</span>
                  <span>{{ item.result?.days }}天</span>
                </div>
              </div>
              <span class="plan-date">{{ formatDate(item.createdAt) }}</span>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'

const username = ref(localStorage.getItem('username') || '用户')
const history = ref([])
const posts = ref([])
const activeTab = ref('plans')

const tabs = [
  { id: 'plans', name: '旅行规划' },
  { id: 'posts', name: '我的帖子' }
]

const formatDate = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

const loadData = () => {
  if (activeTab.value === 'plans') {
    try {
      const stored = localStorage.getItem('assistantHistory')
      if (stored) history.value = JSON.parse(stored)
    } catch { history.value = [] }
  } else if (activeTab.value === 'posts') {
    try {
      const stored = localStorage.getItem('travelPosts')
      if (stored) posts.value = JSON.parse(stored)
    } catch { posts.value = [] }
  }
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
</style>