<template>
  <div class="profile-page">
    <div class="profile-inner">
      <div class="profile-card">
        <div class="profile-avatar">
          <SvgIcon name="user" :size="32" />
        </div>
        <div class="profile-info">
          <h2>{{ username }}</h2>
          <p>旅行计划助手用户</p>
        </div>
      </div>

      <div class="history-section">
        <h3>我的规划历史</h3>
        <div v-if="!history.length" class="empty-state">
          <p>暂无规划记录</p>
          <span>去 AI规划 页面生成你的第一份旅行计划</span>
        </div>
        <div v-else class="history-list">
          <div v-for="item in history" :key="item.id" class="history-item">
            <div class="history-info">
              <h4>{{ item.result?.title || '未命名行程' }}</h4>
              <span class="history-meta">{{ item.result?.destination }} · {{ item.result?.days }}天</span>
              <p>{{ item.query }}</p>
            </div>
            <span class="history-date">{{ formatDate(item.createdAt) }}</span>
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

const formatDate = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

onMounted(() => {
  try {
    const stored = localStorage.getItem('assistantHistory')
    if (stored) history.value = JSON.parse(stored)
  } catch { history.value = [] }
})
</script>

<style scoped>
.profile-page {
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
  min-height: 100%;
}

.profile-inner {
  max-width: 640px;
  margin: 0 auto;
  padding: 28px 32px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
  border: 1px solid var(--color-border);
  margin-bottom: 24px;
}

.profile-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--gradient-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.profile-info h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 4px;
}

.profile-info p {
  font-size: 13px;
  color: var(--color-hint);
  margin: 0;
}

.history-section {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
  border: 1px solid var(--color-border);
}

.history-section h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 16px;
}

.empty-state {
  text-align: center;
  padding: 32px 0;
  color: var(--color-muted);
}

.empty-state p { font-size: 14px; margin: 0 0 6px; }
.empty-state span { font-size: 12px; }

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  background: var(--color-bg);
  border-radius: var(--radius-input);
  transition: background 0.2s;
}

.history-item:hover { background: var(--color-border); }

.history-info h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  margin: 0 0 4px;
}

.history-meta {
  font-size: 12px;
  color: var(--color-hint);
}

.history-info p {
  font-size: 12px;
  color: var(--color-hint);
  margin: 4px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-date {
  font-size: 11px;
  color: var(--color-muted);
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
