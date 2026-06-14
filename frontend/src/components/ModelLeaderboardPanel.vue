<template>
  <section class="arena-board">
    <div v-if="loading" class="board-state">
      <SvgIcon name="loader" :size="20" spin />
      <span>Loading arena results...</span>
    </div>

    <div v-else-if="!entries.length" class="board-state empty">
      <SvgIcon name="trophy" :size="24" />
      <span>暂无排行榜数据。去 AI 规划中完成模型对比投票后会出现在这里。</span>
      <button @click="$emit('refresh')">Refresh</button>
    </div>

    <template v-else>
      <header v-if="viewMode === 'overview'" class="overview-grid">
        <article
          v-for="(item, index) in topEntries"
          :key="`top-${item.model}`"
          class="overview-card"
          :class="`rank-${index + 1}`"
        >
          <div class="overview-rank">#{{ index + 1 }}</div>
          <div class="overview-model">
            <span class="model-logo" :style="{ color: item.meta?.accent || '#ff2442' }">
              <img v-if="getModelLogo(item)" :src="getModelLogo(item)" class="model-logo-img" :alt="item.model" /><span v-else class="model-logo-fallback">{{ item.model?.charAt(0) }}</span>
            </span>
            <div>
              <strong>{{ item.model }}</strong>
              <small>{{ item.meta?.vendor || 'Unknown' }}</small>
            </div>
          </div>
          <div class="overview-score">
            <span>{{ formatScore(item.score) }}</span>
            <small>{{ metricLabel }}</small>
          </div>
        </article>

        <article class="overview-card summary-card">
          <div class="summary-title">竞技场总览</div>
          <div class="summary-metrics">
            <span><strong>{{ entries.length }}</strong>模型数</span>
            <span><strong>{{ formatCount(totalVotes) }}</strong>投票数</span>
            <span><strong>{{ medianScore }}</strong>中位数</span>
          </div>
        </article>
      </header>

      <section v-if="viewMode === 'compact'" class="compact-matrix">
        <div class="matrix-header">
          <div>
            <h2>概览</h2>
          </div>
          <span>{{ metricLabel }}</span>
        </div>
        <div class="matrix-scroll">
          <table class="matrix-table">
            <thead>
              <tr>
                <th>Model</th>
                <th v-for="col in matrixColumns" :key="col.key">{{ col.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in entries" :key="`matrix-${rankBy}-${item.model}`">
                <td>
                  <div class="matrix-model">
                    <span class="model-logo" :style="{ color: item.meta?.accent || '#ff2442' }">
                      <img v-if="getModelLogo(item)" :src="getModelLogo(item)" class="model-logo-img" :alt="item.model" /><span v-else class="model-logo-fallback">{{ item.model?.charAt(0) }}</span>
                    </span>
                    <span>{{ item.model }}</span>
                  </div>
                </td>
                <td v-for="col in matrixColumns" :key="`${item.model}-${col.key}`">
                  <div class="metric-bar">
                    <span :style="{ width: `${metricBarWidth(item, col.key)}%` }"></span>
                  </div>
                  <strong>{{ formatMetric(item, col.key) }}</strong>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

      </section>

      <section v-if="viewMode === 'pareto'" class="pareto-view">
        <div class="matrix-header pareto-header">
          <div>
            <h2>Pareto 曲线图</h2>
          </div>
          <span>Pareto</span>
        </div>

        <div class="pareto-layout">
          <div class="pareto-chart-card">
            <div class="pareto-chart">
              <span class="pareto-axis-title pareto-axis-title-y">{{ paretoYAxisLabel }}</span>
              <span class="pareto-axis-title pareto-axis-title-x">{{ paretoXAxisLabel }}</span>
              <span
                v-for="tick in paretoYTicks"
                :key="`pareto-y-label-${tick.label}`"
                class="pareto-tick-label pareto-y-tick"
                :style="{ top: tick.top }"
              >
                {{ tick.label }}
              </span>
              <span
                v-for="tick in paretoXTicks"
                :key="`pareto-x-label-${tick.label}`"
                class="pareto-tick-label pareto-x-tick"
                :style="{ left: tick.left }"
              >
                {{ tick.label }}
              </span>
              <svg class="pareto-stat-grid" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <line v-for="tick in 9" :key="`pareto-x-${tick}`" :x1="8 + tick * 8.4" :x2="8 + tick * 8.4" y1="8" y2="92" />
                <line v-for="tick in 7" :key="`pareto-y-${tick}`" x1="8" x2="92" :y1="12 + tick * 10" :y2="12 + tick * 10" />
                <line class="axis" x1="8" x2="92" y1="92" y2="92" />
                <line class="axis" x1="8" x2="8" y1="8" y2="92" />
              </svg>

              <svg
                class="pareto-frontier-line"
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                aria-hidden="true"
              >
                <polyline
                  v-if="paretoPolyline"
                  :points="paretoPolyline"
                />
              </svg>

              <button
                v-for="point in paretoPoints"
                :key="`pareto-${rankBy}-${point.model}`"
                class="pareto-point"
                :class="{ frontier: point.frontier, top: point.rank <= 3, 'left-label': point.x > 72 }"
                :style="pointStyle(point)"
                :title="paretoTooltip(point)"
              >
                <img v-if="getModelLogo(point.item)" :src="getModelLogo(point.item)" class="pareto-logo-img" :alt="point.model" />
                <span v-else class="pareto-dot"></span>
                <span v-if="point.frontier || point.rank <= 8" class="pareto-label">
                  {{ shortModelName(point.label) }}
                </span>
              </button>
            </div>

            <div class="pareto-axis-labels">
              <span>低成本</span>
              <span>高成本</span>
              <span>低分</span>
              <span>高分</span>
            </div>
          </div>

          <aside class="pareto-side">
            <div class="pareto-summary-card">
              <div class="summary-title">Pareto Optimal Models</div>
              <div class="frontier-list">
                <button
                  v-for="item in frontierEntries.slice(0, 5)"
                  :key="`frontier-${rankBy}-${item.model}`"
                  class="frontier-item"
                >
                  <img v-if="getModelLogo(item.item)" :src="getModelLogo(item.item)" class="model-logo-img" :alt="item.item.model" />
                  <span v-else class="model-logo-fallback">{{ item.item.model?.charAt(0) }}</span>
                  <div>
                    <strong>{{ shortModelName(item.label) }}</strong>
                    <small>{{ formatMetricValue(item, metricKey) }} · {{ formatCostValue(item.cost) }}</small>
                  </div>
                  <span class="frontier-score">{{ formatRankingValue(item, metricKey) }}</span>
                </button>
              </div>
            </div>

            <div class="pareto-summary-card tone">
              <div class="summary-title">Tradeoff</div>
              <div class="summary-metrics">
                <span><strong>{{ cheapestLabel }}</strong> cheapest</span>
                <span><strong>{{ strongestLabel }}</strong> strongest</span>
                <span><strong>{{ frontierEntries.length }}</strong> frontier models</span>
              </div>
            </div>
          </aside>
        </div>
      </section>

      <div class="ranking-head">
        <div>
          <h2>模型排行</h2>
        </div>
        <span>Models</span>
      </div>

      <div class="board-table-wrap">
        <table class="board-table">
          <thead>
            <tr>
              <th class="rank-col">排名</th>
              <th class="spread-col">排名置信区间</th>
              <th>模型</th>
              <th class="number-col">分数</th>
              <th class="number-col">胜率</th>
              <th class="number-col">票数</th>
              <th class="number-col">价格 $/M</th>
              <th class="number-col">上下文窗口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in entries" :key="`${rankBy}-${item.model}`">
              <td class="rank-col">
                <span class="rank-number">{{ index + 1 }}</span>
              </td>
              <td class="spread-col">
                <span class="spread-text">{{ item.rankSpread || spreadFor(item, index) }}</span>
              </td>
              <td>
                <div class="model-cell">
                  <span class="model-logo" :style="{ color: item.meta?.accent || '#ff2442' }">
                    <img v-if="getModelLogo(item)" :src="getModelLogo(item)" class="model-logo-img" :alt="item.model" /><span v-else class="model-logo-fallback">{{ item.model?.charAt(0) }}</span>
                  </span>
                  <div>
                    <strong>{{ item.model }}</strong>
                    <small>{{ item.meta?.vendor || 'Unknown' }} · {{ item.meta?.family || 'Arena Model' }}</small>
                  </div>
                  <span :class="['stability-pill', stabilityClass(item)]">{{ item.stability || 'Low' }}</span>
                </div>
              </td>
              <td class="number-col score-cell">
                <strong>{{ formatScore(item.score) }}</strong>
                <small>±{{ scoreDelta(item) }}</small>
              </td>
              <td class="number-col">{{ formatPercent(item.winRate) }}</td>
              <td class="number-col">{{ formatCount(item.matches) }}</td>
              <td class="number-col muted-cell">{{ item.meta?.price || 'N/A' }}</td>
              <td class="number-col muted-cell">{{ item.meta?.context || 'N/A' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mobile-rank-list">
        <article v-for="(item, index) in entries" :key="`mobile-${rankBy}-${item.model}`" class="mobile-rank-card">
          <div class="mobile-rank-top">
            <span class="rank-number">#{{ index + 1 }}</span>
            <span :class="['stability-pill', stabilityClass(item)]">{{ item.stability || 'Low' }}</span>
          </div>
          <div class="model-cell">
            <span class="model-logo" :style="{ color: item.meta?.accent || '#ff2442' }">
              <img v-if="getModelLogo(item)" :src="getModelLogo(item)" class="model-logo-img" :alt="item.model" /><span v-else class="model-logo-fallback">{{ item.model?.charAt(0) }}</span>
            </span>
            <div>
              <strong>{{ item.model }}</strong>
              <small>{{ item.meta?.vendor || 'Unknown' }} · {{ item.meta?.family || 'Arena Model' }}</small>
            </div>
          </div>
          <div class="mobile-stats">
            <span><strong>{{ formatScore(item.score) }}</strong> Score</span>
            <span><strong>{{ formatPercent(item.winRate) }}</strong> Win</span>
            <span><strong>{{ formatCount(item.matches) }}</strong> Votes</span>
          </div>
        </article>
      </div>

      <!-- Bottom: Elo Ranking Chart + Pairwise Matrix -->
      <section class="arena-bottom-panel">
        <div class="arena-bottom-grid">
          <!-- Left: Elo / Bradley-Terry ranking bar chart -->
          <div class="elo-panel">
            <div class="matrix-header">
              <div>
                <h2>Elo 排行</h2>
              </div>
            </div>

            <div v-if="!eloEntries.length" class="board-state">
              <span>暂无排行榜数据。</span>
            </div>

            <div v-else class="elo-chart">
              <div
                v-for="(item, index) in eloEntries"
                :key="`elo-${index}`"
                class="elo-row"
              >
                <div class="elo-rank">
                  <span class="elo-rank-num">#{{ index + 1 }}</span>
                </div>
                <div class="elo-bar-wrap">
                  <div class="elo-bar-label">
                    <span class="elo-logo">
                      <img v-if="getModelLogo(item)" :src="getModelLogo(item)" class="elo-logo-img" :alt="item.model" />
                      <span v-else class="elo-logo-fallback" :style="{ background: item.meta?.accent || '#ff2442' }">{{ item.model?.charAt(0) }}</span>
                    </span>
                    <div class="elo-name-col" :title="item.model">
                      <strong>{{ item.model }}</strong>
                      <small>{{ item.meta?.vendor || '' }}</small>
                    </div>
                  </div>
                  <div class="elo-bar-track">
                    <div
                      class="elo-bar-fill"
                      :style="{
                        width: `${eloBarWidth(item)}%`,
                        background: item.meta?.accent || '#ff2442',
                      }"
                    ></div>
                  </div>
                  <div class="elo-bar-value">
                    <strong>{{ formatScore(item.score) }}</strong>
                    <small>±{{ scoreDelta(item) }}</small>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Right: Pairwise Head-to-Head Matrix -->
          <div class="pairwise-panel">
            <div class="matrix-header pairwise-header">
              <div>
                <h2>对战矩阵</h2>
              </div>
            </div>

            <div v-if="pairwiseLoading" class="board-state pairwise-loading">
              <SvgIcon name="loader" :size="18" spin />
              <span>正在加载对战矩阵...</span>
            </div>

            <div v-else-if="!pairwiseData || !pairwiseModels.length" class="board-state pairwise-empty">
              <span>暂无对战数据。去 AI 规划中完成模型对比投票后会出现在这里。</span>
            </div>

            <div v-else class="pairwise-scroll">
              <table class="pairwise-table">
                <thead>
                  <tr>
                    <th class="pairwise-corner">
                      <span class="corner-label">Row ↓ / Col →</span>
                    </th>
                    <th
                      v-for="model in pairwiseModels"
                      :key="`pw-col-${model}`"
                      class="pw-col-header"
                      :title="model"
                    >
                      <div class="pw-header-cell">
                        <img v-if="logoForModel(model)" :src="logoForModel(model)" class="pw-logo-img" :alt="model" />
                        <span v-else class="pw-logo-dot" :style="{ background: getModelMeta(model).accent }"></span>
                        <span class="pw-model-label">{{ shortPairwiseName(model) }}</span>
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="rowModel in pairwiseModels" :key="`pw-row-${rowModel}`">
                    <th class="pw-row-header" :title="rowModel">
                      <div class="pw-header-cell row">
                        <img v-if="logoForModel(rowModel)" :src="logoForModel(rowModel)" class="pw-logo-img" :alt="rowModel" />
                        <span v-else class="pw-logo-dot" :style="{ background: getModelMeta(rowModel).accent }"></span>
                        <span class="pw-model-label">{{ shortPairwiseName(rowModel) }}</span>
                      </div>
                    </th>
                    <td
                      v-for="colModel in pairwiseModels"
                      :key="`pw-cell-${rowModel}-${colModel}`"
                      :class="pairwiseCellClass(rowModel, colModel)"
                      :title="pairwiseCellTitle(rowModel, colModel)"
                    >
                      <template v-if="rowModel === colModel">
                        <span class="pw-diagonal">—</span>
                      </template>
                      <template v-else>
                        <span class="pw-pct">{{ formatPairwisePct(rowModel, colModel) }}</span>
                        <small class="pw-count">{{ formatPairwiseCount(rowModel, colModel) }}</small>
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="pairwise-legend">
              <span class="legend-item"><i class="legend-swatch win"></i> &gt;55% 优势</span>
              <span class="legend-item"><i class="legend-swatch neutral"></i> 45-55% 均势</span>
              <span class="legend-item"><i class="legend-swatch loss"></i> &lt;45% 劣势</span>
              <span class="legend-item"><i class="legend-swatch na"></i> 无数据</span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import SvgIcon from './SvgIcon.vue'
import { getModelLogo, getVendorLogo, getModelMeta, POINT_PALETTE } from '../utils/modelMeta'

const props = defineProps({
  entries: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rankBy: { type: String, default: 'models' },
  viewMode: { type: String, default: 'overview' },
  metricLabel: { type: String, default: 'Overall' },
  metricKey: { type: String, default: 'score' },
  pairwiseData: { type: Object, default: null },
  pairwiseLoading: { type: Boolean, default: false },
})

defineEmits(['refresh'])

const matrixColumns = [
  { key: 'score', label: '总览' },
  { key: 'winRate', label: '胜率' },
  { key: 'matches', label: '投票' },
  { key: 'confidenceScore', label: '置信度' },
  { key: 'costEfficiency', label: '成本效益' },
  { key: 'contextScore', label: '上下文' },
]

const topEntries = computed(() => props.entries.slice(0, 3))

const paretoXAxisLabel = computed(() => 'Price / 1M tokens')
const paretoYAxisLabel = computed(() => `${props.metricLabel} value`)

const paretoPoints = computed(() => {
  const points = props.entries.map((item, index) => {
    const cost = getCostValue(item)
    const value = getMetricValue(item, props.metricKey)
    return {
      item,
      model: item.model,
      label: item.model,
      cost,
      value,
      rank: index + 1,
      frontier: false,
      x: 50,
      y: 50,
      color: pointColor(item, index),
    }
  })

  if (!points.length) return []

  const frontier = buildFrontier(points)
  const minCost = Math.min(...points.map(point => point.cost))
  const maxCost = Math.max(...points.map(point => point.cost))
  const minValue = Math.min(...points.map(point => point.value))
  const maxValue = Math.max(...points.map(point => point.value))
  const costSpan = Math.max(0.0001, maxCost - minCost)
  const valueSpan = Math.max(0.0001, maxValue - minValue)

  return points.map((point) => ({
    ...point,
    frontier: frontier.some(item => item.model === point.model),
    x: 10 + ((point.cost - minCost) / costSpan) * 80,
    y: 88 - ((point.value - minValue) / valueSpan) * 76,
  }))
})

const paretoBounds = computed(() => {
  const points = paretoPoints.value
  if (!points.length) {
    return {
      minCost: 0,
      maxCost: 1,
      minValue: 0,
      maxValue: 1,
      costSpan: 1,
      valueSpan: 1,
    }
  }

  const minCost = Math.min(...points.map(point => point.cost))
  const maxCost = Math.max(...points.map(point => point.cost))
  const minValue = Math.min(...points.map(point => point.value))
  const maxValue = Math.max(...points.map(point => point.value))
  return {
    minCost,
    maxCost,
    minValue,
    maxValue,
    costSpan: Math.max(0.0001, maxCost - minCost),
    valueSpan: Math.max(0.0001, maxValue - minValue),
  }
})

const paretoYTicks = computed(() => {
  const bounds = paretoBounds.value
  return Array.from({ length: 5 }, (_, index) => {
    const ratio = index / 4
    const value = bounds.maxValue - bounds.valueSpan * ratio
    return {
      label: formatAxisValue(value, props.metricKey),
      top: `${12 + ratio * 76}%`,
    }
  })
})

const paretoXTicks = computed(() => {
  const bounds = paretoBounds.value
  return Array.from({ length: 5 }, (_, index) => {
    const ratio = index / 4
    const value = bounds.minCost + bounds.costSpan * ratio
    return {
      label: formatCostValue(value),
      left: `${10 + ratio * 80}%`,
    }
  })
})

const frontierEntries = computed(() =>
  paretoPoints.value
    .filter(point => point.frontier)
    .slice()
    .sort((a, b) => b.value - a.value || a.cost - b.cost),
)

const paretoCurvePoints = computed(() => {
  const frontier = frontierEntries.value.slice().sort((a, b) => a.cost - b.cost)
  if (frontier.length >= 2) return frontier
  return paretoPoints.value.slice().sort((a, b) => a.cost - b.cost || b.value - a.value)
})

const paretoPolyline = computed(() =>
  paretoCurvePoints.value
    .map(point => `${point.x.toFixed(2)},${point.y.toFixed(2)}`)
    .join(' '),
)

const cheapestLabel = computed(() => {
  const cheapest = [...paretoPoints.value].sort((a, b) => a.cost - b.cost)[0]
  return cheapest ? cheapest.label : '-'
})

const strongestLabel = computed(() => {
  const strongest = [...paretoPoints.value].sort((a, b) => b.value - a.value)[0]
  return strongest ? strongest.label : '-'
})

const totalVotes = computed(() =>
  props.entries.reduce((sum, item) => sum + Number(item.matches || 0), 0),
)

const medianScore = computed(() => {
  const scores = props.entries.map(item => Number(item.score || 0)).sort((a, b) => a - b)
  if (!scores.length) return 0
  return Math.round(scores[Math.floor(scores.length / 2)])
})

function rawMetric(item, key) {
  return Number(item?.[key] || 0)
}

function metricMax(key) {
  return Math.max(1, ...props.entries.map(item => rawMetric(item, key)))
}

function metricBarWidth(item, key) {
  return Math.max(6, Math.min(100, (rawMetric(item, key) / metricMax(key)) * 100))
}

function formatScore(score) {
  return Math.round(Number(score || 0))
}

function formatPercent(value) {
  return `${Number(value || 0).toFixed(1)}%`
}

function formatCount(value) {
  const n = Number(value || 0)
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function getCostValue(item) {
  const cost = Number(item?.costValue ?? item?.priceValue ?? item?.meta?.priceValue ?? 5)
  return Number.isFinite(cost) && cost > 0 ? cost : 5
}

function getMetricValue(item, key) {
  const value = Number(item?.[key] ?? 0)
  return Number.isFinite(value) ? value : 0
}

function formatCostValue(value) {
  const cost = Number(value || 0)
  if (!cost) return 'N/A'
  return `$${cost.toFixed(cost < 10 ? 2 : 0)}`
}

function formatRankingValue(item, key) {
  if (key === 'score') return formatScore(item.value)
  if (key === 'winRate') return formatPercent(item.value)
  if (key === 'matches') return formatCount(item.value)
  if (key === 'confidenceScore') return `${Math.round(Number(item.value || 0))}%`
  if (key === 'costEfficiency') return Number(item.value || 0).toFixed(1)
  if (key === 'contextScore') return item.item.meta?.context || 'N/A'
  return String(item.value)
}

function formatMetricValue(item, key) {
  if (key === 'score') return formatScore(item.value)
  if (key === 'winRate') return formatPercent(item.value)
  if (key === 'matches') return formatCount(item.value)
  if (key === 'confidenceScore') return `${Math.round(Number(item.value || 0))}%`
  if (key === 'costEfficiency') return Number(item.value || 0).toFixed(1)
  if (key === 'contextScore') return item.item.meta?.context || 'N/A'
  return String(item.value)
}

function formatAxisValue(value, key) {
  if (key === 'score') return formatScore(value)
  if (key === 'winRate') return formatPercent(value)
  if (key === 'matches') return formatCount(value)
  if (key === 'confidenceScore') return `${Math.round(Number(value || 0))}%`
  if (key === 'costEfficiency') return Number(value || 0).toFixed(1)
  if (key === 'contextScore') return `${Math.round(Number(value || 0))}K`
  return String(Math.round(Number(value || 0)))
}

function paretoTooltip(point) {
  return [
    point.label,
    `${props.metricLabel}: ${formatRankingValue(point, props.metricKey)}`,
    `Cost: ${formatCostValue(point.cost)}`,
  ].join(' · ')
}

function pointStyle(point) {
  return {
    left: `${point.x}%`,
    top: `${point.y}%`,
    '--point-color': point.color,
  }
}

function pointColor(item, index) {
  return item?.meta?.accent || POINT_PALETTE[index % POINT_PALETTE.length]
}

function shortModelName(label) {
  const text = String(label || '')
  return text.length > 24 ? `${text.slice(0, 21)}...` : text
}

function buildFrontier(points) {
  const sorted = [...points].sort((a, b) => a.cost - b.cost || b.value - a.value)
  const frontier = []
  let bestValue = -Infinity
  sorted.forEach((point) => {
    if (point.value > bestValue) {
      frontier.push(point)
      bestValue = point.value
    }
  })
  return frontier
}

function formatMetric(item, key) {
  if (key === 'score') return formatScore(item.score)
  if (key === 'winRate') return formatPercent(item.winRate)
  if (key === 'matches') return formatCount(item.matches)
  if (key === 'confidenceScore') return `${Math.round(Number(item.confidenceScore || 0))}%`
  if (key === 'costEfficiency') return Number(item.costEfficiency || 0).toFixed(1)
  if (key === 'contextScore') return item.meta?.context || 'N/A'
  return item[key] || '-'
}

function scoreDelta(item) {
  const matches = Number(item.matches || 0)
  if (matches >= 10) return 2
  if (matches >= 3) return 5
  return 9
}

function spreadFor(item, index) {
  const start = Math.max(1, index + 1 - scoreDelta(item))
  const end = index + 1 + scoreDelta(item)
  return `${start} - ${end}`
}

function stabilityClass(item) {
  const value = String(item.stability || '').toLowerCase()
  if (value === 'high') return 'high'
  if (value === 'medium') return 'medium'
  return 'low'
}


// ── Elo Ranking Chart ───────────────────────

const eloEntries = computed(() => {
  return props.entries.slice().sort((a, b) => Number(b.score || 0) - Number(a.score || 0))
})

const eloMaxScore = computed(() => {
  return Math.max(1, ...eloEntries.value.map(e => Number(e.score || 0)))
})

function eloBarWidth(item) {
  const score = Number(item.score || 0)
  return Math.max(2, Math.min(100, (score / eloMaxScore.value) * 100))
}

// ── Pairwise Matrix ──────────────────────────

const pairwiseModels = computed(() => {
  if (!props.pairwiseData?.models) return []
  return props.pairwiseData.models
})

const pairwiseMatrix = computed(() => {
  return props.pairwiseData?.matrix || {}
})

function logoForModel(modelName) {
  const meta = getModelMeta(modelName)
  return getVendorLogo(meta.vendor)
}

function shortPairwiseName(model) {
  const text = String(model || '')
  // Abbreviate known long model names — keep informative but compact
  const map = {
    'deepseek-v4-flash': 'DS Flash',
    'deepseek-chat': 'DS Chat',
    'deepseek-reasoner': 'DS Reason',
    'kimi-k2.6': 'Kimi K2.6',
    'MiniMax-M2.5': 'MiniMax',
    'qwen3.6-plus': 'Qwen Plus',
    'glm-5.1': 'GLM 5.1',
  }
  if (map[text]) return map[text]
  // Generic fallback: keep first 12 chars, append … if truncated
  return text.length > 12 ? text.slice(0, 11) + '…' : text
}

function getCell(row, col) {
  const matrix = pairwiseMatrix.value
  const rowData = matrix?.[row]
  if (!rowData) return null
  return rowData[col] || null
}

function pairwiseCellClass(row, col) {
  if (row === col) return 'pw-self'
  const cell = getCell(row, col)
  if (!cell || cell.total === 0) return 'pw-no-data'
  const wr = cell.winRate
  if (wr > 0.55) return 'pw-win'
  if (wr >= 0.45) return 'pw-neutral'
  return 'pw-loss'
}

function pairwiseCellTitle(row, col) {
  if (row === col) return `${row} 自身`
  const cell = getCell(row, col)
  if (!cell || cell.total === 0) return `${row} vs ${col} · 暂无投票`
  return `${row} vs ${col}\n胜 ${cell.wins} · 负 ${cell.losses} · 平 ${cell.ties}\n总 ${cell.total} 场 · 胜率 ${(cell.winRate * 100).toFixed(1)}%`
}

function formatPairwisePct(row, col) {
  const cell = getCell(row, col)
  if (!cell || cell.total === 0) return '–'
  return `${(cell.winRate * 100).toFixed(0)}%`
}

function formatPairwiseCount(row, col) {
  const cell = getCell(row, col)
  if (!cell || cell.total === 0) return ''
  return `${cell.total}v`
}
</script>

<style scoped>
.arena-board {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-card);
  box-shadow: 0 14px 36px rgba(31, 31, 31, 0.05);
}

.board-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 360px;
  color: var(--color-secondary);
  font-size: 14px;
}

.board-state.empty {
  flex-direction: column;
  padding: 24px;
  text-align: center;
}

.board-state button {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--color-red);
  color: #ffffff;
  font-weight: 900;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) 1.1fr;
  gap: 1px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-border);
}

.overview-card {
  min-height: 156px;
  padding: 18px;
  background:
    linear-gradient(180deg, #ffffff, #fffafa);
}

.overview-rank {
  margin-bottom: 18px;
  color: var(--color-red);
  font-size: 13px;
  font-weight: 950;
}

.overview-model,
.model-cell,
.matrix-model {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.overview-model strong,
.model-cell strong,
.matrix-model span:last-child {
  display: block;
  overflow: hidden;
  color: var(--color-title);
  font-size: 15px;
  font-weight: 950;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-model small,
.model-cell small {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-logo-img {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  object-fit: contain;
  flex-shrink: 0;
}

.model-logo-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 800;
  color: inherit;
  background: rgba(255, 36, 66, 0.12);
  flex-shrink: 0;
}

.model-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #fff1f3;
  font-size: 13px;
  font-weight: 950;
}

.overview-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 18px;
}

.overview-score span {
  color: var(--color-title);
  font-size: 28px;
  font-weight: 950;
  line-height: 1;
}

.overview-score small {
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 900;
}

.summary-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: #fff7f8;
}

.summary-title {
  color: var(--color-title);
  font-size: 18px;
  font-weight: 950;
}

.summary-metrics {
  display: grid;
  gap: 10px;
}

.summary-metrics span {
  display: flex;
  justify-content: space-between;
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 850;
}

.summary-metrics strong {
  color: var(--color-red);
  font-size: 18px;
}

.compact-matrix {
  border-bottom: 1px solid var(--color-border);
}

.pareto-view {
  border-bottom: 1px solid var(--color-border);
}

.matrix-header,
.ranking-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.matrix-header h2,
.ranking-head h2 {
  margin: 0 0 4px;
  color: var(--color-title);
  font-size: 18px;
  font-weight: 950;
  letter-spacing: 0;
}

.matrix-header p,
.ranking-head p {
  margin: 0;
  color: var(--color-secondary);
  font-size: 13px;
}

.matrix-header > span,
.ranking-head > span {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff1f3;
  color: var(--color-red);
  font-size: 12px;
  font-weight: 950;
  white-space: nowrap;
}

.pareto-header {
  padding-bottom: 12px;
}

.pareto-header h2 {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.pareto-header h2::before {
  content: '';
  width: 10px;
  height: 10px;
  border: 2px solid #22c55e;
  border-radius: 3px;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.12);
}

.pareto-header p {
  max-width: 720px;
  color: transparent;
  font-size: 0;
}

.pareto-header p::after {
  content: 'Compare the selected ranking metric against estimated model cost. The green path highlights Pareto optimal models, matching the Arena frontier layout.';
  color: var(--color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.pareto-header > span {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.pareto-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 0;
  margin: 0 18px 18px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfffc 100%);
  box-shadow: 0 18px 48px rgba(17, 24, 39, 0.07);
}

.pareto-chart-card {
  min-width: 0;
  padding: 18px 18px 16px;
  background: #ffffff;
}

.pareto-chart-card::before {
  content: 'Pareto Frontier';
  display: block;
  margin-bottom: 3px;
  color: var(--color-title);
  font-size: 20px;
  font-weight: 950;
}

.pareto-chart-card::after {
  content: 'Selected metric value at each cost point';
  display: block;
  margin-bottom: 12px;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 850;
}

.pareto-chart {
  position: relative;
  height: 460px;
  overflow: hidden;
  border: 1px solid #d6dde4;
  border-radius: 10px;
  background:
    linear-gradient(180deg, #ffffff 0%, #f7f9fa 100%);
}

.pareto-chart::before {
  display: none;
}

.pareto-chart::after {
  display: none;
}

.pareto-stat-grid {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.pareto-stat-grid line {
  stroke: rgba(15, 23, 42, 0.26);
  stroke-width: 1.15;
  stroke-dasharray: 5 5;
  vector-effect: non-scaling-stroke;
}

.pareto-stat-grid line.axis {
  stroke: rgba(15, 23, 42, 0.5);
  stroke-dasharray: none;
  stroke-width: 1.4;
}

.pareto-axis-title {
  position: absolute;
  z-index: 2;
  color: #0f172a;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.01em;
}

.pareto-axis-title-y {
  left: 12px;
  top: 12px;
}

.pareto-axis-title-x {
  right: 14px;
  bottom: 12px;
}

.pareto-tick-label {
  position: absolute;
  z-index: 2;
  color: #475569;
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
  pointer-events: none;
}

.pareto-y-tick {
  left: 14px;
  transform: translateY(-50%);
}

.pareto-x-tick {
  bottom: 28px;
  transform: translateX(-50%);
}

.pareto-frontier-line {
  position: absolute;
  inset: 0;
  z-index: 3;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.pareto-frontier-line polyline {
  fill: none;
  stroke: #22c55e;
  stroke-linecap: square;
  stroke-linejoin: round;
  stroke-width: 3;
  vector-effect: non-scaling-stroke;
  filter: drop-shadow(0 4px 6px rgba(34, 197, 94, 0.26));
}

.pareto-point {
  position: absolute;
  z-index: 4;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  transform: translate(-50%, -50%);
}

.pareto-logo-img {
  display: block;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  object-fit: contain;
  box-shadow: 0 2px 6px rgba(0,0,0,0.12);
}

.pareto-dot {
  display: block;
  width: 9px;
  height: 9px;
  border: 1px solid rgba(17, 24, 39, 0.18);
  border-radius: 2px;
  background: var(--point-color, #64748b);
  box-shadow: 0 2px 5px rgba(17, 24, 39, 0.14);
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.pareto-point.frontier {
  z-index: 5;
}

.pareto-point.frontier .pareto-dot {
  width: 15px;
  height: 15px;
  border: 2px solid #22c55e;
  background: #f0fff5;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.14), 0 10px 20px rgba(34, 197, 94, 0.22);
}

.pareto-point.top:not(.frontier) .pareto-dot {
  border-color: rgba(15, 23, 42, 0.42);
  background: var(--point-color, #0f172a);
}

.pareto-point:hover .pareto-dot {
  transform: scale(1.35);
  box-shadow: 0 0 0 5px rgba(255, 36, 66, 0.12), 0 10px 22px rgba(17, 24, 39, 0.18);
}

.pareto-label {
  position: absolute;
  left: 20px;
  top: 50%;
  max-width: 176px;
  overflow: hidden;
  padding: 4px 8px;
  border: 1px solid rgba(31, 41, 55, 0.75);
  border-radius: 4px;
  background: rgba(31, 41, 55, 0.95);
  color: #ffffff;
  font-size: 11px;
  font-weight: 950;
  line-height: 1.05;
  text-overflow: ellipsis;
  white-space: nowrap;
  transform: translateY(-50%);
  box-shadow: 0 8px 18px rgba(17, 24, 39, 0.16);
}

.pareto-point.left-label .pareto-label {
  left: auto;
  right: 20px;
}

.pareto-axis-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--color-secondary);
  font-size: 11px;
  font-weight: 900;
}

.pareto-axis-labels::before {
  content: 'Lower cost';
  font-size: 11px;
}

.pareto-axis-labels::after {
  content: 'Higher cost';
  font-size: 11px;
}

.pareto-axis-labels strong {
  color: var(--color-title);
  font-size: 11px;
  font-weight: 950;
}

.pareto-side {
  display: grid;
  align-content: stretch;
  border-left: 1px solid var(--color-border);
  background: linear-gradient(180deg, #fbfffc 0%, #ffffff 100%);
  overflow: hidden;
}

.pareto-summary-card {
  padding: 18px 18px 18px;
  background: transparent;
}

.pareto-summary-card:first-child {
  min-height: 100%;
}

.pareto-summary-card.tone {
  display: none;
}

.pareto-summary-card .summary-title {
  position: relative;
  padding-left: 22px;
  color: var(--color-title);
  font-size: 17px;
  font-weight: 950;
}

.pareto-summary-card .summary-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  width: 10px;
  height: 10px;
  border: 2px solid #22c55e;
  border-radius: 3px;
  background: #ffffff;
}

.pareto-summary-card p {
  margin: 7px 0 14px;
  color: transparent;
  font-size: 0;
}

.pareto-summary-card p::after {
  content: 'Best non-dominated models across the current cost curve.';
  color: var(--color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.frontier-list {
  position: relative;
  display: grid;
  gap: 8px;
  padding-left: 12px;
}

.frontier-list::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 10px;
  bottom: 10px;
  width: 2px;
  border-radius: 999px;
  background: #22c55e;
}

.frontier-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 10px 10px 10px 16px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-title);
  text-align: left;
}

.frontier-logo {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  margin-right: 2px;
  border-radius: 0;
  background: transparent;
  font-size: 18px;
}

.frontier-item > div {
  min-width: 0;
  flex: 1;
}

.frontier-item::before {
  content: '';
  position: absolute;
  left: -11px;
  top: 21px;
  width: 9px;
  height: 9px;
  border: 2px solid #22c55e;
  border-radius: 3px;
  background: #ffffff;
}

.frontier-item:hover {
  border-color: rgba(34, 197, 94, 0.2);
  background: rgba(34, 197, 94, 0.08);
}

.frontier-item strong {
  display: block;
  max-width: 174px;
  overflow: hidden;
  color: var(--color-title);
  font-size: 14px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frontier-item small {
  display: block;
  margin-top: 4px;
  color: var(--color-secondary);
  font-size: 11px;
  font-weight: 850;
}

.frontier-score {
  color: #15803d;
  font-size: 14px;
  font-weight: 950;
  white-space: nowrap;
}

.matrix-scroll,
.board-table-wrap {
  overflow-x: auto;
}

.matrix-table,
.board-table {
  width: 100%;
  min-width: 1040px;
  border-collapse: collapse;
  color: var(--color-title);
}

.matrix-table th,
.board-table th {
  padding: 13px 16px;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  background: #fffafa;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 950;
  text-align: left;
  white-space: nowrap;
}

.matrix-table td,
.board-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #f1f1f1;
  color: var(--color-body);
  font-size: 14px;
  vertical-align: middle;
}

.matrix-table tbody tr,
.board-table tbody tr {
  transition: background 0.16s ease;
}

.matrix-table tbody tr:hover,
.board-table tbody tr:hover {
  background: #fff7f8;
}

.metric-bar {
  width: 92px;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #f1f1f1;
}

.metric-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--gradient-brand);
}

.matrix-table td strong {
  display: block;
  margin-top: 6px;
  color: var(--color-title);
  font-size: 12px;
  font-weight: 950;
}

.rank-col {
  width: 90px;
  text-align: center;
}

.spread-col {
  width: 130px;
}

.number-col {
  text-align: right;
}

.rank-number {
  color: var(--color-title);
  font-size: 18px;
  font-weight: 950;
}

.spread-text {
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.model-cell {
  min-width: 330px;
}

.stability-pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  margin-left: auto;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 950;
  white-space: nowrap;
}

.stability-pill.high {
  background: #ecfdf5;
  color: #047857;
}

.stability-pill.medium {
  background: #fffbeb;
  color: #b45309;
}

.stability-pill.low {
  background: #f7f7f7;
  color: var(--color-secondary);
}

.score-cell strong {
  color: var(--color-title);
  font-size: 15px;
  font-weight: 950;
}

.score-cell small {
  margin-left: 6px;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 900;
}

.muted-cell {
  color: var(--color-secondary);
  font-weight: 800;
}

.mobile-rank-list {
  display: none;
}

:root[data-theme="dark"] .arena-board {
  background: var(--color-card);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.36);
}

:root[data-theme="dark"] .overview-card {
  background: linear-gradient(180deg, var(--color-card), var(--color-card-muted));
}

:root[data-theme="dark"] .summary-card,
:root[data-theme="dark"] .pareto-summary-card.tone {
  background: var(--color-card-muted);
}

:root[data-theme="dark"] .model-logo,
:root[data-theme="dark"] .matrix-header > span,
:root[data-theme="dark"] .ranking-head > span {
  background: var(--color-soft-red);
}

:root[data-theme="dark"] .pareto-chart-card,
:root[data-theme="dark"] .pareto-summary-card,
:root[data-theme="dark"] .mobile-rank-card {
  background: var(--color-card);
  border-color: var(--color-border);
}

:root[data-theme="dark"] .pareto-header > span {
  background: rgba(34, 197, 94, 0.16);
  color: #86efac;
}

:root[data-theme="dark"] .pareto-layout {
  background: var(--color-card);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.32);
}

:root[data-theme="dark"] .pareto-chart-card,
:root[data-theme="dark"] .pareto-side {
  background: var(--color-card);
}

:root[data-theme="dark"] .pareto-chart {
  border-color: var(--color-border);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02) 0%, rgba(255, 255, 255, 0.01) 100%),
    var(--color-card-muted);
}

:root[data-theme="dark"] .pareto-chart::before {
  color: var(--color-title);
}

:root[data-theme="dark"] .pareto-stat-grid line {
  stroke: rgba(255, 255, 255, 0.18);
}

:root[data-theme="dark"] .pareto-stat-grid line.axis {
  stroke: rgba(255, 255, 255, 0.34);
}

:root[data-theme="dark"] .pareto-dot {
  border-color: rgba(255, 255, 255, 0.3);
}

:root[data-theme="dark"] .pareto-point.top:not(.frontier) .pareto-dot {
  background: var(--point-color, var(--color-title));
}

:root[data-theme="dark"] .pareto-point.frontier .pareto-dot {
  background: rgba(34, 197, 94, 0.18);
}

:root[data-theme="dark"] .pareto-label {
  background: rgba(17, 24, 39, 0.96);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.28);
}

:root[data-theme="dark"] .pareto-header h2::before,
:root[data-theme="dark"] .pareto-summary-card .summary-title::before,
:root[data-theme="dark"] .frontier-item::before {
  background: var(--color-card);
}

:root[data-theme="dark"] .frontier-item {
  background: transparent;
  border-color: transparent;
}

:root[data-theme="dark"] .frontier-logo {
  background: transparent;
}

:root[data-theme="dark"] .frontier-item:hover {
  border-color: rgba(34, 197, 94, 0.24);
  background: rgba(34, 197, 94, 0.1);
}

:root[data-theme="dark"] .matrix-table th,
:root[data-theme="dark"] .board-table th {
  background: var(--color-card-muted);
}

:root[data-theme="dark"] .matrix-table td,
:root[data-theme="dark"] .board-table td {
  border-bottom-color: var(--color-border);
}

:root[data-theme="dark"] .matrix-table tbody tr:hover,
:root[data-theme="dark"] .board-table tbody tr:hover {
  background: var(--color-card-hover);
}

:root[data-theme="dark"] .metric-bar {
  background: var(--color-surface);
}

:root[data-theme="dark"] .stability-pill.high {
  background: rgba(16, 185, 129, 0.16);
  color: #86efac;
}

:root[data-theme="dark"] .stability-pill.medium {
  background: rgba(245, 158, 11, 0.16);
  color: #fbbf24;
}

:root[data-theme="dark"] .stability-pill.low {
  background: var(--color-card-hover);
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pareto-layout {
    grid-template-columns: 1fr;
  }

  .pareto-side {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .arena-bottom-grid {
    grid-template-columns: 1fr;
  }

  .arena-bottom-grid > *:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}

@media (max-width: 720px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .overview-card {
    min-height: auto;
  }

  .matrix-header,
  .ranking-head {
    flex-direction: column;
    padding: 16px;
  }

  .pareto-layout {
    margin: 0 14px 16px;
    padding: 0;
  }

  .pareto-chart-card {
    padding: 14px;
  }

  .pareto-chart {
    height: 320px;
    background-size: 56px 56px, 56px 56px, 100% 100%;
  }

  .pareto-label {
    max-width: 118px;
    font-size: 10px;
  }

  .pareto-side {
    grid-template-columns: 1fr;
  }

  .matrix-scroll,
  .board-table-wrap {
    display: none;
  }

  .mobile-rank-list {
    display: grid;
    gap: 10px;
    padding: 0 14px 16px;
  }

  .mobile-rank-card {
    display: grid;
    gap: 12px;
    padding: 14px;
    border: 1px solid var(--color-border);
    border-radius: 10px;
    background: #ffffff;
  }

  .mobile-rank-top,
  .mobile-stats {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-stats span {
    display: grid;
    gap: 2px;
    color: var(--color-secondary);
    font-size: 11px;
    font-weight: 850;
  }

  .mobile-stats strong {
    color: var(--color-title);
    font-size: 15px;
    font-weight: 950;
  }

  .model-cell {
    min-width: 0;
  }

  .arena-bottom-grid {
    grid-template-columns: 1fr;
  }

  .arena-bottom-grid > *:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .elo-bar-wrap {
    grid-template-columns: 1fr auto;
  }

  .elo-name-col small,
  .elo-bar-track {
    display: none;
  }

  .elo-panel {
    padding: 14px 12px;
  }

  .pairwise-panel {
    padding: 14px 12px;
  }
}

:root[data-theme="dark"] .mobile-rank-card {
  background: var(--color-card);
  border-color: var(--color-border);
}

/* ── Bottom Arena Panel ──────────────────── */

.arena-bottom-panel {
  border-top: 1px solid var(--color-border);
  padding: 0;
}

.arena-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(0, 2.2fr);
  gap: 0;
  min-height: 360px;
}

.arena-bottom-grid > * {
  min-width: 0;
  overflow: hidden;
}

.arena-bottom-grid > *:first-child {
  border-right: 1px solid var(--color-border);
}

/* ── Elo Ranking Chart ───────────────────── */

.elo-panel {
  padding: 18px 18px 18px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.elo-chart {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.elo-row {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-height: 38px;
}

.elo-rank {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  flex-shrink: 0;
}

.elo-rank-num {
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 950;
}

.elo-bar-wrap {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1.8fr) auto;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--color-card);
  border: 1px solid #f1f1f1;
  transition: background 0.14s ease, border-color 0.14s ease;
}

.elo-row:hover .elo-bar-wrap {
  background: #fff7f8;
  border-color: rgba(255, 36, 66, 0.18);
}

.elo-bar-label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.elo-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 3px;
  overflow: hidden;
}

.elo-logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.elo-logo-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 800;
  color: #fff;
}

.elo-name-col {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.elo-name-col strong {
  display: block;
  color: var(--color-title);
  font-size: 12px;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.elo-name-col small {
  display: block;
  margin-top: 1px;
  color: var(--color-hint);
  font-size: 10px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.elo-bar-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #f5f5f5;
}

.elo-bar-fill {
  height: 100%;
  border-radius: inherit;
  min-width: 2px;
  transition: width 0.35s ease;
}

.elo-bar-value {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 46px;
  flex-shrink: 0;
}

.elo-bar-value strong {
  color: var(--color-title);
  font-size: 14px;
  font-weight: 950;
  line-height: 1.2;
}

.elo-bar-value small {
  color: var(--color-secondary);
  font-size: 10px;
  font-weight: 800;
}

/* ── Pairwise Matrix ──────────────────────── */

.pairwise-panel {
  padding: 18px 18px 18px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.pairwise-header {
  /* 与 .matrix-header 的 padding-bottom: 18px 保持一致，不再覆盖 */
}

.pairwise-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fff1f3;
  color: var(--color-red);
  font-size: 12px;
  font-weight: 950;
  white-space: nowrap;
}

.pairwise-loading,
.pairwise-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-secondary);
  font-size: 13px;
}

.pairwise-scroll {
  margin-top: 10px;
  padding: 0 18px 18px;
}

.pairwise-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  color: var(--color-title);
  font-size: 12px;
}

.pairwise-corner {
  width: 60px;
  padding: 4px 4px;
  text-align: right;
  vertical-align: bottom;
  border-bottom: 1px solid var(--color-border);
}

.corner-label {
  color: var(--color-hint);
  font-size: 9px;
  font-weight: 800;
}

.pw-col-header,
.pw-row-header {
  padding: 5px 6px;
  border-bottom: 1px solid var(--color-border);
  background: #fffafa;
  text-align: center;
  white-space: nowrap;
  min-width: 64px;
}

.pw-row-header {
  text-align: right;
  border-bottom: 0;
  border-right: 1px solid var(--color-border);
  background: #fffafa;
  min-width: 74px;
}

.pw-header-cell {
  display: flex;
  align-items: center;
  gap: 5px;
}

.pw-header-cell.row {
  justify-content: flex-end;
}

.pw-logo-img {
  width: 14px;
  height: 14px;
  border-radius: 2px;
  object-fit: contain;
  flex-shrink: 0;
}

.pw-logo-dot {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  flex-shrink: 0;
}

.pw-model-label {
  color: var(--color-title);
  font-size: 10px;
  font-weight: 900;
}

.pairwise-table td {
  padding: 5px 4px;
  border: 1px solid #f1f1f1;
  text-align: center;
  vertical-align: middle;
  transition: background 0.14s ease, border-color 0.14s ease;
}

.pw-self {
  background: #f5f5f5;
  cursor: default;
}

.pw-diagonal {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.pw-win {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.pairwise-table tbody tr:hover td.pw-win {
  background: #d1fae5;
}

.pw-neutral {
  background: #fffbeb;
  border-color: #fde68a;
}

.pairwise-table tbody tr:hover td.pw-neutral {
  background: #fef3c7;
}

.pw-loss {
  background: #fef2f2;
  border-color: #fecaca;
}

.pairwise-table tbody tr:hover td.pw-loss {
  background: #fee2e2;
}

.pw-no-data {
  background: #fafafa;
  color: var(--color-muted);
}

.pw-pct {
  display: block;
  color: var(--color-title);
  font-size: 12px;
  font-weight: 950;
  line-height: 1.2;
}

.pw-win .pw-pct { color: #047857; }
.pw-neutral .pw-pct { color: #b45309; }
.pw-loss .pw-pct { color: #b91c1c; }

.pw-count {
  display: block;
  margin-top: 1px;
  color: var(--color-hint);
  font-size: 9px;
  font-weight: 800;
  line-height: 1;
}

.pw-win .pw-count { color: #6ee7b7; }
.pw-neutral .pw-count { color: #fcd34d; }
.pw-loss .pw-count { color: #fca5a5; }

.pairwise-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 18px 14px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.legend-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid rgba(17, 24, 39, 0.1);
}

.legend-swatch.win { background: #bbf7d0; }
.legend-swatch.neutral { background: #fde68a; }
.legend-swatch.loss { background: #fecaca; }
.legend-swatch.na { background: #e5e7eb; }

:root[data-theme="dark"] .elo-bar-wrap {
  background: var(--color-card);
  border-color: var(--color-border);
}

:root[data-theme="dark"] .elo-row:hover .elo-bar-wrap {
  background: var(--color-card-hover);
  border-color: rgba(255, 36, 66, 0.28);
}

:root[data-theme="dark"] .elo-bar-track {
  background: var(--color-surface);
}

:root[data-theme="dark"] .arena-bottom-grid {
  background: transparent;
}

:root[data-theme="dark"] .arena-bottom-grid > *:first-child {
  border-right-color: var(--color-border);
}

:root[data-theme="dark"] .pairwise-badge {
  background: var(--color-soft-red);
}

:root[data-theme="dark"] .pw-col-header,
:root[data-theme="dark"] .pw-row-header {
  background: var(--color-card-muted);
}

:root[data-theme="dark"] .elo-logo-fallback {
  color: var(--color-title);
}

:root[data-theme="dark"] .elo-logo {
  background: transparent;
}

:root[data-theme="dark"] .pw-self {
  background: var(--color-card-hover);
}

:root[data-theme="dark"] .pw-win {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.22);
}

:root[data-theme="dark"] .pw-neutral {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.2);
}

:root[data-theme="dark"] .pw-loss {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.2);
}

:root[data-theme="dark"] .pw-no-data {
  background: var(--color-card-muted);
}

:root[data-theme="dark"] .pw-win .pw-pct { color: #6ee7b7; }
:root[data-theme="dark"] .pw-neutral .pw-pct { color: #fcd34d; }
:root[data-theme="dark"] .pw-loss .pw-pct { color: #fca5a5; }

:root[data-theme="dark"] .pw-win .pw-count { color: #065f46; }
:root[data-theme="dark"] .pw-neutral .pw-count { color: #92400e; }
:root[data-theme="dark"] .pw-loss .pw-count { color: #7f1d1d; }

</style>
