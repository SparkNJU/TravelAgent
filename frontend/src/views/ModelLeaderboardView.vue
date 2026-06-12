<template>
  <div class="leaderboard-page" :class="{ 'sidebar-collapsed': filtersCollapsed }">
    <aside class="arena-sidebar" :class="{ collapsed: filtersCollapsed }">
      <section class="sidebar-section view-section">
        <button class="collapse-btn" :title="filtersCollapsed ? '展开筛选' : '折叠筛选'" @click="filtersCollapsed = !filtersCollapsed">
          <SvgIcon :name="filtersCollapsed ? 'chevron-right' : 'arrow-left'" :size="15" />
        </button>
        <div class="section-title">
          <SvgIcon name="eye" :size="16" />
          <span>View as</span>
        </div>
        <div class="view-toggle">
          <button :class="{ active: viewMode === 'overview' }" @click="viewMode = 'overview'">
            <SvgIcon name="menu" :size="14" />
            Overview
          </button>
          <button :class="{ active: viewMode === 'compact' }" @click="viewMode = 'compact'">
            <SvgIcon name="eye" :size="14" />
            Compact
          </button>
          <button :class="{ active: viewMode === 'pareto' }" @click="viewMode = 'pareto'">
            <SvgIcon name="refresh" :size="14" />
            Pareto
          </button>
        </div>
      </section>

      <section class="sidebar-section">
        <div class="section-title">
          <SvgIcon name="trophy" :size="16" />
          <span>指标筛选</span>
          <small>{{ metricOptions.length }}</small>
        </div>
        <button
          v-for="metric in metricOptions"
          :key="metric.id"
          :class="['metric-option', { active: activeMetric === metric.id }]"
          @click="activeMetric = metric.id"
        >
          <span class="metric-icon">{{ metric.icon }}</span>
          <span>
            <strong>{{ metric.label }}</strong>
            <small>{{ metric.caption }}</small>
          </span>
          <SvgIcon v-if="activeMetric === metric.id" name="check" :size="15" />
        </button>
      </section>

      <section class="sidebar-section">
        <div class="section-title">
          <SvgIcon name="brain" :size="16" />
          <span>模型厂商</span>
        </div>
        <button
          v-for="vendor in vendorFilterOptions"
          :key="vendor.value"
          :class="['filter-option', { active: vendorFilter === vendor.value }]"
          @click="vendorFilter = vendor.value"
        >
          <span>{{ vendor.label }}</span>
          <small>{{ vendor.count }}</small>
        </button>
      </section>

      <section class="sidebar-section">
        <div class="section-title">
          <SvgIcon name="check" :size="16" />
          <span>可信度</span>
        </div>
        <div class="segmented">
          <button :class="{ active: confidenceFilter === 'all' }" @click="confidenceFilter = 'all'">All</button>
          <button :class="{ active: confidenceFilter === 'tested' }" @click="confidenceFilter = 'tested'">Tested</button>
          <button :class="{ active: confidenceFilter === 'high' }" @click="confidenceFilter = 'high'">High</button>
        </div>
      </section>
    </aside>

    <main class="arena-content">
      <header class="arena-top">
        <div>
          <div class="title-line">
            <h1>模型排行榜</h1>
            <span>{{ activeMetricMeta.icon }} {{ activeMetricMeta.label }}</span>
          </div>
          <div class="meta-row">
            <span><SvgIcon name="calendar" :size="15" /> {{ todayLabel }}</span>
            <span><SvgIcon name="check" :size="15" /> {{ totalMatches }} votes</span>
            <span><SvgIcon name="brain" :size="15" /> {{ enrichedEntries.length }} models</span>
          </div>
        </div>
        <button class="vote-btn" @click="goVote">Start Voting</button>
      </header>

      <section class="arena-tools">
        <button class="plain-tool" @click="filtersCollapsed = !filtersCollapsed">
          <SvgIcon :name="filtersCollapsed ? 'chevron-right' : 'arrow-left'" :size="15" />
          {{ filtersCollapsed ? 'Show Filters' : 'Hide Filters' }}
        </button>
        <div class="tool-spacer"></div>
        <div class="rank-switch" aria-label="Rank by">
          <span>Rank by</span>
          <button :class="{ active: rankBy === 'models' }" @click="rankBy = 'models'">Models</button>
        </div>
        <label class="search-tool">
          <SvgIcon name="search" :size="16" />
          <input v-model="searchText" placeholder="Search" />
        </label>
        <button class="icon-tool" :disabled="loading" title="刷新排行" @click="loadLeaderboard">
          <SvgIcon name="refresh" :size="16" />
        </button>
      </section>

      <ModelLeaderboardPanel
        :entries="displayEntries"
        :loading="loading"
        :rank-by="rankBy"
        :view-mode="viewMode"
        :metric-label="activeMetricMeta.label"
        :metric-key="activeMetricMeta.sortKey"
        :pairwise-data="pairwiseData"
        :pairwise-loading="pairwiseLoading"
        @refresh="loadLeaderboard"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'
import ModelLeaderboardPanel from '../components/ModelLeaderboardPanel.vue'

const router = useRouter()
const entries = ref([])
const loading = ref(false)
const pairwiseData = ref(null)
const pairwiseLoading = ref(false)
const searchText = ref('')
const activeMetric = ref('overall')
const vendorFilter = ref('all')
const confidenceFilter = ref('all')
const viewMode = ref('overview')
const rankBy = ref('models')
const filtersCollapsed = ref(typeof window !== 'undefined' && window.matchMedia('(max-width: 680px)').matches)

const metricOptions = [
  { id: 'overall', icon: '🏆', label: 'Overall', caption: '综合 Arena 分数', sortKey: 'score' },
  { id: 'winRate', icon: '🎯', label: 'Win Rate', caption: '胜率优先', sortKey: 'winRate' },
  { id: 'votes', icon: '🗳️', label: 'Votes', caption: '投票样本量', sortKey: 'matches' },
  { id: 'confidence', icon: '✅', label: 'Confidence', caption: '稳定可信度', sortKey: 'confidenceScore' },
  { id: 'cost', icon: '💰', label: 'Cost Efficiency', caption: '单位成本表现', sortKey: 'costEfficiency' },
  { id: 'context', icon: '🧠', label: 'Context', caption: '上下文能力', sortKey: 'contextScore' },
]

const modelMeta = {
  'deepseek-v4-flash': { vendor: 'DeepSeek', family: 'DeepSeek', accent: '#ff2442', price: '$0.20 / $0.80', context: '128K', priceValue: 0.8, contextScore: 128 },
  'kimi-k2.6': { vendor: 'Moonshot AI', family: 'Kimi', accent: '#111827', price: '$0.60 / $2.00', context: '128K', priceValue: 2, contextScore: 128 },
  'MiniMax-M2.5': { vendor: 'MiniMax', family: 'MiniMax', accent: '#d97706', price: '$0.30 / $1.20', context: '1M', priceValue: 1.2, contextScore: 1000 },
  'qwen3.6-plus': { vendor: 'Alibaba Cloud', family: 'Qwen', accent: '#ff2442', price: '$0.40 / $1.20', context: '1M', priceValue: 1.2, contextScore: 1000 },
  'glm-5.1': { vendor: 'Zhipu AI', family: 'GLM', accent: '#b91c1c', price: '$0.50 / $1.50', context: '128K', priceValue: 1.5, contextScore: 128 },
}

const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  year: 'numeric',
}).format(new Date())

function getMeta(model) {
  if (modelMeta[model]) return modelMeta[model]
  if (/qwen/i.test(model)) return modelMeta['qwen3.6-plus']
  if (/deepseek/i.test(model)) return modelMeta['deepseek-v4-flash']
  if (/kimi/i.test(model)) return modelMeta['kimi-k2.6']
  if (/minimax/i.test(model)) return modelMeta['MiniMax-M2.5']
  if (/glm/i.test(model)) return modelMeta['glm-5.1']
  return { vendor: 'Unknown', family: 'Arena Model', accent: '#6b7280', price: 'N/A', context: 'N/A', priceValue: 5, contextScore: 0 }
}

const loadLeaderboard = async () => {
  loading.value = true
  try {
    const res = await fetch('/api/arena/leaderboard')
    const data = await res.json()
    if (data.code === 200 && data.data?.entries) {
      entries.value = data.data.entries
    }
  } catch {
    entries.value = []
  } finally {
    loading.value = false
  }
}

const loadPairwise = async () => {
  pairwiseLoading.value = true
  try {
    const res = await fetch('/api/arena/pairwise')
    const data = await res.json()
    if (data.code === 200 && data.data) {
      pairwiseData.value = data.data
    }
  } catch {
    pairwiseData.value = null
  } finally {
    pairwiseLoading.value = false
  }
}

onMounted(() => {
  loadLeaderboard()
  loadPairwise()
})

const activeMetricMeta = computed(() => metricOptions.find(item => item.id === activeMetric.value) || metricOptions[0])

const enrichedEntries = computed(() => entries.value.map((item) => {
  const matches = Number(item.matches || 0)
  const wins = Number(item.wins || 0)
  const losses = Number(item.losses || 0)
  const ties = Number(item.ties || 0)
  const resolved = wins + losses + ties
  const winRate = resolved ? ((wins + ties * 0.5) / resolved) * 100 : 0
  const confidenceScore = matches >= 10 ? 100 : matches >= 3 ? 64 : matches > 0 ? 32 : 12
  const meta = getMeta(item.model)
  const score = Number(item.score || 0)
  return {
    ...item,
    score,
    matches,
    wins,
    losses,
    ties,
    meta,
    winRate,
    confidenceScore,
    stability: matches >= 10 ? 'High' : matches >= 3 ? 'Medium' : 'Low',
    rankSpread: matches >= 10 ? '±2' : matches >= 3 ? '±5' : 'prelim',
    costEfficiency: meta.priceValue ? score / meta.priceValue : 0,
    contextScore: meta.contextScore || 0,
  }
}))

const totalMatches = computed(() =>
  enrichedEntries.value.reduce((sum, item) => sum + Number(item.matches || 0), 0),
)

const vendorFilterOptions = computed(() => {
  const vendors = new Map()
  enrichedEntries.value.forEach((item) => {
    vendors.set(item.meta.vendor, (vendors.get(item.meta.vendor) || 0) + 1)
  })
  return [
    { value: 'all', label: 'All providers', count: enrichedEntries.value.length },
    ...Array.from(vendors.entries()).sort((a, b) => a[0].localeCompare(b[0])).map(([label, count]) => ({
      value: label,
      label,
      count,
    })),
  ]
})

const filteredEntries = computed(() => {
  const text = searchText.value.trim().toLowerCase()
  return enrichedEntries.value.filter((item) => {
    if (text) {
      const haystack = `${item.model} ${item.meta.vendor} ${item.meta.family}`.toLowerCase()
      if (!haystack.includes(text)) return false
    }
    if (vendorFilter.value !== 'all' && item.meta.vendor !== vendorFilter.value) return false
    if (confidenceFilter.value === 'tested' && item.matches < 1) return false
    if (confidenceFilter.value === 'high' && item.stability !== 'High') return false
    return true
  })
})

const modelRows = computed(() => {
  const sortKey = activeMetricMeta.value.sortKey
  return filteredEntries.value.slice().sort((a, b) => Number(b[sortKey] || 0) - Number(a[sortKey] || 0))
})

const displayEntries = computed(() => modelRows.value)

function goVote() {
  router.push({ path: '/ai-plan', query: { q: '帮我规划一次适合模型竞技场对比的旅行需求' } })
}
</script>

<style>
.leaderboard-page {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  min-height: 100%;
  background: var(--color-page, #fafafa);
  color: var(--color-title);
  font-family: var(--font-family);
  transition: grid-template-columns 0.2s ease;
}

.leaderboard-page.sidebar-collapsed {
  grid-template-columns: 56px minmax(0, 1fr);
}

.arena-sidebar {
  min-height: 100%;
  padding: 18px 14px;
  border-right: 1px solid var(--color-border);
  background: var(--color-card);
  transition: width 0.18s ease, padding 0.18s ease;
}

.arena-sidebar.collapsed {
  width: 56px;
  padding: 18px 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow-x: hidden;
}

/* Hide everything except collapse btn + hint when collapsed */
.arena-sidebar.collapsed .sidebar-section:not(.view-section),
.arena-sidebar.collapsed .section-title,
.arena-sidebar.collapsed .view-toggle {
  display: none;
}

.sidebar-section {
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--color-secondary);
  font-size: 14px;
  font-weight: 900;
}

.section-title small {
  margin-left: auto;
  color: var(--color-hint);
}

.arena-sidebar.collapsed .collapse-btn {
  margin-bottom: 0;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-bottom: 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-secondary);
}

.view-toggle,
.segmented {
  display: grid;
  gap: 4px;
  padding: 5px;
  border-radius: 12px;
  background: var(--color-surface);
}

.view-toggle {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.segmented {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.view-toggle button,
.segmented button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 800;
}

.view-toggle button.active,
.segmented button.active {
  background: var(--color-card);
  color: var(--color-red);
  box-shadow: 0 1px 4px rgba(31, 31, 31, 0.08);
}

.metric-option,
.filter-option {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 46px;
  padding: 8px 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--color-body);
  text-align: left;
}

.metric-option:hover,
.filter-option:hover {
  background: #fff7f8;
}

.metric-option.active,
.filter-option.active {
  background: var(--color-soft-red);
  color: var(--color-red);
}

.metric-icon {
  width: 24px;
  text-align: center;
}

.metric-option strong,
.filter-option span {
  display: block;
  color: inherit;
  font-size: 13px;
  font-weight: 900;
}

.metric-option small,
.filter-option small {
  display: block;
  margin-top: 2px;
  color: var(--color-hint);
  font-size: 11px;
}

.filter-option {
  justify-content: space-between;
}

.arena-content {
  min-width: 0;
  padding: 34px 30px 44px;
}

.arena-top {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 26px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}

.title-line {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title-line h1 {
  margin: 0;
  color: var(--color-title);
  font-size: 32px;
  line-height: 1.15;
  letter-spacing: 0;
}

.title-line span {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 12px;
  border-radius: 10px;
  background: var(--color-soft-red);
  color: var(--color-red);
  font-size: 14px;
  font-weight: 900;
}

.arena-top p {
  max-width: 980px;
  margin: 10px 0 0;
  color: var(--color-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 14px;
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 800;
}

.meta-row span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.vote-btn {
  height: 40px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: var(--color-red);
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
  box-shadow: 0 12px 26px rgba(255, 36, 66, 0.22);
}

.arena-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px 0;
}

.plain-tool,
.icon-tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  border: 0;
  background: transparent;
  color: var(--color-body);
  font-size: 13px;
  font-weight: 900;
}

.tool-spacer {
  flex: 1;
}

.rank-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 900;
}

.rank-switch span {
  padding: 0 8px;
}

.rank-switch button {
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-secondary);
  font-weight: 900;
}

.rank-switch button.active {
  background: var(--color-card);
  color: var(--color-red);
}

.search-tool {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  width: 210px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-card);
  color: var(--color-hint);
}

.search-tool input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--color-title);
  background: transparent;
}

.icon-tool {
  width: 36px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-card);
}

:root[data-theme="dark"] .sidebar-section {
  border-bottom-color: var(--color-border);
}

:root[data-theme="dark"] .view-toggle button.active,
:root[data-theme="dark"] .segmented button.active,
:root[data-theme="dark"] .rank-switch button.active {
  background: var(--color-card-hover);
  box-shadow: none;
}

:root[data-theme="dark"] .metric-option:hover,
:root[data-theme="dark"] .filter-option:hover {
  background: var(--color-card-hover);
}

:root[data-theme="dark"] .arena-sidebar,
:root[data-theme="dark"] .collapse-btn,
:root[data-theme="dark"] .search-tool,
:root[data-theme="dark"] .icon-tool {
  background: var(--color-card);
  border-color: var(--color-border);
}

@media (max-width: 1020px) {
  .leaderboard-page {
    grid-template-columns: 1fr;
  }

  .leaderboard-page.sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .arena-sidebar {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .arena-sidebar.collapsed {
    width: auto;
  }

  .arena-content {
    padding: 24px 18px 96px;
  }

  .arena-top,
  .arena-tools {
    flex-wrap: wrap;
  }

  .tool-spacer {
    display: none;
  }
}

@media (max-width: 680px) {
  .leaderboard-page {
    display: block;
  }

  .arena-sidebar {
    position: relative;
    top: auto;
    z-index: 8;
    max-height: 46vh;
    overflow-y: auto;
    padding: 10px 14px;
    box-shadow: 0 12px 30px rgba(31, 31, 31, 0.08);
  }

  .arena-sidebar.collapsed {
    max-height: 58px;
    overflow: hidden;
    padding: 10px 14px;
  }

  .arena-sidebar.collapsed .view-section {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0;
    border-bottom: 0;
  }

  .arena-sidebar.collapsed .collapse-btn,
  .arena-sidebar.collapsed .section-title {
    margin-bottom: 0;
  }

  .arena-content {
    padding: 18px 14px 96px;
  }

  .arena-top {
    margin-bottom: 18px;
    padding-bottom: 18px;
  }

  .title-line h1 {
    font-size: 26px;
  }

  .vote-btn {
    width: 100%;
    justify-content: center;
  }

  .arena-tools {
    align-items: stretch;
    gap: 8px;
  }

  .plain-tool {
    flex: 1 1 auto;
  }

  .rank-switch {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) minmax(0, 1fr);
    width: 100%;
  }

  .rank-switch button {
    width: 100%;
  }

  .search-tool {
    width: 100%;
  }
}
</style>
