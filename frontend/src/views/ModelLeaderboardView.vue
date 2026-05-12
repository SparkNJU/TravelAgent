<template>
  <div class="leaderboard-page">
    <header class="leaderboard-header">
      <div>
        <h1>模型排行榜</h1>
      </div>
      <button class="refresh-btn" :disabled="loading" @click="loadLeaderboard">
        <SvgIcon name="refresh" :size="14" />
        刷新
      </button>
    </header>

    <section class="leaderboard-filters">
      <div class="filter-group">
        <label class="filter-label">搜索</label>
        <input v-model="searchText" class="filter-input" placeholder="模型名或厂商" />
      </div>
      <div class="filter-group">
        <label class="filter-label">厂商</label>
        <select v-model="vendorFilter" class="filter-select">
          <option value="all">全部</option>
          <option v-for="vendor in vendorOptions" :key="vendor" :value="vendor">{{ vendor }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label class="filter-label">最低分数</label>
        <input v-model.number="minScore" type="number" class="filter-input" placeholder="例如 1000" />
      </div>
      <div class="filter-group">
        <label class="filter-label">最低场次</label>
        <input v-model.number="minMatches" type="number" class="filter-input" placeholder="例如 5" />
      </div>
      <div class="filter-group">
        <label class="filter-label">排序</label>
        <select v-model="sortKey" class="filter-select">
          <option value="score">分数</option>
          <option value="wins">胜场</option>
          <option value="matches">场次</option>
        </select>
      </div>
      <div class="filter-group">
        <label class="filter-label">顺序</label>
        <select v-model="sortDir" class="filter-select">
          <option value="desc">高到低</option>
          <option value="asc">低到高</option>
        </select>
      </div>
    </section>

    <main class="leaderboard-main">
      <ModelLeaderboardPanel
        :entries="filteredEntries"
        :loading="loading"
        @refresh="loadLeaderboard"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'
import ModelLeaderboardPanel from '../components/ModelLeaderboardPanel.vue'

const entries = ref([])
const loading = ref(false)
const searchText = ref('')
const vendorFilter = ref('all')
const minScore = ref(null)
const minMatches = ref(null)
const sortKey = ref('score')
const sortDir = ref('desc')

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

const vendorOptions = computed(() => {
  const set = new Set()
  entries.value.forEach((item) => {
    const vendor = item.meta?.vendor || item.vendor
    if (vendor) set.add(vendor)
  })
  return Array.from(set).sort()
})

const filteredEntries = computed(() => {
  const text = searchText.value.trim().toLowerCase()
  const minScoreValue = Number.isFinite(minScore.value) ? Number(minScore.value) : null
  const minMatchesValue = Number.isFinite(minMatches.value) ? Number(minMatches.value) : null

  const result = entries.value.filter((item) => {
    const model = String(item.model || '').toLowerCase()
    const vendor = String(item.meta?.vendor || item.vendor || '').toLowerCase()
    if (text && !model.includes(text) && !vendor.includes(text)) return false
    if (vendorFilter.value !== 'all') {
      const normalizedVendor = item.meta?.vendor || item.vendor || ''
      if (normalizedVendor !== vendorFilter.value) return false
    }
    if (minScoreValue !== null && Number(item.score) < minScoreValue) return false
    if (minMatchesValue !== null && Number(item.matches) < minMatchesValue) return false
    return true
  })

  const direction = sortDir.value === 'asc' ? 1 : -1
  return result.slice().sort((a, b) => {
    const av = Number(a[sortKey.value] ?? 0)
    const bv = Number(b[sortKey.value] ?? 0)
    return (av - bv) * direction
  })
})
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

.leaderboard-main {
  max-width: 720px;
}

.leaderboard-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-label {
  font-size: 12px;
  color: var(--color-muted);
}

.filter-input,
.filter-select {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 6px 10px;
  background: var(--color-surface);
  color: var(--color-body);
  font-size: 12px;
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
