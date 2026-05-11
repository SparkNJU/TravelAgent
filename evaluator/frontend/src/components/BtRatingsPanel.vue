<template>
  <article class="surface bt-panel">
    <div class="section-head">
      <div>
        <h3>BT 对比结果</h3>
        <p class="panel-sub">参考主流评测看板：先看头部模型，再看全量排序与差距。</p>
      </div>
      <div class="inline-actions">
        <button class="ghost" @click="reload">刷新</button>
      </div>
    </div>

    <div class="chip-row sort-chip-row">
      <button
        v-for="opt in sortOptions"
        :key="opt.key"
        class="chip"
        :class="{ active: currentSort === opt.key }"
        @click="switchSort(opt.key)"
      >{{ opt.label }}</button>
    </div>

    <p v-if="notice" class="notice-text">{{ notice }}</p>

    <section v-if="topThree.length" class="podium-grid">
      <article v-for="(m, idx) in topThree" :key="m.ratingId" class="podium-card" :class="`rank-${idx + 1}`">
        <span class="rank-no">#{{ idx + 1 }}</span>
        <strong class="mono">{{ m.modelId || `#${m.modelProfileId}` }}</strong>
        <p>{{ m.displayName || '未设置显示名' }}</p>
        <div class="podium-metric">{{ formatPrimary(m) }}</div>
      </article>
    </section>

    <div v-if="ranked.length" class="table-wrap">
      <table class="task-table detail-table">
        <thead>
          <tr>
            <th>排名</th>
            <th>模型ID</th>
            <th>{{ primaryColLabel }}</th>
            <th>强弱条</th>
            <th>Elo</th>
            <th>胜率</th>
            <th>对比场次</th>
            <th>平均延迟</th>
            <th>平均Token</th>
            <th>完成率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, idx) in ranked" :key="r.ratingId">
            <td>{{ idx + 1 }}</td>
            <td class="mono">{{ r.modelId || '#' + r.modelProfileId }}</td>
            <td class="primary-col">{{ formatPrimary(r) }}</td>
            <td>
              <div class="power-track">
                <span :style="{ width: `${primaryBarWidth(r)}%` }"></span>
              </div>
            </td>
            <td>{{ r.elo != null ? r.elo.toFixed(1) : '-' }}</td>
            <td>{{ formatPercent(r.winRate) }}</td>
            <td>{{ r.nComparisons ?? '-' }}</td>
            <td>{{ r.avgLatencyMs != null ? `${r.avgLatencyMs}ms` : '-' }}</td>
            <td>{{ r.avgTokens ?? '-' }}</td>
            <td>{{ formatPercent(r.completionRate) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="notice-text">
      暂无 BT 排行数据 — 任务可能还没跑完，或当前 run 不是 BT 模式
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { getRunRanked, type ModelRating, type SortBy } from '../api/client';

const props = defineProps<{
  runId: number;
}>();

type SortKey = {
  key: SortBy;
  label: string;
  dimension?: 'OVERALL' | 'EFFECTIVENESS' | 'SAFETY';
};

const sortOptions: SortKey[] = [
  { key: 'elo', label: '总分 Elo', dimension: 'OVERALL' },
  { key: 'winRate', label: '效果胜率', dimension: 'EFFECTIVENESS' },
  { key: 'safetyElo', label: '安全 Elo' },
  { key: 'latency', label: '响应速度' },
  { key: 'tokens', label: 'Token消耗' },
  { key: 'completionRate', label: '完成率' },
];

const currentSort = ref<SortBy>('elo');
const ranked = ref<ModelRating[]>([]);
const notice = ref('');
const primaryColLabel = ref('Elo（总维度）');

const topThree = computed(() => ranked.value.slice(0, 3));

const primaryStats = computed(() => {
  const values = ranked.value
    .map((r) => primaryValue(r))
    .filter((v): v is number => v != null && Number.isFinite(v));
  if (!values.length) return { min: 0, max: 1 };
  return {
    min: Math.min(...values),
    max: Math.max(...values),
  };
});

watch(() => props.runId, () => {
  void reload();
});

onMounted(() => {
  void reload();
});

async function switchSort(key: SortBy): Promise<void> {
  currentSort.value = key;
  await reload();
}

async function reload(): Promise<void> {
  if (!props.runId) {
    ranked.value = [];
    return;
  }
  try {
    const opt = sortOptions.find((o) => o.key === currentSort.value)!;
    const res = await getRunRanked(props.runId, currentSort.value, {
      dimension: opt.dimension as any,
    });
    ranked.value = res.ranked;
    primaryColLabel.value = labelOf(currentSort.value, opt.dimension);
    notice.value = ranked.value.length
      ? `共 ${ranked.value.length} 个模型 · 排序：${opt.label} · order=${res.order}`
      : '该 run 没有 BT 评测数据（确认任务以 BT 模式跑完）';
  } catch (err: any) {
    notice.value = `加载失败: ${err.message || String(err)}`;
    ranked.value = [];
  }
}

function labelOf(sortBy: SortBy, dimension?: string): string {
  switch (sortBy) {
    case 'elo': return `Elo（${dimension || 'OVERALL'}）`;
    case 'winRate': return `胜率（${dimension || 'OVERALL'}）`;
    case 'latency': return '平均延迟 (ms)';
    case 'tokens': return '平均Token消耗';
    case 'completionRate': return '完成率';
    case 'safetyElo': return 'Elo（SAFETY）';
    default: return sortBy;
  }
}

function primaryValue(r: ModelRating): number | null {
  switch (currentSort.value) {
    case 'elo':
    case 'safetyElo':
      return r.elo ?? null;
    case 'winRate':
      return r.winRate ?? null;
    case 'latency':
      return r.avgLatencyMs ?? null;
    case 'tokens':
      return r.avgTokens ?? null;
    case 'completionRate':
      return r.completionRate ?? null;
    default:
      return null;
  }
}

function higherIsBetter(): boolean {
  return currentSort.value !== 'latency' && currentSort.value !== 'tokens';
}

function primaryBarWidth(r: ModelRating): number {
  const value = primaryValue(r);
  if (value == null) return 0;
  const { min, max } = primaryStats.value;
  if (max === min) return 100;
  const ratio = (value - min) / (max - min);
  const normalized = higherIsBetter() ? ratio : 1 - ratio;
  const pct = Math.max(0, Math.min(1, normalized)) * 100;
  const minVisible = 12;
  const widened = Math.max(minVisible, pct);
  return Math.round(Math.min(100, widened) * 10) / 10;
}

function formatPrimary(r: ModelRating): string {
  switch (currentSort.value) {
    case 'elo':
    case 'safetyElo':
      return r.elo != null ? r.elo.toFixed(1) : '-';
    case 'winRate':
      return formatPercent(r.winRate);
    case 'latency':
      return r.avgLatencyMs != null ? `${r.avgLatencyMs}ms` : '-';
    case 'tokens':
      return r.avgTokens != null ? String(r.avgTokens) : '-';
    case 'completionRate':
      return formatPercent(r.completionRate);
    default:
      return '-';
  }
}

function formatPercent(v: number | null | undefined): string {
  if (v == null) return '-';
  return `${(v * 100).toFixed(1)}%`;
}
</script>

<style scoped>
.bt-panel {
  display: grid;
  gap: 12px;
}

.panel-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.sort-chip-row {
  margin-bottom: 4px;
}

.sort-chip-row .chip {
  cursor: pointer;
  user-select: none;
}

.sort-chip-row .chip.active {
  background: var(--brand);
  color: #ffffff;
  border-color: var(--brand);
}

.podium-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.podium-card {
  border: 1px solid #f3d4d6;
  border-radius: 12px;
  padding: 12px;
  background: #fff8f8;
  display: grid;
  gap: 6px;
}

.podium-card.rank-1 {
  background: linear-gradient(180deg, #fff3f4, #fffafa);
  border-color: #fca5a5;
}

.rank-no {
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #991b1b;
}

.mono {
  font-family: monospace;
}

.podium-card p {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.podium-metric {
  font-size: 20px;
  font-weight: 800;
  color: #7f1d1d;
}

.primary-col {
  font-weight: 700;
  color: var(--brand-press);
}

.power-track {
  width: 120px;
  height: 8px;
  border-radius: 999px;
  background: #fde3e4;
  overflow: hidden;
}

.power-track > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #fb7185, #dc2626);
}

@media (max-width: 980px) {
  .podium-grid {
    grid-template-columns: 1fr;
  }
}
</style>
