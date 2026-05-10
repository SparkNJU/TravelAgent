<template>
  <div class="leaderboard-page">
    <header class="leaderboard-header">
      <div>
        <h1>模型排行榜</h1>
        <p>基于 Bradley-Terry 的对战评分</p>
      </div>
      <button class="refresh-btn" :disabled="loading" @click="loadLeaderboard">
        <SvgIcon name="refresh" :size="14" />
        刷新
      </button>
    </header>

    <main class="leaderboard-main">
      <ModelLeaderboardPanel
        :entries="entries"
        :loading="loading"
        @refresh="loadLeaderboard"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'
import ModelLeaderboardPanel from '../components/ModelLeaderboardPanel.vue'

const entries = ref([])
const loading = ref(false)

const loadLeaderboard = async () => {
  loading.value = true
  try {
    const res = await fetch('/api/arena/leaderboard')
    const data = await res.json()
    if (data.code === 200 && data.data?.entries) {
      entries.value = data.data.entries
    }
  } catch { /* ignore */ }
  loading.value = false
}

onMounted(loadLeaderboard)
</script>

<style scoped>
.leaderboard-page {
  background: var(--color-bg);
  min-height: 100%;
  padding: 24px;
  font-family: var(--font-family);
  color: var(--color-body);
}

.leaderboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 16px 20px;
  margin-bottom: 16px;
}

.leaderboard-header h1 {
  font-size: 18px;
  color: var(--color-title);
  margin: 0 0 4px;
}

.leaderboard-header p {
  margin: 0;
  font-size: 12px;
  color: var(--color-muted);
}

.leaderboard-main {
  max-width: 720px;
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 600;
  transition: all 0.15s;
}

.refresh-btn:hover {
  border-color: var(--color-red-light);
  color: var(--color-title);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
